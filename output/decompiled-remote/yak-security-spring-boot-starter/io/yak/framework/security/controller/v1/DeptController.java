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
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.dept.DeptDTO;
import io.yak.framework.security.common.dto.dept.DeptSaveDTO;
import io.yak.framework.security.common.vo.dept.DeptDeleteCheckVO;
import io.yak.framework.security.common.vo.dept.DeptTreeVO;
import io.yak.framework.security.common.vo.dept.DeptVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.web.RequiresPermission;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u90e8\u95e8\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/dept"})
@RequiresPermission(value="security:department:read")
@YakPermission(code="security:department:read", name="\u67e5\u770b\u90e8\u95e8\u7ba1\u7406", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-departments", description="\u67e5\u770b\u90e8\u95e8\u6811\u53ca\u90e8\u95e8\u8be6\u60c5")
public class DeptController {
    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @Operation(summary="\u67e5\u8be2\u5b8c\u6574\u90e8\u95e8\u6811")
    @GetMapping(value={"/tree"})
    public Result<DeptTreeVO> tree() {
        return Result.success((Object)this.deptService.buildDeptTree());
    }

    @Operation(summary="\u6839\u636e\u90e8\u95e8 ID \u67e5\u8be2\u90e8\u95e8\u8be6\u60c5")
    @GetMapping(value={"/{id}"})
    public Result<DeptVO> detail(@PathVariable(value="id") Long deptId) {
        return Result.success((Object)this.deptService.getDeptDetail(deptId));
    }

    @Operation(summary="\u65b0\u589e\u90e8\u95e8")
    @PostMapping
    @RequiresPermission(value="security:department:create")
    @YakPermission(code="security:department:create", name="\u65b0\u589e\u90e8\u95e8", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-departments", description="\u521b\u5efa\u6839\u90e8\u95e8\u6216\u5b50\u90e8\u95e8")
    public Result<Void> create(@RequestBody DeptSaveDTO deptSaveDTO) {
        this.deptService.createDept(deptSaveDTO);
        return Result.success(null);
    }

    @Operation(summary="\u7f16\u8f91\u90e8\u95e8")
    @PutMapping
    @RequiresPermission(value="security:department:edit")
    @YakPermission(code="security:department:edit", name="\u7f16\u8f91\u90e8\u95e8", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-departments", description="\u4fee\u6539\u90e8\u95e8\u540d\u79f0\u3001\u63cf\u8ff0\u53ca\u4e0a\u7ea7\u90e8\u95e8")
    public Result<Void> update(@RequestBody DeptSaveDTO deptSaveDTO) {
        this.deptService.updateDept(deptSaveDTO);
        return Result.success(null);
    }

    @Operation(summary="\u5220\u9664\u90e8\u95e8\u524d\u68c0\u67e5\u5173\u8054\u6570\u636e")
    @DeleteMapping(value={"/delete/check/{id}"})
    @RequiresPermission(value="security:department:delete")
    public Result<DeptDeleteCheckVO> checkBeforeDelete(@PathVariable(value="id") Long deptId) {
        return Result.success((Object)this.deptService.checkBeforeDelete(deptId));
    }

    @Operation(summary="\u5220\u9664\u90e8\u95e8")
    @DeleteMapping(value={"/{id}"})
    @RequiresPermission(value="security:department:delete")
    @YakPermission(code="security:department:delete", name="\u5220\u9664\u90e8\u95e8", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-departments", description="\u5220\u9664\u6ca1\u6709\u4e0b\u7ea7\u90e8\u95e8\u548c\u5173\u8054\u7528\u6237\u7684\u90e8\u95e8")
    public Result<Void> delete(@PathVariable(value="id") Long deptId) {
        this.deptService.deleteDept(deptId);
        return Result.success(null);
    }

    @Operation(summary="\u5bfc\u5165\u90e8\u95e8\u6811")
    @PostMapping(value={"/import"})
    @RequiresPermission(value="security:department:import")
    @YakPermission(code="security:department:import", name="\u5bfc\u5165\u90e8\u95e8", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-departments", description="\u6279\u91cf\u5bfc\u5165\u90e8\u95e8\u6811")
    public Result<Void> importDept(@RequestBody List<DeptDTO> deptDTOList) {
        this.deptService.saveDept(deptDTOList);
        return Result.success(null);
    }
}

