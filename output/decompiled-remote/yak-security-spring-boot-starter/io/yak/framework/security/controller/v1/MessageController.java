/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.swagger.v3.oas.annotations.Operation
 *  io.swagger.v3.oas.annotations.tags.Tag
 *  io.yak.framework.common.Result
 *  jakarta.servlet.http.HttpServletRequest
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
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.message.MessageBatchReadDTO;
import io.yak.framework.security.common.dto.message.MessagePageQueryDTO;
import io.yak.framework.security.common.dto.message.MessageReadDTO;
import io.yak.framework.security.common.vo.message.MessagePageVO;
import io.yak.framework.security.common.vo.message.MessageVO;
import io.yak.framework.security.service.MessageService;
import io.yak.framework.security.util.HttpRequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Yak Security - \u6d88\u606f\u7ba1\u7406\u63a5\u53e3")
@RestController
@RequestMapping(value={"/yak-security/api/v1/message"})
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary="\u67e5\u8be2\u5f53\u524d\u7528\u6237\u6d88\u606f")
    @GetMapping(value={"/list", "/list/{readTag}"})
    public Result<List<MessageVO>> list(@PathVariable(required=false) Boolean readTag, HttpServletRequest request) {
        return Result.success(this.messageService.getMessageListByUsernameAndReadTag(HttpRequestUtil.getOperator(request), readTag));
    }

    @Operation(summary="\u5206\u9875\u67e5\u8be2\u5f53\u524d\u7528\u6237\u6d88\u606f")
    @GetMapping(value={"/page"})
    public Result<MessagePageVO> page(MessagePageQueryDTO queryDTO, HttpServletRequest request) {
        return Result.success((Object)this.messageService.getMessagePage(HttpRequestUtil.getOperator(request), queryDTO));
    }

    @Operation(summary="\u67e5\u8be2\u5f53\u524d\u7528\u6237\u6d88\u606f\u8be6\u60c5")
    @GetMapping(value={"/detail"})
    public Result<MessageVO> detail(@RequestParam(value="id") Long messageId, HttpServletRequest request) {
        return Result.success((Object)this.messageService.getMessageDetail(HttpRequestUtil.getOperator(request), messageId));
    }

    @Operation(summary="\u6279\u91cf\u5207\u6362\u6d88\u606f\u5df2\u8bfb\u72b6\u6001")
    @PutMapping(value={"/switch"})
    public Result<Void> switchStatus(@RequestBody List<Long> messageIdList, HttpServletRequest request) {
        this.messageService.changeMessageStatus(HttpRequestUtil.getOperator(request), messageIdList);
        return Result.success(null);
    }

    @Operation(summary="\u6807\u8bb0\u5355\u6761\u6d88\u606f\u4e3a\u5df2\u8bfb")
    @PostMapping(value={"/mark-read"})
    public Result<Void> markRead(@RequestBody MessageReadDTO readDTO, HttpServletRequest request) {
        this.messageService.markMessageRead(HttpRequestUtil.getOperator(request), readDTO == null ? null : readDTO.getId());
        return Result.success(null);
    }

    @Operation(summary="\u6279\u91cf\u6807\u8bb0\u6d88\u606f\u4e3a\u5df2\u8bfb")
    @PostMapping(value={"/batch-read"})
    public Result<Void> batchRead(@RequestBody MessageBatchReadDTO readDTO, HttpServletRequest request) {
        this.messageService.markMessagesRead(HttpRequestUtil.getOperator(request), readDTO == null ? null : readDTO.getIds());
        return Result.success(null);
    }

    @Operation(summary="\u67e5\u8be2\u5f53\u524d\u7528\u6237\u672a\u8bfb\u6d88\u606f\u6570\u91cf")
    @GetMapping(value={"/unread-count"})
    public Result<Integer> unreadCount(HttpServletRequest request) {
        return Result.success((Object)this.messageService.getUnreadMessageCount(HttpRequestUtil.getOperator(request)));
    }
}

