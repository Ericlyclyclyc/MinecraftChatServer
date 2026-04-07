package ChatMethods;

import org.bukkit.entity.Player;

import java.util.Set;

/**
 * ChatServer distribution type <code>MULTICAST_PLAYER</code>
 */
public class Multicast implements BaseChatMethod{
    private final Set<Player> targetPlayers;
    public Multicast(Set<Player> targetPlayers) {
        this.targetPlayers = targetPlayers;
    }
    @Override
    public Set<Player> getTargetPlayers() {
        return targetPlayers;
    }
}
