package io.yak.ops.business.asset.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 上架审批发起单测(M2-5):命令组装与快照、台账缺失/状态不符拦截。 */
class AssetPublishApprovalServiceTest {

  private ApprovalApi approvalApi;
  private AssetItemMapper itemMapper;
  private AssetPublishApprovalService service;

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetItemPO.class);
    approvalApi = mock(ApprovalApi.class);
    itemMapper = mock(AssetItemMapper.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(1L);
    service = new AssetPublishApprovalService(approvalApi, currentProject, itemMapper);
  }

  @Test
  void submitBuildsCommandWithLedgerSnapshot() {
    when(itemMapper.selectOne(any())).thenReturn(item(AssetStatus.PENDING.name()));
    when(approvalApi.submit(any())).thenReturn(new ApprovalInstanceView(
        7L, ApprovalFlowCodes.ASSET_PUBLISH, "资产上架审批", "ASSET", "42", "t", null,
        "tom", "PENDING", 1, LocalDateTime.now(), null));

    service.submit(42L, "tom");

    ArgumentCaptor<ApprovalSubmitCommand> captor =
        ArgumentCaptor.forClass(ApprovalSubmitCommand.class);
    verify(approvalApi).submit(captor.capture());
    ApprovalSubmitCommand cmd = captor.getValue();
    assertEquals(ApprovalFlowCodes.ASSET_PUBLISH, cmd.flowCode());
    assertEquals("ASSET", cmd.bizType());
    assertEquals("42", cmd.bizId());
    assertTrue(cmd.title().contains("订单表"));
    assertTrue(cmd.payloadJson().contains("\"assetId\":42"));
    assertEquals("tom", cmd.applicant());
  }

  @Test
  void submitRejectsMissingAsset() {
    when(itemMapper.selectOne(any())).thenReturn(null);

    assertThrows(AssetException.class, () -> service.submit(404L, "tom"));
    verify(approvalApi, never()).submit(any());
  }

  @Test
  void submitRejectsAlreadyPublished() {
    when(itemMapper.selectOne(any())).thenReturn(item(AssetStatus.PUBLISHED.name()));

    assertThrows(AssetException.class, () -> service.submit(42L, "tom"));
    verify(approvalApi, never()).submit(any());
  }

  private static AssetItemPO item(String status) {
    AssetItemPO po = new AssetItemPO();
    po.setId(42L);
    po.setProjectId(1L);
    po.setName("订单表");
    po.setStatus(status);
    return po;
  }
}
