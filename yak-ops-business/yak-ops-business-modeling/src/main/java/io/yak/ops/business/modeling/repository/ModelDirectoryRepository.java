package io.yak.ops.business.modeling.repository;

import io.yak.ops.business.modeling.domain.ModelingDirectory;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for modeling directories. */
public interface ModelDirectoryRepository {

  default ModelingDirectory insert(Long parentId, String name) {
    return insert(parentId, name, null);
  }

  ModelingDirectory insert(Long parentId, String name, Long domainId);

  Optional<ModelingDirectory> findById(Long id);

  /** 业务域绑定的目录(自动目录反查);手工建的目录不会被命中。 */
  Optional<ModelingDirectory> findByDomainId(Long domainId);

  Optional<ModelingDirectory> findByParentAndName(Long parentId, String name);

  boolean existsByName(Long parentId, String name);

  boolean hasChildren(Long id);

  List<ModelingDirectory> listAll();

  boolean updateName(Long id, String name);

  /** 收养同名手工目录：补上业务域绑定，使其纳入自动目录的重命名跟随。 */
  boolean bindDomain(Long id, Long domainId);

  boolean updateParentId(Long id, Long parentId);

  boolean deleteById(Long id);
}
