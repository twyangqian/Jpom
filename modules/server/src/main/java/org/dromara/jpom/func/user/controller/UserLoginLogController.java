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
package org.dromara.jpom.func.user.controller;

import cn.keepbx.jpom.IJsonMessage;
import cn.keepbx.jpom.model.JsonMessage;
import org.dromara.jpom.common.BaseServerController;
import org.dromara.jpom.func.user.model.UserLoginLogModel;
import org.dromara.jpom.func.user.server.UserLoginLogServer;
import org.dromara.jpom.model.PageResultDto;
import org.dromara.jpom.permission.ClassFeature;
import org.dromara.jpom.permission.Feature;
import org.dromara.jpom.permission.MethodFeature;
import org.dromara.jpom.permission.SystemPermission;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author bwcx_jzy
 * @since 2023/3/9
 */
@RestController
@RequestMapping(value = "/user/login-log")
@Feature(cls = ClassFeature.USER_LOGIN_LOG)
@SystemPermission
public class UserLoginLogController extends BaseServerController {

    private final UserLoginLogServer userLoginLogServer;

    public UserLoginLogController(UserLoginLogServer userLoginLogServer) {
        this.userLoginLogServer = userLoginLogServer;
    }

    /**
     * 登录日志列表
     *
     * @return json
     */
    @RequestMapping(value = "list-data", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public IJsonMessage<PageResultDto<UserLoginLogModel>> listData(HttpServletRequest request) {
        PageResultDto<UserLoginLogModel> pageResult = userLoginLogServer.listPage(request);
        return JsonMessage.success("", pageResult);
    }
}
