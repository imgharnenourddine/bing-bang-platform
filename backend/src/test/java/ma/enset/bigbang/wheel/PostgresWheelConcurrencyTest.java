package ma.enset.bigbang.wheel;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import ma.enset.bigbang.draw.*;
import ma.enset.bigbang.notification.RealtimeEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="F05_TEST_DB_URL",matches=".+")
class PostgresWheelConcurrencyTest {
 @Test void concurrentRetriesHaveOneAwardOneStockDecrementAndOneProgression() throws Exception {
  String url=System.getenv("F05_TEST_DB_URL"),user=System.getenv("F05_TEST_DB_USER"),pass=System.getenv("F05_TEST_DB_PASSWORD");
  String schema="f05_"+UUID.randomUUID().toString().replace("-","");
  try(var root=DriverManager.getConnection(url,user,pass);var statement=root.createStatement()) {
   statement.execute("CREATE SCHEMA "+schema);
   try {
    var ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,pass);
    var jdbc=new JdbcTemplate(ds);
    for(String file:new String[]{"V2__administrator_accounts.sql","V4__participants.sql","V5__draw_engine.sql","V6__prize_wheel.sql"})
     jdbc.execute(Files.readString(Path.of("../database/migrations/"+file)));
    UUID admin=UUID.randomUUID(),person=UUID.randomUUID(),draw=UUID.randomUUID(),prize=UUID.randomUUID();
    jdbc.update("INSERT INTO administrator_accounts(id,username,password_hash,enabled) VALUES (?,?,?,true)",admin,"test","hash");
    jdbc.update("INSERT INTO participants(id,first_name,last_name,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",person,"A","B");
    jdbc.update("INSERT INTO draws(id,cycle_id,participant_id,selected_by,status) SELECT ?,id,?,?,'ACTIVE' FROM draw_cycles WHERE closed_at IS NULL",draw,person,admin);
    jdbc.update("INSERT INTO prizes(id,name,category,base_weight,initial_stock,remaining_stock,enabled) VALUES (?,?,'STANDARD',1,2,2,true)",prize,"Gift");
    var realtime=mock(RealtimeEvents.class);var drawRepo=new DrawRepository(jdbc);var draws=new DrawService(drawRepo,realtime);
    var service=new WheelService(new WheelRepository(jdbc,new ObjectMapper()),drawRepo,draws,realtime);
    var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));var gate=new CountDownLatch(1);
    try(var executor=Executors.newFixedThreadPool(2)) {
     var futures=java.util.stream.IntStream.range(0,2).mapToObj(i->executor.submit(()->{ gate.await();return tx.execute(status->service.spin(draw,admin)); })).toList();
     gate.countDown();var a=futures.get(0).get();var b=futures.get(1).get();
     assertNotNull(a);assertNotNull(b);assertEquals(a.award().awardId(),b.award().awardId());assertNotEquals(a.created(),b.created());
     assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM prize_awards",Long.class));
     assertEquals(1,jdbc.queryForObject("SELECT remaining_stock FROM prizes WHERE id=?",Integer.class,prize));
     assertEquals(1L,jdbc.queryForObject("SELECT consecutive_standard_awards FROM wheel_configuration WHERE id=1",Long.class));
     assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM draws WHERE id=?",String.class,draw));
     verify(realtime,times(1)).prizeWon(eq(person),any());
     tx.execute(status->draws.reset(admin,new DrawDtos.ResetRequest(true,1)));
     assertEquals(1L,jdbc.queryForObject("SELECT consecutive_standard_awards FROM wheel_configuration WHERE id=1",Long.class));
     tx.execute(status->service.patch(prize,new WheelDtos.PatchPrize(null,null,PrizeCategory.PREMIUM,null,null)));
     var next=tx.execute(status->draws.start(admin));
     assertNotNull(next);
     var premium=tx.execute(status->service.spin(next.id(),admin));
     assertNotNull(premium);assertEquals(PrizeCategory.PREMIUM,premium.award().prizeCategory());
     assertEquals(0L,jdbc.queryForObject("SELECT consecutive_standard_awards FROM wheel_configuration WHERE id=1",Long.class));
     assertEquals(0,jdbc.queryForObject("SELECT remaining_stock FROM prizes WHERE id=?",Integer.class,prize));
     assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM prize_awards",Long.class));
     tx.executeWithoutResult(status->service.adjust(prize,new WheelDtos.StockAdjustment(1,"Extra"),admin));
     tx.executeWithoutResult(status->service.adjust(prize,new WheelDtos.StockAdjustment(-1,"Used"),admin));
     assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM prize_stock_adjustments",Long.class));
     assertThrows(WheelException.class,()->tx.executeWithoutResult(status->service.adjust(prize,new WheelDtos.StockAdjustment(-1,"Too much"),admin)));
    }
   } finally { statement.execute("DROP SCHEMA "+schema+" CASCADE"); }
  }
 }
}
