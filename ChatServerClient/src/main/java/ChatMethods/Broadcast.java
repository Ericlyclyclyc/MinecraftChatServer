package ChatMethods;

import org.bukkit.entity.Player;

import java.util.Set;

/**
 * ChatServer distribution type <code>BROADCAST</code>
 */
public class Broadcast implements BaseChatMethod {
    @Override
    public Set<Player> getTargetPlayers() {
        return null;
    }
}
