package io.yak.ops.business.agent.runtime;
import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="ARCHITECTURE_MYSQL_URL",matches=".+")
class SourceSemanticTaskLedgerMysqlContractTest {
 @Test void mysqlCasRestartAndDuplicateReservation() throws Exception {
   var ds=new DriverManagerDataSource(System.getenv("ARCHITECTURE_MYSQL_URL"),
       System.getenv("ARCHITECTURE_MYSQL_USERNAME"),System.getenv("ARCHITECTURE_MYSQL_PASSWORD"));
   String database;
   try(var c=ds.getConnection()){database=c.getCatalog();}
   String table="f039_task_"+UUID.randomUUID().toString().replace("-","");
   var jdbc=new JdbcTemplate(ds);
   try {
     var ledger=new SourceSemanticTaskLedger(
         new MysqlAgentStateStore(ds,database,table,true));
     var scope=SourceSemanticScopeChunkContractTest.scope();
     String id=UUID.randomUUID().toString();
     String plan=SourceSemanticScope.digest(List.of("approved-review"));
     var initial=ledger.create(id,"alice",scope,1,2,plan,3,4);
     ledger.confirmPlan("alice",31,id,scope.fingerprint(),plan);
     var reopened=new SourceSemanticTaskLedger(
         new MysqlAgentStateStore(ds,database,table,false));
     assertEquals(SourceSemanticTaskState.Status.READY,
         reopened.read("alice",31,id).status());
     reopened.reserveTurn("alice",31,id,scope.fingerprint(),plan,
         initial.chunkIds().get(0),"turn-1",2);
     var third=new SourceSemanticTaskLedger(
         new MysqlAgentStateStore(ds,database,table,false));
     assertEquals(2,third.read("alice",31,id).reservedToolCalls());
     assertThrows(IllegalStateException.class,()->third.reserveTurn("alice",31,id,
         scope.fingerprint(),plan,initial.chunkIds().get(0),"turn-duplicate",2));
     assertEquals(2,third.read("alice",31,id).reservedToolCalls());
   }finally{jdbc.execute("DROP TABLE IF EXISTS `"+table+"`");}
 }
}
