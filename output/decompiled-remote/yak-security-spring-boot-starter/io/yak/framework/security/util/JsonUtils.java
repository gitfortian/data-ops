/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.core.JsonProcessingException
 *  com.fasterxml.jackson.databind.JavaType
 *  com.fasterxml.jackson.databind.ObjectMapper
 *  com.fasterxml.jackson.databind.type.CollectionType
 */
package io.yak.framework.security.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.util.List;

public final class JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON \u5e8f\u5217\u5316\u5931\u8d25", exception);
        }
    }

    public static <T> T fromJson(String value, Class<T> type) {
        try {
            return (T)OBJECT_MAPPER.readValue(value, type);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON \u53cd\u5e8f\u5217\u5316\u5931\u8d25", exception);
        }
    }

    public static <T> List<T> toList(String value, Class<T> elementType) {
        CollectionType type = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            return (List)OBJECT_MAPPER.readValue(value, (JavaType)type);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON \u6570\u7ec4\u53cd\u5e8f\u5217\u5316\u5931\u8d25", exception);
        }
    }
}

