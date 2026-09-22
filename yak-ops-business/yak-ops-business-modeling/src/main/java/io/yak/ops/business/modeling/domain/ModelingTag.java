package io.yak.ops.business.modeling.domain;

import java.time.LocalDateTime;

/**
 * A reusable project-scoped label. Names are unique per project; a model can
 * carry multiple tags; deleting a tag detaches it from every model.
 */
public record ModelingTag(Long id, String name, LocalDateTime createTime) {}
