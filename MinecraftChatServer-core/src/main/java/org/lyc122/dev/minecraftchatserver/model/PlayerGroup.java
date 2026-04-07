package org.lyc122.dev.minecraftchatserver.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 玩家群组实体
 */
@Getter
@Setter
@ToString(exclude = "members")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "player_groups")
public class PlayerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * 群组名称（唯一）
     */
    @Column(nullable = false, unique = true, length = 64)
    @EqualsAndHashCode.Include
    private String name;

    /**
     * 群组描述
     */
    @Column(length = 256)
    private String description;

    /**
     * 创建者（玩家名）
     */
    @Column(nullable = false, length = 64)
    private String creator;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private Long createdAt;

    /**
     * 群组成员
     */
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<GroupMember> members = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().getEpochSecond();
        }
    }

    /**
     * 添加成员
     */
    public void addMember(String playerName) {
        GroupMember member = GroupMember.builder()
                .playerName(playerName)
                .group(this)
                .joinedAt(Instant.now().getEpochSecond())
                .build();
        members.add(member);
    }

    /**
     * 移除成员
     */
    public void removeMember(String playerName) {
        members.removeIf(m -> m.getPlayerName().equals(playerName));
    }

    /**
     * 检查是否包含成员
     */
    public boolean hasMember(String playerName) {
        return members.stream().anyMatch(m -> m.getPlayerName().equals(playerName));
    }

    /**
     * 获取成员数量
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * 获取所有成员名称
     */
    public Set<String> getMemberNames() {
        Set<String> names = new HashSet<>();
        for (GroupMember member : members) {
            names.add(member.getPlayerName());
        }
        return names;
    }
}
