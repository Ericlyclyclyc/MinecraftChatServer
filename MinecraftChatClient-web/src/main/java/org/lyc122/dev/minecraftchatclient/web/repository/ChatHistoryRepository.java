package org.lyc122.dev.minecraftchatclient.web.repository;

import org.lyc122.dev.minecraftchatclient.web.entity.ChatHistory;
import org.lyc122.dev.minecraftchatclient.web.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<ChatHistory> findByUserAndMessageTypeOrderByCreatedAtDesc(User user, String messageType, Pageable pageable);
}
