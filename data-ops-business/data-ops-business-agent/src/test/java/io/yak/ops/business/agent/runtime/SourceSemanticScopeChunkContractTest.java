package io.yak.ops.business.agent.runtime;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceSemanticScopeChunkContractTest {
  static SourceSemanticScope scope() {
    return new SourceSemanticScope(31,"source-a","db","schema","capture-9",List.of(
        new SourceSemanticScope.Table("table-b","fp-b",List.of("sku","buyer","amount")),
        new SourceSemanticScope.Table("table-a","fp-a",List.of("id","date","shop","status"))));
  }
  @Test void canonicalAndCaptureBound() {
    var a=scope();
    var b=new SourceSemanticScope(31,"source-a","db","schema","capture-9",List.of(
        new SourceSemanticScope.Table("table-a","fp-a",List.of("status","shop","date","id")),
        new SourceSemanticScope.Table("table-b","fp-b",List.of("buyer","amount","sku"))));
    assertEquals(a.fingerprint(),b.fingerprint());
    assertNotEquals(a.fingerprint(),new SourceSemanticScope(31,"source-a","db",
        "schema","capture-10",a.tables()).fingerprint());
    assertNotEquals(a.fingerprint(),new SourceSemanticScope(32,"source-a","db",
        "schema","capture-9",a.tables()).fingerprint());
  }
  @Test void deterministicSlicesCoverAllColumnsExactlyOnce() {
    var a=scope();
    var chunks=SourceSemanticChunkPlanner.plan(a,1,2);
    assertEquals(4,chunks.size());
    assertEquals(7,chunks.stream().mapToInt(SourceSemanticChunkPlanner.Chunk::columnCount).sum());
    var actual=chunks.stream().flatMap(c->c.slices().stream())
        .flatMap(s->s.columns().stream().map(col->s.tableAssetKey()+"/"+col)).sorted().toList();
    var expected=a.tables().stream().flatMap(t->t.columns().stream()
        .map(col->t.assetKey()+"/"+col)).sorted().toList();
    assertEquals(expected,actual);
    assertTrue(chunks.stream().allMatch(c->c.columnCount()<=2));
    assertEquals(chunks,SourceSemanticChunkPlanner.plan(a,1,2));
  }
  @Test void rejectDuplicatesMissingEvidenceAndInvalidBounds() {
    assertThrows(IllegalArgumentException.class,()->new SourceSemanticScope(31,"source",
        "db","","capture",List.of(new SourceSemanticScope.Table("t","fp",List.of("a","a")))));
    assertThrows(IllegalArgumentException.class,()->new SourceSemanticScope(31,"source",
        "db","","capture",List.of(
            new SourceSemanticScope.Table("t","fp",List.of("a")),
            new SourceSemanticScope.Table("t","fp",List.of("b")))));
    assertThrows(IllegalArgumentException.class,()->new SourceSemanticScope(31,"source",
        "db","","capture",List.of()));
    assertThrows(IllegalArgumentException.class,()->SourceSemanticChunkPlanner.plan(scope(),0,10));
    assertThrows(IllegalArgumentException.class,()->SourceSemanticChunkPlanner.plan(scope(),1,0));
  }
}
