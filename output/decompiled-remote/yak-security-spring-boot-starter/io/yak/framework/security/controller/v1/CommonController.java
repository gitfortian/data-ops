/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.Result
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u516c\u5171\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/common"})
public class CommonController {
    @Operation(summary="\u5065\u5eb7\u68c0\u67e5")
    @GetMapping(value={"/heart"})
    @PublicEndpoint
    public Result<String> health() {
        return Result.success((Object)"\u4e00\u4e2a\u666e\u901a\u7684\u8bf7\u6c42\u54cd\u5e94\u4e86\u666e\u901a\u7684\u7ed3\u679c");
    }
}

