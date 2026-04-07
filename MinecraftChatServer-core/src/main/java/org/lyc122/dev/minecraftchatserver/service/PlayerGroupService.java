package org.lyc122.dev.minecraftchatserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lyc122.dev.minecraftchatserver.model.PlayerGroup;
import org.lyc122.dev.minecraftchatserver.repository.PlayerGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerGroupService {

    private final PlayerGroupRepository groupRepository;

    /**
     * 创建群组
     *
     * @param name        群组名称
     * @param description 群组描述
     * @param creator     创建者
     * @return 创建的群组
     */
    @Transactional
    public PlayerGroup createGroup(String name, String description, String creator) {
        if (groupRepository.existsByName(name)) {
            throw new IllegalArgumentException("群组名称已存在: " + name);
        }

        PlayerGroup group = PlayerGroup.builder()
                .name(name)
                .description(description)
                .creator(creator)
                .build();

        // 创建者自动加入群组
        group.addMember(creator);

        PlayerGroup saved = groupRepository.save(group);
        log.info("玩家 [{}] 创建群组 [{}]", creator, name);
        return saved;
    }

    /**
     * 删除群组（只有创建者可以删除）
     *
     * @param groupName 群组名称
     * @param requester 请求删除的玩家
     */
    @Transactional
    public void deleteGroup(String groupName, String requester) {
        PlayerGroup group = groupRepository.findByName(groupName)
                .orElseThrow(() -> new IllegalArgumentException("群组不存在: " + groupName));

        if (!group.getCreator().equals(requester)) {
            throw new IllegalArgumentException("只有创建者可以删除群组");
        }

        groupRepository.delete(group);
        log.info("群组 [{}] 被 [{}] 删除", groupName, requester);
    }

    /**
     * 加入群组
     *
     * @param groupName 群组名称
     * @param playerName 玩家名称
     */
    @Transactional
    public void joinGroup(String groupName, String playerName) {
        PlayerGroup group = groupRepository.findByName(groupName)
                .orElseThrow(() -> new IllegalArgumentException("群组不存在: " + groupName));

        if (group.hasMember(playerName)) {
            throw new IllegalArgumentException("玩家已在群组中");
        }

        group.addMember(playerName);
        groupRepository.save(group);
        log.info("玩家 [{}] 加入群组 [{}]", playerName, groupName);
    }

    /**
     * 离开群组
     *
     * @param groupName  群组名称
     * @param playerName 玩家名称
     */
    @Transactional
    public void leaveGroup(String groupName, String playerName) {
        PlayerGroup group = groupRepository.findByName(groupName)
                .orElseThrow(() -> new IllegalArgumentException("群组不存在: " + groupName));

        // 创建者不能离开，只能删除群组
        if (group.getCreator().equals(playerName)) {
            throw new IllegalArgumentException("创建者不能离开群组，请删除群组");
        }

        if (!group.hasMember(playerName)) {
            throw new IllegalArgumentException("玩家不在群组中");
        }

        group.removeMember(playerName);
        groupRepository.save(group);
        log.info("玩家 [{}] 离开群组 [{}]", playerName, groupName);
    }

    /**
     * 获取所有群组
     */
    public List<PlayerGroup> getAllGroups() {
        return groupRepository.findAll();
    }

    /**
     * 根据名称获取群组
     */
    public Optional<PlayerGroup> getGroupByName(String name) {
        return groupRepository.findByName(name);
    }

    /**
     * 获取玩家相关的所有群组（创建的和加入的）
     *
     * @param playerName 玩家名称
     */
    public List<PlayerGroup> getPlayerGroups(String playerName) {
        return groupRepository.findAllGroupsByPlayer(playerName);
    }

    /**
     * 获取玩家当前所在的群组（用于消息路由）
     * 优先返回玩家最近加入的群组，如果没有则返回null
     *
     * @param playerName 玩家名称
     */
    public Optional<PlayerGroup> getCurrentGroup(String playerName) {
        List<PlayerGroup> groups = groupRepository.findAllGroupsByPlayer(playerName);
        // 返回第一个（可以扩展为记录玩家当前选择的群组）
        return groups.isEmpty() ? Optional.empty() : Optional.of(groups.get(0));
    }

    /**
     * 获取群组中的所有成员
     *
     * @param groupName 群组名称
     */
    public Set<String> getGroupMembers(String groupName) {
        PlayerGroup group = groupRepository.findByName(groupName)
                .orElseThrow(() -> new IllegalArgumentException("群组不存在: " + groupName));
        return group.getMemberNames();
    }

    /**
     * 检查玩家是否在群组中
     *
     * @param groupName  群组名称
     * @param playerName 玩家名称
     */
    public boolean isMember(String groupName, String playerName) {
        return groupRepository.findByName(groupName)
                .map(g -> g.hasMember(playerName))
                .orElse(false);
    }
}
