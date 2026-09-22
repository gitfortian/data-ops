/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.PagingData
 *  io.yak.framework.common.Result
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.vo.oplog.OplogOptionsVO;
import io.yak.framework.security.common.vo.oplog.OplogVO;
import io.yak.framework.security.service.OplogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u64cd\u4f5c\u65e5\u5fd7\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/oplog"})
public class OplogController {
    private final OplogService oplogService;

    public OplogController(OplogService oplogService) {
        this.oplogService = oplogService;
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u64cd\u4f5c\u65e5\u5fd7")
    @PostMapping(value={"/page"})
    public Result<PagingData<OplogVO>> page(@RequestBody(required=false) OplogQueryDTO queryDTO) {
        PagingData<OplogVO> pagingData = this.oplogService.getOplogPage(queryDTO);
        return Result.success(pagingData);
    }

    @Operation(summary="\u67e5\u8be2\u64cd\u4f5c\u65e5\u5fd7\u7b5b\u9009\u9009\u9879")
    @GetMapping(value={"/options"})
    public Result<OplogOptionsVO> options() {
        return Result.success((Object)this.oplogService.getOptions());
    }

    @Operation(summary="\u6839\u636e\u64cd\u4f5c\u65e5\u5fd7 ID \u67e5\u8be2\u65e5\u5fd7\u8be6\u60c5")
    @GetMapping(value={"/{id}"})
    public Result<OplogVO> detail(@PathVariable(value="id") Long oplogId) {
        return Result.success((Object)this.oplogService.getOplogDetailByOplogId(oplogId));
    }

    @Operation(summary="\u67e5\u8be2\u5168\u90e8\u64cd\u4f5c\u76ee\u6807\u7c7b\u578b")
    @GetMapping(value={"/type/list"})
    public Result<List<String>> targetTypeList() {
        return Result.success(this.oplogService.listTargetType());
    }
}

