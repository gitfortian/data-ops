package io.yak.ops.business.lineage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.lineage.dao.impl.LineageDaoImpl;
import io.yak.ops.business.lineage.dao.mapper.LineageAssetMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageQueryMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageRelationMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageWriteMapper;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.repository.LineageRepository;
import io.yak.ops.core.project.ProjectContext;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LineageDownstreamCountTest {
  @Test void verifiesVisibleRootAndCountsRelationsWithoutMaterializingGraph() {
    var repository = mock(LineageRepository.class);
    var assets = new LineageAssetReader(repository);
    var query = new LineageQueryService(assets, new LineageGraphReader(repository, assets));
    when(repository.findAsset(7)).thenReturn(Optional.of(mock(LineageAsset.class)));
    when(repository.countOutgoingRelations(7)).thenReturn(4L);
    assertEquals(4, query.downstreamRelationCount(7));
    verify(repository).findAsset(7); verify(repository).countOutgoingRelations(7); verifyNoMoreInteractions(repository);
    when(repository.findAsset(8)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> query.downstreamRelationCount(8));
    verify(repository, never()).countOutgoingRelations(8);
  }

  @Test void databaseCountAlwaysBindsCurrentProjectAndSourceAndFailsWithoutProject() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "count-test"), LineageRelationPO.class);
    var relation = mock(LineageRelationMapper.class);
    when(relation.selectCount(any())).thenReturn(4L);
    var dao = new LineageDaoImpl(mock(LineageAssetMapper.class), relation, mock(LineageWriteMapper.class), mock(LineageQueryMapper.class),
        () -> Optional.of(new ProjectContext(42L, "selected")));
    assertEquals(4, dao.countOutgoingRelations(7));
    ArgumentCaptor<LambdaQueryWrapper<LineageRelationPO>> argument = ArgumentCaptor.captor(); verify(relation).selectCount(argument.capture());
    var query = argument.getValue();
    assertTrue(query.getSqlSegment().contains("project_id =")); assertTrue(query.getSqlSegment().contains("source_asset_id ="));
    assertEquals(java.util.Set.of(42L, 7L), new java.util.HashSet<>(query.getParamNameValuePairs().values()));
    clearInvocations(relation);
    var unscoped = new LineageDaoImpl(mock(LineageAssetMapper.class), relation, mock(LineageWriteMapper.class), mock(LineageQueryMapper.class), Optional::empty);
    assertThrows(IllegalStateException.class, () -> unscoped.countOutgoingRelations(7)); verifyNoInteractions(relation);
  }
}
