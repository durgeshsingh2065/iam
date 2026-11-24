package com.iam.assistant.server.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iam.assistant.server.config.DocsPdfProperties;
import com.iam.assistant.server.model.mcp.ToolCallResult;
import com.iam.assistant.server.model.mcp.ToolDefinition;
import com.iam.assistant.server.service.PdfDocsLoader;
import com.iam.assistant.server.service.PdfDocsLoader.DocChunk;
import com.iam.assistant.server.tools.McpTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PdfDocsSearchTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(PdfDocsSearchTool.class);

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "in", "of", "for", "to", "with",
            "how", "do", "i", "can", "what", "this", "that", "it", "be", "as",
            "at", "by", "from", "on", "or", "and", "not", "does", "was",
            "which", "where", "when", "why", "who", "whom", "will", "would",
            "should", "could", "may", "might", "has", "have", "had", "been", "being");

    private static final Map<String, String> ACRONYM_EXPANSIONS = Map.ofEntries(
            Map.entry("iam",  "identity access management"),
            Map.entry("idm",  "identity management"),
            Map.entry("iga",  "identity governance administration"),
            Map.entry("sod",  "segregation of duties"),
            Map.entry("jml",  "joiner mover leaver"),
            Map.entry("rbac", "role based access control"),
            Map.entry("pam",  "privileged access management"),
            Map.entry("mfa",  "multi factor authentication"),
            Map.entry("sso",  "single sign on"),
            Map.entry("ar",   "access request"),
            Map.entry("cert", "certification"));

    private static final double MIN_SCORE        = 3.0;
    private static final int    PROXIMITY_WINDOW = 250;
    private static final double PROXIMITY_BONUS  = 4.0;
    private static final double ALL_TERMS_BONUS  = 3.0;
    private static final double PHRASE_BONUS     = 10.0;

    private final PdfDocsLoader loader;
    private final DocsPdfProperties props;
    private final ObjectMapper objectMapper;

    // Unified IDF index over all loaded documents — rebuilt on reload
    private final AtomicReference<Map<String, Double>> idfIndex = new AtomicReference<>(Map.of());
    private volatile double defaultIdf = 1.0;

    public PdfDocsSearchTool(PdfDocsLoader loader,
                              DocsPdfProperties props,
                              ObjectMapper objectMapper) {
        this.loader = loader;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void buildIdfIndex() {
        rebuildIdfIndex();
    }

    public void rebuildIdfIndex() {
        List<DocChunk> all = loader.getChunks();
        Map<String, Double> idf = buildIdf(all);
        idfIndex.set(idf);
        defaultIdf = all.isEmpty() ? 1.0 : Math.log(1.0 + all.size());
        log.info("IDF index rebuilt: {} terms over {} chunks (default IDF={})",
                idf.size(), all.size(), String.format("%.2f", defaultIdf));
    }

    private Map<String, Double> buildIdf(List<DocChunk> chunks) {
        if (chunks.isEmpty()) return Map.of();
        Map<String, Integer> df = new HashMap<>();
        for (DocChunk c : chunks) {
            Set<String> distinctTerms = new HashSet<>();
            for (String w : c.text().toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+")) {
                if (w.length() > 2 && !STOP_WORDS.contains(w)) distinctTerms.add(w);
            }
            for (String t : distinctTerms) df.merge(t, 1, Integer::sum);
        }
        int n = chunks.size();
        Map<String, Double> idf = new HashMap<>(df.size());
        df.forEach((term, freq) -> idf.put(term, Math.log(1.0 + (double) n / (1 + freq))));
        return idf;
    }

    @Override
    public String getName() {
        return "docs_search";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query")
                .put("type", "string")
                .put("description", "Search keywords or question to look up in product documentation");
        properties.putObject("document")
                .put("type", "string")
                .put("description",
                        "Optional document filter: a word from the document name (for example " +
                        "'access-request' or 'certification'). Omit to search the whole knowledge base.");

        schema.putArray("required").add("query");

        return ToolDefinition.builder()
                .name(getName())
                .description("""
                    Search the IAM knowledge base (policies, process guides, SailPoint IdentityIQ
                    documentation) and return the most relevant sections with page references.
                    Use this for any general IAM, IDM or IGA question: access request process,
                    approval and certification concepts, joiner/mover/leaver lifecycle, SoD,
                    roles, provisioning, reconciliation, and how to use the IAM portal.
                    Pass the user's question as the query, using the key terms.
                    Use the optional document parameter to restrict to one document.
                    """)
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolCallResult execute(JsonNode arguments) {
        String query = arguments.path("query").asText("").trim();
        if (query.isBlank()) {
            return ToolCallResult.error("query is required");
        }

        if (!loader.isAvailable()) {
            return ToolCallResult.error("No PDF documentation is loaded. " +
                    "Add PDF files to the docs-library directory (or set DOCS_PDF_DIRECTORY).");
        }

        String productParam = arguments.path("document").asText("").trim().toLowerCase();
        log.info("docs_search: query='{}' product='{}'", query, productParam.isBlank() ? "all" : productParam);

        QueryContext ctx = buildQueryContext(query);
        if (ctx.terms().length == 0) {
            return ToolCallResult.error("Query contains no searchable terms");
        }
        log.debug("Tokenized terms={} phrase='{}'", Arrays.toString(ctx.terms()), ctx.exactPhrase());

        // Resolve which chunks to search
        List<DocChunk> searchChunks = resolveChunks(productParam);
        if (searchChunks.isEmpty()) {
            return ToolCallResult.error(
                    "No documentation available for the specified filter: '" + productParam + "'");
        }

        // Search and format results, grouped by document label
        Map<String, List<DocChunk>> byLabel = groupByLabel(searchChunks);
        Map<String, Double> idf = idfIndex.get();

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<DocChunk>> entry : byLabel.entrySet()) {
            sb.append(formatResults(entry.getValue(), ctx, idf, entry.getKey())).append("\n");
        }

        return ToolCallResult.text(sb.toString().trim());
    }

    private List<DocChunk> resolveChunks(String documentFilter) {
        return documentFilter.isBlank() ? loader.getChunks() : loader.getChunks(documentFilter);
    }

    private Map<String, List<DocChunk>> groupByLabel(List<DocChunk> chunkList) {
        Map<String, List<DocChunk>> grouped = new java.util.LinkedHashMap<>();
        for (DocChunk c : chunkList) {
            grouped.computeIfAbsent(c.label(), k -> new ArrayList<>()).add(c);
        }
        return grouped;
    }

    // ── Per-query context ─────────────────────────────────────────────────────

    private record QueryContext(String originalQuery, String[] terms, String exactPhrase) {}

    private QueryContext buildQueryContext(String originalQuery) {
        String lower = originalQuery.toLowerCase();
        StringBuilder expanded = new StringBuilder(lower);
        for (String w : lower.split("\\s+")) {
            String cleaned = w.replaceAll("[^a-z0-9]", "");
            String expansion = ACRONYM_EXPANSIONS.get(cleaned);
            if (expansion != null) expanded.append(' ').append(expansion);
        }

        String[] terms = Arrays.stream(
                        expanded.toString()
                                .replaceAll("[^a-z0-9\\s]", " ")
                                .trim()
                                .split("\\s+"))
                .filter(t -> t.length() > 2 && !STOP_WORDS.contains(t))
                .distinct()
                .toArray(String[]::new);

        String cleanedPhrase = lower
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String phrase = (cleanedPhrase.split(" ").length >= 2 && cleanedPhrase.length() >= 6)
                ? cleanedPhrase : null;

        return new QueryContext(originalQuery, terms, phrase);
    }

    // ── Scoring and formatting ────────────────────────────────────────────────

    private String formatResults(List<DocChunk> chunks,
                                  QueryContext ctx,
                                  Map<String, Double> idfMap,
                                  String documentLabel) {
        record Scored(DocChunk chunk, double score) {}

        List<Scored> scored = chunks.stream()
                .map(c -> new Scored(c, score(c.text(), ctx, idfMap)))
                .filter(s -> s.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(props.getMaxResults())
                .toList();

        if (scored.isEmpty()) {
            return "=== " + documentLabel + " ===\nNo results found for: " + ctx.originalQuery() + "\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== DOCUMENTATION SEARCH RESULTS — ").append(documentLabel).append(" ===\n");
        sb.append("Query: ").append(ctx.originalQuery()).append("\n\n");

        int idx = 1;
        for (Scored s : scored) {
            String text = s.chunk().text();
            if (text.length() > 450) text = text.substring(0, 450) + "...";
            sb.append("[").append(idx++).append("] ")
              .append("Page ").append(s.chunk().pageNumber())
              .append(" (relevance: ").append(String.format("%.1f", s.score())).append(")\n");
            sb.append(text).append("\n\n");
        }
        return sb.toString();
    }

    private double score(String text, QueryContext ctx, Map<String, Double> idfMap) {
        String lower = text.toLowerCase();
        double total = 0;
        boolean[] termFound = new boolean[ctx.terms().length];

        for (int i = 0; i < ctx.terms().length; i++) {
            int count = countWordBoundaryMatches(lower, ctx.terms()[i]);
            if (count > 0) {
                termFound[i] = true;
                double idf = idfMap.getOrDefault(ctx.terms()[i], defaultIdf);
                total += count * idf;
            }
        }
        if (total == 0) return 0;

        boolean allFound = true;
        for (boolean f : termFound) { if (!f) { allFound = false; break; } }
        if (allFound) {
            total += ALL_TERMS_BONUS;
            if (ctx.terms().length > 1) total += proximityBonus(lower, ctx.terms());
        }

        if (ctx.exactPhrase() != null && lower.contains(ctx.exactPhrase())) {
            total += PHRASE_BONUS;
        }

        return total;
    }

    private int countWordBoundaryMatches(String lower, String term) {
        int count = 0;
        int idx = 0;
        while ((idx = lower.indexOf(term, idx)) >= 0) {
            boolean leftOk  = (idx == 0) || !Character.isLetterOrDigit(lower.charAt(idx - 1));
            int end = idx + term.length();
            boolean rightOk = (end >= lower.length()) || !Character.isLetterOrDigit(lower.charAt(end));
            if (leftOk && rightOk) count++;
            idx = end;
        }
        return count;
    }

    private double proximityBonus(String lower, String[] terms) {
        List<Integer> anchors = new ArrayList<>();
        int idx = 0;
        while ((idx = lower.indexOf(terms[0], idx)) >= 0) {
            anchors.add(idx);
            idx += terms[0].length();
        }
        for (int anchor : anchors) {
            String window = lower.substring(anchor, Math.min(anchor + PROXIMITY_WINDOW, lower.length()));
            boolean allInWindow = true;
            for (int i = 1; i < terms.length; i++) {
                if (!window.contains(terms[i])) { allInWindow = false; break; }
            }
            if (allInWindow) return PROXIMITY_BONUS;
        }
        return 0;
    }
}
