package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.dto.resource.AssignToManyUserDTO;
import io.yak.framework.security.common.dto.resource.AssignToOneUserDTO;
import io.yak.framework.security.common.dto.resource.BatchAssignDTO;
import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUQueryDTO;
import io.yak.framework.security.common.dto.resource.ResourceDTO;
import io.yak.framework.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.enums.resource.ShowLevelCode;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.ResourceTypeService;

/** Validates resource hierarchy and assignment inputs before reads or commands. */
final class ResourceGrantPolicy {
  private final ProjectService projectService;
  private final ResourceTypeService resourceTypeService;

  ResourceGrantPolicy(ProjectService projectService, ResourceTypeService resourceTypeService) {
    this.projectService = projectService;
    this.resourceTypeService = resourceTypeService;
  }

  /**
   * 校验资源控制层级参数。
   */
  void validate(
          Integer controlLevel,
          Long projectId,
          Long resourceTypeId,
          Long resourceId) {

    if (projectId == null) {
      throw new YakSecurityException(
              ResultCode.PROJECT_ID_CANNOT_BE_NULL);
    }

    if (resourceTypeId == null
            && resourceId != null) {

      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR);
    }

    if (controlLevel == null
            || ControlLevelCode.getByType(
            controlLevel) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }
  }

  /**
   * 校验按资源查询参数。
   */
  void validate(
          MByRDataQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "按资源查询条件不能为空");
    }

    validate(
            queryDTO.getControlLevel(),
            queryDTO.getProjectId(),
            queryDTO.getResourceTypeId(),
            queryDTO.getResourceId());
  }

  /**
   * 校验按用户查询参数。
   */
  void validate(
          MByUDataQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "按用户查询条件不能为空");
    }

    if (queryDTO.getUserId() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    if (ControlLevelCode.getByType(
            queryDTO.getControlLevel()) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }

    validate(
            queryDTO.getShowLevel(),
            queryDTO.getProjectId(),
            queryDTO.getResourceTypeId());
  }

  /**
   * 校验单用户分配参数。
   */
  void validate(
          AssignToOneUserDTO assignDTO) {

    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "单用户资源分配参数不能为空");
    }

    if (assignDTO.getUserId() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    if (ControlLevelCode.getByType(
            assignDTO.getControlLevel()) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }

    if (assignDTO.getProjectId() == null
            && assignDTO.getResourceTypeId()
            != null) {

      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR_2);
    }
  }

  /**
   * 校验多用户分配参数。
   */
  void validate(
          AssignToManyUserDTO assignDTO) {

    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "多用户资源分配参数不能为空");
    }

    validate(
            assignDTO.getControlLevel(),
            assignDTO.getProjectId(),
            assignDTO.getResourceTypeId(),
            assignDTO.getResourceId());
  }

  /**
   * 校验批量分配参数。
   */
  void validate(
          BatchAssignDTO assignDTO) {

    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "批量资源分配参数不能为空");
    }

    if (assignDTO.getUserIdList() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    if (assignDTO.getAssignFlag() == null) {
      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_ASSIGN_BATCH_FLAG_CANNOT_BE_NULL);
    }

    if (assignDTO.getProjectId() == null
            && assignDTO.getResourceTypeId()
            != null) {

      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR_2);
    }

    if (ControlLevelCode.getByType(
            assignDTO.getControlLevel()) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }
  }

  /**
   * 校验按资源分页查询参数。
   */
  void validate(
          MByRQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "资源权限分页查询条件不能为空");
    }

    validate(
            queryDTO.getShowLevel(),
            queryDTO.getProjectId(),
            queryDTO.getResourceTypeId());
  }

  /**
   * 校验资源展示层级。
   */
  void validate(
          Integer showLevel,
          Long projectId,
          Long resourceTypeId) {

    ShowLevelCode showLevelCode =
            ShowLevelCode.getByType(
                    showLevel);

    if (showLevelCode == null) {
      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_SHOW_LEVEL);
    }

    if (showLevel
            >= ShowLevelCode
            .RESOURCE_TYPE
            .getType()) {

      if (projectId == null) {
        throw new YakSecurityException(
                ResultCode
                        .RESOURCE_SHOW_LEVEL_ERROR);
      }

      ProjectBriefVO project =
              projectService
                      .getProjectBriefByProjectId(
                              projectId);

      if (project == null) {
        throw new YakSecurityException(
                ResultCode.PROJECT_NOT_EXISTS);
      }
    }

    if (showLevel
            >= ShowLevelCode
            .RESOURCE
            .getType()) {

      if (resourceTypeId == null) {
        throw new YakSecurityException(
                ResultCode
                        .RESOURCE_SHOW_LEVEL_ERROR_2);
      }

      ResourceTypeVO resourceType =
              resourceTypeService
                      .getResourceTypeByResourceTypeId(
                              resourceTypeId);

      if (resourceType == null) {
        throw new YakSecurityException(
                ResultCode
                        .RESOURCE_TYPE_NOT_EXISTS);
      }
    }
  }

}
