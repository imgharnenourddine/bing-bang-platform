package ma.enset.bigbang.participant;

import java.util.UUID;
import ma.enset.bigbang.auth.*;
import ma.enset.bigbang.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ParticipantController.class)
@Import(SecurityConfig.class)
class ParticipantControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ParticipantService participants;
    @MockBean AdministratorRepository administrators;
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ParticipantResponse PERSON = new ParticipantResponse(ID, 1, "أحمد", "Alami", Role.PARTICIPANT);
    private static final String BODY = "{\"firstName\":\"  أحمد  \",\"lastName\":\"Alami\"}";
    private UsernamePasswordAuthenticationToken participant() {
        var principal = new SessionPrincipal(ID, ID.toString(), Role.PARTICIPANT, true, null);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
    @Test void registerCreatesAuthenticatedSession() throws Exception {
        when(participants.register(anyString(), any())).thenReturn(new ParticipantService.Registration(PERSON, true));
        var session = new MockHttpSession();
        mvc.perform(post("/api/participants/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.firstName").value("أحمد"))
            .andExpect(jsonPath("$.participantNumber").value(1));
        when(participants.findById(ID)).thenReturn(PERSON);
        mvc.perform(get("/api/participants/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ID.toString()));
        verify(participants).register(anyString(), argThat(r -> r.firstName().equals("أحمد")));
    }
    @Test void registeredSessionIsIdempotent() throws Exception {
        when(participants.findById(ID)).thenReturn(PERSON);
        mvc.perform(post("/api/participants/register").with(authentication(participant())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        verify(participants, never()).register(anyString(), any());
    }
    @Test void adminCannotRegisterAsParticipant() throws Exception {
        mvc.perform(post("/api/participants/register").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }
    @Test void requiresCsrfAndValidNames() throws Exception {
        mvc.perform(post("/api/participants/register").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post("/api/participants/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"   \",\"lastName\":\"Alami\"}")).andExpect(status().isBadRequest());
    }
    @Test void protectedEndpointsEnforceRoles() throws Exception {
        mvc.perform(get("/api/participants/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/participants/me").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/participants").with(authentication(participant()))).andExpect(status().isForbidden());
        when(participants.count()).thenReturn(42L);
        mvc.perform(get("/api/admin/participants/count").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(42));
    }
    @Test void repeatedAnonymousRequestReturnsPreviouslyMappedIdentity() throws Exception {
        when(participants.register(anyString(), any())).thenReturn(new ParticipantService.Registration(PERSON, false));
        mvc.perform(post("/api/participants/register").session(new MockHttpSession()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
    }
}
