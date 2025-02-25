/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Code Technology Studio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.dromara.jpom.controller.monitor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.RegexPool;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import com.alibaba.fastjson2.JSONArray;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.common.validator.ValidatorItem;
import org.dromara.jpom.common.validator.ValidatorRule;
import org.dromara.jpom.model.PageResultDto;
import org.dromara.jpom.model.data.MonitorModel;
import org.dromara.jpom.model.user.UserModel;
import org.dromara.jpom.permission.ClassFeature;
import org.dromara.jpom.permission.Feature;
import org.dromara.jpom.permission.MethodFeature;
import org.dromara.jpom.service.dblog.DbMonitorNotifyLogService;
import org.dromara.jpom.service.monitor.MonitorService;
import org.dromara.jpom.service.node.ProjectInfoCacheService;
import org.dromara.jpom.service.user.UserService;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;

/**
 * 监控列表
 *
 * @author bwcx_jzy
 * @since 2019/6/15
 */
@RestController
@RequestMapping(value = "/monitor")
@Feature(cls = ClassFeature.MONITOR)
public class MonitorListController extends BaseServerController {

    private final MonitorService monitorService;
    private final DbMonitorNotifyLogService dbMonitorNotifyLogService;
    private final UserService userService;
    private final ProjectInfoCacheService projectInfoCacheService;

    public MonitorListController(MonitorService monitorService,
                                 DbMonitorNotifyLogService dbMonitorNotifyLogService,
                                 UserService userService,
                                 ProjectInfoCacheService projectInfoCacheService) {
        this.monitorService = monitorService;
        this.dbMonitorNotifyLogService = dbMonitorNotifyLogService;
        this.userService = userService;
        this.projectInfoCacheService = projectInfoCacheService;
    }

    /**
     * 展示监控列表
     *
     * @return json
     */
    @RequestMapping(value = "getMonitorList", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<PageResultDto<MonitorModel>> getMonitorList() {
        PageResultDto<MonitorModel> pageResultDto = monitorService.listPage(getRequest());
        return JsonMessage.success("", pageResultDto);
    }

    /**
     * 删除列表
     *
     * @param id id
     * @return json
     */
    @RequestMapping(value = "deleteMonitor", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.DEL)
    public IJsonMessage<Object> deleteMonitor(@ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "删除失败") String id) throws SQLException {
        //
        HttpServletRequest request = getRequest();
        int delByKey = monitorService.delByKey(id, request);
        if (delByKey > 0) {
            // 删除日志
            dbMonitorNotifyLogService.delByWorkspace(request, entity -> entity.set("monitorId", id));
        }
        return JsonMessage.success("删除成功");
    }


    /**
     * 增加或修改监控
     *
     * @param id         id
     * @param name       name
     * @param notifyUser user
     * @return json
     */
    @RequestMapping(value = "updateMonitor", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EDIT)
    public IJsonMessage<Object> updateMonitor(String id,
                                             @ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "监控名称不能为空") String name,
                                             @ValidatorItem(msg = "请配置监控周期") String execCron,
                                             String notifyUser, String webhook) {
        String status = getParameter("status");
        String autoRestart = getParameter("autoRestart");

        JSONArray jsonArray = JSONArray.parseArray(notifyUser);
//		List<String> notifyUsers = jsonArray.toJavaList(String.class);
        List<String> notifyUserList = jsonArray.toJavaList(String.class);
        if (CollUtil.isNotEmpty(notifyUserList)) {
            for (String userId : notifyUserList) {
                Assert.state(userService.exists(new UserModel(userId)), "没有对应的用户：" + userId);
            }
        }
        String projects = getParameter("projects");
        JSONArray projectsArray = JSONArray.parseArray(projects);
        List<MonitorModel.NodeProject> nodeProjects = projectsArray.toJavaList(MonitorModel.NodeProject.class);
        Assert.notEmpty(nodeProjects, "请至少选择一个节点");
        for (MonitorModel.NodeProject nodeProject : nodeProjects) {
            Assert.notEmpty(nodeProject.getProjects(), "请至少选择一个项目");
            for (String project : nodeProject.getProjects()) {
                boolean exists = projectInfoCacheService.exists(nodeProject.getNode(), project);
                Assert.state(exists, "没有对应的项目：" + project);
            }
        }
        //
        // 设置参数
        if (StrUtil.isNotEmpty(webhook)) {
            Validator.validateMatchRegex(RegexPool.URL_HTTP, webhook, "WebHooks 地址不合法");
        }
        Assert.state(CollUtil.isNotEmpty(notifyUserList) || StrUtil.isNotEmpty(webhook), "请选择一位报警联系人或者填写webhook");

        boolean start = "on".equalsIgnoreCase(status);
        MonitorModel monitorModel = monitorService.getByKey(id);
        if (monitorModel == null) {
            monitorModel = new MonitorModel();
        }
        monitorModel.setAutoRestart("on".equalsIgnoreCase(autoRestart));
        monitorModel.setExecCron(this.checkCron(execCron));
        monitorModel.projects(nodeProjects);
        monitorModel.setStatus(start);
        monitorModel.setWebhook(webhook);
        monitorModel.notifyUser(notifyUserList);
        monitorModel.setName(name);

        if (StrUtil.isEmpty(id)) {
            //添加监控
            monitorService.insert(monitorModel);
            return JsonMessage.success("添加成功");
        }
        HttpServletRequest request = getRequest();
        monitorService.updateById(monitorModel, request);
        return JsonMessage.success("修改成功");
    }
}
