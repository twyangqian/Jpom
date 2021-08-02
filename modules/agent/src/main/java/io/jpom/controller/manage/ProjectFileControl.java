package io.jpom.controller.manage;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.HttpUtil;
import cn.jiangzeyin.common.DefaultSystemLog;
import cn.jiangzeyin.common.JsonMessage;
import cn.jiangzeyin.controller.multipart.MultipartFileBuilder;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.jpom.common.BaseAgentController;
import io.jpom.common.commander.AbstractProjectCommander;
import io.jpom.model.AfterOpt;
import io.jpom.model.BaseEnum;
import io.jpom.model.data.AgentWhitelist;
import io.jpom.model.data.ProjectInfoModel;
import io.jpom.service.WhitelistDirectoryService;
import io.jpom.service.manage.ConsoleService;
import io.jpom.socket.ConsoleCommandOp;
import io.jpom.system.AgentConfigBean;
import io.jpom.util.CompressionFileUtil;
import io.jpom.util.FileUtils;
import io.jpom.util.StringUtil;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 项目文件管理
 *
 * @author jiangzeyin
 * @date 2019/4/17
 */
@RestController
@RequestMapping(value = "/manage/file/")
public class ProjectFileControl extends BaseAgentController {

	@Resource
	private ConsoleService consoleService;

	@Resource
	private WhitelistDirectoryService whitelistDirectoryService;

