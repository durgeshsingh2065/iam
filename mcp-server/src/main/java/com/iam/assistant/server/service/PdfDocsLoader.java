package com.iam.assistant.server.service;

import com.iam.assistant.server.config.DocsPdfProperties;
import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Loads PDF documentation files and splits them into searchable text chunks.
 * Supports any number of documents from a directory scan or explicit list.
 * Thread-safe hot-reload via AtomicReference.
 */
@Service
public class PdfDocsLoader implements ResourceLoaderAware {

    private static final Logger log = LoggerFactory.getLogger(PdfDocsLoader.class);

    private final DocsPdfProperties props;
    private ResourceLoader resourceLoader;

    private final AtomicReference<List<DocChunk>> chunks = new AtomicReference<>(Collections.emptyList());

    public PdfDocsLoader(DocsPdfProperties props) {
        this.props = props;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        List<DocEntry> entries = collectDocEntries();
        if (entries.isEmpty()) {
            log.warn("No PDF documents configured. " +
                    "Set DOCS_PDF_DIRECTORY or configure docs.pdf.files in application.yml");
            chunks.set(Collections.emptyList());
            return;
        }

        List<DocChunk> merged = new ArrayList<>();
        for (DocEntry entry : entries) {
            merged.addAll(loadPdf(entry.path(), entry.label()));
        }

        chunks.set(Collections.unmodifiableList(merged));
        log.info("PDF index ready: {} documents, {} total chunks",
                entries.size(), merged.size());
    }

    public List<DocChunk> getChunks() {
        return chunks.get();
    }

    public List<DocChunk> getChunks(String labelFilter) {
        if (labelFilter == null || labelFilter.isBlank()) return getChunks();
        String filter = labelFilter.toLowerCase();
        return chunks.get().stream()
                .filter(c -> c.label().toLowerCase().contains(filter))
                .toList();
    }

    public boolean isAvailable() {
        return !chunks.get().isEmpty();
    }

    public Map<String, Long> getDocumentSummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (DocChunk chunk : chunks.get()) {
            summary.merge(chunk.label(), 1L, Long::sum);
        }
        return summary;
    }

    // ── Entry collection ──────────────────────────────────────────────────────

    private List<DocEntry> collectDocEntries() {
        Set<String> seenPaths = new LinkedHashSet<>();
        List<DocEntry> entries = new ArrayList<>();

        // 1. Directory scan
        String dir = props.getDirectory();
        if (dir != null && !dir.isBlank()) {
            try {
                Resource dirResource = resourceLoader.getResource(dir);
                if (dirResource.exists()) {
                    Path dirPath = dirResource.getFile().toPath();
                    try (Stream<Path> walk = Files.list(dirPath)) {
                        walk.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                            .sorted()
                            .forEach(p -> {
                                String path = p.toUri().toString();
                                String label = labelFromFilename(p.getFileName().toString());
                                if (seenPaths.add(path)) {
                                    entries.add(new DocEntry(path, label));
                                }
                            });
                    }
                    log.info("Directory scan of '{}' found {} PDFs", dir, entries.size());
                } else {
                    log.warn("Configured docs.pdf.directory not found: {}", dir);
                }
            } catch (IOException ex) {
                log.warn("Failed to scan docs.pdf.directory '{}': {}", dir, ex.getMessage());
            }
        }

        // 2. Explicit files list (supplement or fallback)
        for (DocsPdfProperties.DocumentConfig fileConfig : props.getFiles()) {
            String path = fileConfig.getPath();
            if (path == null || path.isBlank()) continue;
            String label = (fileConfig.getLabel() != null && !fileConfig.getLabel().isBlank())
                    ? fileConfig.getLabel()
                    : labelFromFilename(path);
            if (seenPaths.add(path)) {
                entries.add(new DocEntry(path, label));
            }
        }

        return entries;
    }

    private static String labelFromFilename(String filename) {
        String name = filename;
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        if (name.contains("\\")) name = name.substring(name.lastIndexOf('\\') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ── PDF loading ───────────────────────────────────────────────────────────

    private List<DocChunk> loadPdf(String path, String label) {
        if (path == null || path.isBlank()) return Collections.emptyList();

        File file;
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                log.warn("PDF not found for '{}': {}", label, path);
                return Collections.emptyList();
            }
            file = resource.getFile();
        } catch (Exception e) {
            log.warn("Cannot access PDF for '{}': {} — {}", label, path, e.getMessage());
            return Collections.emptyList();
        }

        String source = file.getName();

        try (PDDocument doc = Loader.loadPDF(file)) {
            List<DocChunk> result = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = doc.getNumberOfPages();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc).trim();
                if (pageText.isBlank()) continue;

                if (pageText.length() <= props.getChunkSize()) {
                    result.add(new DocChunk(source, label, page, pageText));
                } else {
                    int start = 0;
                    while (start < pageText.length()) {
                        int end = Math.min(start + props.getChunkSize(), pageText.length());
                        result.add(new DocChunk(source, label, page, pageText.substring(start, end)));
                        start += props.getChunkSize() - props.getChunkOverlap();
                        if (start >= pageText.length()) break;
                    }
                }
            }

            log.info("Loaded {} chunks from '{}' ({})", result.size(), label, path);
            return result;

        } catch (Exception e) {
            log.error("Failed to load PDF for '{}': {} — {}", label, path, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Domain model ──────────────────────────────────────────────────────────

    /** A single searchable text chunk extracted from a PDF document. */
    public record DocChunk(String source, String label, int pageNumber, String text) {}

    private record DocEntry(String path, String label) {}
}
