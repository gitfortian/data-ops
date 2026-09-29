package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import java.util.List;
import java.util.Optional;

/** Repository for collection landing links (project-scoped). */
public interface MdmCollectLinkRepository {

  MdmCollectLink insert(MdmCollectLink link, String operator);

  Optional<MdmCollectLink> findBySourceId(Long sourceId);

  List<MdmCollectLink> listByEntity(Long entityId);

  List<MdmCollectLink> listAll();
}
