package com.iam.assistant.chatbot.controller;

import com.iam.assistant.chatbot.model.SessionDetail;
import com.iam.assistant.chatbot.model.SessionSummary;
import com.iam.assistant.chatbot.service.PersistentSessionService;
import com.iam.assistant.chatbot.service.UserManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class SessionController {

    private final PersistentSessionService sessionService;
    private final UserManagementService userManagementService;

    public SessionController(PersistentSessionService sessionService,
                             UserManagementService userManagementService) {
        this.sessionService = sessionService;
        this.userManagementService = userManagementService;
    }

    @GetMapping("/me")
    public Map<String, String> currentUser(Authentication auth) {
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("USER");
        return Map.of("username", auth.getName(), "role", role);
    }

    @GetMapping("/api/user/token")
    public Map<String, String> getMyToken(Authentication auth) {
        return Map.of("apiToken", userManagementService.getUserToken(auth.getName()));
    }

    @PutMapping("/api/user/token")
    public ResponseEntity<?> updateMyToken(@RequestBody Map<String, String> body, Authentication auth) {
        String token = body.getOrDefault("apiToken", "").trim();
        userManagementService.updateToken(auth.getName(), token);
        return ResponseEntity.ok(Map.of("apiToken", token));
    }

    @GetMapping("/sessions")
    public List<SessionSummary> listSessions(Authentication auth) {
        return sessionService.listSessions(auth.getName());
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionSummary> createSession(Authentication auth) {
        var session = sessionService.createSession(auth.getName());
        var summary = new SessionSummary(
                session.getSessionId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getLastActivity(),
                0
        );
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionDetail> getSession(@PathVariable String sessionId,
                                                     Authentication auth) {
        SessionDetail detail = sessionService.getSessionDetail(auth.getName(), sessionId);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }

    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<?> renameSession(@PathVariable String sessionId,
                                            @RequestBody Map<String, String> body,
                                            Authentication auth) {
        String title = body.getOrDefault("title", "").trim();
        if (title.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Title cannot be empty"));
        if (title.length() > 100) title = title.substring(0, 100);
        boolean ok = sessionService.renameSession(auth.getName(), sessionId, title);
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("title", title));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId,
                                               Authentication auth) {
        sessionService.deleteSession(auth.getName(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
