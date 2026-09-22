/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.PagingData
 */
package io.yak.framework.security.extend;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.resource.ResourceDTO;
import java.util.List;

public interface ResourceExtend {
    public PagingData<ResourceDTO> getResourcePage(Long var1, Long var2, String var3, int var4, int var5);

    public List<ResourceDTO> getResourceList(Long var1, Long var2);

    public int getResourceCnt(Long var1, Long var2);
}

