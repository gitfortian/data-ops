package io.yak.ops.business.semantic.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.dao.mapper.SemanticDomainMapper;
import io.yak.ops.business.semantic.dao.model.SemanticDomainPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Domain updates must persist explicit clears, not rely on MP's default null-skipping entity SETs. */
class BusinessDomainRepositoryAdapterTest {

  @BeforeAll
  static void initializeMyBatisColumnMapping() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new Configuration(), "semantic-domain-test"),
        SemanticDomainPO.class);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void clearingOwnerAndDescriptionIssuesExplicitNullSetsWithinCurrentProject() {
    SemanticDomainMapper mapper = mock(SemanticDomainMapper.class);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(42L, null));
    BusinessDomainRepositoryAdapter repository =
        new BusinessDomainRepositoryAdapter(mapper, currentProject);
    when(mapper.update(isNull(), any())).thenReturn(1);

    BusinessDomain cleared =
        new BusinessDomain(7L, "trade", "交易域", 0L, null, null, 3, "creator", null, null);

    assertThat(repository.update(cleared)).isTrue();

    ArgumentCaptor<LambdaUpdateWrapper> wrapperCaptor =
        ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(mapper).update(isNull(), wrapperCaptor.capture());
    LambdaUpdateWrapper<SemanticDomainPO> wrapper = wrapperCaptor.getValue();

    String setSql = wrapper.getSqlSet();
    assertThat(setSql).contains("domainName", "owner", "description", "sortOrder", "updateTime");
    assertThat(setSql).doesNotContain("parentId", "domainCode", "createdBy", "createTime");
    assertThat(wrapper.getParamNameValuePairs().values().stream().filter(Objects::isNull).count())
        .isEqualTo(2L);
    assertThat(wrapper.getSqlSegment()).contains("projectId", "id");
    assertThat(wrapper.getParamNameValuePairs().values()).contains(42L, 7L);
  }

  @Test
  void missingRowDoesNotReportUpdateSuccess() {
    SemanticDomainMapper mapper = mock(SemanticDomainMapper.class);
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(42L, null));
    BusinessDomainRepositoryAdapter repository =
        new BusinessDomainRepositoryAdapter(mapper, currentProject);
    when(mapper.update(isNull(), any())).thenReturn(0);

    assertThat(repository.update(
        new BusinessDomain(7L, "trade", "交易域", 0L, null, null, 3, "creator", null, null)))
        .isFalse();
  }
}
