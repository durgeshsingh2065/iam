package com.iam.assistant.server.service;

/**
 * Per-request SailPoint credentials supplied by the caller (the chatbot user's own login).
 *
 * Holds the ready-made {@code Authorization} header value ({@code Basic ...}) for the current thread
 * only. McpController sets it before running a tool and always clears it afterwards. Tools run
 * synchronously on the request thread, so the SailPoint client can read it without any plumbing.
 * The value is never logged.
 */
public final class SailPointCredentialContext {

    private static final ThreadLocal<String> AUTHORIZATION = new ThreadLocal<>();

    private SailPointCredentialContext() {}

    /** Accepts only a Basic header; anything else is ignored so the server default is used. */
    public static void set(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)
                && authorizationHeader.length() > 6) {
            AUTHORIZATION.set(authorizationHeader);
        } else {
            AUTHORIZATION.remove();
        }
    }

    public static String get() { return AUTHORIZATION.get(); }

    public static void clear() { AUTHORIZATION.remove(); }
}
