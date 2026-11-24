package com.iam.assistant.chatbot.service;

import com.iam.assistant.chatbot.model.ChatSession;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @deprecated Superseded by {@link PersistentSessionService} which adds disk persistence,
 * per-user isolation, and FIFO session capping. This class is no longer injected anywhere.
 */
@Deprecated
@Service
public class SessionService {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_TTL_MINUTES = 60;

    public ChatSession getOrCreate(String sessionId) {
        evictExpired(); // lazy eviction
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            String id = sessionId != null ? sessionId : UUID.randomUUID().toString();
            sessions.put(id, new ChatSession(id, "", 30));
            return sessions.get(id);
        }
        return sessions.get(sessionId);
    }

    public ChatSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(SESSION_TTL_MINUTES * 60);
        sessions.entrySet().removeIf(e ->
                e.getValue().getLastActivity().isBefore(cutoff));
    }
}
