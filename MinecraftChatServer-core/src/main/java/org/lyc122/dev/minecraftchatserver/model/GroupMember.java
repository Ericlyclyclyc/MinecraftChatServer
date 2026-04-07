package org.lyc122.dev.minecraftchatserver.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * 群组成员关联实体
 */
@Getter
@Setter
@ToString(exclude = "group")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "group_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"group_id", "player_name"}, name = "uk_group_player")
})
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * 所属群组
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private PlayerGroup group;

    /**
     * 玩家名称
     */
    @Column(name = "player_name", nullable = false, length = 64)
    @EqualsAndHashCode.Include
    private String playerName;

    /**
     * 加入时间
     */
    @Column(nullable = false)
    private Long joinedAt;
}
