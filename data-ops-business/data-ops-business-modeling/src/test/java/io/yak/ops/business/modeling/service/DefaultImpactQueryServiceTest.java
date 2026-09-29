package io.yak.ops.business.modeling.service;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultImpactQueryServiceTest {

    @Test
    void shouldReturnEmptyWhenInputInvalid() {
        DefaultImpactQueryService service = new DefaultImpactQueryService(Collections.emptyList());

        assertTrue(service.queryImpact(null, null).isEmpty());
    }
}
