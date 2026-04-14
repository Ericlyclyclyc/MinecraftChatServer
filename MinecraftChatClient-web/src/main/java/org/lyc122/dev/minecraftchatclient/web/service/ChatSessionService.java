package org.lyc122.dev.minecraftchatclient.web.service;

import lombok.RequiredArgsConstructor;
import org.lyc122.dev.minecraftchatclient.web.entity.ChatSession;
import org.lyc122.dev.minecraftchatclient.web.entity.User;
import org.lyc122.dev.minecraftchatclient.web.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    @Value("${chat.server.name:WebClient}")
    private String serverName;

    @Value("${chat.session.timeout:1440}")
    private int sessionTimeoutMinutes;

    @Transactional
    public ChatSession createSession(User user) {
        String sessionToken = UUID.randomUUID().toString().replace("-", "");

        ChatSession session = ChatSession.builder()
                .user(user)
                .sessionToken(sessionToken)
                .serverName(serverName)
                .expiresAt(LocalDateTime.now().plusMinutes(sessionTimeoutMinutes))
                .active(true)
                .build();

        return chatSessionRepository.save(session);
    }

    public Optional<ChatSession> findByToken(String token) {
        return chatSessionRepository.findBySessionToken(token)
                .filter(session -> session.getActive() && session.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Transactional
    public void invalidateSession(String token) {
        chatSessionRepository.findBySessionToken(token).ifPresent(session -> {
            session.setActive(false);
            chatSessionRepository.save(session);
        });
    }

    @Transactional
    public void invalidateAllUserSessions(User user) {
        chatSessionRepository.findByUserAndActiveTrue(user).forEach(session -> {
            session.setActive(false);
            chatSessionRepository.save(session);
        });
    }

    @Transactional
    public void updateLastActivity(String token) {
        chatSessionRepository.findBySessionToken(token).ifPresent(session -> {
            session.setLastActivityAt(LocalDateTime.now());
            chatSessionRepository.save(session);
        });
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredSessions() {
        chatSessionRepository.findByActiveTrueAndExpiresAtBefore(LocalDateTime.now())
                .forEach(session -> {
                    session.setActive(false);
                    chatSessionRepository.save(session);
                });
    }
}
