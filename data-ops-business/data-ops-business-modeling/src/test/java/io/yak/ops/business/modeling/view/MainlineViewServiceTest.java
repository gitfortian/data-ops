package io.yak.ops.business.modeling.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主线视图单元测试:覆盖分组、未分层兜底、未知过程拒绝。 */
class MainlineViewServiceTest {

  private ProcessApi processApi;
  private ModelRepository modelRepository;
  private ModelingModelMapper modelMapper;
  private MainlineViewService service;

  @BeforeEach
  void setUp() {
    processApi = mock(ProcessApi.class);
    modelRepository = mock(ModelRepository.class);
    modelMapper = mock(ModelingModelMapper.class);
    CurrentProject currentProject = () -> Optional.of(new io.yak.ops.core.project.ProjectContext(1L, "p"));
    service = new MainlineViewService(processApi, modelRepository, modelMapper, currentProject);
  }

  @Test
  void coverageGroupsModelsByLayer() {
    BusinessProcess process = new BusinessProcess(50L, "place_order", "下单", 7L, null, "FACT",
        null, null, 0, null, null, null);
    when(processApi.listProcesses(null)).thenReturn(List.of(process));
    when(modelRepository.modelIdsByProcess(50L)).thenReturn(List.of(1L, 2L));
    ModelingModelPO dwd = new ModelingModelPO();
    dwd.setId(1L);
    dwd.setModelCode("dwd_order");
    dwd.setModelName("订单明细");
    dwd.setStatus("DRAFT");
    dwd.setLayerCode("DWD");
    ModelingModelPO ods = new ModelingModelPO();
    ods.setId(2L);
    ods.setModelCode("ods_order");
    ods.setModelName("订单贴源");
    ods.setStatus("PUBLISHED");
    ods.setLayerCode("ODS");
    Mockito.when(modelMapper.selectList(Mockito.any()))
        .thenAnswer(invocation -> List.of(dwd))
        .thenAnswer(invocation -> List.of(ods));

    List<MainlineViewService.ProcessCoverage> coverage = service.mainline();
    assertEquals(1, coverage.size());
    assertEquals(2, coverage.get(0).totalModels());
    assertEquals(2, coverage.get(0).layers().size());
    assertEquals("DWD", coverage.get(0).layers().get(0).layerCode());
  }

  @Test
  void unknownProcessIsRejected() {
    when(processApi.listProcesses(null)).thenReturn(List.of());
    org.junit.jupiter.api.Assertions.assertThrows(
        ModelingException.class, () -> service.coverageOf(99L));
  }
}
