package ma.enset.bigbang.quiz;

import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=QuizResultsController.class)
@Import(SecurityConfig.class)
class QuizResultsSecurityTest {
 @Autowired MockMvc mvc; @MockBean QuizResultsService results; @MockBean AdministratorRepository administrators;
 @Test void participantIsRestrictedToOwnResult() throws Exception {
  UUID id=UUID.randomUUID(); var principal=new SessionPrincipal(id,"participant",Role.PARTICIPANT,true,null);
  when(results.personal(eq(id))).thenReturn(new QuizResultsDtos.Personal(UUID.randomUUID(),"Quiz","FINISHED",3,2,1,1,1,100,300,new BigDecimal("33.3"),Instant.now()));
  mvc.perform(get("/api/quiz/me/results").with(user(principal)).param("participantId",UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.rank").doesNotExist()).andExpect(jsonPath("$.leaderboard").doesNotExist()).andExpect(jsonPath("$.highestScore").doesNotExist());
  verify(results).personal(id);
  mvc.perform(get("/api/admin/quiz/leaderboard").with(user(principal))).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/quiz/leaderboard")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/quiz/me/results").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
 }
}
