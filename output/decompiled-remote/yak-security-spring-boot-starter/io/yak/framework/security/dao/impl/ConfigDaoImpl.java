/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.conditions.Wrapper
 *  com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper
 *  com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper
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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.po.BasePO;
import io.yak.framework.security.common.po.ConfigPO;
import io.yak.framework.security.dao.ConfigDao;
import io.yak.framework.security.dao.mapper.ConfigMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class ConfigDaoImpl
implements ConfigDao {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private final ConfigMapper configMapper;

    @Override
    public int insert(ConfigPO config) {
        return this.configMapper.insert(config);
    }

    @Override
    public int updateById(ConfigPO config) {
        return this.configMapper.updateById(config);
    }

    @Override
    public int update(ConfigPO config) {
        if (config == null) {
            return 0;
        }
        if (config.getId() != null) {
            return this.configMapper.updateById(config);
        }
        LambdaUpdateWrapper wrapper = (LambdaUpdateWrapper)((LambdaUpdateWrapper)Wrappers.lambdaUpdate().eq(ConfigPO::getValueGroup, (Object)config.getValueGroup())).eq(ConfigPO::getValueName, (Object)config.getValueName());
        return this.configMapper.update(config, (Wrapper)wrapper);
    }

    @Override
    public int deleteById(Long id) {
        return this.configMapper.deleteById(id);
    }

    @Override
    public IPage<ConfigPO> selectPage(ConfigQueryDTO queryDTO) {
        ConfigQueryDTO query = queryDTO == null ? new ConfigQueryDTO() : queryDTO;
        long pageNo = Math.max(1, query.getPage());
        long pageSize = query.getSize() <= 0 ? 10L : (long)Math.min(query.getSize(), 200);
        Page page = Page.of((long)pageNo, (long)pageSize);
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(query.getId() != null, ConfigPO::getId, (Object)query.getId())).eq(StringUtils.hasText((String)query.getValueGroup()), ConfigPO::getValueGroup, (Object)ConfigDaoImpl.trim(query.getValueGroup()))).eq(query.getStatus() != null, ConfigPO::getStatus, (Object)query.getStatus())).like(StringUtils.hasText((String)query.getOperator()), ConfigPO::getOperator, (Object)ConfigDaoImpl.trim(query.getOperator()))).like(StringUtils.hasText((String)query.getMemo()), ConfigPO::getMemo, (Object)ConfigDaoImpl.trim(query.getMemo()))).like(StringUtils.hasText((String)query.getValueName()), ConfigPO::getValueName, (Object)ConfigDaoImpl.trim(query.getValueName()))).orderByDesc(BasePO::getUpdateTime)).orderByDesc(BasePO::getCreateTime)).orderByDesc(ConfigPO::getId);
        return this.configMapper.selectPage((IPage)page, (Wrapper)wrapper);
    }

    @Override
    public List<ConfigPO> listByCondition(ConfigPO condition) {
        if (condition == null) {
            return this.configMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().orderByAsc(ConfigPO::getValueGroup)).orderByAsc(ConfigPO::getValueName));
        }
        LambdaQueryWrapper wrapper = (LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(StringUtils.hasText((String)condition.getValueGroup()), ConfigPO::getValueGroup, (Object)ConfigDaoImpl.trim(condition.getValueGroup()))).eq(StringUtils.hasText((String)condition.getValueName()), ConfigPO::getValueName, (Object)ConfigDaoImpl.trim(condition.getValueName()))).eq(condition.getStatus() != null, ConfigPO::getStatus, (Object)condition.getStatus())).orderByAsc(ConfigPO::getValueGroup)).orderByAsc(ConfigPO::getValueName);
        return this.configMapper.selectList((Wrapper)wrapper);
    }

    @Override
    public List<ConfigPO> listConfigByGroup(String groupName) {
        if (!StringUtils.hasText((String)groupName)) {
            return new ArrayList<ConfigPO>();
        }
        return this.configMapper.selectList((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(ConfigPO::getValueGroup, (Object)ConfigDaoImpl.trim(groupName))).orderByAsc(ConfigPO::getValueName));
    }

    @Override
    public List<String> listDistinctGroup() {
        List configs = this.configMapper.selectList((Wrapper)((LambdaQueryWrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().select(new SFunction[]{ConfigPO::getValueGroup}).isNotNull(ConfigPO::getValueGroup)).groupBy(ConfigPO::getValueGroup)).orderByAsc(ConfigPO::getValueGroup));
        if (configs == null || configs.isEmpty()) {
            return new ArrayList<String>();
        }
        return configs.stream().map(ConfigPO::getValueGroup).filter(StringUtils::hasText).map(String::trim).distinct().sorted().collect(Collectors.toList());
    }

    @Override
    public ConfigPO getbyId(Long configId) {
        if (configId == null) {
            return null;
        }
        return (ConfigPO)this.configMapper.selectById(configId);
    }

    @Override
    public ConfigPO getByGroupAndName(String valueGroup, String valueName) {
        if (!StringUtils.hasText((String)valueGroup) || !StringUtils.hasText((String)valueName)) {
            return null;
        }
        return (ConfigPO)this.configMapper.selectOne((Wrapper)((LambdaQueryWrapper)Wrappers.lambdaQuery().eq(ConfigPO::getValueGroup, (Object)ConfigDaoImpl.trim(valueGroup))).eq(ConfigPO::getValueName, (Object)ConfigDaoImpl.trim(valueName)));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    @Generated
    public ConfigDaoImpl(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }
}

