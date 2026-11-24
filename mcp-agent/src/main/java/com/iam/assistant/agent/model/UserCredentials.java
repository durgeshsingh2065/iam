package com.iam.assistant.agent.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SailPoint username/password of the chatbot user, forwarded per request. Never log or persist this.
 * toString is masked so an accidental log line cannot leak the password.
 */
public record UserCredentials(String username, String password) {

    /** Returns null unless both a username and a password were supplied. */
    public static UserCredentials of(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) return null;
        return new UserCredentials(username.trim(), password);
    }

    /** Value for an HTTP Authorization header: {@code Basic base64(user:password)}. */
    public String toBasicAuthorization() {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() { return "UserCredentials[username=" + username + ", password=****]"; }
}
