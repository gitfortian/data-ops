package io.yak.ops.business.quality.controller.v1.converter;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.asset.QualityTableAssetCommand;
import io.yak.ops.business.quality.asset.QualityTableAssetManager.RegisterResult;
import io.yak.ops.business.quality.asset.QualityTableCandidateReader.CandidatePage;
import io.yak.ops.business.quality.domain.QualityDomain.TableAsset;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.common.bean.dto.quality.QualityTableAssetDTO;
import io.yak.ops.common.bean.vo.quality.QualityTableAssetVO;
import org.springframework.stereotype.Component;

@Component
public class QualityTableAssetConverter {
  public QualityQuery.TableAsset query(QualityTableAssetDTO.PageRequest request) {
    if (request.dataSourceId() == null || request.dataSourceId() <= 0L) throw new IllegalArgumentException("数据源编号无效");
    return new QualityQuery.TableAsset(request.normalizedCurrent(), request.normalizedPageSize(), request.dataSourceId(), request.databaseName(), request.databaseName() != null, request.schemaName(), request.schemaName() != null, request.keyword());
  }
  public QualityTableAssetCommand.Register command(QualityTableAssetDTO.RegisterRequest request) { return new QualityTableAssetCommand.Register(request.dataSourceId(), request.dataSourceName(), request.databaseName(), request.tables().stream().map(item -> new QualityTableAssetCommand.Item(item.databaseName(), item.schemaName(), item.tableName(), item.tableType(), item.remarks())).toList()); }
  public QualityTableAssetVO.Page page(PageData<TableAsset> page, QualityQuery.TableAsset query) { return new QualityTableAssetVO.Page(page.records().stream().map(this::asset).toList(), page.total(), query.current(), query.pageSize()); }
  public QualityTableAssetVO.CandidatePage candidates(CandidatePage page) { return new QualityTableAssetVO.CandidatePage(page.records().stream().map(table -> new QualityTableAssetVO.Candidate(table.databaseName(), table.schemaName(), table.tableName(), table.tableType(), table.remarks())).toList(), page.total(), page.current(), page.pageSize()); }
  public QualityTableAssetVO.RegisterResult register(RegisterResult result) { return new QualityTableAssetVO.RegisterResult(result.requested(), result.registered()); }
  private QualityTableAssetVO.Asset asset(TableAsset value) { return new QualityTableAssetVO.Asset(value.id(), value.dataSourceId(), value.dataSourceName(), value.databaseName(), value.schemaName(), value.tableName(), value.tableType(), value.remarks(), value.monitorId(), value.monitorName(), value.monitorCount(), value.ruleCount(), value.lastResult(), value.lastRunTime(), value.registeredBy(), value.registeredAt()); }
}
