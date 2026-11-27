package ma.enset.bigbang.draw;

import java.util.UUID;
import ma.enset.bigbang.auth.*;
import ma.enset.bigbang.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DrawController.class)
@Import(SecurityConfig.class)
class DrawControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DrawService draws;
    @MockBean AdministratorRepository administrators;
    private UsernamePasswordAuthenticationToken user(Role role, UUID id) {
        var principal = new SessionPrincipal(id, "test", role, true, null);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
    @Test void mutationsRequireAdminAndCsrf() throws Exception {
        mvc.perform(post("/api/admin/draw/start").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/draw/start").with(authentication(user(Role.PARTICIPANT, UUID.randomUUID()))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/draw/start").with(authentication(user(Role.ADMIN, UUID.randomUUID())))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/draw/start").with(authentication(user(Role.ADMIN, UUID.randomUUID()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"participantId\":\"123\"}")).andExpect(status().isBadRequest());
    }
    @Test void resetRequiresExplicitConfirmation() throws Exception {
        mvc.perform(post("/api/admin/draw/reset").with(authentication(user(Role.ADMIN, UUID.randomUUID()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":false,\"expectedCycleNumber\":1}"))
                .andExpect(status().isBadRequest());
    }
    @Test void participantReadsOnlyOwnState() throws Exception {
        UUID id = UUID.randomUUID();
        when(draws.myState(id)).thenReturn(new DrawDtos.MyState(1, false, false, null, null));
        mvc.perform(get("/api/participants/me/draw").with(authentication(user(Role.PARTICIPANT, id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cycleNumber").value(1));
        verify(draws).myState(id);
        mvc.perform(get("/api/admin/draw/history").with(authentication(user(Role.PARTICIPANT, id)))).andExpect(status().isForbidden());
    }
    @Test void adminCanReadStatusAndParticipantCanReadPublicState() throws Exception {
        when(draws.status()).thenReturn(new DrawDtos.Status(1, 3, 0, 3, null, true, true));
        when(draws.current()).thenReturn(new DrawDtos.PublicState(1, false, null, null));
        mvc.perform(get("/api/admin/draw/status").with(authentication(user(Role.ADMIN, UUID.randomUUID())))).andExpect(status().isOk());
        mvc.perform(get("/api/draw/current").with(authentication(user(Role.PARTICIPANT, UUID.randomUUID())))).andExpect(status().isOk());
    }
}
