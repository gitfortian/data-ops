/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 */
package io.yak.framework.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.entity.Oplog;
import java.util.List;

public interface OplogDao {
    public IPage<Oplog> selectPageWithoutDetail(OplogQueryDTO var1);

    public Oplog selectByOplogId(Long var1);

    public void insert(Oplog var1);

    public List<String> listOperateType();

    public List<String> listOperatePage();

    public List<String> listOperationMethods();

    public List<String> listTargetType();
}

