package io.yak.ops.business.alert.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.alert.service.AlertService;
import io.yak.ops.common.bean.dto.alert.AlertChannelSaveDTO;
import io.yak.ops.common.bean.dto.alert.AlertSendDTO;
import io.yak.ops.common.bean.dto.alert.AlertTestDTO;
import io.yak.ops.common.bean.vo.alert.AlertChannelVO;
import io.yak.ops.common.constant.alert.AlertConstants;
import io.yak.ops.plugin.alert.api.AlertResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 告警管理接口。 */
@Tag(name = "告警管理接口")
@RestController
@RequiredArgsConstructor
@RequestMapping(AlertConstants.API_PREFIX)
public class AlertController {

  private final AlertService alertService;

  @Operation(summary = "发送告警消息")
  @PostMapping("/send")
  public Result<AlertResult> send(@Valid @RequestBody AlertSendDTO dto) {
    return Result.success(alertService.send(dto));
  }

  @Operation(summary = "测试告警渠道连通性")
  @PostMapping("/test-connection")
  public Result<Boolean> testConnection(@RequestBody AlertTestDTO dto) {
    return Result.success(alertService.testConnection(dto.getChannelType(), dto.getConfigJson()));
  }

  @Operation(summary = "列出所有已注册的告警渠道")
  @GetMapping("/channels")
  public Result<List<AlertChannelVO>> listChannels() {
    return Result.success(alertService.listChannels());
  }

  @Operation(summary = "获取指定渠道的详细配置")
  @GetMapping("/channels/{channelType}")
  public Result<AlertChannelVO> getChannel(@PathVariable String channelType) {
    return Result.success(alertService.getChannel(channelType));
  }

  @Operation(summary = "保存告警渠道配置")
  @PutMapping("/channels")
  public Result<Boolean> saveChannel(@Valid @RequestBody AlertChannelSaveDTO dto) {
    return Result.success(alertService.saveChannel(dto));
  }

  @Operation(summary = "切换告警渠道启用状态")
  @PutMapping("/channels/{channelType}/enabled")
  public Result<Boolean> toggleEnabled(
      @PathVariable String channelType,
      @RequestParam boolean enabled) {
    return Result.success(alertService.toggleEnabled(channelType, enabled));
  }
}
