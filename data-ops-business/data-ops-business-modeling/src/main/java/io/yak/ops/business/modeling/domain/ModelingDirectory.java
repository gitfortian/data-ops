package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;

/**
 * A catalog directory organizing models in a project space. parentId is null
 * for root; storage stores 0. Sibling names are unique; deletion requires an
 * empty directory; moving into self/descendants is rejected (no cycles).
 */
public record ModelingDirectory(
    Long id,
    Long parentId,
    String name,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    /** 绑定的业务域(semantic 松散引用);手工建的目录为 null。 */
    Long domainId) {

  public boolean isRoot() {
    return parentId == null;
  }
}
