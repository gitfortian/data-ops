package io.yak.ops.business.agent.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic bounded plan over exactly the authorized scope; splits oversized single tables
 * without dropping columns. This plan contains identifiers only, never arbitrary LLM instructions.
 */
public final class SourceSemanticChunkPlanner {
  private SourceSemanticChunkPlanner() {}

  public record Slice(String tableAssetKey, String tableFingerprint, List<String> columns) {
    public Slice {
      columns = List.copyOf(columns);
      if (columns.isEmpty()) throw new IllegalArgumentException("empty slice");
    }
  }

  public record Chunk(String id, List<Slice> slices, int columnCount) {
    public Chunk {
      slices = List.copyOf(slices);
    }
  }

  public static List<Chunk> plan(SourceSemanticScope scope, int tablesPerChunk,
      int columnsPerChunk) {
    if (tablesPerChunk < 1 || tablesPerChunk > 20 || columnsPerChunk < 1
        || columnsPerChunk > 500) {
      throw new IllegalArgumentException("chunk bounds must be explicit and finite");
    }
    var chunks = new ArrayList<Chunk>();
    var pending = new ArrayList<Slice>();
    int count = 0;
    for (var table : scope.tables()) {
      List<String> cols = table.columns();
      for (int index = 0; index < cols.size();) {
        if (!pending.isEmpty() && (count == columnsPerChunk
            || distinctTables(pending, table.assetKey()) > tablesPerChunk)) {
          append(chunks, scope, pending, count);
          pending = new ArrayList<>();
          count = 0;
        }
        int take = Math.min(cols.size() - index, columnsPerChunk - count);
        pending.add(new Slice(table.assetKey(), table.fingerprint(),
            cols.subList(index, index + take)));
        index += take;
        count += take;
        if (count == columnsPerChunk) {
          append(chunks, scope, pending, count);
          pending = new ArrayList<>();
          count = 0;
        }
      }
    }
    if (!pending.isEmpty()) append(chunks, scope, pending, count);
    if (chunks.size() > 500) throw new IllegalArgumentException("too many bounded chunks");
    return List.copyOf(chunks);
  }

  private static int distinctTables(List<Slice> pending, String nextKey) {
    return (int) pending.stream().map(Slice::tableAssetKey).distinct().count()
        + (pending.stream().anyMatch(s -> s.tableAssetKey().equals(nextKey)) ? 0 : 1);
  }

  private static void append(List<Chunk> target, SourceSemanticScope scope,
      List<Slice> slices, int count) {
    var components = new ArrayList<String>();
    components.add("f039-chunk-v1");
    components.add(scope.fingerprint());
    components.add(Integer.toString(target.size()));
    for (Slice slice : slices) {
      components.add(slice.tableAssetKey());
      components.add(slice.tableFingerprint());
      components.addAll(slice.columns());
    }
    target.add(new Chunk(SourceSemanticScope.digest(components), slices, count));
  }

  public static String planFingerprint(SourceSemanticScope scope, List<Chunk> chunks) {
    var parts = new ArrayList<String>();
    parts.add("f039-chunk-plan-v1");
    parts.add(scope.fingerprint());
    parts.add(Integer.toString(chunks.size()));
    for (Chunk chunk : chunks) parts.add(chunk.id());
    return SourceSemanticScope.digest(parts);
  }
}
