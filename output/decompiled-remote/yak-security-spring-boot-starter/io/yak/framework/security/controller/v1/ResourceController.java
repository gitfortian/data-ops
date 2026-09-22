/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  org.springframework.web.bind.annotation.GetMapping
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
import io.yak.framework.security.common.dto.PageParamDTO;
import io.yak.framework.security.common.dto.resource.AssignToManyUserDTO;
import io.yak.framework.security.common.dto.resource.AssignToOneUserDTO;
import io.yak.framework.security.common.dto.resource.BatchAssignDTO;
import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUQueryDTO;
import io.yak.framework.security.common.dto.resource.ResourceViewControlDTO;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.enums.resource.ShowLevelCode;
import io.yak.framework.security.common.vo.resource.MByRDataVO;
import io.yak.framework.security.common.vo.resource.MByRVO;
import io.yak.framework.security.common.vo.resource.MByUDataVO;
import io.yak.framework.security.common.vo.resource.MByUVO;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import io.yak.framework.security.service.ResourceTypeService;
import io.yak.framework.security.service.UserResourceService;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u8d44\u6e90\u6743\u9650\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/resource"})
public class ResourceController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private final UserResourceService userResourceService;
    private final ResourceTypeService resourceTypeService;

    public ResourceController(UserResourceService userResourceService, ResourceTypeService resourceTypeService) {
        this.userResourceService = userResourceService;
        this.resourceTypeService = resourceTypeService;
    }

    @Operation(summary="\u67e5\u8be2\u5168\u90e8\u8d44\u6e90\u7c7b\u578b")
    @GetMapping(value={"/type/list"})
    public Result<List<ResourceTypeVO>> typeList() {
        List<ResourceTypeVO> resourceTypes = this.resourceTypeService.getAllResourceTypeList();
        return Result.success(resourceTypes == null ? Collections.emptyList() : resourceTypes);
    }

    @Operation(summary="\u5bfc\u5165\u8d44\u6e90\u7c7b\u578b")
    @PostMapping(value={"/type/import"})
    public Result<Void> typeImport(@RequestBody(required=false) List<String> typeNameList) {
        List<String> names = typeNameList == null ? Collections.emptyList() : typeNameList.stream().filter(name -> name != null && !name.trim().isEmpty()).map(String::trim).distinct().collect(Collectors.toList());
        this.resourceTypeService.saveResourceType(names);
        return Result.success();
    }

    @Operation(summary="\u67e5\u8be2\u8d44\u6e90\u67e5\u770b\u6743\u9650\u63a7\u5236\u72b6\u6001")
    @GetMapping(value={"/vpc/status", "/view-control/status"})
    public Result<Boolean> viewControlStatus() {
        return Result.success((Object)this.userResourceService.getViewPermissionControlStatus());
    }

    @Operation(summary="\u663e\u5f0f\u8bbe\u7f6e\u8d44\u6e90\u67e5\u770b\u6743\u9650\u63a7\u5236\u72b6\u6001")
    @PutMapping(value={"/view-control/status"})
    public Result<Void> setViewControlStatus(@RequestBody(required=false) ResourceViewControlDTO request) {
        if (request == null || request.getEnabled() == null) {
            return Result.buildParamIllegal((String)"\u67e5\u770b\u6743\u9650\u63a7\u5236\u72b6\u6001\u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.userResourceService.setViewPermissionControlStatus(request.getEnabled());
        return Result.success();
    }

    @Operation(summary="\u5207\u6362\u8d44\u6e90\u67e5\u770b\u6743\u9650\u63a7\u5236\u72b6\u6001\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @PutMapping(value={"/vpc/switch"})
    public Result<Void> vpcSwitch() {
        this.userResourceService.changeResourceViewControlStatus();
        return Result.success();
    }

    @Operation(summary="\u67e5\u8be2\u6309\u7528\u6237\u7ba1\u7406\u7684\u8d44\u6e90\u6743\u9650\u6570\u636e")
    @PostMapping(value={"/mbu/list", "/by-user/data"})
    public Result<List<MByUDataVO>> byUserData(@RequestBody(required=false) MByUDataQueryDTO queryDTO) {
        MByUDataQueryDTO query;
        MByUDataQueryDTO mByUDataQueryDTO = query = queryDTO == null ? new MByUDataQueryDTO() : queryDTO;
        if (query.getShowLevel() == null) {
            query.setShowLevel(ShowLevelCode.PROJECT.getType());
        }
        if (query.getBatch() == null) {
            query.setBatch(Boolean.FALSE);
        }
        return Result.success(this.userResourceService.getManagerByUserDataList(query));
    }

    @Operation(summary="\u67e5\u8be2\u6309\u8d44\u6e90\u7ba1\u7406\u7684\u7528\u6237\u6743\u9650\u6570\u636e")
    @PostMapping(value={"/mbr/list", "/by-resource/data"})
    public Result<List<MByRDataVO>> byResourceData(@RequestBody(required=false) MByRDataQueryDTO queryDTO) {
        MByRDataQueryDTO query;
        MByRDataQueryDTO mByRDataQueryDTO = query = queryDTO == null ? new MByRDataQueryDTO() : queryDTO;
        if (query.getBatch() == null) {
            query.setBatch(Boolean.FALSE);
        }
        return Result.success(this.userResourceService.getManagerByResourceDataList(query));
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u6309\u8d44\u6e90\u7ba1\u7406\u7684\u6743\u9650\u4fe1\u606f")
    @PostMapping(value={"/mbr/page", "/by-resource/page"})
    public Result<PagingData<MByRVO>> byResourcePage(@RequestBody(required=false) MByRQueryDTO queryDTO) {
        MByRQueryDTO query = this.normalize(queryDTO);
        PagingData<MByRVO> pagingData = this.userResourceService.getManageByResourcePage(query);
        return Result.success(pagingData);
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u6309\u7528\u6237\u7ba1\u7406\u7684\u6743\u9650\u4fe1\u606f")
    @PostMapping(value={"/mbu/page", "/by-user/page"})
    public Result<PagingData<MByUVO>> byUserPage(@RequestBody(required=false) MByUQueryDTO queryDTO) {
        MByUQueryDTO query = this.normalize(queryDTO);
        PagingData<MByUVO> pagingData = this.userResourceService.getManageByUserPage(query);
        return Result.success(pagingData);
    }

    @Operation(summary="\u4e3a\u5355\u4e2a\u7528\u6237\u5206\u914d\u8d44\u6e90\u6743\u9650")
    @PutMapping(value={"/by-user/assign"})
    public Result<Void> assignByUser(@RequestBody AssignToOneUserDTO assignDTO) {
        return this.assignByUserInternal(assignDTO);
    }

    @Operation(summary="\u4e3a\u5355\u4e2a\u7528\u6237\u5206\u914d\u8d44\u6e90\u6743\u9650\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @PostMapping(value={"/permission/mbu/assign"})
    public Result<Void> mbuAssign(@RequestBody AssignToOneUserDTO assignDTO) {
        return this.assignByUserInternal(assignDTO);
    }

    @Operation(summary="\u4e3a\u591a\u4e2a\u7528\u6237\u5206\u914d\u8d44\u6e90\u6743\u9650")
    @PutMapping(value={"/by-resource/assign"})
    public Result<Void> assignByResource(@RequestBody AssignToManyUserDTO assignDTO) {
        return this.assignByResourceInternal(assignDTO);
    }

    @Operation(summary="\u4e3a\u591a\u4e2a\u7528\u6237\u5206\u914d\u8d44\u6e90\u6743\u9650\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @PostMapping(value={"/permission/mbr/assign"})
    public Result<Void> mbrAssign(@RequestBody AssignToManyUserDTO assignDTO) {
        return this.assignByResourceInternal(assignDTO);
    }

    @Operation(summary="\u6279\u91cf\u5206\u914d\u8d44\u6e90\u6743\u9650")
    @PutMapping(value={"/batch/assign"})
    public Result<Void> batchAssignStandard(@RequestBody BatchAssignDTO assignDTO) {
        return this.batchAssignInternal(assignDTO);
    }

    @Operation(summary="\u6279\u91cf\u5206\u914d\u8d44\u6e90\u6743\u9650\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @PostMapping(value={"/permission/assign/batch"})
    public Result<Void> batchAssign(@RequestBody BatchAssignDTO assignDTO) {
        return this.batchAssignInternal(assignDTO);
    }

    @Operation(summary="\u67e5\u8be2\u8d44\u6e90\u6743\u9650\u63a7\u5236\u7ea7\u522b")
    @PostMapping(value={"/control/level"})
    public Result<Integer> getControlLevel(@RequestBody ControlLevelQueryDTO queryDTO) {
        ControlLevelCode controlLevel = this.userResourceService.getControlLevel(queryDTO);
        return Result.success((Object)controlLevel.getType());
    }

    private Result<Void> assignByUserInternal(AssignToOneUserDTO assignDTO) {
        this.userResourceService.assignResourcePermission(assignDTO);
        return Result.success();
    }

    private Result<Void> assignByResourceInternal(AssignToManyUserDTO assignDTO) {
        this.userResourceService.assignResourcePermission(assignDTO);
        return Result.success();
    }

    private Result<Void> batchAssignInternal(BatchAssignDTO assignDTO) {
        this.userResourceService.batchAssignResourcePermission(assignDTO);
        return Result.success();
    }

    private MByRQueryDTO normalize(MByRQueryDTO queryDTO) {
        MByRQueryDTO query = queryDTO == null ? new MByRQueryDTO() : queryDTO;
        this.normalizePage(query);
        if (query.getShowLevel() == null) {
            query.setShowLevel(ShowLevelCode.PROJECT.getType());
        }
        query.setName(this.trim(query.getName()));
        return query;
    }

    private MByUQueryDTO normalize(MByUQueryDTO queryDTO) {
        MByUQueryDTO query = queryDTO == null ? new MByUQueryDTO() : queryDTO;
        this.normalizePage(query);
        query.setDeptName(this.trim(query.getDeptName()));
        query.setUserName(this.trim(query.getUserName()));
        query.setRealName(this.trim(query.getRealName()));
        return query;
    }

    private void normalizePage(PageParamDTO query) {
        query.setPage(Math.max(1, query.getPage()));
        query.setSize(query.getSize() <= 0 ? 10 : Math.min(query.getSize(), 200));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}

