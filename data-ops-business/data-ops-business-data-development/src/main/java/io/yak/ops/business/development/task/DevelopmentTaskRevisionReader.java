package io.yak.ops.business.development.task;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Read side for immutable DevelopmentTaskRevision history. */
@Component
public class DevelopmentTaskRevisionReader {

  private final DevelopmentTaskRevisionRepository revisionRepository;

  public DevelopmentTaskRevisionReader(DevelopmentTaskRevisionRepository revisionRepository) {
    this.revisionRepository = revisionRepository;
  }

  public List<DevelopmentTaskRevisionSummary> list(DevelopmentNode node) {
    return revisionRepository.listByNodeId(node.id());
  }

  public DevelopmentTaskRevision get(DevelopmentNode node, int revisionNo) {
    if (revisionNo <= 0) {
      throw new IllegalArgumentException("revisionNo 必须大于 0");
    }
    return revisionRepository.findByRevisionNo(node.id(), revisionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "发布版本不存在：nodeId=" + node.id() + ", revisionNo=" + revisionNo));
  }
}
