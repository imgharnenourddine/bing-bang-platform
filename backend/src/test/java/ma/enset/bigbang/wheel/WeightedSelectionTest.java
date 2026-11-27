package ma.enset.bigbang.wheel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeightedSelectionTest {
 private WheelDtos.Prize p(String name,PrizeCategory category,String weight,int stock,boolean enabled) { return new WheelDtos.Prize(UUID.randomUUID(),name,null,category,new BigDecimal(weight),stock,stock,enabled,Instant.now(),Instant.now()); }
 private WheelDtos.Config config(long count) { return new WheelDtos.Config(new BigDecimal("1.5"),BigDecimal.TEN,count,Instant.now()); }
 @Test void computesProgressionAndNormalizedProbabilities() {
  var calc=new WeightedSelection(n->BigInteger.ZERO);
  var distribution=calc.calculate(List.of(p("standard",PrizeCategory.STANDARD,"3",1,true),p("premium",PrizeCategory.PREMIUM,"1",1,true),p("off",PrizeCategory.PREMIUM,"9",4,false),p("empty",PrizeCategory.STANDARD,"100",0,true)),config(2));
  assertEquals(2,distribution.segments().size());
  assertEquals(0,new BigDecimal("2.25").compareTo(distribution.multiplier()));
  assertEquals(0,new BigDecimal("3").compareTo(distribution.segments().get(0).effectiveWeight()));
  assertEquals(0,new BigDecimal("2.25").compareTo(distribution.segments().get(1).effectiveWeight()));
  assertEquals(0,new BigDecimal("1").compareTo(distribution.segments().get(0).probability().add(distribution.segments().get(1).probability())));
  assertEquals(0,BigDecimal.TEN.compareTo(calc.multiplier(new BigDecimal("1.5"),BigDecimal.TEN,Long.MAX_VALUE)));
 }
 @Test void usesExactThresholdBoundaries() {
  var input=List.of(p("a",PrizeCategory.STANDARD,"1",1,true),p("b",PrizeCategory.PREMIUM,"2",1,true));
  var low=new WeightedSelection(n->new BigInteger("999999999999"));
  var boundary=new WeightedSelection(n->new BigInteger("1000000000000"));
  assertEquals("a",low.select(low.calculate(input,config(0))).name());
  assertEquals("b",boundary.select(boundary.calculate(input,config(0))).name());
  assertEquals("a",low.select(low.calculate(List.of(input.get(0)),config(0))).name());
  assertThrows(IllegalArgumentException.class,()->low.select(low.calculate(List.of(),config(0))));
 }
}
