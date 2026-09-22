package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation;
import java.util.Optional;

public interface DataServiceDocumentationRepository {
  Optional<DataServiceDocumentation> findByApiId(Long apiId);
  DataServiceDocumentation save(DataServiceDocumentation documentation);
  void delete(Long apiId);
}
