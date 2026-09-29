package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Picks the dialect template and renders one model's CREATE TABLE script.
 * Rendering only — the platform never executes the script (decision D3).
 *
 * <p>脚本以已发布快照为源：改草稿不发布，下发脚本保持旧发布版内容。
 */
@Service
public class DdlService {

  private final ModelRepository modelRepository;
  private final ModelPublishedStructureReader structureReader;
  private final List<DdlGenerator> generators;

  public DdlService(
      ModelRepository modelRepository,
      ModelPublishedStructureReader structureReader,
      List<DdlGenerator> generators) {
    this.modelRepository = modelRepository;
    this.structureReader = structureReader;
    this.generators = generators;
  }

  /** Generates the CREATE TABLE script from the model's published snapshot. */
  public DdlView generate(Long modelId) {
    Model model = modelRepository
        .findById(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
    StructureView structure = structureReader.publishedStructure(modelId);
    String tableName = structure.tableName() == null || structure.tableName().isBlank()
        ? model.code()
        : structure.tableName();
    StructureView.PartitionView partition = structure.partition() == null
        ? new StructureView.PartitionView(null, List.of(), null)
        : structure.partition();
    DdlModel ddlModel = new DdlModel(
        tableName,
        structure.tableComment() == null ? "" : structure.tableComment(),
        ModelPublishedStructureReader.toColumns(structure),
        structure.primaryKey() == null ? List.of() : structure.primaryKey(),
        ModelPublishedStructureReader.toIndexes(structure),
        partition.type() == null ? "" : partition.type(),
        partition.columns() == null ? List.of() : partition.columns(),
        partition.expression() == null ? "" : partition.expression(),
        structure.tableProperties() == null ? Map.of() : structure.tableProperties());
    DdlGenerator generator = generators.stream()
        .filter(candidate -> candidate.dialect().equals(model.dialect().name()))
        .findFirst()
        .orElseThrow(
            () ->
                new ModelingException(
                    ModelingErrorCode.INVALID_DIALECT,
                    "方言 " + model.dialect().name() + " 的建库脚本模板尚未提供"));
    return new DdlView(model.dialect().name(), generator.generate(ddlModel));
  }

  /** View with dialect and rendered script. */
  public record DdlView(String dialect, String script) {}
}
