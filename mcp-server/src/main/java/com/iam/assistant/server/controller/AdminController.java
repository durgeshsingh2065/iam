package com.iam.assistant.server.controller;

import com.iam.assistant.server.service.PdfDocsLoader;
import com.iam.assistant.server.tools.impl.PdfDocsSearchTool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final PdfDocsLoader pdfDocsLoader;
    private final PdfDocsSearchTool pdfDocsSearchTool;

    public AdminController(PdfDocsLoader pdfDocsLoader, PdfDocsSearchTool pdfDocsSearchTool) {
        this.pdfDocsLoader = pdfDocsLoader;
        this.pdfDocsSearchTool = pdfDocsSearchTool;
    }

    @PostMapping("/knowledge/reload")
    public ResponseEntity<Map<String, Object>> reloadKnowledge() {
        long start = System.currentTimeMillis();
        pdfDocsLoader.reload();
        pdfDocsSearchTool.rebuildIdfIndex();
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Long> summary = pdfDocsLoader.getDocumentSummary();
        long totalChunks = summary.values().stream().mapToLong(Long::longValue).sum();

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "documentCount", summary.size(),
                "chunkCount", totalChunks,
                "elapsedMs", elapsed
        ));
    }

    @GetMapping("/knowledge/info")
    public ResponseEntity<Map<String, Object>> knowledgeInfo() {
        Map<String, Long> summary = pdfDocsLoader.getDocumentSummary();
        long totalChunks = summary.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> documents = new ArrayList<>();
        summary.forEach((label, chunks) ->
                documents.add(Map.of("label", label, "chunks", chunks)));

        return ResponseEntity.ok(Map.of(
                "available", pdfDocsLoader.isAvailable(),
                "documentCount", summary.size(),
                "chunkCount", totalChunks,
                "documents", documents
        ));
    }
}
