package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ModelImpactResolver {

    public List<ModelImpactRecord> resolve(String objectType, Long objectId) {
        List<ModelImpactRecord> impacts = new ArrayList<>();

        if (objectType == null || objectId == null) {
            return impacts;
        }

        switch (objectType) {
            case "LOGICAL_MODEL" -> impacts.add(createImpact(objectType, objectId,
                    "LOGICAL_ENTITY", objectId, "DIRECT", "logical model change affects entities"));
            case "LOGICAL_ENTITY" -> {
                impacts.add(createImpact(objectType, objectId,
                        "LOGICAL_ATTRIBUTE", objectId, "DIRECT", "entity change affects attributes"));
                impacts.add(createImpact(objectType, objectId,
                        "MAPPING", objectId, "DEPENDENCY", "entity change affects mappings"));
            }
            default -> {
            }
        }

        return impacts;
    }

    private ModelImpactRecord createImpact(String sourceType, Long sourceId,
                                           String targetType, Long targetId,
                                           String impactType, String description) {
        ModelImpactRecord record = new ModelImpactRecord();
        record.setSourceObjectType(sourceType);
        record.setSourceObjectId(sourceId);
        record.setTargetObjectType(targetType);
        record.setTargetObjectId(targetId);
        record.setImpactType(impactType);
        record.setDescription(description);
        record.setCreatedTime(LocalDateTime.now());
        return record;
    }
}
