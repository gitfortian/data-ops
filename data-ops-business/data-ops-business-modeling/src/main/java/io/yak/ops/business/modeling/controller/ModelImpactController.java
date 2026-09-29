package io.yak.ops.business.modeling.controller;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;
import io.yak.ops.business.modeling.service.ModelImpactResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/modeling/impact")
public class ModelImpactController {

    private final ModelImpactResolver resolver = new ModelImpactResolver();

    @GetMapping("/{objectType}/{objectId}")
    public List<ModelImpactRecord> queryImpact(@PathVariable String objectType,
                                               @PathVariable Long objectId) {
        return resolver.resolve(objectType, objectId);
    }
}
