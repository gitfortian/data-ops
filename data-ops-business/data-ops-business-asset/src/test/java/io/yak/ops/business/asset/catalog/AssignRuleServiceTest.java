package io.yak.ops.business.asset.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.catalog.AssignRuleService.DryRunResult;
import io.yak.ops.business.asset.catalog.AssignRuleService.RuleConditions;
import io.yak.ops.business.asset.catalog.AssignRuleService.RuleView;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests;
import io.yak.ops.business.asset.dao.mapper.AssetAssignRuleMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.asset.dao.model.AssetAssignRulePO;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 编目规则单测:匹配纯函数、D10 试跑门槛、建议目录/标签、重应用。 */
class AssignRuleServiceTest {

  private AssetAssignRuleMapper ruleMapper;
  private AssetItemMapper itemMapper;
  private AssetTagRelMapper tagRelMapper;
  private DirectoryService directoryService;
  private AssignRuleService service;

  @BeforeEach
  void setUp() {
    ruleMapper = Mockito.mock(AssetAssignRuleMapper.class);
    itemMapper = Mockito.mock(AssetItemMapper.class);
    tagRelMapper = Mockito.mock(AssetTagRelMapper.class);
    directoryService = mock(DirectoryService.class);
    TagService tagService = mock(TagService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    AuditOperationHandle handle = mock(AuditOperationHandle.class);
    lenient().when(currentProject.requireProjectId()).thenReturn(1L);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    service = new AssignRuleService(currentProject, ruleMapper, itemMapper, tagRelMapper,
        directoryService, tagService, auditService);
  }

  // ---------- 匹配纯函数 ----------

  @Test
  void wildcardConditionsMatchEverything() {
    assertTrue(AssignRuleService.matches(RuleConditions.WILDCARD, item("TABLE", "DWD", null)));
  }

  @Test
  void listConditionsAreOrWithinFieldAndAndAcrossFields() {
    RuleConditions c = new RuleConditions(List.of("TABLE", "METRIC"), List.of("dwd"),
        null, null, null, null);
    assertTrue(AssignRuleService.matches(c, item("TABLE", "DWD", null)));
    assertTrue(AssignRuleService.matches(c, item("METRIC", "dwd", null)));
    assertFalse(AssignRuleService.matches(c, item("DOC", "DWD", null)));
    assertFalse(AssignRuleService.matches(c, item("TABLE", "ADS", null)));
    // null 字段不匹配显式列表条件
    assertFalse(AssignRuleService.matches(c, item("TABLE", null, null)));
  }

  @Test
  void regexAndKeywordMatching() {
    RuleConditions regex = new RuleConditions(null, null, null, null, "^dwd_", null);
    assertTrue(AssignRuleService.matches(regex, itemNamed("TABLE", "dwd_order_detail")));
    assertFalse(AssignRuleService.matches(regex, itemNamed("TABLE", "ads_dwd_x")));

    RuleConditions keyword = new RuleConditions(null, null, null, null, null, "支付");
    AssetItemPO byDesc = itemNamed("TABLE", "t");
    byDesc.setDescription("用户支付金额明细");
    assertTrue(AssignRuleService.matches(keyword, byDesc));
    assertFalse(AssignRuleService.matches(keyword, itemNamed("TABLE", "订单")));
  }

  @Test
  void invalidRegexIsRejectedOnCreate() {
    AssetException ex = assertThrows(AssetException.class,
        () -> service.create(dto("DIRECTORY", "订单", "([unclosed", 5L, null), "tester"));
    assertEquals(AssetErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    verify(ruleMapper, never()).insert(any(AssetAssignRulePO.class));
  }

  @Test
  void directoryRuleRequiresTargetDirectory() {
    AssetException ex = assertThrows(AssetException.class,
        () -> service.create(dto("DIRECTORY", "订单", null, null, null), "tester"));
    assertEquals(AssetErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
  }

  // ---------- D10 试跑门槛 ----------

  @Test
  void enableBlockedUntilDryRun() {
    AssetAssignRulePO rule = rule(1L, "DIRECTORY", true, null);
    when(ruleMapper.selectOne(any())).thenReturn(rule);
    AssetException ex = assertThrows(AssetException.class,
        () -> service.setEnabled(1L, true, "tester"));
    assertEquals(AssetErrorCode.RULE_DRY_RUN_REQUIRED, ex.getErrorCode());

    when(itemMapper.selectList(any())).thenReturn(List.of(item("TABLE", "DWD", null)));
    DryRunResult dry = service.dryRun(1L, "tester");
    assertEquals(1, dry.hitCount());
    assertEquals(Integer.valueOf(1), rule.getLastApplyHit());

    service.setEnabled(1L, true, "tester");
    assertTrue(rule.getEnabled());
  }

  @Test
  void createStartsDisabledAndUntested() {
    RuleView view = service.create(dto("TAG", "订单", null, null, 9L), "tester");
    assertFalse(view.enabled());
    assertNull(view.lastApplyHit());
    verify(ruleMapper).insert(any(AssetAssignRulePO.class));
  }

  @Test
  void updateResetsTestedFlag() {
    AssetAssignRulePO rule = rule(1L, "DIRECTORY", true, 3);
    rule.setEnabled(true);
    when(ruleMapper.selectOne(any())).thenReturn(rule);
    RuleView view = service.update(1L, dto("DIRECTORY", "订单新", null, 5L, null), "tester");
    assertFalse(view.enabled());
    assertNull(view.lastApplyHit());
  }

  // ---------- 对账建议 & 重应用 ----------

  @Test
  void suggestedDirectoryTakesFirstMatchInPriorityOrder() {
    AssetAssignRulePO first = rule(1L, "DIRECTORY", true, 1);
    first.setConditions(toJson(Map.of("layerCodes", List.of("DWD"))));
    first.setTargetDirectoryId(100L);
    AssetAssignRulePO second = rule(2L, "DIRECTORY", true, 2);
    second.setConditions(RuleConditions.toJson(RuleConditions.WILDCARD));
    second.setTargetDirectoryId(200L);
    when(ruleMapper.selectList(any())).thenReturn(List.of(first, second));

    assertEquals(100L,
        service.suggestedDirectoryId(1L, item("TABLE", "DWD", null)));
    assertEquals(200L, service.suggestedDirectoryId(1L, item("TABLE", "ADS", null)));
  }

  @Test
  void applyAgainUpdatesOnlyMismatchedItems() {
    AssetAssignRulePO rule = rule(1L, "DIRECTORY", true, 1);
    rule.setTargetDirectoryId(100L);
    when(ruleMapper.selectOne(any())).thenReturn(rule);
    AssetItemPO already = item("TABLE", "DWD", null);
    already.setDirectoryId(100L);
    AssetItemPO mismatch = item("TABLE", "DWD", null);
    when(itemMapper.selectList(any())).thenReturn(List.of(already, mismatch));

    int applied = service.applyAgain(1L, "tester");

    assertEquals(1, applied);
    assertEquals(100L, mismatch.getDirectoryId());
    verify(itemMapper, Mockito.times(1)).updateById(any(AssetItemPO.class));
  }

  // ---------- fixtures ----------

  private static String toJson(Map<String, Object> map) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static AssetRequests.RuleUpsertDTO dto(String type, String keyword, String regex,
      Long dirId, Long tagId) {
    AssetRequests.RuleUpsertDTO dto = new AssetRequests.RuleUpsertDTO();
    dto.setRuleName("规则");
    dto.setRuleType(type);
    AssetRequests.RuleConditionsDTO conditions = new AssetRequests.RuleConditionsDTO();
    conditions.setKeyword(keyword);
    conditions.setNameRegex(regex);
    dto.setConditions(conditions);
    dto.setTargetDirectoryId(dirId);
    dto.setTargetTagId(tagId);
    dto.setPriority(10);
    return dto;
  }

  private static AssetAssignRulePO rule(Long id, String type, boolean deleted, Integer hit) {
    AssetAssignRulePO po = new AssetAssignRulePO();
    po.setId(id);
    po.setProjectId(1L);
    po.setRuleName("规则");
    po.setRuleType(type);
    po.setConditions(RuleConditions.toJson(RuleConditions.WILDCARD));
    po.setPriority(10);
    po.setEnabled(false);
    po.setLastApplyHit(hit);
    po.setDeleted(deleted);
    return po;
  }

  private static AssetItemPO item(String assetType, String layerCode, String domainCode) {
    AssetItemPO po = new AssetItemPO();
    po.setProjectId(1L);
    po.setAssetType(assetType);
    po.setLayerCode(layerCode);
    po.setDomainCode(domainCode);
    po.setSourceType("MODEL");
    po.setName("dwd_order");
    return po;
  }

  private static AssetItemPO itemNamed(String assetType, String name) {
    AssetItemPO po = item(assetType, "DWD", null);
    po.setName(name);
    return po;
  }
}
