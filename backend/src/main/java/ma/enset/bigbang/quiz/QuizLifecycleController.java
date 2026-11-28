package ma.enset.bigbang.quiz;

import jakarta.validation.Valid;
import ma.enset.bigbang.auth.SessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuizLifecycleController {
 private final QuizLifecycleService service;
 public QuizLifecycleController(QuizLifecycleService service){this.service=service;}
 @PostMapping("/api/admin/quiz/start") public QuizLifecycleDtos.AdminState start(@Valid @RequestBody QuizLifecycleDtos.VersionRequest input){return service.start(input.expectedVersion());}
 @PostMapping("/api/admin/quiz/questions/open") public QuizLifecycleDtos.AdminState open(@Valid @RequestBody QuizLifecycleDtos.VersionRequest input){return service.openFirst(input.expectedVersion());}
 @PostMapping("/api/admin/quiz/questions/close") public QuizLifecycleDtos.AdminState close(@Valid @RequestBody QuizLifecycleDtos.QuestionRequest input){return service.closeCurrent(input.expectedQuestionId(),input.expectedVersion());}
 @PostMapping("/api/admin/quiz/questions/next") public QuizLifecycleDtos.AdminState next(@Valid @RequestBody QuizLifecycleDtos.QuestionRequest input){return service.openNext(input.expectedQuestionId(),input.expectedVersion());}
 @PostMapping("/api/admin/quiz/finish") public QuizLifecycleDtos.AdminState finish(@Valid @RequestBody QuizLifecycleDtos.QuestionRequest input){return service.finish(input.expectedQuestionId(),input.expectedVersion());}
 @GetMapping("/api/admin/quiz/status") public QuizLifecycleDtos.AdminState adminStatus(){return service.status();}
 @GetMapping("/api/quiz/status") public QuizLifecycleDtos.ParticipantState participantStatus(@AuthenticationPrincipal SessionPrincipal principal){return service.participant(principal.id());}
}
