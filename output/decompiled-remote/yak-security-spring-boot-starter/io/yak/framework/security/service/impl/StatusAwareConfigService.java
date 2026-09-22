/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Qualifier
 *  org.springframework.context.annotation.Primary
 *  org.springframework.stereotype.Service
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.config.ConfigDTO;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.enums.ConfigStatusEnum;
import io.yak.framework.security.common.po.ConfigPO;
import io.yak.framework.security.common.vo.config.ConfigVO;
import io.yak.framework.security.dao.ConfigDao;
import io.yak.framework.security.service.ConfigService;
import io.yak.framework.security.util.JsonUtils;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service(value="yakSecurityStatusAwareConfigService")
@Primary
public class StatusAwareConfigService
implements ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusAwareConfigService.class);
    private final ConfigService delegate;
    private final ConfigDao configDao;

    public StatusAwareConfigService(@Qualifier(value="configServiceImpl") ConfigService delegate, ConfigDao configDao) {
        this.delegate = delegate;
        this.configDao = configDao;
    }

    @Override
    public Result<Long> addConfig(ConfigDTO configDTO, String operator) {
        return this.delegate.addConfig(configDTO, operator);
    }

    @Override
    public Result<Long> addConfig(String valueGroup, String valueName, String value, String operator) {
        return this.delegate.addConfig(valueGroup, valueName, value, operator);
    }

    @Override
    public Result<Void> delConfig(Long configId, String operator) {
        return this.delegate.delConfig(configId, operator);
    }

    @Override
    public Result<Void> editConfig(ConfigDTO configDTO, String operator) {
        return this.delegate.editConfig(configDTO, operator);
    }

    @Override
    public Result<Void> switchConfig(Long configId, Integer status, String operator) {
        return this.delegate.switchConfig(configId, status, operator);
    }

    @Override
    public PagingData<ConfigVO> pagingConfig(ConfigQueryDTO queryDTO) {
        return this.delegate.pagingConfig(queryDTO);
    }

    @Override
    public List<ConfigVO> queryByCondt(ConfigDTO condition) {
        return this.delegate.queryByCondt(condition);
    }

    @Override
    public List<String> listGroups() {
        return this.delegate.listGroups();
    }

    @Override
    public List<ConfigVO> listConfigByGroup(String valueGroup) {
        return this.delegate.listConfigByGroup(valueGroup);
    }

    @Override
    public ConfigVO getConfigById(Long configId) {
        return this.delegate.getConfigById(configId);
    }

    @Override
    public String stringSetting(String valueGroup, String valueName, String defaultValue) {
        return this.getEnabledSetting(valueGroup, valueName, defaultValue, value -> value, "stringSetting");
    }

    @Override
    public Boolean booleanSetting(String valueGroup, String valueName, Boolean defaultValue) {
        return this.getEnabledSetting(valueGroup, valueName, defaultValue, this::parseBoolean, "booleanSetting");
    }

    @Override
    public Integer intSetting(String valueGroup, String valueName, Integer defaultValue) {
        return this.getEnabledSetting(valueGroup, valueName, defaultValue, Integer::valueOf, "intSetting");
    }

    @Override
    public Long longSetting(String valueGroup, String valueName, Long defaultValue) {
        return this.getEnabledSetting(valueGroup, valueName, defaultValue, Long::valueOf, "longSetting");
    }

    @Override
    public Double doubleSetting(String valueGroup, String valueName, Double defaultValue) {
        return this.getEnabledSetting(valueGroup, valueName, defaultValue, Double::valueOf, "doubleSetting");
    }

    @Override
    public <T> T objectSetting(String valueGroup, String valueName, T defaultValue, Class<T> type) {
        if (type == null) {
            return defaultValue;
        }
        return (T)this.getEnabledSetting(valueGroup, valueName, defaultValue, value -> JsonUtils.fromJson(value, type), "objectSetting");
    }

    private <T> T getEnabledSetting(String valueGroup, String valueName, T defaultValue, Function<String, T> converter, String methodName) {
        try {
            ConfigPO config = this.configDao.getByGroupAndName(valueGroup, valueName);
            if (config == null || !Objects.equals(config.getStatus(), ConfigStatusEnum.NORMAL.getCode()) || !StringUtils.hasText((String)config.getValue())) {
                return defaultValue;
            }
            return converter.apply(config.getValue());
        }
        catch (Exception exception) {
            LOGGER.warn("\u8bfb\u53d6\u542f\u7528\u914d\u7f6e\u5931\u8d25\uff0c\u65b9\u6cd5={}\uff0c\u914d\u7f6e\u7ec4={}\uff0c\u914d\u7f6e\u540d={}", new Object[]{methodName, valueGroup, valueName, exception});
            return defaultValue;
        }
    }

    private Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("\u914d\u7f6e\u503c\u4e0d\u662f\u6709\u6548\u7684\u5e03\u5c14\u7c7b\u578b");
    }
}

