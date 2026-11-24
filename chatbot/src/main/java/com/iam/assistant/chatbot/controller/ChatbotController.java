package com.iam.assistant.chatbot.controller;

import com.iam.assistant.chatbot.model.ChatRequest;
import com.iam.assistant.chatbot.model.ChatResponse;
import com.iam.assistant.chatbot.service.ChatbotService;
import com.iam.assistant.chatbot.service.PersistentSessionService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final PersistentSessionService sessionService;

    public ChatbotController(ChatbotService chatbotService, PersistentSessionService sessionService) {
        this.chatbotService = chatbotService;
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                              HttpSession httpSession,
                                              Authentication auth) {
        ChatResponse response = chatbotService.chat(request, auth.getName(), sailPointPassword(httpSession));
        return ResponseEntity.ok(response);
    }

    /** The password typed at login, or null if the session has none (e.g. after a restart). */
    private static String sailPointPassword(HttpSession s) {
        Object v = s == null ? null : s.getAttribute(LoginController.SESSION_PASSWORD_ATTR);
        return v instanceof String str && !str.isEmpty() ? str : null;
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId, Authentication auth) {
        sessionService.deleteSession(auth.getName(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
