package cat.maki.MakiScreen.connection;

import org.bukkit.entity.Player;

import java.util.UUID;

public class KeepAlive {
    private final UUID playerUuid;
    private final long timeSent;

    public KeepAlive(Player player) {
        this.playerUuid = player.getUniqueId();
        timeSent = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public long received() {
        long timeReceived = System.currentTimeMillis();
        return (timeReceived - timeSent) / 2;
    }
}
