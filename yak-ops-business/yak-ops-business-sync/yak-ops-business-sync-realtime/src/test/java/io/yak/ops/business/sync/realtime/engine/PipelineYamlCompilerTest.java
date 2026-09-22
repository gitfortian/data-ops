package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineYamlCompilerTest {

  @Test
  void mapsResolvedCoordinatesAndUsesSubmissionSecretReferences() {
    PipelineYamlCompiler compiler = new PipelineYamlCompiler();
    CdcPipelineSpec spec =
        new CdcPipelineSpec(
            1L,
            2L,
            List.of(
                new CdcPipelineSpec.TableRoute(
                    "orders", "public.orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
            "initial",
            CdcPipelineSpec.SchemaEvolution.EVOLVE,
            2,
            60_000,
            new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 1_000),
            new CdcPipelineSpec.SinkTuning(3, 100, 1_000, 1_048_576, 20, true));
    ResolvedCdcPipeline resolved =
        new ResolvedCdcPipeline(
            new ResolvedCdcPipeline.Endpoint(
                1,
                "source",
                DataSourceDbType.MYSQL,
                "mysql",
                3306,
                "jdbc:mysql://mysql:3306/shop",
                "com.mysql.cj.jdbc.Driver",
                "reader",
                "shop"),
            new ResolvedCdcPipeline.Endpoint(
                2,
                "sink",
                DataSourceDbType.POSTGRE_SQL,
                "pg",
                5432,
                "jdbc:postgresql://pg:5432/dw",
                "org.postgresql.Driver",
                "writer",
                "dw"));

    PipelineYamlCompiler.CompiledPipeline result = compiler.compile("orders", spec, resolved);

    assertThat(result.yaml())
        .contains(
            "type: mysql",
            "tables: '\\Qshop\\E.\\Qorders\\E'",
            "type: yak-jdbc",
            "dialect: postgres",
            "password: ${SECRET:source.password}",
            "password: ${SECRET:sink.password}",
            "max-batch-bytes: 1048576",
            "replay-safety: strict")
        .doesNotContain(
            "pipeline_yaml",
            "database-name:",
            "table-name:",
            "checkpoint-interval",
            "restart-strategy");
    assertThat(result.summary()).contains("mysql#1 -> postgres#2", "tables=1");
  }

  @Test
  void emitsFlinkCdcTablesOptionAndProtectsRegexSeparators() {
    CdcPipelineSpec spec =
        new CdcPipelineSpec(
            1L,
            2L,
            List.of(
                new CdcPipelineSpec.TableRoute(
                    "orders_.*", "public.orders", CdcPipelineSpec.MatchMode.REGEX, List.of("id")),
                new CdcPipelineSpec.TableRoute(
                    "customers",
                    "public.customers",
                    CdcPipelineSpec.MatchMode.EXACT,
                    List.of("id"))),
            "latest-offset",
            CdcPipelineSpec.SchemaEvolution.IGNORE,
            1,
            60_000,
            new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 1_000),
            new CdcPipelineSpec.SinkTuning(3, 100, 1_000, 1_048_576, 20, true));
    ResolvedCdcPipeline resolved =
        new ResolvedCdcPipeline(
            new ResolvedCdcPipeline.Endpoint(
                1,
                "source",
                DataSourceDbType.MYSQL,
                "mysql",
                3306,
                "jdbc:mysql://mysql:3306/shop",
                "com.mysql.cj.jdbc.Driver",
                "reader",
                "shop"),
            new ResolvedCdcPipeline.Endpoint(
                2,
                "sink",
                DataSourceDbType.POSTGRE_SQL,
                "pg",
                5432,
                "jdbc:postgresql://pg:5432/dw",
                "org.postgresql.Driver",
                "writer",
                "dw"));

    String yaml =
        new PipelineYamlCompiler()
            .compile("job", spec, resolved)
            .yaml();

    assertThat(yaml)
        .contains(
            "tables: '\\Qshop\\E.orders_\\.*, \\Qshop\\E.\\Qcustomers\\E'",
            "source-table: '\\Qshop\\E.orders_\\.*'");
  }
}
