/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.Result
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.permission.PermissionDTO;
import io.yak.framework.security.common.vo.permission.PermissionTreeVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.PermissionService;
import io.yak.framework.security.service.impl.PermissionAdministrationService;
import io.yak.framework.security.web.RequiresPermission;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u6743\u9650\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/permission"})
@RequiresPermission(value="security:permission:read")
@YakPermission(code="security:permission:read", name="\u67e5\u770b\u6743\u9650\u7ba1\u7406", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-permissions", description="\u67e5\u770b\u6743\u9650\u76ee\u5f55\u53ca\u6743\u9650\u6811")
public class PermissionController {
    private final PermissionService permissionService;
    private final PermissionAdministrationService permissionAdministrationService;

    public PermissionController(PermissionService permissionService, PermissionAdministrationService permissionAdministrationService) {
        this.permissionService = permissionService;
        this.permissionAdministrationService = permissionAdministrationService;
    }

    @Operation(summary="\u67e5\u8be2\u5b8c\u6574\u6743\u9650\u6811")
    @GetMapping(value={"/tree"})
    public Result<PermissionTreeVO> tree() {
        return Result.success((Object)this.permissionService.buildPermissionTree());
    }

    @Operation(summary="\u5bfc\u5165\u6743\u9650\u6811")
    @PostMapping(value={"/import"})
    @RequiresPermission(value="security:permission:import")
    @YakPermission(code="security:permission:import", name="\u5bfc\u5165\u6743\u9650", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-permissions", description="\u5bfc\u5165\u624b\u5de5\u7ef4\u62a4\u7684\u6743\u9650\u76ee\u5f55")
    public Result<Void> importPermission(@RequestBody List<PermissionDTO> permissionDTOList) {
        this.permissionService.savePermission(permissionDTOList);
        return Result.success(null);
    }

    @Operation(summary="\u5220\u9664\u624b\u5de5\u6743\u9650\u53ca\u5176\u89d2\u8272\u5173\u8054")
    @DeleteMapping(value={"/{permissionId}"})
    @RequiresPermission(value="security:permission:delete")
    @YakPermission(code="security:permission:delete", name="\u5220\u9664\u6743\u9650", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-permissions", description="\u5220\u9664\u624b\u5de5\u6743\u9650\u5e76\u89e3\u9664\u89d2\u8272\u6388\u6743")
    public Result<Void> deletePermission(@PathVariable Long permissionId) {
        this.permissionAdministrationService.deletePermission(permissionId);
        return Result.success(null);
    }
}

