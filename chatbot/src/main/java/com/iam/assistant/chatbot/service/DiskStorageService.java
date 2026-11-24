package com.iam.assistant.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.assistant.chatbot.config.ChatbotProperties;
import com.iam.assistant.chatbot.exception.StorageException;
import com.iam.assistant.chatbot.model.PersistedSession;
import com.iam.assistant.chatbot.model.UserIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

@Service
public class DiskStorageService {

    private static final Logger log = LoggerFactory.getLogger(DiskStorageService.class);

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    public DiskStorageService(ChatbotProperties properties, ObjectMapper objectMapper) {
        this.storageRoot = Paths.get(properties.storage().path());
        this.objectMapper = objectMapper;
        initStorageRoot();
    }

    private void initStorageRoot() {
        try {
            Files.createDirectories(storageRoot);
            log.info("Chat storage initialized at: {}", storageRoot.toAbsolutePath());
        } catch (IOException e) {
            throw new StorageException("Failed to initialize storage directory", e);
        }
    }

    public void ensureUserDirectory(String userId) {
        try {
            Files.createDirectories(userDir(userId));
            Path indexPath = resolveIndexPath(userId);
            if (!Files.exists(indexPath)) {
                UserIndex empty = new UserIndex(userId);
                writeAtomic(indexPath, empty);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to create user directory", e);
        }
    }

    public PersistedSession loadSession(String userId, String sessionId) {
        Path path = resolveSessionPath(userId, sessionId);
        if (!Files.exists(path)) return null;
        try {
            return objectMapper.readValue(path.toFile(), PersistedSession.class);
        } catch (IOException e) {
            log.warn("Failed to read session file {}: {}", path, e.getMessage());
            return null;
        }
    }

    public void saveSession(String userId, PersistedSession session) {
        ensureUserDirectory(userId);
        Path path = resolveSessionPath(userId, session.getSessionId());
        try {
            writeAtomic(path, session);
        } catch (IOException e) {
            throw new StorageException("Failed to save session", e);
        }
    }

    public void deleteSession(String userId, String sessionId) {
        Path path = resolveSessionPath(userId, sessionId);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete session file {}: {}", path, e.getMessage());
        }
    }

    public UserIndex loadIndex(String userId) {
        ensureUserDirectory(userId);
        Path path = resolveIndexPath(userId);
        try {
            return objectMapper.readValue(path.toFile(), UserIndex.class);
        } catch (IOException e) {
            log.warn("Failed to read index for user {}, returning empty: {}", userId, e.getMessage());
            return new UserIndex(userId);
        }
    }

    public void saveIndex(String userId, UserIndex index) {
        Path path = resolveIndexPath(userId);
        try {
            writeAtomic(path, index);
        } catch (IOException e) {
            throw new StorageException("Failed to save index", e);
        }
    }

    public void deleteUserData(String userId) {
        Path dir = userDir(userId);
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> {
                      try { Files.deleteIfExists(p); }
                      catch (IOException e) { log.warn("Failed to delete {}: {}", p, e.getMessage()); }
                  });
            log.info("Deleted data directory for user: {}", userId);
        } catch (IOException e) {
            log.warn("Failed to walk data directory for user {}: {}", userId, e.getMessage());
        }
    }

    public Path resolveSessionPath(String userId, String sessionId) {
        return userDir(userId).resolve(sessionId + ".json");
    }

    public Path resolveIndexPath(String userId) {
        return userDir(userId).resolve("index.json");
    }

    private Path userDir(String userId) {
        return storageRoot.resolve(sanitize(userId));
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }

    private void writeAtomic(Path target, Object value) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        objectMapper.writeValue(tmp.toFile(), value);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
