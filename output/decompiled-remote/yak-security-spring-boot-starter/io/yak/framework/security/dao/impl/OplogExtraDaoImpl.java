/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  lombok.Generated
 *  org.springframework.stereotype.Repository
 */
package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.entity.OplogExtra;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.OplogExtraPO;
import io.yak.framework.security.dao.OplogExtraDao;
import io.yak.framework.security.dao.mapper.OplogExtraMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.Collections;
import java.util.List;
import lombok.Generated;
import org.springframework.stereotype.Repository;

@Repository
public class OplogExtraDaoImpl
implements OplogExtraDao {
    private final OplogExtraMapper oplogExtraMapper;

    @Override
    public List<OplogExtra> selectListByType(Integer type) {
        if (type == null) {
            return Collections.emptyList();
        }
        List oplogExtraPOList = this.oplogExtraMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(OplogExtraPO::getType, (Object)type)).orderByAsc(BasePO::getId));
        return CopyBeanUtil.copyList(oplogExtraPOList, OplogExtra.class);
    }

    @Override
    public void insertBatch(List<OplogExtra> oplogExtraList) {
        if (oplogExtraList == null || oplogExtraList.isEmpty()) {
            return;
        }
        CopyBeanUtil.copyList(oplogExtraList, OplogExtraPO.class).forEach(arg_0 -> ((OplogExtraMapper)this.oplogExtraMapper).insert(arg_0));
    }

    @Generated
    public OplogExtraDaoImpl(OplogExtraMapper oplogExtraMapper) {
        this.oplogExtraMapper = oplogExtraMapper;
    }
}

