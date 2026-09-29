package io.yak.ops.business.approval.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** steps_json 编解码单测(ticket 102):级数/人数上限、trim、同级去重、脏数据拒绝。 */
class FlowStepsCodecTest {

  @Test
  void serializeThenParseRoundTrips() {
    String json = FlowStepsCodec.serialize(List.of(List.of("alice", "bob"), List.of("carol")));

    List<FlowStepConfig> steps = FlowStepsCodec.parse(json);

    assertEquals(2, steps.size());
    assertEquals(1, steps.get(0).level());
    assertEquals(List.of("alice", "bob"), steps.get(0).approvers());
    assertEquals(2, steps.get(1).level());
    assertEquals(List.of("carol"), steps.get(1).approvers());
  }

  @Test
  void serializeTrimsAndDedupesSameLevel() {
    String json = FlowStepsCodec.serialize(
        List.of(Arrays.asList(" alice ", "alice", "bob", null, " ")));

    List<FlowStepConfig> steps = FlowStepsCodec.parse(json);
    assertEquals(List.of("alice", "bob"), steps.get(0).approvers());
  }

  @Test
  void serializeRewritesLevelByOrder() {
    String json = FlowStepsCodec.serialize(List.of(List.of("a"), List.of("b")));
    assertTrue(json.contains("\"level\":1"));
    assertTrue(json.contains("\"level\":2"));
  }

  @Test
  void serializeRejectsMoreThanTwoLevels() {
    ApprovalException ex = assertThrows(ApprovalException.class,
        () -> FlowStepsCodec.serialize(List.of(List.of("a"), List.of("b"), List.of("c"))));
    assertEquals(ApprovalErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("2 级"));
  }

  @Test
  void serializeRejectsEmptyLevels() {
    assertThrows(ApprovalException.class, () -> FlowStepsCodec.serialize(List.of()));
    assertThrows(ApprovalException.class, () -> FlowStepsCodec.serialize(null));
  }

  @Test
  void serializeRejectsLevelWithOnlyBlankApprovers() {
    ApprovalException ex = assertThrows(ApprovalException.class,
        () -> FlowStepsCodec.serialize(List.of(List.of("a"), List.of(" ", ""))));
    assertTrue(ex.getMessage().contains("第 2 级"));
  }

  @Test
  void serializeRejectsMoreThanTenApproversPerLevel() {
    List<String> tooMany = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      tooMany.add("user" + i);
    }
    assertThrows(ApprovalException.class, () -> FlowStepsCodec.serialize(List.of(tooMany)));
  }

  @Test
  void parseRejectsMalformedJson() {
    ApprovalException ex =
        assertThrows(ApprovalException.class, () -> FlowStepsCodec.parse("not-json"));
    assertEquals(ApprovalErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
  }

  @Test
  void parseRejectsEmptyArrayAndBlank() {
    assertThrows(ApprovalException.class, () -> FlowStepsCodec.parse("[]"));
    assertThrows(ApprovalException.class, () -> FlowStepsCodec.parse("  "));
    assertThrows(ApprovalException.class, () -> FlowStepsCodec.parse(null));
  }

  @Test
  void flowStepConfigIsImmutable() {
    List<String> mutable = new ArrayList<>(List.of("a"));
    FlowStepConfig config = new FlowStepConfig(1, mutable);
    mutable.add("b");
    assertEquals(List.of("a"), config.approvers());
    assertThrows(UnsupportedOperationException.class, () -> config.approvers().add("c"));
    // null 归一化为空列表
    assertEquals(Collections.emptyList(), new FlowStepConfig(1, null).approvers());
  }
}
