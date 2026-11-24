package com.iam.assistant.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "docs.pdf")
public class DocsPdfProperties {

    /** Scan all *.pdf files in this directory (file: or classpath: URI). */
    private String directory = "";

    /** Explicit list of documents; supplements or replaces directory scan. */
    private List<DocumentConfig> files = new ArrayList<>();

    private int chunkSize = 800;
    private int chunkOverlap = 150;
    private int maxResults = 5;

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }

    public List<DocumentConfig> getFiles() { return files; }
    public void setFiles(List<DocumentConfig> files) { this.files = files; }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public static class DocumentConfig {
        /** Resource path: file:/path/to/doc.pdf or classpath:/docs/doc.pdf */
        private String path = "";
        /** Human-readable label shown in search results (e.g., "Secure Proxy (SSP)") */
        private String label = "";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
}
