/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  io.yak.framework.common.PageData
 *  io.yak.framework.common.PagingData
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.CollectionUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.entity.ResourceType;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import io.yak.framework.security.dao.ResourceTypeDao;
import io.yak.framework.security.service.ResourceTypeService;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityResourceTypeServiceImpl")
public class ResourceTypeServiceImpl
implements ResourceTypeService {
    private final ResourceTypeDao resourceTypeDao;

    public ResourceTypeServiceImpl(ResourceTypeDao resourceTypeDao) {
        this.resourceTypeDao = resourceTypeDao;
    }

    @Override
    public List<ResourceTypeVO> getAllResourceTypeList() {
        List<ResourceType> resourceTypeList = this.resourceTypeDao.selectAll();
        if (CollectionUtils.isEmpty(resourceTypeList)) {
            return new ArrayList<ResourceTypeVO>();
        }
        ArrayList result = CopyBeanUtil.copyList(resourceTypeList, ResourceTypeVO.class);
        return result == null ? new ArrayList() : result;
    }

    @Override
    public List<Long> getAllResourceTypeIdList() {
        List<ResourceType> resourceTypeList = this.resourceTypeDao.selectAll();
        if (CollectionUtils.isEmpty(resourceTypeList)) {
            return new ArrayList<Long>();
        }
        return resourceTypeList.stream().filter(Objects::nonNull).map(ResourceType::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    @Override
    public PagingData<ResourceTypeVO> getResourceTypePage(ResourceTypeQueryDTO queryDTO) {
        if (queryDTO == null) {
            throw new IllegalArgumentException("\u8d44\u6e90\u7c7b\u578b\u67e5\u8be2\u6761\u4ef6\u4e0d\u80fd\u4e3a\u7a7a");
        }
        IPage<ResourceType> resourceTypePage = this.resourceTypeDao.selectPage(queryDTO);
        List<ResourceTypeVO> resourceTypeList = CopyBeanUtil.copyList(resourceTypePage.getRecords(), ResourceTypeVO.class);
        if (resourceTypeList == null) {
            resourceTypeList = new ArrayList<ResourceTypeVO>();
        }
        return PagingData.from((PageData)new PageData(resourceTypeList, resourceTypePage.getTotal(), resourceTypePage.getPages(), resourceTypePage.getCurrent(), resourceTypePage.getSize()));
    }

    @Override
    public ResourceTypeVO getResourceTypeByResourceTypeId(Long resourceTypeId) {
        if (resourceTypeId == null) {
            return null;
        }
        ResourceType resourceType = this.resourceTypeDao.selectByResourceTypeId(resourceTypeId);
        return CopyBeanUtil.copy(resourceType, ResourceTypeVO.class);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public void saveResourceType(List<String> resourceTypeNameList) {
        List<String> validTypeNames = this.normalizeTypeNames(resourceTypeNameList);
        if (validTypeNames.isEmpty()) {
            return;
        }
        List<ResourceType> resourceTypeList = this.buildResourceTypeList(validTypeNames);
        this.resourceTypeDao.insertBatch(resourceTypeList);
    }

    private List<ResourceType> buildResourceTypeList(List<String> resourceTypeNameList) {
        ArrayList<ResourceType> resourceTypeList = new ArrayList<ResourceType>(resourceTypeNameList.size());
        for (String resourceTypeName : resourceTypeNameList) {
            ResourceType resourceType = new ResourceType();
            resourceType.setTypeName(resourceTypeName);
            resourceTypeList.add(resourceType);
        }
        return resourceTypeList;
    }

    private List<String> normalizeTypeNames(List<String> resourceTypeNameList) {
        if (CollectionUtils.isEmpty(resourceTypeNameList)) {
            return new ArrayList<String>();
        }
        return resourceTypeNameList.stream().filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.toList());
    }
}

