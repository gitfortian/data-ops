package io.yak.ops.business.dataset.observability;

import java.util.Optional;

/** Runtime adapter boundary for resolving a stable authenticated Dataset query actor. */
public interface DatasetQueryActorResolver {

  Optional<DatasetQueryActor> currentActor();
}
