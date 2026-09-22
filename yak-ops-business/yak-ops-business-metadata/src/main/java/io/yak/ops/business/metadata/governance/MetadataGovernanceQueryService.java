package io.yak.ops.business.metadata.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.metadata.dao.mapper.MdChangeMapper;
import io.yak.ops.business.metadata.dao.mapper.MdLabelMapper;
import io.yak.ops.common.bean.po.metadata.MdChangePO;
import io.yak.ops.common.bean.po.metadata.MdLabelPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 治理侧表的读路径（ticket 118）：变更历史 + 标签。
 *
 * <p>为什么不并进 {@code CatalogQueryService}：那边读的是共表目录行，这里读的是本模块自己的两张
 * 治理侧表（{@code yak_md_change} / {@code yak_md_label}），走各自的 mapper。放一起会把
 * "目录行只有一条读路径" 这条结构保证说糊。
 *
 * <p>每条读都过 {@link CurrentProject}：变更流水与标签都带 {@code project_id}，跨空间读到别人的
 * 历史比读不到更糟。
 *
 * <p><b>只有读侧</b>：标签的写侧归工单 124，本类不提供任何打标签/摘标签的入口。
 */
@Service
@RequiredArgsConstructor
public class MetadataGovernanceQueryService {

  /** 详情页时间线一屏的量；更多走分页端点，不在聚合里塞全量。 */
  public static final int DETAIL_HISTORY_SIZE = 20;

  private final MdChangeMapper changeMapper;
  private final MdLabelMapper labelMapper;
  private final CurrentProject currentProject;

  public PageData<ChangeView> pageChanges(long assetId, int pageNo, int pageSize) {
    Page<MdChangePO> result =
        changeMapper.selectPage(
            new Page<>(pageNo, pageSize),
            new LambdaQueryWrapper<MdChangePO>()
                .eq(MdChangePO::getProjectId, currentProject.requireProjectId())
                .eq(MdChangePO::getAssetId, assetId)
                .orderByDesc(MdChangePO::getChangedAt)
                .orderByDesc(MdChangePO::getId));
    return PageData.of(
        result.getRecords().stream().map(ChangeView::from).toList(),
        result.getTotal(),
        pageNo,
        pageSize);
  }

  /**
   * 一个实体的标签。条数上界是"人工 + 机器 + 继承"三类各几条，不值得分页。
   *
   * <p>{@code labelCode} 的显示名不在这里解析：标签字典归 asset 侧，跨模块只允许走对方的
   * {@code api} 包（plan §0.3），而那层目前没有字典查询。宁可显示码值，也不为此 import 内部包。
   */
  public List<LabelView> labels(long assetId) {
    return labelMapper
        .selectList(
            new LambdaQueryWrapper<MdLabelPO>()
                .eq(MdLabelPO::getProjectId, currentProject.requireProjectId())
                .eq(MdLabelPO::getAssetId, assetId)
                .orderByAsc(MdLabelPO::getLabelType)
                .orderByDesc(MdLabelPO::getAppliedAt))
        .stream()
        .map(LabelView::from)
        .toList();
  }

  /** 变更流水一行。{@code detail} 只在属性级变更时有值，实体级为 NULL。 */
  public record ChangeView(
      long id,
      String changeType,
      String fieldName,
      String beforeValue,
      String afterValue,
      String detail,
      Long collectRunId,
      String changedBy,
      LocalDateTime changedAt) {

    static ChangeView from(MdChangePO po) {
      return new ChangeView(
          po.getId(),
          po.getChangeType(),
          po.getFieldName(),
          po.getBeforeValue(),
          po.getAfterValue(),
          po.getDetail(),
          po.getCollectRunId(),
          po.getChangedBy(),
          po.getChangedAt());
    }
  }

  /** 标签一行。{@code state}=SUGGESTED 表示机器/继承的默认，等人工确认（工单 124 的写侧）。 */
  public record LabelView(
      long id,
      String labelCode,
      String labelType,
      String state,
      String appliedBy,
      LocalDateTime appliedAt,
      String reason,
      LocalDateTime expiresAt) {

    static LabelView from(MdLabelPO po) {
      return new LabelView(
          po.getId(),
          po.getLabelCode(),
          po.getLabelType(),
          po.getState(),
          po.getAppliedBy(),
          po.getAppliedAt(),
          po.getReason(),
          po.getExpiresAt());
    }
  }
}
