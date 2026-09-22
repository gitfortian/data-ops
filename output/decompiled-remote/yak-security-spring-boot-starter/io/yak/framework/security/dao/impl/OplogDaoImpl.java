/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  com.baomidou.mybatisplus.core.toolkit.Wrappers
 *  com.baomidou.mybatisplus.core.toolkit.support.SFunction
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
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.entity.Oplog;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.OplogPO;
import io.yak.framework.security.dao.OplogDao;
import io.yak.framework.security.dao.mapper.OplogMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class OplogDaoImpl
implements OplogDao {
    private final OplogMapper oplogMapper;

    private static Timestamp toTimestamp(Long timeMillis) {
        return timeMillis == null ? null : new Timestamp(timeMillis);
    }

    @Override
    public IPage<Oplog> selectPageWithoutDetail(OplogQueryDTO queryDTO) {
        Page page = Page.of((long)queryDTO.getPage(), (long)queryDTO.getSize());
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().select(new SFunction[]{BasePO::getId, OplogPO::getOperateType, OplogPO::getOperatePage, OplogPO::getOperationMethods, OplogPO::getTarget, OplogPO::getTargetType, OplogPO::getOperatorIp, OplogPO::getOperator, BasePO::getCreateTime, BasePO::getUpdateTime}).eq(StringUtils.hasText((String)queryDTO.getOperateType()), OplogPO::getOperateType, (Object)OplogDaoImpl.trim(queryDTO.getOperateType()))).eq(StringUtils.hasText((String)queryDTO.getOperatePage()), OplogPO::getOperatePage, (Object)OplogDaoImpl.trim(queryDTO.getOperatePage()))).eq(StringUtils.hasText((String)queryDTO.getTargetType()), OplogPO::getTargetType, (Object)OplogDaoImpl.trim(queryDTO.getTargetType()))).eq(StringUtils.hasText((String)queryDTO.getOperationMethods()), OplogPO::getOperationMethods, (Object)OplogDaoImpl.trim(queryDTO.getOperationMethods()))).like(StringUtils.hasText((String)queryDTO.getDetail()), OplogPO::getDetail, (Object)OplogDaoImpl.trim(queryDTO.getDetail()))).like(StringUtils.hasText((String)queryDTO.getTarget()), OplogPO::getTarget, (Object)OplogDaoImpl.trim(queryDTO.getTarget()))).like(StringUtils.hasText((String)queryDTO.getOperator()), OplogPO::getOperator, (Object)OplogDaoImpl.trim(queryDTO.getOperator()))).like(StringUtils.hasText((String)queryDTO.getOperatorIp()), OplogPO::getOperatorIp, (Object)OplogDaoImpl.trim(queryDTO.getOperatorIp()))).ge(queryDTO.getStartTime() != null, BasePO::getCreateTime, (Object)OplogDaoImpl.toTimestamp(queryDTO.getStartTime()))).le(queryDTO.getEndTime() != null, BasePO::getCreateTime, (Object)OplogDaoImpl.toTimestamp(queryDTO.getEndTime()))).orderByDesc(BasePO::getCreateTime)).orderByDesc(BasePO::getId);
        IPage result = this.oplogMapper.selectPage((IPage)page, (Wrapper)wrapper);
        return CopyBeanUtil.copyPage(result, Oplog.class);
    }

    @Override
    public Oplog selectByOplogId(Long oplogId) {
        if (oplogId == null) {
            return null;
        }
        return CopyBeanUtil.copy(this.oplogMapper.selectById(oplogId), Oplog.class);
    }

    @Override
    public void insert(Oplog oplog) {
        OplogPO oplogPO = CopyBeanUtil.copy(oplog, OplogPO.class);
        if (oplogPO == null) {
            throw new IllegalStateException("\u64cd\u4f5c\u65e5\u5fd7\u6301\u4e45\u5316\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        this.oplogMapper.insert(oplogPO);
        oplog.setId(oplogPO.getId());
    }

    @Override
    public List<String> listOperateType() {
        return this.listDistinctStrings((SFunction<OplogPO, String>)((SFunction & Serializable)OplogPO::getOperateType));
    }

    @Override
    public List<String> listOperatePage() {
        return this.listDistinctStrings((SFunction<OplogPO, String>)((SFunction & Serializable)OplogPO::getOperatePage));
    }

    @Override
    public List<String> listOperationMethods() {
        return this.listDistinctStrings((SFunction<OplogPO, String>)((SFunction & Serializable)OplogPO::getOperationMethods));
    }

    @Override
    public List<String> listTargetType() {
        return this.listDistinctStrings((SFunction<OplogPO, String>)((SFunction & Serializable)OplogPO::getTargetType));
    }

    private List<String> listDistinctStrings(SFunction<OplogPO, String> column) {
        List records = this.oplogMapper.selectList((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().select(new SFunction[]{column}).isNotNull(column)).groupBy(column)).orderByAsc(column));
        if (records == null || records.isEmpty()) {
            return new ArrayList<String>();
        }
        return records.stream().map(column).filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.toList());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    @Generated
    public OplogDaoImpl(OplogMapper oplogMapper) {
        this.oplogMapper = oplogMapper;
    }
}

