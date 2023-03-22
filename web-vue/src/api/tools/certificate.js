import axios from "@/api/config";
import { loadRouterBase } from "@/api/config";
// 导入证书
export function certificateImportFile(formData) {
  return axios({
    url: "/certificate/import-file",
    headers: {
      "Content-Type": "multipart/form-data;charset=UTF-8",
    },
    method: "post",

    data: formData,
  });
}

/**
 * cert 列表
 */
export function certList(params) {
  return axios({
    url: "/certificate/list",
    method: "post",
    data: params,
  });
}

/**
 * 删除 cert
 * @param {
 *
 * } params
 */
export function deleteCert(params) {
  return axios({
    url: "/certificate/del",
    method: "get",
    params: params,
  });
}

/**
 * 导出 cert
 * @param {
 *
 * } params
 */
export function downloadCert(params) {
  return loadRouterBase("/certificate/export", params);
}