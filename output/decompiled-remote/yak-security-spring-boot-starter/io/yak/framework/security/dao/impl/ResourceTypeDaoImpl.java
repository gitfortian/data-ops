/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.extension.plugins.pagination.Page
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.entity.ResourceType;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.ResourceTypePO;
import io.yak.framework.security.dao.ResourceTypeDao;
import io.yak.framework.security.dao.mapper.ResourceTypeMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.List;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ResourceTypeDaoImpl
implements ResourceTypeDao {
    private final ResourceTypeMapper resourceTypeMapper;

    @Override
    public List<ResourceType> selectAll() {
        List resourceTypePOList = this.resourceTypeMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().orderByAsc(ResourceTypePO::getTypeName)).orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(resourceTypePOList, ResourceType.class);
    }

    @Override
    public IPage<ResourceType> selectPage(ResourceTypeQueryDTO queryDTO) {
        Page page = Page.of((long)queryDTO.getPage(), (long)queryDTO.getSize());
        IPage result = this.resourceTypeMapper.selectPage((IPage)page, (Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().like(StringUtils.hasText((String)queryDTO.getTypeName()), ResourceTypePO::getTypeName, (Object)queryDTO.getTypeName())).orderByDesc(BasePO::getId));
        return CopyBeanUtil.copyPage(result, ResourceType.class);
    }

    @Override
    public ResourceType selectByResourceTypeId(Long resourceTypeId) {
        if (resourceTypeId == null) {
            return null;
        }
        return CopyBeanUtil.copy(this.resourceTypeMapper.selectById(resourceTypeId), ResourceType.class);
    }

    @Override
    public void insertBatch(List<ResourceType> resourceTypeList) {
        if (resourceTypeList == null || resourceTypeList.isEmpty()) {
            return;
        }
        CopyBeanUtil.copyList(resourceTypeList, ResourceTypePO.class).forEach(arg_0 -> ((ResourceTypeMapper)this.resourceTypeMapper).insert(arg_0));
    }

    @Generated
    public ResourceTypeDaoImpl(ResourceTypeMapper resourceTypeMapper) {
        this.resourceTypeMapper = resourceTypeMapper;
    }
}

