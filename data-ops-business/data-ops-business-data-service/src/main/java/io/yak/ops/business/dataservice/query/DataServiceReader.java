package io.yak.ops.business.dataservice.query;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceReader {

  private final DataServiceRepository repository;

  /** Management-plane lookup: always scoped to the trusted CurrentProject by the repository. */
  public DataServiceDefinition require(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("数据服务不存在：" + id));
  }

  /** Management-plane catalog: always scoped to the trusted CurrentProject. */
  public List<DataServiceDefinition> list() {
    return repository.findAll();
  }

  /**
   * Platform-wide aggregate count used by the Home cockpit. This returns only one scalar and does
   * not expose Data Service identities or definitions across Project Spaces.
   */
  public long count() {
    return repository.count();
  }

  /** Management-plane source lookup: always scoped to the trusted CurrentProject. */
  public Optional<DataServiceDefinition> findBySource(String sourceType, String sourceRef) {
    return repository.findBySource(sourceType, sourceRef);
  }

  /**
   * Invocation-plane lookup. Runtime paths are globally unique and external callers do not carry a
   * Yak Project header, so this is the deliberate global definition-read corridor.
   */
  public DataServiceDefinition requireByPath(String path) {
    return repository.findByRuntimePath(path)
        .orElseThrow(() -> new IllegalArgumentException("数据服务不存在：" + path));
  }
}
