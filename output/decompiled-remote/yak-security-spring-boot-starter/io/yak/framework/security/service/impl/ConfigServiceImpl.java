/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  io.yak.framework.common.PageData
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.config.ConfigDTO;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.enums.ConfigStatusEnum;
import io.yak.framework.security.common.po.ConfigPO;
import io.yak.framework.security.common.vo.config.ConfigVO;
import io.yak.framework.security.dao.ConfigDao;
import io.yak.framework.security.service.ConfigService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.JsonUtils;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConfigServiceImpl
implements ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigServiceImpl.class);
    private static final String CONFIG_NOT_EXIST = "\u914d\u7f6e\u4e0d\u5b58\u5728";
    private static final String CONFIG_DUPLICATE = "\u914d\u7f6e\u91cd\u590d";
    private final ConfigDao configDao;

    public ConfigServiceImpl(ConfigDao configDao) {
        this.configDao = configDao;
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Result<Long> addConfig(ConfigDTO configDTO, String operator) {
        Result<Void> checkResult = this.checkParam(configDTO);
        if (checkResult.failed()) {
            LOGGER.warn("\u65b0\u589e\u914d\u7f6e\u53c2\u6570\u6821\u9a8c\u5931\u8d25\uff0c\u539f\u56e0\uff1a{}", (Object)checkResult.getMessage());
            return Result.buildFrom(checkResult);
        }
        this.initConfig(configDTO);
        Result<Void> statusCheckResult = this.checkStatus(configDTO.getStatus());
        if (statusCheckResult.failed()) {
            return Result.buildFrom(statusCheckResult);
        }
        ConfigPO existingConfig = this.getByGroupAndNameFromDB(configDTO.getValueGroup(), configDTO.getValueName());
        if (existingConfig != null) {
            return Result.buildDuplicate((String)CONFIG_DUPLICATE);
        }
        configDTO.setOperator(operator);
        ConfigPO configPO = CopyBeanUtil.copy(configDTO, ConfigPO.class);
        if (configPO == null) {
            throw new IllegalStateException("\u914d\u7f6e\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        int affectedRows = this.configDao.insert(configPO);
        if (affectedRows != 1) {
            throw new IllegalStateException("\u65b0\u589e\u914d\u7f6e\u5931\u8d25");
        }
        return Result.success((Object)configPO.getId());
    }

    @Override
    public Result<Long> addConfig(String valueGroup, String valueName, String value, String operator) {
        ConfigDTO configDTO = new ConfigDTO();
        configDTO.setValueGroup(valueGroup);
        configDTO.setValueName(valueName);
        configDTO.setValue(value);
        configDTO.setStatus(ConfigStatusEnum.NORMAL.getCode());
        return this.addConfig(configDTO, operator);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Result<Void> delConfig(Long configId, String operator) {
        boolean success;
        if (configId == null) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        ConfigPO configPO = this.configDao.getbyId(configId);
        if (configPO == null) {
            return Result.buildNotExist((String)CONFIG_NOT_EXIST);
        }
        boolean bl = success = this.configDao.deleteById(configId) == 1;
        if (success) {
            LOGGER.info("\u5220\u9664\u914d\u7f6e\u6210\u529f\uff0c\u914d\u7f6e ID={}\uff0c\u64cd\u4f5c\u4eba={}", (Object)configId, (Object)operator);
        }
        return Result.build((boolean)success);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Result<Void> editConfig(ConfigDTO configDTO, String operator) {
        Result<Void> checkResult = this.checkParam(configDTO);
        if (checkResult.failed()) {
            return checkResult;
        }
        this.initConfig(configDTO);
        Result<Void> statusCheckResult = this.checkStatus(configDTO.getStatus());
        if (statusCheckResult.failed()) {
            return statusCheckResult;
        }
        ConfigPO currentConfig = this.getCurrentConfig(configDTO);
        if (currentConfig == null) {
            Result<Long> addResult = this.addConfig(configDTO, operator);
            return Result.buildFrom(addResult);
        }
        ConfigPO sameNameConfig = this.getByGroupAndNameFromDB(configDTO.getValueGroup(), configDTO.getValueName());
        if (sameNameConfig != null && !Objects.equals(sameNameConfig.getId(), currentConfig.getId())) {
            return Result.buildDuplicate((String)CONFIG_DUPLICATE);
        }
        ConfigPO updateConfig = CopyBeanUtil.copy(configDTO, ConfigPO.class);
        if (updateConfig == null) {
            throw new IllegalStateException("\u914d\u7f6e\u5bf9\u8c61\u8f6c\u6362\u5931\u8d25");
        }
        updateConfig.setId(currentConfig.getId());
        updateConfig.setOperator(operator);
        boolean success = this.configDao.update(updateConfig) == 1;
        return Result.build((boolean)success);
    }

    @Override
    @Transactional(transactionManager="yakSecurityTransactionManager", rollbackFor={Exception.class})
    public Result<Void> switchConfig(Long configId, Integer status, String operator) {
        if (configId == null) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Result<Void> statusCheckResult = this.checkStatus(status);
        if (statusCheckResult.failed()) {
            return statusCheckResult;
        }
        ConfigPO configPO = this.configDao.getbyId(configId);
        if (configPO == null) {
            return Result.buildNotExist((String)CONFIG_NOT_EXIST);
        }
        configPO.setStatus(status);
        configPO.setOperator(operator);
        boolean success = this.configDao.updateById(configPO) == 1;
        return Result.build((boolean)success);
    }

    @Override
    public PagingData<ConfigVO> pagingConfig(ConfigQueryDTO queryDTO) {
        IPage<ConfigPO> configPage = this.configDao.selectPage(queryDTO);
        List<ConfigVO> configList = CopyBeanUtil.copyList(configPage.getRecords(), ConfigVO.class);
        return PagingData.from((PageData)new PageData(configList, configPage.getTotal(), configPage.getPages(), configPage.getCurrent(), configPage.getSize()));
    }

    @Override
    public List<ConfigVO> queryByCondt(ConfigDTO condition) {
        ConfigPO queryCondition = CopyBeanUtil.copy(condition, ConfigPO.class);
        List<ConfigPO> configList = this.configDao.listByCondition(queryCondition);
        return CopyBeanUtil.copyList(configList, ConfigVO.class);
    }

    @Override
    public List<String> listGroups() {
        return this.configDao.listDistinctGroup();
    }

    @Override
    public List<ConfigVO> listConfigByGroup(String valueGroup) {
        List<ConfigPO> configList = this.configDao.listConfigByGroup(valueGroup);
        return CopyBeanUtil.copyList(configList, ConfigVO.class);
    }

    @Override
    public ConfigVO getConfigById(Long configId) {
        ConfigPO configPO = this.configDao.getbyId(configId);
        return CopyBeanUtil.copy(configPO, ConfigVO.class);
    }

    @Override
    public String stringSetting(String valueGroup, String valueName, String defaultValue) {
        return this.getSetting(valueGroup, valueName, defaultValue, value -> value, "stringSetting");
    }

    @Override
    public Integer intSetting(String valueGroup, String valueName, Integer defaultValue) {
        return this.getSetting(valueGroup, valueName, defaultValue, Integer::valueOf, "intSetting");
    }

    @Override
    public Long longSetting(String valueGroup, String valueName, Long defaultValue) {
        return this.getSetting(valueGroup, valueName, defaultValue, Long::valueOf, "longSetting");
    }

    @Override
    public Double doubleSetting(String valueGroup, String valueName, Double defaultValue) {
        return this.getSetting(valueGroup, valueName, defaultValue, Double::valueOf, "doubleSetting");
    }

    @Override
    public Boolean booleanSetting(String valueGroup, String valueName, Boolean defaultValue) {
        return this.getSetting(valueGroup, valueName, defaultValue, this::parseBoolean, "booleanSetting");
    }

    @Override
    public <T> T objectSetting(String valueGroup, String valueName, T defaultValue, Class<T> type) {
        return (T)this.getSetting(valueGroup, valueName, defaultValue, value -> JsonUtils.fromJson(value, type), "objectSetting");
    }

    private <T> T getSetting(String valueGroup, String valueName, T defaultValue, Function<String, T> converter, String methodName) {
        try {
            ConfigPO configPO = this.getByGroupAndNameFromDB(valueGroup, valueName);
            if (configPO == null || !StringUtils.hasText((String)configPO.getValue())) {
                return defaultValue;
            }
            return converter.apply(configPO.getValue());
        }
        catch (Exception exception) {
            LOGGER.warn("\u8bfb\u53d6\u914d\u7f6e\u5931\u8d25\uff0c\u65b9\u6cd5={}\uff0c\u914d\u7f6e\u7ec4={}\uff0c\u914d\u7f6e\u540d={}", new Object[]{methodName, valueGroup, valueName, exception});
            return defaultValue;
        }
    }

    private ConfigPO getCurrentConfig(ConfigDTO configDTO) {
        if (configDTO.getId() != null) {
            return this.configDao.getbyId(configDTO.getId());
        }
        return this.getByGroupAndNameFromDB(configDTO.getValueGroup(), configDTO.getValueName());
    }

    private Result<Void> checkParam(ConfigDTO configDTO) {
        if (configDTO == null) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (!StringUtils.hasText((String)configDTO.getValueGroup())) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e\u7ec4\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (!StringUtils.hasText((String)configDTO.getValueName())) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return Result.success(null);
    }

    private Result<Void> checkStatus(Integer status) {
        boolean normal = Objects.equals(status, ConfigStatusEnum.NORMAL.getCode());
        boolean disabled = Objects.equals(status, ConfigStatusEnum.DISABLE.getCode());
        if (!normal && !disabled) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e\u72b6\u6001\u53ea\u80fd\u662f\u6b63\u5e38\u6216\u7981\u7528");
        }
        return Result.success(null);
    }

    private void initConfig(ConfigDTO configDTO) {
        if (configDTO.getStatus() == null) {
            configDTO.setStatus(ConfigStatusEnum.NORMAL.getCode());
        }
        if (configDTO.getValue() == null) {
            configDTO.setValue("");
        }
        if (configDTO.getMemo() == null) {
            configDTO.setMemo("");
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

    private ConfigPO getByGroupAndNameFromDB(String valueGroup, String valueName) {
        return this.configDao.getByGroupAndName(valueGroup, valueName);
    }
}

