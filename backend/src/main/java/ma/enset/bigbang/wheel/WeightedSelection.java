package ma.enset.bigbang.wheel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Decimal weights are converted to integer units; rejection sampling avoids modulo bias. */
public class WeightedSelection {
 private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
 private final Function<BigInteger,BigInteger> below;
 public WeightedSelection(SecureRandom random) { this(bound -> { BigInteger value; do { value=new BigInteger(bound.bitLength(),random); } while(value.compareTo(bound)>=0); return value; }); }
 WeightedSelection(Function<BigInteger,BigInteger> below) { this.below=below; }
 public record Distribution(BigDecimal multiplier,List<WheelDtos.Segment> segments,BigInteger total) { }
 public BigDecimal multiplier(BigDecimal growth,BigDecimal cap,long k) {
  if(growth.compareTo(BigDecimal.ONE)<0 || cap.compareTo(BigDecimal.ONE)<0 || k<0) throw new IllegalArgumentException("Invalid wheel configuration");
  BigDecimal result=BigDecimal.ONE, base=growth.min(cap); long exponent=k;
  while(exponent>0 && result.compareTo(cap)<0) {
   if((exponent&1)==1) result=result.multiply(base,MC).min(cap);
   exponent >>>= 1;
   if(exponent>0) base=base.multiply(base,MC).min(cap);
  }
  return result.min(cap).setScale(6,RoundingMode.HALF_EVEN);
 }
 public Distribution calculate(List<WheelDtos.Prize> prizes,WheelDtos.Config config) {
  BigDecimal multiplier=multiplier(config.geometricGrowthFactor(),config.maximumPremiumMultiplier(),config.consecutiveStandardAwards());
  var eligible=prizes.stream().filter(p->p.enabled() && p.remainingStock()>0).toList();
  var units=new ArrayList<BigInteger>(); BigInteger total=BigInteger.ZERO;
  for(var p:eligible) {
   BigDecimal weight=p.baseWeight().multiply(p.category()==PrizeCategory.PREMIUM?multiplier:BigDecimal.ONE).setScale(12,RoundingMode.UNNECESSARY);
   BigInteger n=weight.movePointRight(12).toBigIntegerExact();
   if(n.signum()<=0) throw new IllegalArgumentException("Invalid weight"); units.add(n); total=total.add(n);
  }
  var segments=new ArrayList<WheelDtos.Segment>();
  for(int i=0;i<eligible.size();i++) {
   var p=eligible.get(i); var weight=new BigDecimal(units.get(i),12);
   var probability=new BigDecimal(units.get(i)).divide(new BigDecimal(total),30,RoundingMode.HALF_UP);
   if(probability.signum()==0) throw new IllegalStateException("Probability below representable precision");
   segments.add(new WheelDtos.Segment(p.id(),p.name(),p.category(),p.remainingStock(),p.baseWeight(),weight,probability));
  }
  return new Distribution(multiplier,List.copyOf(segments),total);
 }
 public WheelDtos.Segment select(Distribution distribution) {
  if(distribution.total().signum()<=0) throw new IllegalArgumentException("No eligible prizes");
  BigInteger threshold=below.apply(distribution.total());
  if(threshold.signum()<0 || threshold.compareTo(distribution.total())>=0) throw new IllegalArgumentException("Invalid random threshold");
  BigInteger cumulative=BigInteger.ZERO;
  for(var segment:distribution.segments()) { cumulative=cumulative.add(segment.effectiveWeight().movePointRight(12).toBigIntegerExact()); if(threshold.compareTo(cumulative)<0) return segment; }
  throw new IllegalStateException("Invalid distribution");
 }
}
