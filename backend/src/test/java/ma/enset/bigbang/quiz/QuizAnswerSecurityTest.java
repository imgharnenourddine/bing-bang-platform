package ma.enset.bigbang.quiz;

import java.time.Instant;
import java.util.UUID;
import ma.enset.bigbang.auth.AdministratorRepository;
import ma.enset.bigbang.auth.Role;
import ma.enset.bigbang.auth.SessionPrincipal;
import ma.enset.bigbang.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=QuizAnswerController.class)
@Import(SecurityConfig.class)
class QuizAnswerSecurityTest {
 @Autowired MockMvc mvc;@MockBean QuizAnswerService service;@MockBean AdministratorRepository administrators;
 @Test void participantSubmissionRequiresCsrfAndNeverReturnsScore() throws Exception {
  var pid=UUID.randomUUID();var q=UUID.randomUUID();var o=UUID.randomUUID();var principal=new SessionPrincipal(pid,"p",Role.PARTICIPANT,true,null);String body="{\"questionId\":\""+q+"\",\"optionId\":\""+o+"\"}";
  when(service.submit(eq(pid),any())).thenReturn(new QuizAnswerService.Submission(new QuizAnswerDtos.Acknowledgment(q,o,"ACCEPTED",Instant.now()),true));
  mvc.perform(post("/api/quiz/answers").with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/quiz/answers").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/quiz/answers").with(user(principal)).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/quiz/answers").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.awardedPoints").doesNotExist()).andExpect(jsonPath("$.correct").doesNotExist());
  mvc.perform(post("/api/quiz/answers").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"questionId\":\""+q+"\",\"optionId\":\""+o+"\",\"participantId\":\""+pid+"\"}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/admin/quiz/questions/"+q+"/statistics").with(user(principal))).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/quiz/questions/"+q+"/statistics")).andExpect(status().isUnauthorized());
 }
}
