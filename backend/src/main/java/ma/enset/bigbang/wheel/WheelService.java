package ma.enset.bigbang.wheel;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import ma.enset.bigbang.draw.DrawRepository;
import ma.enset.bigbang.draw.DrawService;
import ma.enset.bigbang.notification.EventType;
import ma.enset.bigbang.notification.RealtimeEvents;
import ma.enset.bigbang.notification.RealtimePayload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WheelService {
 private final WheelRepository repo; private final DrawRepository drawRepo; private final DrawService draws; private final RealtimeEvents realtime;
 private final WeightedSelection selection=new WeightedSelection(new SecureRandom());
 public WheelService(WheelRepository repo,DrawRepository drawRepo,DrawService draws,RealtimeEvents realtime) { this.repo=repo;this.drawRepo=drawRepo;this.draws=draws;this.realtime=realtime; }
 public record SpinResult(WheelDtos.Award award,boolean created) { }
 private WheelException error(HttpStatus status,String code,String message) { return new WheelException(status,code,message); }
 private WheelDtos.Prize prize(UUID id,boolean lock) { return repo.prize(id,lock).orElseThrow(()->error(HttpStatus.NOT_FOUND,"PRIZE_NOT_FOUND","Prize not found")); }
 private void lock() { drawRepo.lockControl();repo.config(true); }
 @Transactional public WheelDtos.Prize create(WheelDtos.CreatePrize p) { lock(); UUID id=UUID.randomUUID();repo.insertPrize(id,p);var result=prize(id,false);inventory(result);return result; }
 @Transactional(readOnly=true) public List<WheelDtos.Prize> list() { return repo.prizes(false); }
 @Transactional(readOnly=true) public WheelDtos.Prize get(UUID id) { return prize(id,false); }
 @Transactional public WheelDtos.Prize patch(UUID id,WheelDtos.PatchPrize p) {
  lock();var old=prize(id,true);String name=p.name()==null?old.name():p.name().strip();
  if(name.isBlank()) throw error(HttpStatus.BAD_REQUEST,"INVALID_NAME","Name cannot be blank");
  repo.updatePrize(id,name,p.description()==null?old.description():p.description(),p.category()==null?old.category():p.category(),p.baseWeight()==null?old.baseWeight():p.baseWeight(),p.enabled()==null?old.enabled():p.enabled());
  var result=prize(id,false);inventory(result);return result;
 }
 @Transactional public WheelDtos.Prize adjust(UUID id,WheelDtos.StockAdjustment request,UUID admin) {
  lock();var old=prize(id,true);int delta=request.quantityDelta();if(delta==0) throw error(HttpStatus.BAD_REQUEST,"INVALID_ADJUSTMENT","Adjustment cannot be zero");
  long next=(long)old.remainingStock()+delta;if(next<0 || next>Integer.MAX_VALUE) throw error(HttpStatus.CONFLICT,"INVALID_STOCK","Adjustment exceeds stock limits");
  repo.stock(id,(int)next);repo.audit(UUID.randomUUID(),id,old.remainingStock(),delta,(int)next,request.reason().strip(),admin);
  var result=prize(id,false);inventory(result);return result;
 }
 @Transactional(readOnly=true) public WheelDtos.Config config() { return repo.config(false); }
 @Transactional public WheelDtos.Config updateConfig(WheelDtos.ConfigUpdate input) { lock();repo.updateConfig(input);availability();return repo.config(false); }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public WheelDtos.AdminStatus status() {
  var c=repo.config(false);var distribution=selection.calculate(repo.prizes(false),c);var active=drawRepo.active().map(ma.enset.bigbang.draw.DrawDtos::toDto).orElse(null);
  return new WheelDtos.AdminStatus(active,c.consecutiveStandardAwards(),distribution.multiplier(),distribution.segments(),active!=null&&!distribution.segments().isEmpty());
 }
 @Transactional public SpinResult spin(UUID drawId,UUID admin) {
  // Lock order is draw_control -> wheel_configuration -> prizes (ascending UUID).
  drawRepo.lockControl();
  var previous=repo.byDraw(drawId);if(previous.isPresent()) return new SpinResult(previous.get(),false);
  var target=drawRepo.find(drawId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"DRAW_NOT_FOUND","Draw not found"));
  if(target.status()!=ma.enset.bigbang.draw.DrawStatus.ACTIVE) throw error(HttpStatus.CONFLICT,"DRAW_NOT_ACTIVE","Draw is not active");
  var active=draws.lockActiveForPrizeAllocation();
  if(!active.id().equals(drawId)) throw error(HttpStatus.CONFLICT,"DRAW_NOT_ACTIVE","Target draw is not active");
  var config=repo.config(true);var distribution=selection.calculate(repo.prizes(true),config);
  if(distribution.segments().isEmpty()) throw error(HttpStatus.CONFLICT,"NO_ELIGIBLE_PRIZES","No enabled prizes with stock");
  var winner=selection.select(distribution);long before=config.consecutiveStandardAwards();
  long after=winner.category()==PrizeCategory.PREMIUM?0:Math.addExact(before,1);
  var current=prize(winner.prizeId(),false);if(current.remainingStock()<1) throw error(HttpStatus.CONFLICT,"OUT_OF_STOCK","Prize stock changed");
  repo.stock(winner.prizeId(),current.remainingStock()-1);UUID awardId=UUID.randomUUID();
  repo.insertAward(awardId,drawId,target.participantId(),target.participantNumber(),admin,winner,before,after,distribution.segments());
  repo.progression(after);draws.completeAfterPrizeAllocation(drawId);
  var award=repo.byDraw(drawId).orElseThrow();
  var publicResult=publicAward(award);
  realtime.wheelResult(EventType.WHEEL_STARTED,new RealtimePayload.WheelResult(awardId,drawId,target.participantNumber(),winner.prizeId(),winner.name(),winner.category().name(),publicResult.segments()));
  realtime.wheelResult(EventType.WHEEL_RESULT,new RealtimePayload.WheelResult(awardId,drawId,target.participantNumber(),winner.prizeId(),winner.name(),winner.category().name(),publicResult.segments()));
  realtime.wheelResult(EventType.WHEEL_STATE_UPDATED,new RealtimePayload.WheelResult(awardId,drawId,target.participantNumber(),winner.prizeId(),winner.name(),winner.category().name(),publicResult.segments()));
  realtime.wheelAdmin(EventType.PRIZE_AWARDED,new RealtimePayload.WheelAdmin(awardId,drawId,target.participantId(),winner.prizeId(),winner.name(),current.remainingStock()-1));
  realtime.prizeWon(target.participantId(),new RealtimePayload.PrizeWon(awardId,drawId,winner.prizeId(),winner.name(),"You won a prize!"));
  inventory(prize(winner.prizeId(),false));
  return new SpinResult(award,true);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public WheelDtos.PublicStatus publicStatus() {
  var active=drawRepo.active().orElse(null);var segments=selection.calculate(repo.prizes(false),repo.config(false)).segments();
  return new WheelDtos.PublicStatus(active!=null,active==null?null:active.id(),active==null?null:active.participantNumber(),segments.stream().map(WheelService::publicSegment).toList(),repo.lastAward().map(WheelService::publicAward).orElse(null));
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public WheelDtos.History history(UUID participant,UUID prize,PrizeCategory category,UUID draw,int page,int size) {
  if(page<0||size<1||size>100) throw error(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION","Invalid pagination");
  return repo.history(participant,prize,category,draw,page,size);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public WheelDtos.PublicHistory myPrizes(UUID participant,int page,int size) {
  var result=history(participant,null,null,null,page,size);
  return new WheelDtos.PublicHistory(result.content().stream().map(WheelService::publicAward).toList(),result.page(),result.size(),result.totalElements());
 }
 private void inventory(WheelDtos.Prize p) { realtime.wheelInventory(new RealtimePayload.WheelAdmin(null,null,null,p.id(),p.name(),p.remainingStock())); availability(); }
 private void availability() { realtime.wheelAvailability(new RealtimePayload.WheelAvailability(selection.calculate(repo.prizes(false),repo.config(false)).segments().stream().map(WheelService::publicSegment).toList())); }
 private static WheelDtos.PublicSegment publicSegment(WheelDtos.Segment s) { return new WheelDtos.PublicSegment(s.prizeId(),s.name(),s.category()); }
 private static WheelDtos.PublicAward publicAward(WheelDtos.Award a) { return new WheelDtos.PublicAward(a.awardId(),a.drawId(),a.participantNumber(),a.prizeId(),a.prizeName(),a.prizeCategory(),a.awardedAt(),a.segments().stream().map(WheelService::publicSegment).toList()); }
}
