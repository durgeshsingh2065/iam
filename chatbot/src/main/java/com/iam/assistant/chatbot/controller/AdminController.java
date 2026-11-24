package com.iam.assistant.chatbot.controller;

import com.iam.assistant.chatbot.model.UserAccount;
import com.iam.assistant.chatbot.service.PersistentSessionService;
import com.iam.assistant.chatbot.service.UserManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Controller
public class AdminController {

    private final UserManagementService userService;
    private final PersistentSessionService sessionService;

    public AdminController(UserManagementService userService, PersistentSessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "admin";
    }

    @GetMapping("/api/admin/users")
    @ResponseBody
    public List<Map<String, Object>> listUsers() {
        return userService.listUsers().stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping("/api/admin/users")
    @ResponseBody
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String role = body.getOrDefault("role", "USER");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }
        try {
            userService.createUser(username.trim(), password, role);
            UserAccount created = userService.listUsers().stream()
                    .filter(u -> u.getUsername().equalsIgnoreCase(username.trim()))
                    .findFirst().orElseThrow();
            return ResponseEntity.ok(toDto(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/users/{username}")
    @ResponseBody
    public ResponseEntity<?> updateUser(@PathVariable String username,
                                         @RequestBody Map<String, Object> body) {
        String password = (String) body.get("password");
        String role = (String) body.get("role");
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", "true")));
        String apiToken = (String) body.get("apiToken");

        try {
            userService.updateUser(username, password, role, enabled);
            if (apiToken != null && !apiToken.isBlank()) {
                userService.updateToken(username, apiToken);
            }
            UserAccount updated = userService.listUsers().stream()
                    .filter(u -> u.getUsername().equalsIgnoreCase(username))
                    .findFirst().orElseThrow();
            return ResponseEntity.ok(toDto(updated));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/api/admin/users/{username}")
    @ResponseBody
    public ResponseEntity<?> deleteUser(@PathVariable String username, Authentication auth) {
        if (auth.getName().equalsIgnoreCase(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot delete your own account"));
        }
        try {
            userService.deleteUser(username);
            sessionService.deleteAllSessionsForUser(username);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> toDto(UserAccount u) {
        return Map.of(
                "username", u.getUsername(),
                "role", u.getRole(),
                "apiToken", u.getApiToken() != null ? u.getApiToken() : "",
                "enabled", u.isEnabled()
        );
    }
}
