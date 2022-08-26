package cat.maki.MakiScreen;

import cat.maki.MakiScreen.customWrapper.WrapperPlayServerMapData;
import com.github.puregero.multilib.MultiLib;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.image.BufferedImage;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.*;

class FramePacketSender extends BukkitRunnable implements Listener, org.bukkit.event.Listener {
    private final Queue<byte[][]> frameBuffers;
    private long[] lastPartSendTimes = new long[0];
    private WrapperPlayServerMapData[] cachedParts = new WrapperPlayServerMapData[0];
    private final Map<UUID, Long> lastSendTimes = new HashMap<>();
    private final MakiScreen plugin;
    private int mapBufferIndex = 0;

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

            synchronized (frameBuffers) {
                frameBuffers.offer(frameBuffer);
                frameBuffers.notify();
            }
        });
    }

    @Override
    public void run() {
        while (MakiScreen.getInstance().isEnabled() && !this.isCancelled()) {
            try {
                byte[][] buffers;
                synchronized (frameBuffers) {
                    buffers = frameBuffers.poll();
                    if (buffers == null) {
                        frameBuffers.wait(1000);
                        continue;
                    }
                }

                if (lastPartSendTimes.length != buffers.length) {
                    lastPartSendTimes = new long[buffers.length];
                    cachedParts = new WrapperPlayServerMapData[buffers.length];
                }

                long time = System.currentTimeMillis();

                Set<ScreenPart> screenParts = new HashSet<>(MakiScreen.screens);
                for (ScreenPart screenPart : screenParts) {
                    byte[] buffer = buffers[screenPart.partId];
                    if (buffer != null && buffer.length > 0) {
                        lastPartSendTimes[screenPart.partId] = time;
                        WrapperPlayServerMapData packet = getPacket(screenPart.mapId, buffer);
                        cachedParts[screenPart.partId] = packet;
        //                if (!screenPart.modified) {
        //                    packets.add(0, packet);
        //                } else {
        //                    packets.add(packet);
        //                }
                        screenPart.modified = true;
                        screenPart.lastFrameBuffer = buffer;
                    } else {
                        screenPart.modified = false;
                    }
                }

                mapBufferIndex = (++mapBufferIndex) % MakiScreen.BUFFER_MAP_COUNT;

                for (Player onlinePlayer : MultiLib.getLocalOnlinePlayers()) {
                    boolean shouldSend = MakiScreen.getInstance().getPlayerConnectionManager().shouldSendMapPlayer(onlinePlayer);
                    if (!shouldSend) {
                        continue;
                    }

                    long lastTime = lastSendTimes.getOrDefault(onlinePlayer.getUniqueId(), 0L);
                    List<PacketWrapper<?>> packets = new ArrayList<>(MakiScreen.screens.size());
                    List<PacketWrapper<?>> postPackets = new ArrayList<>(MakiScreen.screens.size());

                    for (ScreenPart screenPart : MakiScreen.screens) {
                        if (lastPartSendTimes[screenPart.partId] > lastTime) {
                            WrapperPlayServerMapData wrapperPlayServerMapData = new WrapperPlayServerMapData();
                            wrapperPlayServerMapData.copy(cachedParts[screenPart.partId]);
                            int originalMapId = wrapperPlayServerMapData.mapId;
                            wrapperPlayServerMapData.mapId += mapBufferIndex;
                            packets.add(wrapperPlayServerMapData);
                            if (MakiScreen.getInstance().getPlayerConnectionManager().mapIdToEntityID.get(originalMapId) != null) {
                                int entityId = MakiScreen.getInstance().getPlayerConnectionManager().mapIdToEntityID.get(originalMapId);
                                postPackets.add(MakiScreen.getInstance().getPlayerConnectionManager().createSetItemFrameMapPacket(entityId, wrapperPlayServerMapData.mapId)); // TODO
                            }
                        }
                    }

                    lastSendTimes.put(onlinePlayer.getUniqueId(), time);

                    sendToPlayer(onlinePlayer, packets);
                    sendToPlayer(onlinePlayer, postPackets);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    private void sendToPlayer(Player player, List<PacketWrapper<?>> packets) {
        for (PacketWrapper<?> packet : packets) {
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
                List<PacketWrapper<?>> packets = new ArrayList<>();
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
