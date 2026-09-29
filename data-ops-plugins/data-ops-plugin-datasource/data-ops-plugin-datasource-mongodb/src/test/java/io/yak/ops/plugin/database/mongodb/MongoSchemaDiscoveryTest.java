package io.yak.ops.plugin.database.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class MongoSchemaDiscoveryTest {

  @Test
  void discoversPortableTypesAndDottedNestedFieldsAcrossSamples() {
    BsonDocument first =
        new BsonDocument()
            .append("_id", new BsonObjectId(new ObjectId("64b7abdecf2160b649ab6085")))
            .append("age", new BsonInt32(18))
            .append(
                "address",
                new BsonDocument("city", new BsonString("Chengdu")));
    BsonDocument second =
        new BsonDocument()
            .append("_id", new BsonObjectId(new ObjectId("64b7abdecf2160b649ab6086")))
            .append("age", new BsonInt64(20L));

    List<DataSourceColumn> columns = MongoSchemaDiscovery.discover(List.of(first, second));
    Map<String, DataSourceColumn> byName =
        columns.stream().collect(Collectors.toMap(DataSourceColumn::getName, Function.identity()));

    assertThat(byName.get("_id").isPrimaryKey()).isTrue();
    assertThat(byName.get("_id").getTypeName()).isEqualTo("ObjectId");
    assertThat(byName.get("age").getJdbcType()).isEqualTo(Types.BIGINT);
    assertThat(byName.get("age").getTypeName()).contains("Int32", "Int64");
    assertThat(byName.get("address").getJdbcType()).isEqualTo(Types.VARCHAR);
    assertThat(byName.get("address").getRemarks()).contains("Extended JSON");
    assertThat(byName.get("address.city").getTypeName()).isEqualTo("String");
    assertThat(byName.get("address.city").isNullable()).isTrue();
  }

  @Test
  void heterogeneousScalarFallsBackToTextMetadata() {
    BsonDocument first = new BsonDocument("value", new BsonInt32(1));
    BsonDocument second = new BsonDocument("value", new BsonString("unknown"));

    DataSourceColumn value = MongoSchemaDiscovery.discover(List.of(first, second)).get(0);

    assertThat(value.getJdbcType()).isEqualTo(Types.VARCHAR);
    assertThat(value.getTypeName()).contains("Int32", "String");
    assertThat(value.getRemarks()).contains("heterogeneous");
  }
}
