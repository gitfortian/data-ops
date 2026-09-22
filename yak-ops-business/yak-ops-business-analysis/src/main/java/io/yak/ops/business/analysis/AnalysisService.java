package io.yak.ops.business.analysis;

import io.yak.ops.business.analysis.definition.AnalysisManager;
import io.yak.ops.business.analysis.definition.AnalysisReader;
import io.yak.ops.business.analysis.definition.AnalysisSaveCommand;
import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Stable Analysis application facade retained for HTTP and cross-module callers. */
@Service
public class AnalysisService {

  private final AnalysisManager manager;
  private final AnalysisReader reader;

  public AnalysisService(AnalysisManager manager, AnalysisReader reader) {
    this.manager = manager;
    this.reader = reader;
  }

  public List<AnalysisAsset> list() {
    return reader.list();
  }

  public AnalysisAsset get(long analysisId) {
    return reader.require(analysisId);
  }

  public AnalysisAsset create(SaveCommand command) {
    return manager.create(toCommand(command));
  }

  public AnalysisAsset update(long analysisId, SaveCommand command) {
    return manager.update(analysisId, toCommand(command));
  }

  public void delete(long analysisId) {
    manager.delete(analysisId);
  }

  private AnalysisSaveCommand toCommand(SaveCommand command) {
    Objects.requireNonNull(command, "command");
    return new AnalysisSaveCommand(
        command.name(),
        command.description(),
        command.datasetId(),
        command.chartType(),
        command.querySpec(),
        command.visualConfig());
  }

  /** Stable compatibility command used by existing HTTP/application callers. */
  public record SaveCommand(
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      AnalysisQuerySpec querySpec,
      AnalysisVisualConfig visualConfig) {
  }
}
