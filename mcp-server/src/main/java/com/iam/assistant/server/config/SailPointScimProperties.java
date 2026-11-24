package com.iam.assistant.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the SailPoint SCIM 2.0 read APIs (any SCIM resource). */
@ConfigurationProperties(prefix = "sailpoint.scim")
public class SailPointScimProperties {

    public enum AuthType { BASIC, BEARER }

    /** SailPoint base URL, e.g. https://iiq.example.com. Blank disables the SCIM tools. Used when the caller sends no credentials of its own. */
    private String baseUrl = "";

    /** BASIC (username/password) or BEARER (token). */
    private AuthType authType = AuthType.BASIC;

    private String username = "";
    private String password = "";
    private String bearerToken = "";

    /** SCIM root used by the generic resource tools (scim_list_resources / scim_get_resource). */
    private String basePath = "/identityiq/scim/v2";

    /** Cap on characters of JSON returned to the LLM per call. */
    private int maxResponseChars = 20000;

    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 30;

    /** Upper bound on records returned to the LLM per call (also sent as SCIM {@code count}). */
    private int maxResults = 25;

    public boolean hasBaseUrl() {
        return !baseUrl.isBlank();
    }


    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getBearerToken() { return bearerToken; }
    public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public int getMaxResponseChars() { return maxResponseChars; }
    public void setMaxResponseChars(int v) { this.maxResponseChars = v; }

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int v) { this.connectTimeoutSeconds = v; }

    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int v) { this.readTimeoutSeconds = v; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
}
