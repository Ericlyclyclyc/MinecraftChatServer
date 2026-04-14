package org.lyc122.dev.minecraftchatclient.web.repository;

import org.lyc122.dev.minecraftchatclient.web.entity.ChatSession;
import org.lyc122.dev.minecraftchatclient.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findBySessionToken(String sessionToken);

    List<ChatSession> findByUserAndActiveTrue(User user);

    List<ChatSession> findByActiveTrueAndExpiresAtBefore(LocalDateTime now);

    void deleteBySessionToken(String sessionToken);
}
