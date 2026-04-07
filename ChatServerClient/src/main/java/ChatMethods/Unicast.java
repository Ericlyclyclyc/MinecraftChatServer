package ChatMethods;

import org.bukkit.entity.Player;

import java.util.Set;

/**
 * ChatServer distribution type <code>UNICAST_PLAYER</code>
 */
public class Unicast implements BaseChatMethod {
    private final Player targetPlayer;
    public Unicast(Player targetPlayer) {
        this.targetPlayer = targetPlayer;
    }
    @Override
    public Set<Player> getTargetPlayers() {
        return Set.of(targetPlayer);
    }
}
