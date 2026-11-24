package com.iam.assistant.chatbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.assistant.chatbot.config.ChatbotProperties;
import com.iam.assistant.chatbot.model.UserAccount;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UserManagementService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final Path usersFilePath;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final String defaultAdminUsername;
    private final String defaultAdminPassword;

    private final Map<String, UserAccount> usersCache = new ConcurrentHashMap<>();

    public UserManagementService(ChatbotProperties properties,
                                  PasswordEncoder passwordEncoder,
                                  ObjectMapper objectMapper) {
        this.usersFilePath = Paths.get(properties.storage().path(), "users.json");
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.defaultAdminUsername = properties.auth().adminUsername();
        this.defaultAdminPassword = properties.auth().adminPassword();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(usersFilePath.getParent());
        } catch (IOException e) {
            log.warn("Could not create storage directory: {}", e.getMessage());
        }
        loadUsersFromDisk();
        if (usersCache.isEmpty()) {
            createDefaultAdmin();
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount u = usersCache.get(username.toLowerCase());
        if (u == null) throw new UsernameNotFoundException("User not found: " + username);
        return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRole())
                .disabled(!u.isEnabled())
                .build();
    }

    public synchronized List<UserAccount> listUsers() {
        return usersCache.values().stream()
                .sorted(Comparator.comparing(UserAccount::getUsername))
                .collect(Collectors.toList());
    }

    public synchronized void createUser(String username, String plainPassword, String role) {
        String lower = username.toLowerCase();
        if (usersCache.containsKey(lower)) {
            throw new IllegalArgumentException("User already exists: " + username);
        }
        UserAccount u = new UserAccount(
                lower,
                passwordEncoder.encode(plainPassword),
                role.toUpperCase(),
                //UUID.randomUUID().toString(),
                "",
                true
        );
        usersCache.put(lower, u);
        saveUsersToDisk();
        log.info("Created user: {}", lower);
    }

    public synchronized void updateUser(String username, String newPlainPassword,
                                         String role, boolean enabled) {
        String lower = username.toLowerCase();
        UserAccount u = usersCache.get(lower);
        if (u == null) throw new NoSuchElementException("User not found: " + username);

        if (newPlainPassword != null && !newPlainPassword.isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(newPlainPassword));
        }
        if (role != null && !role.isBlank()) {
            u.setRole(role.toUpperCase());
        }
        u.setEnabled(enabled);
        saveUsersToDisk();
        log.info("Updated user: {}", lower);
    }

    public synchronized void deleteUser(String username) {
        String lower = username.toLowerCase();
        if (!usersCache.containsKey(lower)) {
            throw new NoSuchElementException("User not found: " + username);
        }
        usersCache.remove(lower);
        saveUsersToDisk();
        log.info("Deleted user: {}", lower);
    }

    public synchronized void updateToken(String username, String newToken) {
        String lower = username.toLowerCase();
        UserAccount u = usersCache.get(lower);
        if (u == null) throw new NoSuchElementException("User not found: " + username);
        u.setApiToken(newToken != null ? newToken.trim() : "");
        saveUsersToDisk();
        log.info("Updated API token for user: {}", lower);
    }

    public String getUserToken(String username) {
        UserAccount u = usersCache.get(username.toLowerCase());
        return u != null ? (u.getApiToken() != null ? u.getApiToken() : "") : "";
    }

    public boolean userExists(String username) {
        return usersCache.containsKey(username.toLowerCase());
    }

    private void createDefaultAdmin() {
        String lower = defaultAdminUsername.toLowerCase();
        UserAccount admin = new UserAccount(
                lower,
                passwordEncoder.encode(defaultAdminPassword),
                "ADMIN",
                UUID.randomUUID().toString(),
                true
        );
        usersCache.put(lower, admin);
        saveUsersToDisk();
        log.info("Created default admin user: {}", lower);
    }

    private void loadUsersFromDisk() {
        if (!Files.exists(usersFilePath)) return;
        try {
            List<UserAccount> list = objectMapper.readValue(
                    usersFilePath.toFile(),
                    new TypeReference<List<UserAccount>>() {}
            );
            list.forEach(u -> usersCache.put(u.getUsername().toLowerCase(), u));
            log.info("Loaded {} user(s) from disk", usersCache.size());
        } catch (IOException e) {
            log.warn("Could not read users.json: {}", e.getMessage());
        }
    }

    private void saveUsersToDisk() {
        List<UserAccount> list = new ArrayList<>(usersCache.values());
        Path tmp = usersFilePath.resolveSibling("users.json.tmp");
        try {
            objectMapper.writeValue(tmp.toFile(), list);
            try {
                Files.move(tmp, usersFilePath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, usersFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to save users.json: {}", e.getMessage());
        }
    }
}
