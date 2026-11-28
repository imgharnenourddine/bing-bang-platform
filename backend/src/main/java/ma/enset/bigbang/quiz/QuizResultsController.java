package ma.enset.bigbang.quiz;

import ma.enset.bigbang.auth.SessionPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizResultsController {
 private final QuizResultsService service;
 public QuizResultsController(QuizResultsService service) { this.service=service; }
 @GetMapping("/api/admin/quiz/leaderboard") public QuizResultsDtos.Leaderboard leaderboard(){return service.leaderboard();}
 @GetMapping("/api/quiz/me/results") public QuizResultsDtos.Personal personal(@AuthenticationPrincipal SessionPrincipal principal){return service.personal(principal==null?null:principal.id());}
}
