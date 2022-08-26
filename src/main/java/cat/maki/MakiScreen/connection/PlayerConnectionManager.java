package cat.maki.MakiScreen.connection;

import cat.maki.MakiScreen.MakiScreen;
import com.github.puregero.multilib.MultiLib;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConnectionManager {
    public PlayerConnectionManager() {
        PacketEvents.getAPI().getEventManager().registerListener(new KeepAlivePacketListener(), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().init();

        builder.type(ItemTypes.FILLED_MAP);
        builder.amount(1);
        builder.legacyData(-1);

        Bukkit.getScheduler().runTaskTimer(MakiScreen.getInstance(), () -> {
            for (Player player : MultiLib.getLocalOnlinePlayers()) {
                Bukkit.getLogger().info(player.getName() + ": ping " + playerPings.get(player.getUniqueId()) + "ms" + " - stability " + playerStability.get(player.getUniqueId()) + ".");
            }
        }, 0, 20);
    }

    private final Map<UUID, Long> playerPings = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerStability = new ConcurrentHashMap<>();
    private final Map<Long, KeepAlive> keepAliveMap = new ConcurrentHashMap<>();
    private final Map<Integer, KeepAlive> pingMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sendCount = new ConcurrentHashMap<>();
    public final Map<Integer, Integer> mapIdToEntityID = new ConcurrentHashMap<>();

    public void addPlayer(Player player) {
//        playerPings.put(player.getUniqueId(), 0L);
//        playerStability.put(player.getUniqueId(), 1D);
    }

    public void removePlayer(Player player) {
        playerPings.remove(player.getUniqueId());
        playerStability.remove(player.getUniqueId());
    }

    public void addKeepAlive(Object id, Player player) {
        if (player == null) {
            return;
        }

        KeepAlive keepAlive = new KeepAlive(player);
        if (id instanceof Long) {
            keepAliveMap.put((long) id, keepAlive);
        } else {
            pingMap.put((int) id, keepAlive);
        }
    }

    public void receivedResponse(Object id, Player player) {
        KeepAlive keepAlive = null;
        if (id instanceof Long) {
            keepAlive = keepAliveMap.get(id);
        } else {
            keepAlive = pingMap.get(id);
        }
        if (keepAlive == null) {
            return;
        }
        if (keepAlive.getPlayerUuid() != player.getUniqueId())
            return;
        updatePlayerPing(keepAlive.getPlayerUuid(), keepAlive.received());
        keepAliveMap.remove(id);
    }

    private void updatePlayerPing(UUID uuid, long currentPing) {
        Long oldPing = playerPings.getOrDefault(uuid, 0L);
        playerPings.put(uuid, currentPing);
        if (oldPing == null)
            return;
        if (oldPing == 0L)
            return;
        double stability = playerStability.getOrDefault(uuid, 1D);
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
        } else if (playerStability.containsKey(player.getUniqueId())) {
            int count = sendCount.get(player.getUniqueId()) + 1;
            double stability = playerStability.get(player.getUniqueId());
            if (count > 1000) {
                sendCount.put(player.getUniqueId(), 1);
            } else {
                sendCount.put(player.getUniqueId(), count);
            }
            return count % (int) stability == 0 || stability <= 1;
        } else {
            return false;
        }
    }

    private boolean isNear(long currentPing, long oldPing, int difference) {
        return Math.abs(Math.abs(currentPing) - Math.abs(oldPing)) < difference;
    }

    private ItemStack.Builder builder = new ItemStack.Builder();
    private NBTCompound nbtCompound = new NBTCompound();

    public WrapperPlayServerEntityMetadata createSetItemFrameMapPacket(int entityId, int mapId) {
        NBTInt nbtInt = new NBTInt(mapId);
        nbtCompound.setTag("map", nbtInt);
        builder.nbt(nbtCompound);
        ItemStack itemstack = builder.build();
        List<EntityData> entityDataList = new ArrayList<>();
        EntityData entityData = new EntityData(8, EntityDataTypes.ITEMSTACK, itemstack);
        entityDataList.add(entityData);
        return new WrapperPlayServerEntityMetadata(entityId, entityDataList);
    }
}
