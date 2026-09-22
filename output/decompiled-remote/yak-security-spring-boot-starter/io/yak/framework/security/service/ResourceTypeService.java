/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.PagingData
 */
package io.yak.framework.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import java.util.List;

public interface ResourceTypeService {
    public List<ResourceTypeVO> getAllResourceTypeList();

    public List<Long> getAllResourceTypeIdList();

    public PagingData<ResourceTypeVO> getResourceTypePage(ResourceTypeQueryDTO var1);

    public ResourceTypeVO getResourceTypeByResourceTypeId(Long var1);

    public void saveResourceType(List<String> var1);
}

