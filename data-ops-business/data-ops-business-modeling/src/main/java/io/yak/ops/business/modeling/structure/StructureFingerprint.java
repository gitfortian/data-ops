package io.yak.ops.business.modeling.structure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/** Hashes record components only, avoiding bean getter recursion and unstable map ordering. */
public final class StructureFingerprint {
  private StructureFingerprint() {}
  public static String of(StructureView view) {
    var values = new LinkedHashMap<String, Object>();
    values.put("modelId", view.modelId()); values.put("modelCode", view.modelCode());
    values.put("modelName", view.modelName()); values.put("dialect", view.dialect());
    values.put("status", view.status()); values.put("description", view.modelDescription());
    values.put("tableName", view.tableName()); values.put("tableComment", view.tableComment());
    values.put("columns", view.columns()); values.put("primaryKey", view.primaryKey());
    values.put("indexes", view.indexes()); values.put("partition", view.partition());
    values.put("tableProperties", view.tableProperties());
    try {
      byte[] json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(values);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (Exception failure) { throw new IllegalStateException("无法核验模型定义", failure); }
  }
}
