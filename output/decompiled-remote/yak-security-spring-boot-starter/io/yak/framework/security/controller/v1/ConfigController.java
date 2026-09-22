/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  jakarta.servlet.http.HttpServletRequest
 *  org.springframework.web.bind.annotation.DeleteMapping
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.PutMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.config.ConfigDTO;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.vo.config.ConfigVO;
import io.yak.framework.security.service.ConfigService;
import io.yak.framework.security.util.HttpRequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u914d\u7f6e\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/config"})
public class ConfigController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(summary="\u6839\u636e\u6761\u4ef6\u67e5\u8be2\u914d\u7f6e\u5217\u8868")
    @PostMapping(value={"/list"})
    public Result<List<ConfigVO>> list(@RequestBody(required=false) ConfigDTO condition) {
        this.normalizeConfig(condition);
        return Result.success(this.configService.queryByCondt(condition));
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u914d\u7f6e")
    @PostMapping(value={"/page"})
    public Result<PagingData<ConfigVO>> page(@RequestBody(required=false) ConfigQueryDTO queryDTO) {
        ConfigQueryDTO query = this.normalizeQuery(queryDTO);
        PagingData<ConfigVO> pagingData = this.configService.pagingConfig(query);
        return Result.success(pagingData);
    }

    @Operation(summary="\u67e5\u8be2\u5168\u90e8\u914d\u7f6e\u5206\u7ec4")
    @GetMapping(value={"/group/list"})
    public Result<List<String>> groups() {
        return Result.success(this.configService.listGroups());
    }

    @Operation(summary="\u6839\u636e\u914d\u7f6e ID \u67e5\u8be2\u914d\u7f6e\u8be6\u60c5")
    @GetMapping(value={"/{id}"})
    public Result<ConfigVO> detail(@PathVariable(value="id") Long configId) {
        return this.getConfigResult(configId);
    }

    @Operation(summary="\u6839\u636e\u914d\u7f6e ID \u67e5\u8be2\u914d\u7f6e\u8be6\u60c5\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @GetMapping(value={"/get"})
    public Result<ConfigVO> get(@RequestParam(value="configId") Long configId) {
        return this.getConfigResult(configId);
    }

    @Operation(summary="\u65b0\u589e\u914d\u7f6e")
    @PostMapping
    public Result<Long> create(HttpServletRequest request, @RequestBody ConfigDTO configDTO) {
        return this.addConfig(request, configDTO);
    }

    @Operation(summary="\u65b0\u589e\u914d\u7f6e\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @PutMapping(value={"/add"})
    public Result<Long> add(HttpServletRequest request, @RequestBody ConfigDTO configDTO) {
        return this.addConfig(request, configDTO);
    }

    @Operation(summary="\u7f16\u8f91\u914d\u7f6e")
    @PutMapping
    public Result<Void> update(HttpServletRequest request, @RequestBody ConfigDTO configDTO) {
        return this.editConfig(request, configDTO);
    }

    @Operation(summary="\u7f16\u8f91\u914d\u7f6e\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @PostMapping(value={"/edit"})
    public Result<Void> edit(HttpServletRequest request, @RequestBody ConfigDTO configDTO) {
        return this.editConfig(request, configDTO);
    }

    @Operation(summary="\u5207\u6362\u914d\u7f6e\u72b6\u6001")
    @PutMapping(value={"/{id}/status"})
    public Result<Void> updateStatus(HttpServletRequest request, @PathVariable(value="id") Long configId, @RequestBody ConfigDTO configDTO) {
        return this.configService.switchConfig(configId, configDTO == null ? null : configDTO.getStatus(), HttpRequestUtil.getOperator(request));
    }

    @Operation(summary="\u5207\u6362\u914d\u7f6e\u72b6\u6001\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @PostMapping(value={"/switch"})
    public Result<Void> switchConfig(HttpServletRequest request, @RequestBody ConfigDTO configDTO) {
        return this.configService.switchConfig(configDTO == null ? null : configDTO.getId(), configDTO == null ? null : configDTO.getStatus(), HttpRequestUtil.getOperator(request));
    }

    @Operation(summary="\u5220\u9664\u914d\u7f6e")
    @DeleteMapping(value={"/{id}"})
    public Result<Void> deleteById(HttpServletRequest request, @PathVariable(value="id") Long configId) {
        return this.deleteConfig(request, configId);
    }

    @Operation(summary="\u5220\u9664\u914d\u7f6e\uff08\u517c\u5bb9\u63a5\u53e3\uff09")
    @DeleteMapping(value={"/del"})
    public Result<Void> delete(HttpServletRequest request, @RequestParam(value="id") Long configId) {
        return this.deleteConfig(request, configId);
    }

    private Result<ConfigVO> getConfigResult(Long configId) {
        if (configId == null) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        ConfigVO config = this.configService.getConfigById(configId);
        if (config == null) {
            return Result.buildNotExist((String)"\u914d\u7f6e\u4e0d\u5b58\u5728");
        }
        return Result.success((Object)config);
    }

    private Result<Long> addConfig(HttpServletRequest request, ConfigDTO configDTO) {
        this.normalizeConfig(configDTO);
        return this.configService.addConfig(configDTO, HttpRequestUtil.getOperator(request));
    }

    private Result<Void> editConfig(HttpServletRequest request, ConfigDTO configDTO) {
        if (configDTO == null) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e\u4fe1\u606f\u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (configDTO.getId() == null) {
            return Result.buildParamIllegal((String)"\u914d\u7f6e ID \u4e0d\u80fd\u4e3a\u7a7a");
        }
        if (this.configService.getConfigById(configDTO.getId()) == null) {
            return Result.buildNotExist((String)"\u914d\u7f6e\u4e0d\u5b58\u5728");
        }
        this.normalizeConfig(configDTO);
        return this.configService.editConfig(configDTO, HttpRequestUtil.getOperator(request));
    }

    private Result<Void> deleteConfig(HttpServletRequest request, Long configId) {
        return this.configService.delConfig(configId, HttpRequestUtil.getOperator(request));
    }

    private ConfigQueryDTO normalizeQuery(ConfigQueryDTO queryDTO) {
        ConfigQueryDTO query = queryDTO == null ? new ConfigQueryDTO() : queryDTO;
        query.setPage(Math.max(1, query.getPage()));
        query.setSize(query.getSize() <= 0 ? 10 : Math.min(query.getSize(), 200));
        query.setValueGroup(this.trim(query.getValueGroup()));
        query.setValueName(this.trim(query.getValueName()));
        query.setMemo(this.trim(query.getMemo()));
        query.setOperator(this.trim(query.getOperator()));
        return query;
    }

    private void normalizeConfig(ConfigDTO configDTO) {
        if (configDTO == null) {
            return;
        }
        configDTO.setValueGroup(this.trim(configDTO.getValueGroup()));
        configDTO.setValueName(this.trim(configDTO.getValueName()));
        configDTO.setMemo(this.trim(configDTO.getMemo()));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}

