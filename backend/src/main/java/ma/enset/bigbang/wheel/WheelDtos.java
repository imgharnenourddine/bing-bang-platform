package ma.enset.bigbang.wheel;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WheelDtos {
 private WheelDtos() { }
 public record Prize(UUID id,String name,String description,PrizeCategory category,BigDecimal baseWeight,int initialStock,int remainingStock,boolean enabled,Instant createdAt,Instant updatedAt) { }
 public record CreatePrize(@NotBlank @Size(max=120) String name,@Size(max=500) String description,@NotNull PrizeCategory category,
   @NotNull @DecimalMin(value="0.000001") @DecimalMax("1000000000") @Digits(integer=10,fraction=6) BigDecimal baseWeight,@NotNull @Min(0) Integer initialStock,@NotNull Boolean enabled) { }
 public record PatchPrize(@Size(max=120) String name,@Size(max=500) String description,PrizeCategory category,
   @DecimalMin(value="0.000001") @DecimalMax("1000000000") @Digits(integer=10,fraction=6) BigDecimal baseWeight,Boolean enabled) { }
 public record StockAdjustment(@NotNull Integer quantityDelta,@NotBlank @Size(max=500) String reason) { }
 public record Config(BigDecimal geometricGrowthFactor,BigDecimal maximumPremiumMultiplier,long consecutiveStandardAwards,Instant updatedAt) { }
 public record ConfigUpdate(@NotNull @DecimalMin("1") @DecimalMax("100") @Digits(integer=3,fraction=6) BigDecimal geometricGrowthFactor,
  @NotNull @DecimalMin("1") @DecimalMax("1000") @Digits(integer=4,fraction=6) BigDecimal maximumPremiumMultiplier) { }
 public record Segment(UUID prizeId,String name,PrizeCategory category,int remainingStock,BigDecimal baseWeight,BigDecimal effectiveWeight,BigDecimal probability) { }
 public record Award(UUID awardId,UUID drawId,UUID participantId,int participantNumber,UUID prizeId,String prizeName,PrizeCategory prizeCategory,UUID awardedBy,Instant awardedAt,
  BigDecimal baseWeight,BigDecimal effectiveWeight,BigDecimal winningProbability,long progressionBefore,long progressionAfter,List<Segment> segments) { }
 public record AdminStatus(ma.enset.bigbang.draw.DrawDtos.Draw activeDraw,long consecutiveStandardAwards,BigDecimal premiumMultiplier,List<Segment> eligiblePrizes,boolean canSpin) { }
 public record PublicSegment(UUID prizeId,String name,PrizeCategory category) { }
 public record PublicAward(UUID awardId,UUID drawId,int participantNumber,UUID prizeId,String prizeName,PrizeCategory prizeCategory,Instant awardedAt,List<PublicSegment> segments) { }
 public record PublicStatus(boolean active,UUID drawId,Integer participantNumber,List<PublicSegment> eligiblePrizes,PublicAward lastResult) { }
 public record History(List<Award> content,int page,int size,long totalElements) { }
 public record PublicHistory(List<PublicAward> content,int page,int size,long totalElements) { }
 public record Spin(UUID drawId) { }
}