	@RequestMapping(value = "getFileList", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String getFileList(String id, String path) {
		// 查询项目路径
		ProjectInfoModel pim = projectInfoService.getItem(id);
		if (pim == null) {
			return JsonMessage.getString(500, "查询失败：项目不存在");
		}
		String lib = pim.allLib();
		File fileDir = FileUtil.file(lib, StrUtil.emptyToDefault(path, FileUtil.FILE_SEPARATOR));
		if (!fileDir.exists()) {
			return JsonMessage.getString(500, "目录不存在");
		}
		File[] filesAll = fileDir.listFiles();
		if (filesAll == null) {
			return JsonMessage.getString(500, "目录是空");
		}
		JSONArray arrayFile = FileUtils.parseInfo(filesAll, false, lib);
		AgentWhitelist whitelist = whitelistDirectoryService.getWhitelist();
		List<String> allowEditSuffix = whitelist.getAllowEditSuffix();
		if (CollUtil.isNotEmpty(allowEditSuffix)) {
			for (Object o : arrayFile) {
				JSONObject jsonObject = (JSONObject) o;
				String filename = jsonObject.getString("filename");
				jsonObject.put("textFileEdit", this.checkSilentFileSuffix(filename));
			}
		}
		return JsonMessage.getString(200, "查询成功", arrayFile);
	}


	@RequestMapping(value = "upload", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String upload() throws Exception {
		ProjectInfoModel pim = getProjectInfoModel();
		MultipartFileBuilder multipartFileBuilder = createMultipart()
				.addFieldName("file");
		// 压缩文件
		String type = getParameter("type");
		// 是否清空
		String clearType = getParameter("clearType");
		String levelName = getParameter("levelName");
		File lib;
		if (StrUtil.isEmpty(levelName)) {
			lib = new File(pim.allLib());
		} else {
			lib = FileUtil.file(pim.allLib(), levelName);
		}

		if ("unzip".equals(type)) {
			multipartFileBuilder.setFileExt(StringUtil.PACKAGE_EXT);
			multipartFileBuilder.setSavePath(AgentConfigBean.getInstance().getTempPathName());
			String path = multipartFileBuilder.save();
			// 判断是否需要清空
			if ("clear".equalsIgnoreCase(clearType)) {
				if (!FileUtil.clean(lib)) {
					FileUtil.del(lib.toPath());
					//return JsonMessage.getString(500, "清除旧lib失败");
				}
			}
			// 解压
			File file = new File(path);
			try {
				CompressionFileUtil.unCompress(file, lib);
			} finally {
				if (!file.delete()) {
					DefaultSystemLog.getLog().error("删除文件失败：" + file.getPath());
				}
			}
		} else {
			multipartFileBuilder.setSavePath(FileUtil.getAbsolutePath(lib))
					.setUseOriginalFilename(true);
			// 保存
			multipartFileBuilder.save();
		}
		// 修改使用状态
		pim.setUseLibDesc("upload");
		projectInfoService.updateItem(pim);
		//
		String after = getParameter("after");
		if (StrUtil.isNotEmpty(after)) {
			//
			List<ProjectInfoModel.JavaCopyItem> javaCopyItemList = pim.getJavaCopyItemList();
			//
			AfterOpt afterOpt = BaseEnum.getEnum(AfterOpt.class, Convert.toInt(after, AfterOpt.No.getCode()));
			if ("restart".equalsIgnoreCase(after) || afterOpt == AfterOpt.Restart) {
				String result = consoleService.execCommand(ConsoleCommandOp.restart, pim, null);
				// 自动处理副本集
				if (javaCopyItemList != null) {
					ThreadUtil.execute(() -> javaCopyItemList.forEach(javaCopyItem -> {
						try {
							consoleService.execCommand(ConsoleCommandOp.restart, pim, javaCopyItem);
						} catch (Exception e) {
							DefaultSystemLog.getLog().error("重启副本集失败", e);
						}
					}));
				}
				return JsonMessage.getString(200, "上传成功并重启：" + result);
			}
			if (afterOpt == AfterOpt.Order_Restart || afterOpt == AfterOpt.Order_Must_Restart) {
				boolean restart = this.restart(pim, null, afterOpt);
				if (javaCopyItemList != null) {
					ThreadUtil.execute(() -> {
						// 副本
						for (ProjectInfoModel.JavaCopyItem javaCopyItem : javaCopyItemList) {
							if (!this.restart(pim, javaCopyItem, afterOpt)) {
								return;
							}
							// 休眠30秒 等待之前项目正常启动
							try {
								TimeUnit.SECONDS.sleep(30);
							} catch (InterruptedException ignored) {
							}
						}
					});
				}
			}
		}
		return JsonMessage.getString(200, "上传成功");
	}


	private boolean restart(ProjectInfoModel projectInfoModel, ProjectInfoModel.JavaCopyItem javaCopyItem, AfterOpt afterOpt) {
		try {
			String result = consoleService.execCommand(ConsoleCommandOp.restart, projectInfoModel, javaCopyItem);
			int pid = AbstractProjectCommander.parsePid(result);
			if (pid <= 0) {
				// 完整重启，不再继续剩余的节点项目
				return afterOpt != AfterOpt.Order_Must_Restart;
			}
			return true;
		} catch (Exception e) {
			DefaultSystemLog.getLog().error("重复失败", e);
			// 完整重启，不再继续剩余的节点项目
			return afterOpt != AfterOpt.Order_Must_Restart;
		}
	}

	@RequestMapping(value = "deleteFile", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public String deleteFile(String filename, String type, String levelName) {
		ProjectInfoModel pim = getProjectInfoModel();
		if ("clear".equalsIgnoreCase(type)) {
			// 清空文件
			File file = new File(pim.allLib());
			if (FileUtil.clean(file)) {
				return JsonMessage.getString(200, "清除成功");
			}
			if (pim.tryGetStatus()) {
				return JsonMessage.getString(501, "文件被占用，请先停止项目");
			}
			return JsonMessage.getString(500, "删除失败：" + file.getAbsolutePath());
		} else {
			// 删除文件
			String fileName = pathSafe(filename);
			if (StrUtil.isEmpty(fileName)) {
				return JsonMessage.getString(405, "非法操作");
			}
			File file;
			if (StrUtil.isEmpty(levelName)) {
				file = FileUtil.file(pim.allLib(), fileName);
			} else {
				file = FileUtil.file(pim.allLib(), levelName, fileName);
			}
			if (file.exists()) {
				if (FileUtil.del(file)) {
					return JsonMessage.getString(200, "删除成功");
				}
			} else {
				return JsonMessage.getString(404, "文件不存在");
			}
			return JsonMessage.getString(500, "删除失败");
		}
	}

	/**
	 * 静默判断是否可以编辑对应的文件
	 *
	 * @param filename 文件名
	 * @return true 可以编辑
	 */
	private boolean checkSilentFileSuffix(String filename) {
		AgentWhitelist whitelist = whitelistDirectoryService.getWhitelist();
		if (whitelist == null) {
			return false;
		}
		List<String> allowEditSuffix = whitelist.getAllowEditSuffix();
		if (CollUtil.isEmpty(allowEditSuffix)) {
			return false;
		}
		Charset charset = this.parserFileSuffixMap(allowEditSuffix, filename);
		return charset != null;
	}

	private Charset parserFileSuffixMap(List<String> allowEditSuffix, String filename) {
		Map<String, Charset> map = CollStreamUtil.toMap(allowEditSuffix, s -> {
			List<String> split = StrUtil.split(s, StrUtil.AT);
			return CollUtil.getFirst(split);
		}, s -> {
			List<String> split = StrUtil.split(s, StrUtil.AT);
			if (split.size() > 1) {
				String last = CollUtil.getLast(split);
				return CharsetUtil.charset(last);
			} else {
				return CharsetUtil.defaultCharset();
			}
		});
		Set<Map.Entry<String, Charset>> entries = map.entrySet();
		for (Map.Entry<String, Charset> entry : entries) {
			if (StrUtil.endWithIgnoreCase(filename, StrUtil.DOT + entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * 获取文件可以编辑的 文件编码格式
	 *
	 * @param filename 文件名
	 * @return charset 不能编辑情况会抛出异常
	 */
	private Charset checkFileSuffix(String filename) {
		AgentWhitelist whitelist = whitelistDirectoryService.getWhitelist();
		List<String> allowEditSuffix = whitelist.getAllowEditSuffix();
		Assert.notEmpty(allowEditSuffix, "没有配置可允许编辑的后缀");
		Charset charset = this.parserFileSuffixMap(allowEditSuffix, filename);
		Assert.notNull(charset, "不允许编辑的文件后缀");
		return charset;
	}


	/**
	 * 读取文件内容 （只能处理文本文件）
	 *
	 * @param filePath 相对项目文件的文件夹
	 * @param filename 读取的文件名
	 * @return json
	 */
	@PostMapping(value = "read_file", produces = MediaType.APPLICATION_JSON_VALUE)
	public String readFile(String filePath, String filename) {
		ProjectInfoModel pim = getProjectInfoModel();
		filePath = StrUtil.emptyToDefault(filePath, File.separator);
		// 判断文件后缀
		Charset charset = this.checkFileSuffix(filename);
		File file = FileUtil.file(pim.allLib(), filePath, filename);
		String ymlString = FileUtil.readString(file, charset);
		return JsonMessage.getString(200, "", ymlString);
	}

	/**
	 * 保存文件内容 （只能处理文本文件）
	 *
	 * @param filePath 相对项目文件的文件夹
	 * @param filename 读取的文件名
	 * @param fileText 文件内容
	 * @return json
	 */
	@PostMapping(value = "update_config_file", produces = MediaType.APPLICATION_JSON_VALUE)
	public String updateConfigFile(String filePath, String filename, String fileText) {
		ProjectInfoModel pim = getProjectInfoModel();
		filePath = StrUtil.emptyToDefault(filePath, File.separator);
		// 判断文件后缀
		Charset charset = this.checkFileSuffix(filename);
		FileUtil.writeString(fileText, FileUtil.file(pim.allLib(), filePath, filename), charset);
		return JsonMessage.getString(200, "文件写入成功");
	}


	/**
	 * 将执行文件下载到客户端 本地
	 *
	 * @param id        项目id
	 * @param filename  文件名
	 * @param levelName 文件夹名
	 * @return 正常情况返回文件流，非正在返回 text plan
	 */
	@GetMapping(value = "download", produces = MediaType.APPLICATION_JSON_VALUE)
	public String download(String id, String filename, String levelName) {
		String safeFileName = pathSafe(filename);
		if (StrUtil.isEmpty(safeFileName)) {
			return JsonMessage.getString(405, "非法操作");
		}
		try {
			ProjectInfoModel pim = projectInfoService.getItem(id);
			File file = FileUtil.file(pim.allLib(), StrUtil.emptyToDefault(levelName, FileUtil.FILE_SEPARATOR), filename);
			if (file.isDirectory()) {
				return "暂不支持下载文件夹";
			}
			ServletUtil.write(getResponse(), file);
		} catch (Exception e) {
			DefaultSystemLog.getLog().error("下载文件异常", e);
		}
		return "下载失败。请刷新页面后重试";
	}

	/**
	 * 下载远程文件
	 *
	 * @param id        项目id
	 * @param url       远程 url 地址
	 * @param levelName 保存的文件夹
	 * @param unzip     是否为压缩包、true 将自动解压
	 * @return json
	 */
	@PostMapping(value = "remote_download", produces = MediaType.APPLICATION_JSON_VALUE)
	public String remoteDownload(String id, String url, String levelName, String unzip) {
		if (StrUtil.isEmpty(url)) {
			return JsonMessage.getString(405, "请输入正确的远程地址");
		}
		AgentWhitelist whitelist = whitelistDirectoryService.getWhitelist();
		Set<String> allowRemoteDownloadHost = whitelist.getAllowRemoteDownloadHost();
		Assert.state(CollUtil.isNotEmpty(allowRemoteDownloadHost), "还没有配置运行的远程地址");
		List<String> collect = allowRemoteDownloadHost.stream().filter(s -> StrUtil.startWith(url, s)).collect(Collectors.toList());
		Assert.state(CollUtil.isNotEmpty(collect), "不允许下载当前地址的文件");
		try {
			ProjectInfoModel pim = projectInfoService.getItem(id);
			File file = FileUtil.file(pim.allLib(), StrUtil.emptyToDefault(levelName, FileUtil.FILE_SEPARATOR));
			File downloadFile = HttpUtil.downloadFileFromUrl(url, file);
			if (BooleanUtil.toBoolean(unzip)) {
				// 需要解压文件
				try {
					CompressionFileUtil.unCompress(file, downloadFile);
				} finally {
					if (!FileUtil.del(downloadFile)) {
						DefaultSystemLog.getLog().error("删除文件失败：" + file.getPath());
					}
				}
			}
			return JsonMessage.getString(200, "下载成功文件大小：" + FileUtil.readableFileSize(downloadFile));
		} catch (Exception e) {
			DefaultSystemLog.getLog().error("下载远程文件异常", e);
			return JsonMessage.getString(500, "下载远程文件失败:" + e.getMessage());
		}
	}

}
