package cat.maki.MakiScreen.connection;

import cat.maki.MakiScreen.MakiScreen;
import com.github.puregero.multilib.MultiLib;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConnectionManager {
    public PlayerConnectionManager() {
        if (!MakiScreen.getInstance().getConfig().getBoolean("isMaster")) {
            PacketEvents.getAPI().getEventManager().registerListener(new KeepAlivePacketListener(), PacketListenerPriority.MONITOR);
            PacketEvents.getAPI().init();
            return;
        }

        MultiLib.onString(MakiScreen.getInstance(), "maki:keepAliveSent", string -> {
            String[] split = string.split(":");
            UUID playerUuid = UUID.fromString(split[0]);
            long id = Long.parseLong(split[1]);
            Player player = MakiScreen.getInstance().getServer().getPlayer(playerUuid);
            if (player == null) {
                return;
            }
            addKeepAlive(id, player);

        });

        MultiLib.onString(MakiScreen.getInstance(), "maki:keepAliveReceived", string -> {
            String[] split = string.split(":");
            UUID playerUuid = UUID.fromString(split[0]);
            long id = Long.parseLong(split[1]);
            Player player = MakiScreen.getInstance().getServer().getPlayer(playerUuid);
            if (player == null) {
                return;
            }
            receivedResponse(id, player);
        });

        PacketEvents.getAPI().getEventManager().registerListener(new KeepAlivePacketListener(), PacketListenerPriority.MONITOR);
        PacketEvents.getAPI().init();

        Bukkit.getScheduler().runTaskTimer(MakiScreen.getInstance(), () -> {
            for (Player player : MultiLib.getAllOnlinePlayers()) {
                Bukkit.getLogger().info(player.getName() + ": ping " + playerPings.get(player.getUniqueId()) + "ms" + " - stability " + playerStability.get(player.getUniqueId()) + ".");
            }
        }, 0, 20);
    }

    private final Map<UUID, Long> playerPings = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerStability = new ConcurrentHashMap<>();
    private final Map<Long, KeepAlive> keepAliveMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sendCount = new ConcurrentHashMap<>();

    public void addPlayer(Player player) {
        playerPings.put(player.getUniqueId(), 0L);
        playerStability.put(player.getUniqueId(), 1D);
    }

    public void removePlayer(Player player) {
        playerPings.remove(player.getUniqueId());
        playerStability.remove(player.getUniqueId());
    }

    public void addKeepAlive(long id, Player player) {
        KeepAlive keepAlive = new KeepAlive(player);
        keepAliveMap.put(id, keepAlive);
    }

    public void receivedResponse(long id, Player player) {
        KeepAlive keepAlive = keepAliveMap.get(id);
        if (keepAlive == null) {
            return;
        }
        if (keepAlive.getPlayerUuid() != player.getUniqueId())
            return;
        updatePlayerPing(keepAlive.getPlayerUuid(), keepAlive.received());
        keepAliveMap.remove(id);
    }

    private void updatePlayerPing(UUID uuid, long currentPing) {
        Long oldPing = playerPings.get(uuid);
        playerPings.put(uuid, currentPing);
        if (oldPing == null)
            return;
        if (oldPing == 0L)
            return;
        double stability = playerStability.get(uuid);
        int difference = (int) stability/10;
        if (difference == 0)
            difference = 1;
        if (isNear(currentPing, oldPing, difference)) {
            stability *= 0.9;
        } else {
            stability += (currentPing - oldPing) / 20D;
        }
        if (stability < 1)
            stability = 1;
        playerStability.put(uuid, stability);
    }

    public boolean shouldSendMapPlayer(Player player) {
        if (!sendCount.containsKey(player.getUniqueId())) {
            sendCount.put(player.getUniqueId(), 1);
            return true;
        } else {
            int count = sendCount.get(player.getUniqueId()) + 1;
            double stability = playerStability.get(player.getUniqueId());
            if (count > 1000) {
                sendCount.put(player.getUniqueId(), 1);
            } else {
                sendCount.put(player.getUniqueId(), count);
            }
            return count % (int) stability == 0 || stability <= 1;
        }
    }

    private boolean isNear(long currentPing, long oldPing, int difference) {
        return Math.abs(Math.abs(currentPing) - Math.abs(oldPing)) < difference;
    }
}
