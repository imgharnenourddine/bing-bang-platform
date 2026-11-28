package ma.enset.bigbang.quiz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=QuizAdminController.class)
@Import(SecurityConfig.class)
class QuizAdminSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean QuizAdminService service;
    @MockBean AdministratorRepository administrators;
    @Test void onlyAdminCanReadQuestions() throws Exception {
        var question=new QuizAdminDtos.Question(UUID.randomUUID(),"Q",1,30,100,List.of(new QuizAdminDtos.Option(UUID.randomUUID(),"A",1,true)),Instant.now(),Instant.now(),0);
        when(service.list()).thenReturn(List.of(question));
        mvc.perform(get("/api/admin/quiz/questions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/quiz/questions").with(user("p").roles("PARTICIPANT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/quiz/questions").with(user("a").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].options[0].correct").value(true));
        mvc.perform(get("/api/quiz/questions").with(user("p").roles("PARTICIPANT"))).andExpect(status().isForbidden());
    }
    @Test void mutationRequiresAdminAndCsrf() throws Exception {
        String body="{\"title\":\"Quiz\"}";
        mvc.perform(post("/api/admin/quiz").with(user("a").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/quiz").with(user("p").roles("PARTICIPANT")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/quiz").with(user("a").roles("ADMIN")).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/quiz").with(user("a").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}")).andExpect(status().isBadRequest());
    }
}
