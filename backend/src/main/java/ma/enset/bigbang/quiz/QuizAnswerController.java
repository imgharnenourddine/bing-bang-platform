package ma.enset.bigbang.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import ma.enset.bigbang.auth.Role;
import ma.enset.bigbang.auth.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuizAnswerController {
 private final QuizAnswerService service;private final ObjectMapper mapper;private final Validator validator;
 public QuizAnswerController(QuizAnswerService service,ObjectMapper mapper,Validator validator){this.service=service;this.mapper=mapper;this.validator=validator;}
 private UUID participant(SessionPrincipal principal){if(principal==null||principal.role()!=Role.PARTICIPANT)throw new QuizAdminException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","Participant authentication required.");return principal.id();}
 @PostMapping("/api/quiz/answers") public ResponseEntity<QuizAnswerDtos.Acknowledgment> submit(@AuthenticationPrincipal SessionPrincipal principal,@RequestBody JsonNode body){
  if(body==null||!body.isObject())throw new QuizAdminException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","Expected questionId and optionId.");
  var fields=body.fieldNames();while(fields.hasNext())if(!Set.of("questionId","optionId").contains(fields.next()))throw new QuizAdminException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","Unexpected field.");
  QuizAnswerDtos.Submit input;try{input=mapper.treeToValue(body,QuizAnswerDtos.Submit.class);}catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException ex){throw new QuizAdminException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","Invalid answer identifiers.");}
  if(!validator.validate(input).isEmpty())throw new QuizAdminException(HttpStatus.BAD_REQUEST,"INVALID_ANSWER","Question and option are required.");
  var result=service.submit(participant(principal),input);return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.answer());
 }
 @GetMapping("/api/quiz/questions/{questionId}/answer") public QuizAnswerDtos.Acknowledgment answer(@AuthenticationPrincipal SessionPrincipal principal,@PathVariable UUID questionId){return service.answer(participant(principal),questionId);}
 @GetMapping("/api/quiz/questions/{questionId}/feedback") public QuizAnswerDtos.Feedback feedback(@AuthenticationPrincipal SessionPrincipal principal,@PathVariable UUID questionId){return service.feedback(participant(principal),questionId);}
 @GetMapping("/api/admin/quiz/questions/{questionId}/statistics") public QuizAnswerDtos.Statistics stats(@PathVariable UUID questionId){return service.statistics(questionId);}
}
