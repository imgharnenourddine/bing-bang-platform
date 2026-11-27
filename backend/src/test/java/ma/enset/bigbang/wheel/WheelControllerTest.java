package ma.enset.bigbang.wheel;

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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WheelController.class) @Import(SecurityConfig.class)
class WheelControllerTest {
 @Autowired MockMvc mvc; @MockBean WheelService service; @MockBean AdministratorRepository admins;
 private UsernamePasswordAuthenticationToken user(Role role,UUID id) { var p=new SessionPrincipal(id,"test",role,true,null);return UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()); }
 @Test void spinRequiresAdminCsrfAndRejectsClientWinner() throws Exception {
  var admin=user(Role.ADMIN,UUID.randomUUID());var participant=user(Role.PARTICIPANT,UUID.randomUUID());
  mvc.perform(post("/api/admin/wheel/spin").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/admin/wheel/spin").with(authentication(participant)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/wheel/spin").with(authentication(admin)).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/wheel/spin").with(authentication(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"drawId\":\""+UUID.randomUUID()+"\",\"prizeId\":\""+UUID.randomUUID()+"\"}" )).andExpect(status().isBadRequest());
 }
 @Test void ownHistoryUsesSessionIdentity() throws Exception {
  UUID id=UUID.randomUUID();when(service.myPrizes(id,0,20)).thenReturn(new WheelDtos.PublicHistory(java.util.List.of(),0,20,0));
  mvc.perform(get("/api/participants/me/prizes").with(authentication(user(Role.PARTICIPANT,id)))).andExpect(status().isOk());
  verify(service).myPrizes(id,0,20);
 }
}
