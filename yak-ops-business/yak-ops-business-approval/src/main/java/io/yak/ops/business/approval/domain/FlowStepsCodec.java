package io.yak.ops.business.approval.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** steps_json 编解码:序列化前统一校验(1~2 级、每级 1~10 人、trim、同级去重、level 按序重编)。 */
public final class FlowStepsCodec {

  public static final int MAX_LEVELS = 2;
  public static final int MAX_APPROVERS_PER_LEVEL = 10;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 校验并归一化各审批人名单,返回可直接落库的 steps_json。 */
  public static String serialize(List<List<String>> levels) {
    try {
      return MAPPER.writeValueAsString(normalize(levels));
    } catch (JsonProcessingException e) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, "流程配置序列化失败", e);
    }
  }

  public static List<FlowStepConfig> normalize(List<List<String>> levels) {
    if (levels == null || levels.isEmpty()) {
      throw invalid("审批级数至少 1 级");
    }
    if (levels.size() > MAX_LEVELS) {
      throw invalid("审批级数最多 " + MAX_LEVELS + " 级");
    }
    List<FlowStepConfig> result = new ArrayList<>(levels.size());
    for (int i = 0; i < levels.size(); i++) {
      List<String> approvers = distinctTrimmed(levels.get(i));
      if (approvers.isEmpty()) {
        throw invalid("第 " + (i + 1) + " 级审批人不能为空");
      }
      if (approvers.size() > MAX_APPROVERS_PER_LEVEL) {
        throw invalid("第 " + (i + 1) + " 级审批人最多 " + MAX_APPROVERS_PER_LEVEL + " 人");
      }
      result.add(new FlowStepConfig(i + 1, approvers));
    }
    return result;
  }

  /** 解析落库的 steps_json;数据只经本类写入,非法内容视为脏数据并拒绝。 */
  public static List<FlowStepConfig> parse(String stepsJson) {
    if (stepsJson == null || stepsJson.isBlank()) {
      throw invalid("流程配置为空");
    }
    List<FlowStepConfig> raw;
    try {
      raw = MAPPER.readValue(stepsJson, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, "流程配置解析失败", e);
    }
    List<List<String>> levels = raw == null ? List.of()
        : raw.stream().map(FlowStepConfig::approvers).toList();
    return normalize(levels);
  }

  private static List<String> distinctTrimmed(List<String> approvers) {
    if (approvers == null) {
      return List.of();
    }
    Set<String> distinct = new LinkedHashSet<>();
    for (String approver : approvers) {
      if (approver != null && !approver.isBlank()) {
        distinct.add(approver.trim());
      }
    }
    return List.copyOf(distinct);
  }

  private static ApprovalException invalid(String detail) {
    return new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, detail);
  }

  private FlowStepsCodec() {}
}
