package ma.enset.bigbang;

import java.util.Optional;
import java.util.UUID;
import ma.enset.bigbang.auth.*;
import ma.enset.bigbang.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockBean AdministratorRepository repository;
    @MockBean LoginRateLimiter limiter;
    @BeforeEach void setup() { when(limiter.tryAcquire(anyString())).thenReturn(true); }
    @Test void csrfIsRequiredForLogin() throws Exception {
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void unknownUserGetsGenericUnauthorized() throws Exception {
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"missing\",\"password\":\"invalid\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Invalid credentials"));
    }
    @Test void wrongPasswordIsUnauthorized() throws Exception {
        var admin = new Administrator("admin", encoder.encode("a-long-test-password"));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void disabledAdministratorIsRejected() throws Exception {
        var admin = mock(Administrator.class);
        when(admin.getId()).thenReturn(UUID.randomUUID());
        when(admin.getUsername()).thenReturn("disabled");
        when(admin.getPasswordHash()).thenReturn(encoder.encode("a-long-test-password"));
        when(admin.isEnabled()).thenReturn(false);
        when(repository.findByUsername("disabled")).thenReturn(Optional.of(admin));
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"disabled\",\"password\":\"a-long-test-password\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void loginRotatesSessionAndLogoutInvalidatesIt() throws Exception {
        var admin = new Administrator("admin", encoder.encode("a-long-test-password"));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));
        MockHttpSession session = new MockHttpSession();
        String oldId = session.getId();
        mvc.perform(post("/api/auth/admin/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"a-long-test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
        org.junit.jupiter.api.Assertions.assertNotEquals(oldId, session.getId());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void participantCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/admin/test").with(user("participant").roles("PARTICIPANT"))).andExpect(status().isForbidden());
    }
    @Test void unknownRouteIsDenied() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
    }
    @Test void csrfEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token", not(emptyString())));
    }
    @Test void rateLimitReturns429() throws Exception {
        when(limiter.tryAcquire(anyString())).thenReturn(false);
        mvc.perform(post("/api/auth/admin/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"anything\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
