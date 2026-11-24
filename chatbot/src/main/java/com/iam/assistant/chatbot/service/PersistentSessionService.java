package com.iam.assistant.chatbot.service;

import com.iam.assistant.chatbot.config.ChatbotProperties;
import com.iam.assistant.chatbot.exception.StorageException;
import com.iam.assistant.chatbot.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PersistentSessionService {

    private static final Logger log = LoggerFactory.getLogger(PersistentSessionService.class);

    private final DiskStorageService diskStorage;
    private final TitleGenerationService titleService;
    private final int maxSessions;
    private final int maxHistory;
    private final long ttlMinutes;

    private final Map<String, ChatSession> memoryCache = new ConcurrentHashMap<>();

    public PersistentSessionService(DiskStorageService diskStorage,
                                     TitleGenerationService titleService,
                                     ChatbotProperties properties) {
        this.diskStorage = diskStorage;
        this.titleService = titleService;
        this.maxSessions = properties.session().maxSessionsPerUser();
        this.maxHistory = properties.session().maxHistory();
        this.ttlMinutes = properties.session().ttlMinutes();
    }

    /**
     * Returns an existing session from cache or disk, or creates a new one.
     */
    public ChatSession getOrCreate(String userId, String sessionId) {
        evictExpired();
        if (sessionId != null && memoryCache.containsKey(cacheKey(userId, sessionId))) {
            return memoryCache.get(cacheKey(userId, sessionId));
        }
        if (sessionId != null) {
            PersistedSession persisted = diskStorage.loadSession(userId, sessionId);
            if (persisted != null) {
                ChatSession session = fromPersisted(persisted);
                memoryCache.put(cacheKey(userId, sessionId), session);
                return session;
            }
        }
        return createSession(userId);
    }

    /**
     * Creates a new session, enforcing the FIFO cap.
     */
    public synchronized ChatSession createSession(String userId) {
        diskStorage.ensureUserDirectory(userId);
        UserIndex index = diskStorage.loadIndex(userId);

        // Enforce FIFO cap
        if (index.getSessions().size() >= maxSessions) {
            List<SessionSummary> sorted = index.getSessions();
            SessionSummary oldest = sorted.get(sorted.size() - 1);
            diskStorage.deleteSession(userId, oldest.getSessionId());
            memoryCache.remove(cacheKey(userId, oldest.getSessionId()));
            sorted.remove(sorted.size() - 1);
            log.info("FIFO eviction: removed session {} for user {}", oldest.getSessionId(), userId);
        }

        String newId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        SessionSummary summary = new SessionSummary(newId, "New Chat", now, now, 0);
        index.getSessions().add(0, summary);
        diskStorage.saveIndex(userId, index);

        PersistedSession persisted = new PersistedSession();
        persisted.setSessionId(newId);
        persisted.setUserId(userId);
        persisted.setTitle("New Chat");
        persisted.setCreatedAt(now);
        persisted.setLastActivity(now);
        diskStorage.saveSession(userId, persisted);

        ChatSession session = new ChatSession(newId, userId, maxHistory);
        session.setTitle("New Chat");
        memoryCache.put(cacheKey(userId, newId), session);
        return session;
    }

    /**
     * Persists the current in-memory session state to disk.
     */
    public void persist(String userId, ChatSession session) {
        try {
            PersistedSession persisted = toPersisted(session);
            diskStorage.saveSession(userId, persisted);
            updateIndexEntry(userId, session);
        } catch (StorageException e) {
            log.warn("Failed to persist session {} for user {}: {}", session.getSessionId(), userId, e.getMessage());
        }
    }

    /**
     * Sets the title on a session if it does not have one yet (called after first user message).
     */
    public void setTitleIfAbsent(String userId, String sessionId, String firstMessage) {
        ChatSession session = memoryCache.get(cacheKey(userId, sessionId));
        if (session == null || session.getTitle() != null && !"New Chat".equals(session.getTitle())) return;

        String title = titleService.generateTitle(firstMessage);
        if (session != null) session.setTitle(title);

        try {
            UserIndex index = diskStorage.loadIndex(userId);
            index.getSessions().stream()
                    .filter(s -> s.getSessionId().equals(sessionId))
                    .findFirst()
                    .ifPresent(s -> s.setTitle(title));
            diskStorage.saveIndex(userId, index);
        } catch (StorageException e) {
            log.warn("Failed to update title in index for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Returns the metadata list for a user's sessions (newest first).
     */
    public List<SessionSummary> listSessions(String userId) {
        try {
            return diskStorage.loadIndex(userId).getSessions();
        } catch (StorageException e) {
            log.warn("Failed to list sessions for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns the full session detail (with messages) from disk.
     */
    public SessionDetail getSessionDetail(String userId, String sessionId) {
        PersistedSession p = diskStorage.loadSession(userId, sessionId);
        if (p == null) return null;
        return new SessionDetail(p.getSessionId(), p.getTitle(),
                p.getCreatedAt(), p.getLastActivity(), p.getMessages());
    }

    /**
     * Renames a session, updating cache, persisted file, and index.
     */
    public boolean renameSession(String userId, String sessionId, String newTitle) {
        ChatSession cached = memoryCache.get(cacheKey(userId, sessionId));
        if (cached != null) cached.setTitle(newTitle);

        try {
            PersistedSession persisted = diskStorage.loadSession(userId, sessionId);
            if (persisted == null) return false;
            persisted.setTitle(newTitle);
            diskStorage.saveSession(userId, persisted);

            UserIndex index = diskStorage.loadIndex(userId);
            index.getSessions().stream()
                    .filter(s -> s.getSessionId().equals(sessionId))
                    .findFirst()
                    .ifPresent(s -> s.setTitle(newTitle));
            diskStorage.saveIndex(userId, index);
            return true;
        } catch (StorageException e) {
            log.warn("Failed to rename session {} for user {}: {}", sessionId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a session from both memory cache and disk.
     */
    public void deleteSession(String userId, String sessionId) {
        memoryCache.remove(cacheKey(userId, sessionId));
        diskStorage.deleteSession(userId, sessionId);
        try {
            UserIndex index = diskStorage.loadIndex(userId);
            index.getSessions().removeIf(s -> s.getSessionId().equals(sessionId));
            diskStorage.saveIndex(userId, index);
        } catch (StorageException e) {
            log.warn("Failed to remove session from index {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Deletes all sessions for a user: clears the in-memory cache entries and removes the
     * entire user data directory from disk. Called when an admin deletes a user account.
     */
    public void deleteAllSessionsForUser(String userId) {
        memoryCache.entrySet().removeIf(e -> e.getKey().startsWith(userId + ":"));
        diskStorage.deleteUserData(userId);
        log.info("Deleted all session data for user: {}", userId);
    }

    private void updateIndexEntry(String userId, ChatSession session) {
        try {
            UserIndex index = diskStorage.loadIndex(userId);
            index.getSessions().stream()
                    .filter(s -> s.getSessionId().equals(session.getSessionId()))
                    .findFirst()
                    .ifPresent(s -> {
                        s.setLastActivity(session.getLastActivity());
                        s.setMessageCount(session.getHistory().size());
                        if (session.getTitle() != null) s.setTitle(session.getTitle());
                    });
            // Re-sort newest-first
            index.getSessions().sort(Comparator.comparing(SessionSummary::getLastActivity).reversed());
            diskStorage.saveIndex(userId, index);
        } catch (StorageException e) {
            log.warn("Failed to update index entry for session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(ttlMinutes * 60);
        memoryCache.entrySet().removeIf(e -> e.getValue().getLastActivity().isBefore(cutoff));
    }

    private String cacheKey(String userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    private ChatSession fromPersisted(PersistedSession p) {
        ChatSession session = new ChatSession(p.getSessionId(), p.getUserId(), maxHistory);
        session.setTitle(p.getTitle());
        if (p.getLastActivity() != null) session.setLastActivity(p.getLastActivity());
        for (PersistedMessage m : p.getMessages()) {
            if ("user".equals(m.getRole())) {
                session.getHistory().add(new ChatSession.Turn("user", m.getContent(), m.getTimestamp()));
            } else {
                session.getHistory().add(new ChatSession.Turn("assistant", m.getContent(), m.getTimestamp()));
            }
        }
        return session;
    }

    private PersistedSession toPersisted(ChatSession session) {
        PersistedSession p = new PersistedSession();
        p.setSessionId(session.getSessionId());
        p.setUserId(session.getUserId());
        p.setTitle(session.getTitle());
        p.setCreatedAt(session.getCreatedAt());
        p.setLastActivity(session.getLastActivity());
        p.setMessages(session.getHistory().stream()
                .map(t -> new PersistedMessage(t.role(), t.content(), t.timestamp()))
                .collect(Collectors.toList()));
        return p;
    }
}
