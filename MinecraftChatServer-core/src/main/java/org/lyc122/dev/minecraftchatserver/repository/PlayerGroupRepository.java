package org.lyc122.dev.minecraftchatserver.repository;

import org.lyc122.dev.minecraftchatserver.model.PlayerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerGroupRepository extends JpaRepository<PlayerGroup, Long> {

    /**
     * 根据名称查找群组
     */
    Optional<PlayerGroup> findByName(String name);

    /**
     * 检查群组名称是否存在
     */
    boolean existsByName(String name);

    /**
     * 查找玩家创建的所有群组
     */
    List<PlayerGroup> findByCreator(String creator);

    /**
     * 查找玩家加入的所有群组（包括创建的）
     */
    @Query("SELECT DISTINCT g FROM PlayerGroup g JOIN g.members m WHERE m.playerName = :playerName OR g.creator = :playerName")
    List<PlayerGroup> findAllGroupsByPlayer(@Param("playerName") String playerName);

    /**
     * 查找玩家作为成员加入的群组（不包括创建的）
     */
    @Query("SELECT g FROM PlayerGroup g JOIN g.members m WHERE m.playerName = :playerName")
    List<PlayerGroup> findJoinedGroupsByPlayer(@Param("playerName") String playerName);
}
