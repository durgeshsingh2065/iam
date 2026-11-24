package com.iam.assistant.agent.controller;

import com.iam.assistant.agent.service.SystemPromptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final SystemPromptService systemPromptService;

    public AdminController(SystemPromptService systemPromptService) {
        this.systemPromptService = systemPromptService;
    }

    @PostMapping("/prompt/reload")
    public ResponseEntity<Map<String, Object>> reloadPrompt() {
        systemPromptService.reload();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "source", systemPromptService.getSource(),
                "promptLength", systemPromptService.getPrompt().length(),
                "reloadedAt", systemPromptService.getLastLoaded().toString()
        ));
    }

    @GetMapping("/prompt/info")
    public ResponseEntity<Map<String, Object>> promptInfo() {
        return ResponseEntity.ok(Map.of(
                "source", systemPromptService.getSource(),
                "promptLength", systemPromptService.getPrompt().length(),
                "lastLoaded", systemPromptService.getLastLoaded().toString()
        ));
    }
}
