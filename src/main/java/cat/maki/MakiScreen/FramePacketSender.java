package cat.maki.MakiScreen;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.github.puregero.multilib.MultiLib;
import net.minecraft.world.level.saveddata.maps.MapIcon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.http.WebSocket.Listener;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

class FramePacketSender extends BukkitRunnable implements Listener, org.bukkit.event.Listener {
  private long frameNumber = 0;
  private final Queue<byte[][]> frameBuffers;
  private final MakiScreen plugin;
  private final ProtocolManager protocolManager;

  public FramePacketSender(MakiScreen plugin, Queue<byte[][]> frameBuffers, ProtocolManager protocolManager) {
    this.protocolManager = protocolManager;
    this.frameBuffers = frameBuffers;
    this.plugin = plugin;
    this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    MultiLib.onString(plugin, "maki:playerJoin", stringUuid -> {
      UUID uuid = UUID.fromString(stringUuid);
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
        List<PacketContainer> packets = new ArrayList<>();
        for (ScreenPart screenPart : MakiScreen.screens) {
          if (screenPart.lastFrameBuffer != null) {
            packets.add(getPacket(screenPart.mapId, screenPart.lastFrameBuffer));
          }
        }
        sendToPlayer(player, packets);
      }
    });

  }

  @Override
  public void run() {
    byte[][] buffers = frameBuffers.poll();
    if (buffers == null) {
      return;
    }
    List<PacketContainer> packets = new ArrayList<>(MakiScreen.screens.size());
    for (ScreenPart screenPart : MakiScreen.screens) {
      byte[] buffer = buffers[screenPart.partId];
      if (buffer != null) {
        PacketContainer packet = getPacket(screenPart.mapId, buffer);
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

    for (Player onlinePlayer : MultiLib.getAllOnlinePlayers()) {
      sendToPlayer(onlinePlayer, packets);
    }

    if (frameNumber % 300 == 0) {
      byte[][] peek = frameBuffers.peek();
      if (peek != null) {
        frameBuffers.clear();
        frameBuffers.offer(peek);
      }
    }
    frameNumber++;
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    new BukkitRunnable() {
      @Override
      public void run() {
        List<PacketContainer> packets = new ArrayList<>();
        for (ScreenPart screenPart : MakiScreen.screens) {
          if (screenPart.lastFrameBuffer != null) {
            packets.add(getPacket(screenPart.mapId, screenPart.lastFrameBuffer));
          }
        }
        sendToPlayer(event.getPlayer(), packets);
      }
    }.runTaskLater(plugin, 10);
  }

  private void sendToPlayer(Player player, List<PacketContainer> packets) {
    for (PacketContainer packet : packets) {
      if (packet != null) {
        protocolManager.sendServerPacket(player, packet);
      }
    }
  }

  private PacketContainer getPacket(int mapId, byte[] data) {
    if (data == null) {
      throw new NullPointerException("data is null");
    }
    PacketContainer map = new PacketContainer(PacketType.Play.Server.MAP);
    map.getIntegers().write(0, mapId);
    map.getBytes().write(0, (byte) 0);
    map.getIntegers().write(3, 128);
    map.getIntegers().write(4, 128);
    map.getByteArrays().write(0, data);
    map.getSpecificModifier(MapIcon[].class).write(0, new MapIcon[0]);
    return map;
    /*return new PacketPlayOutMap(
        mapId, (byte) 0, false, null,
        new b(0, 0, 128, 128, data));
  }*/
  }
}
