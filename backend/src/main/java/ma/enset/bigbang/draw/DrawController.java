package ma.enset.bigbang.draw;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.UUID;
import ma.enset.bigbang.auth.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class DrawController {
    private final DrawService draws;
    public DrawController(DrawService draws) { this.draws = draws; }
    private UUID identity(Authentication auth) { return ((SessionPrincipal) auth.getPrincipal()).id(); }
    @PostMapping("/api/admin/draw/start")
    public ResponseEntity<DrawDtos.Draw> start(Authentication auth, @RequestBody(required = false) JsonNode body) {
        if (body != null && !body.isNull() && (!body.isObject() || !body.isEmpty()))
            throw new DrawException(HttpStatus.BAD_REQUEST, "UNEXPECTED_INPUT", "Draw start does not accept selection parameters");
        return ResponseEntity.status(HttpStatus.CREATED).body(draws.start(identity(auth)));
    }
    @GetMapping("/api/admin/draw/status") public DrawDtos.Status status() { return draws.status(); }
    @PostMapping("/api/admin/draw/reset")
    public DrawDtos.ResetResult reset(Authentication auth, @Valid @RequestBody DrawDtos.ResetRequest request) { return draws.reset(identity(auth), request); }
    @PostMapping("/api/admin/draw/{drawId}/cancel")
    public DrawDtos.Draw cancel(Authentication auth, @PathVariable UUID drawId) { return draws.cancel(drawId, identity(auth)); }
    @GetMapping("/api/admin/draw/history")
    public DrawDtos.History history(@RequestParam(required = false) Integer cycleNumber, @RequestParam(required = false) DrawStatus status,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return draws.history(cycleNumber, status, page, size);
    }
    @GetMapping("/api/draw/current") public DrawDtos.PublicState current() { return draws.current(); }
    @GetMapping("/api/participants/me/draw") public DrawDtos.MyState mine(Authentication auth) { return draws.myState(identity(auth)); }
}
