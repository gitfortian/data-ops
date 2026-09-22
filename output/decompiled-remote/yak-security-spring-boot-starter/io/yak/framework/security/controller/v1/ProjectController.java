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
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.project.ProjectQueryDTO;
import io.yak.framework.security.common.dto.project.ProjectSaveDTO;
import io.yak.framework.security.common.dto.project.ProjectStatusDTO;
import io.yak.framework.security.common.dto.project.ProjectUserAssignDTO;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.project.ProjectDeleteCheckVO;
import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.util.HttpRequestUtil;
import io.yak.framework.security.web.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u9879\u76ee\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/project"})
@RequiresPermission(value="security:project:read")
@YakPermission(code="security:project:read", name="\u67e5\u770b\u6388\u6743\u9879\u76ee", group="\u7cfb\u7edf\u7ba1\u7406", groupCode="security", menuCode="system-security-projects", description="\u67e5\u770b\u5de5\u4f5c\u7a7a\u95f4\u5217\u8868\u53ca\u8be6\u60c5")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary="\u6839\u636e\u9879\u76ee ID \u67e5\u8be2\u9879\u76ee\u8be6\u60c5")
    @GetMapping(value={"/{id}"})
    public Result<ProjectVO> detail(@PathVariable(value="id") Long projectId) {
        return Result.success((Object)this.projectService.getProjectDetailByProjectId(projectId));
    }

    @Operation(summary="\u6821\u9a8c\u9879\u76ee\u662f\u5426\u5b58\u5728")
    @GetMapping(value={"/{id}/exist"})
    public Result<Boolean> checkExist(@PathVariable(value="id") Long projectId) {
        return Result.success((Object)this.projectService.checkProjectExist(projectId));
    }

    @Operation(summary="\u5207\u6362\u9879\u76ee\u72b6\u6001")
    @PutMapping(value={"/switch/{id}"})
    @RequiresPermission(value="security:root")
    public Result<Void> switchStatus(HttpServletRequest request, @PathVariable(value="id") Long projectId) {
        this.projectService.changeProjectStatus(projectId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u6309\u76ee\u6807\u503c\u66f4\u65b0\u9879\u76ee\u72b6\u6001")
    @PutMapping(value={"/{id}/status"})
    @RequiresPermission(value="security:root")
    public Result<Void> updateStatus(HttpServletRequest request, @PathVariable(value="id") Long projectId, @RequestBody ProjectStatusDTO statusDTO) {
        if (statusDTO == null || statusDTO.getRunning() == null) {
            throw new IllegalArgumentException("\u9879\u76ee\u72b6\u6001\u4e0d\u80fd\u4e3a\u7a7a");
        }
        ProjectVO project = this.projectService.getProjectDetailByProjectId(projectId);
        if (!Objects.equals(project.getRunning(), statusDTO.getRunning())) {
            this.projectService.changeProjectStatus(projectId, HttpRequestUtil.getOperator(request));
        }
        return Result.success(null);
    }

    @Operation(summary="\u66f4\u65b0\u9879\u76ee")
    @PutMapping
    @RequiresPermission(value="security:root")
    public Result<Void> update(HttpServletRequest request, @RequestBody ProjectSaveDTO projectSaveDTO) {
        this.projectService.updateProject(projectSaveDTO, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u521b\u5efa\u9879\u76ee")
    @PostMapping
    @RequiresPermission(value="security:root")
    public Result<ProjectVO> create(HttpServletRequest request, @RequestBody ProjectSaveDTO projectSaveDTO) {
        return Result.success((Object)this.projectService.createProject(projectSaveDTO, HttpRequestUtil.getOperator(request)));
    }

    @Operation(summary="\u6267\u884c\u9879\u76ee\u5220\u9664\u524d\u6821\u9a8c")
    @GetMapping(value={"/delete/check/{id}"})
    @RequiresPermission(value="security:root")
    public Result<ProjectDeleteCheckVO> deleteCheck(@PathVariable(value="id") Long projectId) {
        return Result.success((Object)this.projectService.checkBeforeDelete(projectId));
    }

    @Operation(summary="\u6839\u636e\u9879\u76ee ID \u5220\u9664\u9879\u76ee")
    @DeleteMapping(value={"/{id}"})
    @RequiresPermission(value="security:root")
    public Result<Void> delete(HttpServletRequest request, @PathVariable(value="id") Long projectId) {
        this.projectService.deleteProjectByProjectId(projectId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u9879\u76ee")
    @PostMapping(value={"/page"})
    public Result<PagingData<ProjectVO>> page(@RequestBody ProjectQueryDTO queryDTO) {
        PagingData<ProjectVO> pagingData = this.projectService.getProjectPage(queryDTO);
        return Result.success(pagingData);
    }

    @Operation(summary="\u67e5\u8be2\u5168\u90e8\u9879\u76ee\u7b80\u8981\u4fe1\u606f")
    @GetMapping(value={"/list"})
    public Result<List<ProjectBriefVO>> list() {
        return Result.success(this.projectService.getProjectBriefList());
    }

    @Operation(summary="\u5168\u91cf\u66f4\u65b0\u9879\u76ee\u8d1f\u8d23\u4eba")
    @PutMapping(value={"/{id}/owners"})
    @RequiresPermission(value="security:root")
    public Result<Void> replaceProjectOwners(HttpServletRequest request, @PathVariable(value="id") Long projectId, @RequestBody ProjectUserAssignDTO assignDTO) {
        ProjectSaveDTO projectSaveDTO = this.buildRelationUpdateDTO(projectId);
        projectSaveDTO.setOwnerIdList(assignDTO == null ? null : assignDTO.getUserIdList());
        this.projectService.updateProject(projectSaveDTO, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u5168\u91cf\u66f4\u65b0\u9879\u76ee\u6210\u5458")
    @PutMapping(value={"/{id}/users"})
    @RequiresPermission(value="security:root")
    public Result<Void> replaceProjectUsers(HttpServletRequest request, @PathVariable(value="id") Long projectId, @RequestBody ProjectUserAssignDTO assignDTO) {
        ProjectSaveDTO projectSaveDTO = this.buildRelationUpdateDTO(projectId);
        projectSaveDTO.setUserIdList(assignDTO == null ? null : assignDTO.getUserIdList());
        this.projectService.updateProject(projectSaveDTO, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u6dfb\u52a0\u9879\u76ee\u8d1f\u8d23\u4eba")
    @PutMapping(value={"/{id}/owner/{ownerId}"})
    @RequiresPermission(value="security:root")
    public Result<Void> addProjectOwner(HttpServletRequest request, @PathVariable(value="id") Long projectId, @PathVariable Long ownerId) {
        this.projectService.addProjectOwner(projectId, ownerId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u5220\u9664\u9879\u76ee\u8d1f\u8d23\u4eba")
    @DeleteMapping(value={"/{id}/owner/{ownerId}"})
    @RequiresPermission(value="security:root")
    public Result<Void> deleteProjectOwner(HttpServletRequest request, @PathVariable(value="id") Long projectId, @PathVariable Long ownerId) {
        this.projectService.delProjectOwner(projectId, ownerId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u6dfb\u52a0\u9879\u76ee\u7528\u6237")
    @PutMapping(value={"/{id}/user/{userId}"})
    @RequiresPermission(value="security:root")
    public Result<Void> addProjectUser(HttpServletRequest request, @PathVariable(value="id") Long projectId, @PathVariable Long userId) {
        this.projectService.addProjectUser(projectId, userId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u5220\u9664\u9879\u76ee\u7528\u6237")
    @DeleteMapping(value={"/{id}/user/{userId}"})
    @RequiresPermission(value="security:root")
    public Result<Void> deleteProjectUser(HttpServletRequest request, @PathVariable(value="id") Long projectId, @PathVariable Long userId) {
        this.projectService.delProjectUser(projectId, userId, HttpRequestUtil.getOperator(request));
        return Result.success(null);
    }

    @Operation(summary="\u67e5\u8be2\u9879\u76ee\u672a\u5206\u914d\u7528\u6237")
    @GetMapping(value={"/unassigned"})
    @RequiresPermission(value="security:root")
    public Result<List<UserBriefVO>> unassigned(@RequestParam(value="id") Long projectId) {
        return this.projectService.unassignedByProjectId(projectId);
    }

    @Operation(summary="\u6839\u636e\u7528\u6237 ID \u67e5\u8be2\u9879\u76ee\u7b80\u8981\u4fe1\u606f")
    @GetMapping(value={"/user/{userId}"})
    public Result<List<ProjectBriefVO>> getProjectBriefByUserId(@PathVariable Long userId) {
        return this.projectService.getProjectBriefByUserId(userId);
    }

    private ProjectSaveDTO buildRelationUpdateDTO(Long projectId) {
        ProjectVO project = this.projectService.getProjectDetailByProjectId(projectId);
        ProjectSaveDTO projectSaveDTO = new ProjectSaveDTO();
        projectSaveDTO.setId(projectId);
        projectSaveDTO.setProjectName(project.getProjectName());
        return projectSaveDTO;
    }
}

