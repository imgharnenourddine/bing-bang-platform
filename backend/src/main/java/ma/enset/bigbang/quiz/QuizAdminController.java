package ma.enset.bigbang.quiz;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/quiz")
@Validated
public class QuizAdminController {
    private final QuizAdminService service;
    public QuizAdminController(QuizAdminService service){this.service=service;}
    @PostMapping public ResponseEntity<QuizAdminDtos.Quiz> create(@Valid @RequestBody QuizAdminDtos.CreateQuiz input){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(input));}
    @GetMapping public QuizAdminDtos.Quiz get(){return service.get();}
    @PatchMapping public QuizAdminDtos.Quiz update(@Valid @RequestBody QuizAdminDtos.UpdateQuiz input){return service.update(input);}
    @GetMapping("/questions") public List<QuizAdminDtos.Question> questions(){return service.list();}
    @PostMapping("/questions") public ResponseEntity<QuizAdminDtos.Question> createQuestion(@Valid @RequestBody QuizAdminDtos.QuestionInput input){return ResponseEntity.status(HttpStatus.CREATED).body(service.createQuestion(input));}
    @GetMapping("/questions/{questionId}") public QuizAdminDtos.Question question(@PathVariable UUID questionId){return service.getQuestion(questionId);}
    @PutMapping("/questions/{questionId}") public QuizAdminDtos.Question updateQuestion(@PathVariable UUID questionId,@Valid @RequestBody QuizAdminDtos.QuestionInput input){return service.updateQuestion(questionId,input);}
    @DeleteMapping("/questions/{questionId}") public ResponseEntity<Void> delete(@PathVariable UUID questionId,@RequestParam long expectedVersion){if(expectedVersion<0)throw new QuizAdminException(HttpStatus.BAD_REQUEST,"INVALID_VERSION","Version must be non-negative.");service.deleteQuestion(questionId,expectedVersion);return ResponseEntity.noContent().build();}
    @PutMapping("/questions/order") public ResponseEntity<Void> reorder(@Valid @RequestBody QuizAdminDtos.Reorder input){service.reorder(input);return ResponseEntity.noContent().build();}
    @GetMapping("/readiness") public QuizAdminDtos.Readiness readiness(){return service.readiness();}
}
