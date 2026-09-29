package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.source.MdmSource;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the master data source binding. */
public interface MdmSourceRepository {

  MdmSource insert(MdmSource source, String operator);

  Optional<MdmSource> findById(Long id);

  List<MdmSource> listByEntity(Long entityId);

  List<MdmSource> listByDatasource(Long datasourceId);

  List<MdmSource> listAll();

  boolean exists(
      Long entityId, Long datasourceId, String database, String schema, String table);

  boolean deleteById(Long id);
}
