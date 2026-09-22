/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  jakarta.servlet.http.HttpServletRequest
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.role.RoleAssignDTO;
import io.yak.framework.security.common.dto.role.RoleQueryDTO;
import io.yak.framework.security.common.dto.role.RoleSaveDTO;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.role.RoleBriefVO;
import io.yak.framework.security.common.vo.role.RoleDeleteCheckVO;
import io.yak.framework.security.common.vo.role.RoleVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.RoleService;
import io.yak.framework.security.util.HttpRequestUtil;
import io.yak.framework.security.web.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u89d2\u8272\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/role"})
@RequiresPermission(value="security:role:read")
@YakPermission(code="security:role:read", name="\u67e5\u770b\u89d2\u8272\u7ba1\u7406", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u67e5\u770b\u89d2\u8272\u5217\u8868\u3001\u8be6\u60c5\u53ca\u7528\u6237\u5206\u914d\u4fe1\u606f")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary="\u6839\u636e\u89d2\u8272 ID \u67e5\u8be2\u89d2\u8272\u8be6\u60c5")
    @GetMapping(value={"/{id}"})
    public Result<RoleVO> detail(@PathVariable(value="id") Long roleId) {
        return Result.success((Object)this.roleService.getRoleDetailByRoleId(roleId));
    }

    @Operation(summary="\u66f4\u65b0\u89d2\u8272")
    @PutMapping
    @RequiresPermission(value="security:role:update")
    @YakPermission(code="security:role:update", name="\u7f16\u8f91\u89d2\u8272", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u7f16\u8f91\u89d2\u8272\u8d44\u6599\u53ca\u89d2\u8272\u6743\u9650")
    public Result<Void> update(HttpServletRequest request, @RequestBody RoleSaveDTO roleSaveDTO) {
        this.roleService.updateRole(roleSaveDTO, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u521b\u5efa\u89d2\u8272")
    @PostMapping
    @RequiresPermission(value="security:role:create")
    @YakPermission(code="security:role:create", name="\u65b0\u589e\u89d2\u8272", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u521b\u5efa\u89d2\u8272\u5e76\u914d\u7f6e\u89d2\u8272\u6743\u9650")
    public Result<Void> create(HttpServletRequest request, @RequestBody RoleSaveDTO roleSaveDTO) {
        this.roleService.createRole(roleSaveDTO, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u6267\u884c\u89d2\u8272\u5220\u9664\u524d\u6821\u9a8c")
    @DeleteMapping(value={"/delete/check/{id}"})
    @RequiresPermission(value="security:role:delete")
    public Result<RoleDeleteCheckVO> check(@PathVariable(value="id") Long roleId) {
        return Result.success((Object)this.roleService.checkBeforeDelete(roleId));
    }

    @Operation(summary="\u4ece\u89d2\u8272\u4e2d\u5220\u9664\u7528\u6237")
    @DeleteMapping(value={"/{id}/user/{userId}"})
    @RequiresPermission(value="security:role:assign")
    public Result<Void> deleteUser(HttpServletRequest request, @PathVariable(value="id") Long roleId, @PathVariable Long userId) {
        this.roleService.deleteUserFromRole(roleId, userId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u6839\u636e\u89d2\u8272 ID \u5220\u9664\u89d2\u8272")
    @DeleteMapping(value={"/{id}"})
    @RequiresPermission(value="security:role:delete")
    @YakPermission(code="security:role:delete", name="\u5220\u9664\u89d2\u8272", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u5220\u9664\u89d2\u8272\u5e76\u89e3\u9664\u76f8\u5173\u6388\u6743")
    public Result<Void> delete(HttpServletRequest request, @PathVariable(value="id") Long roleId) {
        this.roleService.deleteRoleByRoleId(roleId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u89d2\u8272")
    @PostMapping(value={"/page"})
    public Result<PagingData<RoleVO>> page(@RequestBody RoleQueryDTO queryDTO) {
        PagingData<RoleVO> pagingData = this.roleService.getRolePage(queryDTO);
        return Result.success(pagingData);
    }

    @Operation(summary="\u5206\u914d\u89d2\u8272\u6216\u4e3a\u89d2\u8272\u5206\u914d\u7528\u6237")
    @PostMapping(value={"/assign"})
    @RequiresPermission(value="security:role:assign")
    @YakPermission(code="security:role:assign", name="\u5206\u914d\u7528\u6237\u89d2\u8272", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", description="\u4e3a\u7528\u6237\u5206\u914d\u89d2\u8272\u6216\u4e3a\u89d2\u8272\u5206\u914d\u7528\u6237")
    public Result<Void> assign(HttpServletRequest request, @RequestBody RoleAssignDTO assignDTO) {
        this.roleService.assignRoles(assignDTO, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u6839\u636e\u89d2\u8272 ID \u67e5\u8be2\u7528\u6237\u5206\u914d\u4fe1\u606f")
    @GetMapping(value={"/assign/list/{roleId}"})
    public Result<List<AssignInfoVO>> assignList(@PathVariable Long roleId) {
        return Result.success(this.roleService.getAssignInfoByRoleId(roleId));
    }

    @Operation(summary="\u6839\u636e\u89d2\u8272\u540d\u79f0\u67e5\u8be2\u89d2\u8272")
    @GetMapping(value={"/list/{roleName}", "/list"})
    public Result<List<RoleBriefVO>> list(@PathVariable(required=false) String roleName) {
        return Result.success(this.roleService.getRoleBriefListByRoleName(roleName));
    }
}

