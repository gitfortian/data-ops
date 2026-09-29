package io.yak.ops.business.development.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentStandardCheck;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.service.DerivedAwareSqlColumnLineageParser;
import io.yak.ops.business.development.service.SqlColumnLineageParser;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.business.semantic.api.StandardRecommendApi.NamingCheck;
import io.yak.ops.business.semantic.api.StandardRecommendApi.RecommendRequest;
import io.yak.ops.business.semantic.api.StandardRecommendApi.RecommendationReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DevelopmentStandardCheckServiceTest {

  @Test
  void checksInsertTargetColumnsInOutputOrder() {
    DevelopmentStandardCheckService service = service(api -> switch (api) {
      case "order_id" -> naming(true, true, 11L, "ID_STD", "标识标准", "^[a-z]+_id$");
      case "total_amount" -> naming(true, false, 12L, "AMT_STD", "金额标准", "^[a-z]+_amt$");
      default -> abstained();
    });

    DevelopmentStandardCheck report = service.check(
        1L,
        "SQL",
        "INSERT INTO dws.order_copy (order_id, total_amount) "
            + "SELECT s.id, SUM(s.amount) AS total_amount FROM ods.orders s GROUP BY s.id;");

    assertNull(report.parseError());
    assertEquals(2, report.fieldCount());
    assertEquals(
        List.of("order_id", "total_amount"),
        report.items().stream().map(DevelopmentStandardCheck.FieldCheck::field).toList());
    assertTrue(report.items().get(0).matched());
    assertEquals("ID_STD", report.items().get(0).standardCode());
    assertEquals(12L, report.items().get(1).standardId());
    assertEquals("^[a-z]+_amt$", report.items().get(1).ruleExpr());
  }

  @Test
  void checksBareSelectProjectionsViaSyntheticWrap() {
    DevelopmentStandardCheckService service = service(field -> abstained());

    DevelopmentStandardCheck report = service.check(
        1L,
        "SQL",
        "SELECT o.id AS order_id, o.amount * 2 AS ext_price FROM ods.orders o");

    assertNull(report.parseError());
    assertEquals(
        List.of("order_id", "ext_price"),
        report.items().stream().map(DevelopmentStandardCheck.FieldCheck::field).toList());
  }

  @Test
  void semanticFailureDegradesToUnevaluatedField() {
    DevelopmentStandardCheckService service = service(field -> {
      throw new IllegalStateException("semantic down");
    });

    DevelopmentStandardCheck report = service.check(
        1L, "SQL", "INSERT INTO dws.order_copy (order_id) SELECT s.id FROM ods.orders s");

    assertEquals("order_id", report.items().get(0).field());
    assertFalse(report.items().get(0).evaluated());
  }

  @Test
  void reportsParseErrorWithoutBlockingTheResponse() {
    DevelopmentStandardCheckService service = service(field -> abstained());

    DevelopmentStandardCheck report = service.check(1L, "SQL", "SELECT (");

    assertTrue(report.items().isEmpty());
    assertTrue(report.parseError() != null && !report.parseError().isBlank());
  }

  @Test
  void capsCheckedFieldsAndMarksTruncation() {
    DevelopmentStandardCheckService service = service(field -> abstained());
    String projection = IntStream.rangeClosed(1, 201)
        .mapToObj(i -> "c" + i + " AS f" + i)
        .collect(Collectors.joining(", "));

    DevelopmentStandardCheck report = service.check(
        1L, "SQL", "SELECT " + projection + " FROM ods.orders");

    assertEquals(201, report.fieldCount());
    assertTrue(report.truncated());
    assertEquals(200, report.items().size());
  }

  @Test
  void rejectsNonSqlNodeAndForeignTaskType() {
    DevelopmentNodeRepository repository = mock(DevelopmentNodeRepository.class);
    DevelopmentNode python = new DevelopmentNode(
        5L, "跑批", "PYTHON", null, null, true, Instant.now(), Instant.now());
    when(repository.findById(5L)).thenReturn(Optional.of(python));
    DevelopmentStandardCheckService service = new DevelopmentStandardCheckService(
        repository, new DerivedAwareSqlColumnLineageParser(), mockApi(field -> abstained()));

    assertThrows(
        IllegalArgumentException.class, () -> service.check(5L, "SQL", "SELECT 1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.check(1L, "PYTHON", "SELECT 1"));
    assertThrows(
        IllegalArgumentException.class, () -> service.check(1L, "SQL", "  "));
  }

  private static DevelopmentStandardCheckService service(
      Function<String, RecommendationReport> byField) {
    DevelopmentNodeRepository repository = mock(DevelopmentNodeRepository.class);
    when(repository.findById(1L)).thenReturn(Optional.of(new DevelopmentNode(
        1L, "订单汇总", "SQL", null, null, true, Instant.now(), Instant.now())));
    return new DevelopmentStandardCheckService(
        repository, new DerivedAwareSqlColumnLineageParser(), mockApi(byField));
  }

  private static StandardRecommendApi mockApi(
      Function<String, RecommendationReport> byField) {
    StandardRecommendApi api = mock(StandardRecommendApi.class);
    when(api.recommend(any(RecommendRequest.class)))
        .thenAnswer(invocation ->
            byField.apply(((RecommendRequest) invocation.getArgument(0)).fieldName()));
    return api;
  }

  private static RecommendationReport abstained() {
    return new RecommendationReport(
        new NamingCheck(false, false, null, null, null, null),
        List.of(), List.of(), List.of(),
        List.of(), List.of());
  }

  private static RecommendationReport naming(
      boolean evaluated, boolean matched, Long id, String code, String name, String ruleExpr) {
    return new RecommendationReport(
        new NamingCheck(evaluated, matched, id, code, name, ruleExpr),
        List.of(), List.of(), List.of(),
        List.of(), List.of());
  }
}
