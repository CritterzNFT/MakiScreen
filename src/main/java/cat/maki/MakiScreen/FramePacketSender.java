package cat.maki.MakiScreen;

import cat.maki.MakiScreen.customWrapper.WrapperPlayServerMapData;
import com.github.puregero.multilib.MultiLib;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

class FramePacketSender extends BukkitRunnable implements Listener, org.bukkit.event.Listener {
    private final Queue<byte[][]> frameBuffers;
    private final MakiScreen plugin;

    public FramePacketSender(MakiScreen plugin, Queue<byte[][]> frameBuffers) {
        this.frameBuffers = frameBuffers;
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);

        MultiLib.onString(plugin, "maki:playerJoin", stringUuid -> {
            UUID uuid = UUID.fromString(stringUuid);
            Player player = Bukkit.getPlayer(uuid);
            playerJoin(player);
        });
        MultiLib.onString(plugin, "maki:playerLeave", stringUuid -> {
            UUID uuid = UUID.fromString(stringUuid);
            Player player = Bukkit.getPlayer(uuid);
            playerLeave(player);
        });
        MultiLib.on(plugin, "maki:frameBuffer", data -> {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int size = buffer.getInt();
            byte[][] frameBuffer = new byte[size][];
            for (int i = 0; i < size; i++) {
                int length = buffer.getInt();
                frameBuffer[i] = new byte[length];
                buffer.get(frameBuffer[i]);
            }
            this.frameBuffers.offer(frameBuffer);
        });
    }

    @Override
    public void run() {
        // Skip some frames if we're running behind
        while (frameBuffers.size() > 3) {
            frameBuffers.poll();
        }

        byte[][] buffers = frameBuffers.poll();
        if (buffers == null) {
            return;
        }
        List<WrapperPlayServerMapData> packets = new ArrayList<>(MakiScreen.screens.size());
        for (ScreenPart screenPart : MakiScreen.screens) {
            byte[] buffer = buffers[screenPart.partId];
            if (buffer != null && buffer.length > 0) {
                WrapperPlayServerMapData packet = getPacket(screenPart.mapId, buffer);
                if (!screenPart.modified) {
                    packets.add(0, packet);
                } else {
                    packets.add(packet);
                }
                screenPart.modified = true;
                screenPart.lastFrameBuffer = buffer;
            } else {
                screenPart.modified = false;
            }
        }

        for (Player onlinePlayer : MultiLib.getLocalOnlinePlayers()) {
            sendToPlayer(onlinePlayer, packets);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerLeave(player);
    }

    private void sendToPlayer(Player player, List<WrapperPlayServerMapData> packets) {
        boolean shouldSend = MakiScreen.getInstance().getPlayerConnectionManager().shouldSendMapPlayer(player);
        if (!shouldSend) {
            return;
        }
        for (WrapperPlayServerMapData packet : packets) {
            if (packet != null) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
            }
        }
    }

    private WrapperPlayServerMapData getPacket(int mapId, byte[] data) {
        if (data == null) {
            throw new NullPointerException("data is null");
        }
        return new WrapperPlayServerMapData(mapId, 0, false, null,
                data, 0, 0, 128, 128);
    }

    private void playerJoin(Player player) {
        if (player != null) {
            MakiScreen.getInstance().getPlayerConnectionManager().addPlayer(player);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                List<WrapperPlayServerMapData> packets = new ArrayList<>();
                for (ScreenPart screenPart : MakiScreen.screens) {
                    if (screenPart.lastFrameBuffer != null) {
                        packets.add(getPacket(screenPart.mapId, screenPart.lastFrameBuffer));
                    }
                }
                sendToPlayer(player, packets);
            }, 10);
        }
    }

    private void playerLeave(Player player) {
        MakiScreen.getInstance().getPlayerConnectionManager().removePlayer(player);
    }
}
