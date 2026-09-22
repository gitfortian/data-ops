/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  jakarta.servlet.http.HttpServletRequest
 *  org.springframework.util.StringUtils
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.common.dto.user.UserPasswordResetDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.common.vo.user.UserVO;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.service.impl.UserAdministrationService;
import io.yak.framework.security.util.HttpRequestUtil;
import io.yak.framework.security.util.JsonUtils;
import io.yak.framework.security.web.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u7528\u6237\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/user"})
@RequiresPermission(value="security:user:read")
@YakPermission(code="security:user:read", name="\u67e5\u770b\u7528\u6237\u7ba1\u7406", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u67e5\u770b\u7528\u6237\u5217\u8868\u3001\u8be6\u60c5\u53ca\u89d2\u8272\u5206\u914d\u4fe1\u606f")
public class UserController {
    private final UserService userService;
    private final UserAdministrationService userAdministrationService;

    public UserController(UserService userService, UserAdministrationService userAdministrationService) {
        this.userService = userService;
        this.userAdministrationService = userAdministrationService;
    }

    @Operation(summary="\u6821\u9a8c\u7528\u6237\u5b57\u6bb5\u662f\u5426\u53ef\u7528")
    @GetMapping(value={"/{type}/{value}/check"})
    public Result<Void> check(@PathVariable Integer type, @PathVariable String value) {
        return this.userService.check(type, value);
    }

    @Operation(summary="\u6839\u636e\u7528\u6237 ID \u96c6\u5408\u6279\u91cf\u67e5\u8be2\u7528\u6237\u8be6\u60c5")
    @GetMapping
    public Result<List<UserVO>> detailList(@RequestParam(value="ids") String ids) {
        try {
            List<Long> userIds = JsonUtils.toList(ids, Long.class);
            return this.userService.getUserDetailsByUserIds(userIds);
        }
        catch (Exception exception) {
            throw new YakSecurityException(ResultCode.PARAM_NOT_VALID, (Throwable)exception);
        }
    }

    @Operation(summary="\u6839\u636e\u7528\u6237 ID \u67e5\u8be2\u7528\u6237\u8be6\u60c5")
    @GetMapping(value={"/{id}"})
    public Result<UserVO> detail(@PathVariable(value="id") Long userId) {
        return Result.success((Object)this.userService.getUserDetailByUserId(userId));
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u7528\u6237")
    @PostMapping(value={"/page"})
    public Result<PagingData<UserVO>> page(@RequestBody UserQueryDTO queryDTO) {
        PagingData<UserVO> pagingData = this.userService.getUserPage(queryDTO);
        return Result.success(pagingData);
    }

    @Operation(summary="\u6839\u636e\u90e8\u95e8 ID \u67e5\u8be2\u7528\u6237")
    @GetMapping(value={"/list/dept/{deptId}"})
    public Result<List<UserBriefVO>> listByDeptId(@PathVariable Long deptId) {
        return Result.success(this.userService.getUserBriefListByDeptId(deptId));
    }

    @Operation(summary="\u6839\u636e\u89d2\u8272 ID \u67e5\u8be2\u7528\u6237")
    @GetMapping(value={"/list/role/{roleId}"})
    public Result<List<UserBriefVO>> listByRoleId(@PathVariable Long roleId) {
        return Result.success(this.userService.getUserBriefListByRoleId(roleId));
    }

    @Operation(summary="\u67e5\u8be2\u7528\u6237\u7684\u89d2\u8272\u5206\u914d\u4fe1\u606f")
    @GetMapping(value={"/assign/list/{userId}"})
    public Result<List<AssignInfoVO>> assignList(@PathVariable Long userId) {
        return Result.success(this.userService.getAssignInfoListByUserId(userId));
    }

    @Operation(summary="\u6839\u636e\u7528\u6237\u540d\u6216\u771f\u5b9e\u59d3\u540d\u6a21\u7cca\u67e5\u8be2\u7528\u6237")
    @GetMapping(value={"/list/{keyword}"})
    public Result<List<UserBriefVO>> listByName(@PathVariable String keyword) {
        return Result.success(this.userService.searchUserBriefList(keyword));
    }

    @Operation(summary="\u65b0\u589e\u7528\u6237")
    @PutMapping(value={"/add"})
    @RequiresPermission(value="security:user:create")
    @YakPermission(code="security:user:create", name="\u65b0\u589e\u7528\u6237", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u521b\u5efa\u7cfb\u7edf\u7528\u6237")
    public Result<Void> add(HttpServletRequest request, @RequestBody UserDTO userDTO) {
        return this.userService.addUser(userDTO, HttpRequestUtil.getOperator(request));
    }

    @Operation(summary="\u7f16\u8f91\u7528\u6237")
    @PostMapping(value={"/edit"})
    @RequiresPermission(value="security:user:update")
    @YakPermission(code="security:user:update", name="\u7f16\u8f91\u7528\u6237", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u7f16\u8f91\u7528\u6237\u57fa\u672c\u8d44\u6599")
    public Result<Void> edit(HttpServletRequest request, @RequestBody UserDTO userDTO) {
        String operator = HttpRequestUtil.getOperator(request);
        Result<Void> result = this.userService.editUser(userDTO, operator);
        if (!result.failed() && userDTO != null && StringUtils.hasText((String)userDTO.getPw())) {
            this.userAdministrationService.invalidateSessionsAfterPasswordChange(userDTO.getUserName(), operator);
        }
        return result;
    }

    @Operation(summary="\u7ba1\u7406\u5458\u91cd\u7f6e\u7528\u6237\u5bc6\u7801")
    @PutMapping(value={"/{id}/password"})
    @RequiresPermission(value="security:user:reset-password")
    @YakPermission(code="security:user:reset-password", name="\u91cd\u7f6e\u7528\u6237\u5bc6\u7801", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u7ba1\u7406\u5458\u91cd\u7f6e\u6307\u5b9a\u7528\u6237\u5bc6\u7801")
    public Result<Void> resetPassword(HttpServletRequest request, @PathVariable(value="id") Long userId, @RequestBody UserPasswordResetDTO resetDTO) {
        this.userAdministrationService.resetPassword(userId, resetDTO, HttpRequestUtil.getOperator(request));
        return Result.success();
    }

    @Operation(summary="\u7ba1\u7406\u5458\u5f3a\u5236\u4e0b\u7ebf\u7528\u6237")
    @PostMapping(value={"/{id}/logout"})
    @RequiresPermission(value="security:user:update")
    public Result<Void> forceLogout(HttpServletRequest request, @PathVariable(value="id") Long userId) {
        this.userAdministrationService.forceLogout(userId, HttpRequestUtil.getOperator(request));
        return Result.success();
    }

    @Operation(summary="\u6839\u636e\u7528\u6237 ID \u5220\u9664\u7528\u6237")
    @DeleteMapping(value={"/{id}"})
    @RequiresPermission(value="security:user:delete")
    @YakPermission(code="security:user:delete", name="\u5220\u9664\u7528\u6237", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u5220\u9664\u7cfb\u7edf\u7528\u6237\u5e76\u6e05\u7406\u5173\u8054\u6388\u6743")
    public Result<Void> delete(HttpServletRequest request, @PathVariable(value="id") Long userId) {
        this.userAdministrationService.validateDelete(userId, HttpRequestUtil.getOperatorId(request), HttpRequestUtil.getOperator(request));
        return this.userService.deleteByUserId(userId);
    }
}

