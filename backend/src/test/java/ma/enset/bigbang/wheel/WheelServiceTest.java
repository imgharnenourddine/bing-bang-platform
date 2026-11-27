package ma.enset.bigbang.wheel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.enset.bigbang.draw.*;
import ma.enset.bigbang.notification.RealtimeEvents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WheelServiceTest {
 @Test void retryReturnsPersistedAwardWithoutMutatingOrPublishing() {
  var repo=mock(WheelRepository.class);var drawRepo=mock(DrawRepository.class);var draws=mock(DrawService.class);var realtime=mock(RealtimeEvents.class);
  UUID draw=UUID.randomUUID(),admin=UUID.randomUUID();
  var existing=new WheelDtos.Award(UUID.randomUUID(),draw,UUID.randomUUID(),3,UUID.randomUUID(),"Prize",PrizeCategory.STANDARD,admin,Instant.now(),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,0,1,List.of());
  when(repo.byDraw(draw)).thenReturn(Optional.of(existing));
  var result=new WheelService(repo,drawRepo,draws,realtime).spin(draw,admin);
  assertFalse(result.created());assertEquals(existing,result.award());
  verify(drawRepo).lockControl();verify(repo).byDraw(draw);verifyNoMoreInteractions(repo,drawRepo);verifyNoInteractions(draws,realtime);
 }
 @Test void emptyInventoryRejectsBeforeChangingProgression() {
  var repo=mock(WheelRepository.class);var drawRepo=mock(DrawRepository.class);var draws=mock(DrawService.class);var realtime=mock(RealtimeEvents.class);
  UUID id=UUID.randomUUID(),admin=UUID.randomUUID(),person=UUID.randomUUID();
  var target=new DrawRecord(id,1,1,person,1,"A","B",admin,DrawStatus.ACTIVE,Instant.now(),null,null,null);
  when(repo.byDraw(id)).thenReturn(Optional.empty());when(drawRepo.find(id)).thenReturn(Optional.of(target));
  when(draws.lockActiveForPrizeAllocation()).thenReturn(DrawDtos.toDto(target));
  when(repo.config(true)).thenReturn(new WheelDtos.Config(BigDecimal.ONE,BigDecimal.TEN,3,Instant.now()));when(repo.prizes(true)).thenReturn(List.of());
  assertEquals("NO_ELIGIBLE_PRIZES",assertThrows(WheelException.class,()->new WheelService(repo,drawRepo,draws,realtime).spin(id,admin)).code());
  verify(repo,never()).progression(anyLong());verify(repo,never()).stock(any(),anyInt());verifyNoInteractions(realtime);
 }
 @Test void rejectsNegativeStockAndDoesNotAudit() {
  var repo=mock(WheelRepository.class);var drawRepo=mock(DrawRepository.class);var draws=mock(DrawService.class);var realtime=mock(RealtimeEvents.class);
  UUID id=UUID.randomUUID();when(repo.prize(id,true)).thenReturn(Optional.of(new WheelDtos.Prize(id,"P",null,PrizeCategory.STANDARD,BigDecimal.ONE,1,1,true,Instant.now(),Instant.now())));
  var service=new WheelService(repo,drawRepo,draws,realtime);
  assertEquals("INVALID_STOCK",assertThrows(WheelException.class,()->service.adjust(id,new WheelDtos.StockAdjustment(-2,"reason"),UUID.randomUUID())).code());
  verify(repo,never()).stock(any(),anyInt());verify(repo,never()).audit(any(),any(),anyInt(),anyInt(),anyInt(),any(),any());
 }
 @Test void successfulPremiumSpinDecrementsStockResetsProgressionAndCompletesDraw() {
  var repo=mock(WheelRepository.class);var drawRepo=mock(DrawRepository.class);var draws=mock(DrawService.class);var realtime=mock(RealtimeEvents.class);
  UUID id=UUID.randomUUID(),admin=UUID.randomUUID(),person=UUID.randomUUID(),prizeId=UUID.randomUUID(),awardId=UUID.randomUUID();
  var target=new DrawRecord(id,1,1,person,1,"A","B",admin,DrawStatus.ACTIVE,Instant.now(),null,null,null);
  var prize=new WheelDtos.Prize(prizeId,"Gift",null,PrizeCategory.PREMIUM,BigDecimal.ONE,1,1,true,Instant.now(),Instant.now());
  var sold=new WheelDtos.Prize(prizeId,"Gift",null,PrizeCategory.PREMIUM,BigDecimal.ONE,1,0,true,Instant.now(),Instant.now());
  var config=new WheelDtos.Config(new BigDecimal("1.5"),BigDecimal.TEN,3,Instant.now());
  var award=new WheelDtos.Award(awardId,id,person,1,prizeId,"Gift",PrizeCategory.PREMIUM,admin,Instant.now(),BigDecimal.ONE,new BigDecimal("3.375"),BigDecimal.ONE,3,0,List.of());
  when(repo.byDraw(id)).thenReturn(Optional.empty(),Optional.of(award));when(drawRepo.find(id)).thenReturn(Optional.of(target));
  when(draws.lockActiveForPrizeAllocation()).thenReturn(DrawDtos.toDto(target));when(repo.config(true)).thenReturn(config);when(repo.config(false)).thenReturn(config);
  when(repo.prizes(true)).thenReturn(List.of(prize));when(repo.prizes(false)).thenReturn(List.of(sold));when(repo.prize(prizeId,false)).thenReturn(Optional.of(prize),Optional.of(sold));
  var result=new WheelService(repo,drawRepo,draws,realtime).spin(id,admin);
  assertTrue(result.created());assertEquals(awardId,result.award().awardId());
  verify(repo).stock(prizeId,0);verify(repo).progression(0);verify(draws).completeAfterPrizeAllocation(id);verify(realtime).prizeWon(eq(person),any());
 }
}
