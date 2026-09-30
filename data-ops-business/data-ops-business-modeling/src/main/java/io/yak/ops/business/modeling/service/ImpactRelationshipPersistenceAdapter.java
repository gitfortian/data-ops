package io.yak.ops.business.modeling.service;

import java.util.Collections;
import java.util.List;

/**
 * Persistence extension point for impact relationship traversal.
 *
 * Implementations can connect modeling relationships stored in database
 * and provide downstream impact objects for analysis.
 */
public interface ImpactRelationshipPersistenceAdapter {

    /**
     * Query related objects for an impact source.
     *
     * @param objectType source object type
     * @param objectId source object id
     * @return related object identifiers
     */
    default List<String> findRelatedObjects(String objectType, Long objectId) {
        if (objectType == null || objectId == null) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
