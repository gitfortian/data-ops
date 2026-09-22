/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.PageData
 *  io.yak.framework.common.PagingData
 */
package io.yak.framework.security.extend.impl;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.resource.ResourceDTO;
import io.yak.framework.security.extend.ResourceExtend;
import java.util.Collections;
import java.util.List;

public class DefaultResourceExtendImpl
implements ResourceExtend {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    @Override
    public PagingData<ResourceDTO> getResourcePage(Long projectId, Long resourceTypeId, String resourceName, int page, int size) {
        long currentPage = page > 0 ? (long)page : 1L;
        long pageSize = size > 0 ? (long)size : 10L;
        return PagingData.from((PageData)PageData.empty((long)currentPage, (long)pageSize));
    }

    @Override
    public List<ResourceDTO> getResourceList(Long projectId, Long resourceTypeId) {
        return Collections.emptyList();
    }

    @Override
    public int getResourceCnt(Long projectId, Long resourceTypeId) {
        return 0;
    }
}

