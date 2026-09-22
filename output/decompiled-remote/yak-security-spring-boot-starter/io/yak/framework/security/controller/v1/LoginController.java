/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.Result
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  jakarta.validation.Valid
 *  org.springframework.beans.factory.ObjectProvider
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.account.AccountLoginDTO;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.context.CurrentUser;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.LoginService;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.CurrentUserProjectResolver;
import io.yak.framework.security.service.impl.UserMenuGrantService;
import io.yak.framework.security.web.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u8d26\u6237\u8ba4\u8bc1\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/account"})
public class LoginController {
    private final LoginService loginService;
    private final UserService userService;
    private final RoleService roleService;
    private final CurrentUser currentUser;
    private final ObjectProvider<UserMenuGrantService> userMenuGrantServiceProvider;
    private final CurrentUserProjectResolver currentUserProjectResolver;

    public LoginController(LoginService loginService, UserService userService, RoleService roleService, CurrentUser currentUser, ObjectProvider<UserMenuGrantService> userMenuGrantServiceProvider, CurrentUserProjectResolver currentUserProjectResolver) {
        this.loginService = loginService;
        this.userService = userService;
        this.roleService = roleService;
        this.currentUser = currentUser;
        this.userMenuGrantServiceProvider = userMenuGrantServiceProvider;
        this.currentUserProjectResolver = currentUserProjectResolver;
    }

    @Operation(summary="\u7528\u6237\u767b\u5f55")
    @PostMapping(value={"/login"})
    @PublicEndpoint
    public Result<UserBriefVO> login(HttpServletRequest request, HttpServletResponse response, @Valid @RequestBody AccountLoginDTO loginDTO) {
        UserBriefVO currentUser = this.loginService.verifyLogin(loginDTO, request, response);
        return Result.success((Object)currentUser);
    }

    @Operation(summary="\u83b7\u53d6\u5f53\u524d\u767b\u5f55\u7528\u6237")
    @GetMapping(value={"/current"})
    public Result<CurrentUserVO> current() {
        if (!this.currentUser.isAuthenticated()) {
            throw new YakSecurityException(ResultCode.USER_NOT_LOGIN);
        }
        UserBriefVO brief = this.userService.getUserBriefByUsername(this.currentUser.getUsername());
        if (brief == null) {
            throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        }
        CurrentUserVO user = this.toCurrentUser(brief);
        user.setRoleList(this.roleService.getRoleBriefListByUserId(user.getId()));
        if (user.getRoleList() == null) {
            user.setRoleList(new ArrayList<RoleBriefVO>());
        }
        user.setPermissionCodes(new ArrayList<String>(this.currentUser.getPermissionCodes()));
        user.setMenuCodes(new ArrayList<String>(this.currentUser.getMenuCodes()));
        user.setProjectList(this.currentUserProjectResolver.resolve(user, this.currentUser.getProjectIds()));
        return Result.success((Object)user);
    }

    @Operation(summary="\u7528\u6237\u9000\u51fa\u767b\u5f55")
    @PostMapping(value={"/logout"})
    public Result<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        return this.loginService.logout(request, response);
    }

    private CurrentUserVO toCurrentUser(UserBriefVO brief) {
        CurrentUserVO user = new CurrentUserVO();
        user.setId(brief.getId());
        user.setUserName(brief.getUserName());
        user.setRealName(brief.getRealName());
        user.setDeptId(brief.getDeptId());
        user.setPhone(brief.getPhone());
        user.setEmail(brief.getEmail());
        return user;
    }
}

