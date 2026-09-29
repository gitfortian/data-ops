package io.yak.ops.business.sync.offline.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineExecutionEventMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobExecutionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineWriteMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineJobDefinitionDaoProjectScopeTest {

  @BeforeAll
  static void initializeLambdaCache() {
    // 纯单测无 MyBatis 启动流程，Lambda 条件渲染需要预先注册 TableInfo。
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), ""),
        OfflineJobDefinitionPO.class);
  }

  @Mock private OfflineJobDefinitionMapper mapper;
  @Mock private OfflineJobExecutionMapper executionMapper;
  @Mock private OfflineExecutionEventMapper eventMapper;
  @Mock private OfflineWriteMapper writeMapper;

  @Test
  void insertBindsDefinitionToCurrentProject() {
    when(mapper.insert(any(OfflineJobDefinitionPO.class))).thenReturn(1);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    OfflineJobDefinitionDaoImpl dao =
        new OfflineJobDefinitionDaoImpl(
            mapper, executionMapper, eventMapper, writeMapper, currentProject);
    OfflineJobDefinitionPO definition = new OfflineJobDefinitionPO();
    definition.setId(11L);
    definition.setJobName("orders-sync");

    assertThat(dao.insert(definition)).isTrue();

    ArgumentCaptor<OfflineJobDefinitionPO> captor =
        ArgumentCaptor.forClass(OfflineJobDefinitionPO.class);
    verify(mapper).insert(captor.capture());
    assertThat(captor.getValue().getProjectId()).isEqualTo(7L);
  }

  @Test
  void pageAlwaysUsesCurrentProjectPredicate() {
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project A"));
    OfflineJobDefinitionDaoImpl dao =
        new OfflineJobDefinitionDaoImpl(
            mapper, executionMapper, eventMapper, writeMapper, currentProject);

    dao.selectPage(null);

    verify(mapper).selectPage(
        any(Page.class),
        argThat(
            wrapper ->
                wrapper.getSqlSegment().contains("project_id")
                    && wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper
                    && abstractWrapper.getParamNameValuePairs().containsValue(7L)));
  }
}
