package com.iam.assistant.agent.controller;

import com.iam.assistant.agent.model.AgentRequest;
import com.iam.assistant.agent.model.AgentResponse;
import com.iam.assistant.agent.service.AgentOrchestrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for the MCP Agent.
 * Called by the Chatbot service.
 *
 * POST /agent/query  — process a natural-language query
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentOrchestrationService orchestrationService;

    public AgentController(AgentOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * Process a natural-language IAM query.
     *
     * Request:
     * {
     *   "query": "What is access certification?",
     *   "sessionId": "abc-123",           // optional
     *   "history": [...]                   // optional multi-turn history
     * }
     *
     * Response:
     * {
     *   "answer": "Here is the summary...",
     *   "sessionId": "abc-123",
     *   "toolsUsed": ["docs_search"],
     *   "error": false
     * }
     */
    @PostMapping("/query")
    public ResponseEntity<AgentResponse> query(@Valid @RequestBody AgentRequest request) {
        log.info("Agent query received: session={}", request.getSessionId());
        AgentResponse response = orchestrationService.process(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("MCP Agent is healthy");
    }
}
