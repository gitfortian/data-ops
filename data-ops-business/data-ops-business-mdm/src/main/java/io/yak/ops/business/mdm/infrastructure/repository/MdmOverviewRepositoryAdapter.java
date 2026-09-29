package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.dao.MdmOverviewCardRow;
import io.yak.ops.business.mdm.dao.MdmOverviewTotalsRow;
import io.yak.ops.business.mdm.dao.mapper.MdmOverviewMapper;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the project-scoped overview aggregates. */
@Repository
public class MdmOverviewRepositoryAdapter implements MdmOverviewRepository {

  private final MdmOverviewMapper mapper;
  private final CurrentProject currentProject;

  public MdmOverviewRepositoryAdapter(MdmOverviewMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MdmOverviewTotalsRow totals() {
    return mapper.selectTotals(requiredProjectId());
  }

  @Override
  public List<MdmOverviewCardRow> recentCards(int limit) {
    return mapper.selectRecentCards(requiredProjectId(), limit);
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }
}
