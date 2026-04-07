package ChatMethods;

import org.bukkit.entity.Player;

import java.util.Set;

/**
 * ChatServer distribution type <code>null</code> Because it will not be sent to the ChatServer.
 * </br>
 * This kind of message will be handled locally.
 */
public class LocalServer implements BaseChatMethod {
    @Override
    public Set<Player> getTargetPlayers() {
        return null;
    }
}
