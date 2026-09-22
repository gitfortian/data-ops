package io.yak.ops.business.dataservice.access;

import java.util.List;

public record ConsumerAccessInput(String accessScope, List<Long> apiIds) {}
