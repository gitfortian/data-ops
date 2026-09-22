/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.PagingData
 */
package io.yak.framework.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.oplog.OplogDTO;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.vo.oplog.OplogOptionsVO;
import io.yak.framework.security.common.vo.oplog.OplogVO;
import java.util.List;

public interface OplogService {
    public Long saveOplog(OplogDTO var1);

    public PagingData<OplogVO> getOplogPage(OplogQueryDTO var1);

    public OplogVO getOplogDetailByOplogId(Long var1);

    public OplogOptionsVO getOptions();

    public List<String> listTargetType();
}

