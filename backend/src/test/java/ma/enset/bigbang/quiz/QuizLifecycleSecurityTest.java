package ma.enset.bigbang.quiz;

import java.time.Instant;
import java.util.List;
import ma.enset.bigbang.auth.AdministratorRepository;
import ma.enset.bigbang.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=QuizLifecycleController.class)
@Import(SecurityConfig.class)
class QuizLifecycleSecurityTest {
 @Autowired MockMvc mvc;@MockBean QuizLifecycleService service;@MockBean AdministratorRepository administrators;
 @Test void participantCanReadSafeStateButCannotMutate() throws Exception {
  when(service.participant(any())).thenReturn(new QuizLifecycleDtos.ParticipantState("Quiz",QuizStatus.QUESTION_ACTIVE,null,1,1,"Question",List.of(new QuizLifecycleDtos.PublicOption(java.util.UUID.randomUUID(),"Option",1)),Instant.now(),Instant.now(),Instant.now(),false,null,true));
  mvc.perform(get("/api/quiz/status")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/quiz/status").with(user(new ma.enset.bigbang.auth.SessionPrincipal(java.util.UUID.randomUUID(),"p",ma.enset.bigbang.auth.Role.PARTICIPANT,true,null)))).andExpect(status().isOk()).andExpect(jsonPath("$.options[0].correct").doesNotExist());
  mvc.perform(post("/api/admin/quiz/start").with(user(new ma.enset.bigbang.auth.SessionPrincipal(java.util.UUID.randomUUID(),"p",ma.enset.bigbang.auth.Role.PARTICIPANT,true,null))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/quiz/start").with(user("a").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/quiz/start").with(user("a").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isOk());
 }
}
