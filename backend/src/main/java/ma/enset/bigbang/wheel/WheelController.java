package ma.enset.bigbang.wheel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import ma.enset.bigbang.auth.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class WheelController {
 private final WheelService service; private final ObjectMapper mapper; private final Validator validator;
 public WheelController(WheelService service,ObjectMapper mapper,Validator validator) { this.service=service;this.mapper=mapper;this.validator=validator; }
 private UUID identity(Authentication authentication) { return ((SessionPrincipal)authentication.getPrincipal()).id(); }
 private <T> T parse(JsonNode input,Class<T> type,Set<String> allowed) {
  if(input==null||!input.isObject()) throw new WheelException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Expected a JSON object");
  var fields=input.fieldNames();while(fields.hasNext()) if(!allowed.contains(fields.next())) throw new WheelException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Unexpected field");
  try { T result=mapper.treeToValue(input,type);if(!validator.validate(result).isEmpty()) throw new WheelException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Request validation failed");return result; }
  catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) { throw new WheelException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Invalid request fields"); }
 }
 @PostMapping("/api/admin/prizes") public ResponseEntity<WheelDtos.Prize> create(@Valid @RequestBody WheelDtos.CreatePrize p) { return ResponseEntity.status(201).body(service.create(p)); }
 @GetMapping("/api/admin/prizes") public java.util.List<WheelDtos.Prize> list() { return service.list(); }
 @GetMapping("/api/admin/prizes/{id}") public WheelDtos.Prize get(@PathVariable UUID id) { return service.get(id); }
 @PatchMapping("/api/admin/prizes/{id}") public WheelDtos.Prize patch(@PathVariable UUID id,@RequestBody JsonNode body) {
  return service.patch(id,parse(body,WheelDtos.PatchPrize.class,Set.of("name","description","category","baseWeight","enabled")));
 }
 @PostMapping("/api/admin/prizes/{id}/stock") public WheelDtos.Prize stock(@PathVariable UUID id,@Valid @RequestBody WheelDtos.StockAdjustment request,Authentication auth) { return service.adjust(id,request,identity(auth)); }
 @GetMapping("/api/admin/wheel/config") public WheelDtos.Config config() { return service.config(); }
 @PatchMapping("/api/admin/wheel/config") public WheelDtos.Config configure(@Valid @RequestBody WheelDtos.ConfigUpdate config) { return service.updateConfig(config); }
 @GetMapping("/api/admin/wheel/status") public WheelDtos.AdminStatus status() { return service.status(); }
 @PostMapping("/api/admin/wheel/spin") public ResponseEntity<WheelDtos.Award> spin(@RequestBody JsonNode body,Authentication auth) {
  var input=parse(body,WheelDtos.Spin.class,Set.of("drawId"));if(input.drawId()==null) throw new WheelException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","drawId is required");
  var result=service.spin(input.drawId(),identity(auth));return ResponseEntity.status(result.created()?201:200).body(result.award());
 }
 @GetMapping("/api/admin/wheel/history") public WheelDtos.History history(@RequestParam(required=false) UUID participant,@RequestParam(required=false) UUID prize,@RequestParam(required=false) PrizeCategory category,@RequestParam(required=false) UUID draw,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.history(participant,prize,category,draw,page,size); }
 @GetMapping("/api/wheel/status") public WheelDtos.PublicStatus publicStatus() { return service.publicStatus(); }
 @GetMapping("/api/participants/me/prizes") public WheelDtos.PublicHistory myPrizes(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.myPrizes(identity(auth),page,size); }
}
