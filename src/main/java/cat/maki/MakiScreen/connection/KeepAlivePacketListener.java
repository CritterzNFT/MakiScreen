package cat.maki.MakiScreen.connection;

import cat.maki.MakiScreen.MakiScreen;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KeepAlivePacketListener implements PacketListener {
    private Map<UUID, PacketTypeCommon> packetTypeMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> mapIdToEntityID = new ConcurrentHashMap<>();
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata wrapperPlayServerEntityMetadata = new WrapperPlayServerEntityMetadata(event);
            int entityId = wrapperPlayServerEntityMetadata.getEntityId();
            wrapperPlayServerEntityMetadata.getEntityMetadata().forEach(metadata -> {
                if (metadata.getType().getName().contains("itemstack") && metadata.getIndex() == 8 && ((ItemStack) metadata.getValue()).getType() == ItemTypes.FILLED_MAP) {
                    mapIdToEntityID.put(((NBTInt)(((ItemStack) metadata.getValue()).getNBT().getTagOrThrow("map"))).getAsInt(), entityId);
                }
            });
        }
        Object id = null;
        if (event.getPacketType() == PacketType.Play.Server.KEEP_ALIVE) {
            WrapperPlayServerKeepAlive keepAlive = new WrapperPlayServerKeepAlive(event);
            id = keepAlive.getId();
        }
        if (event.getPacketType() == PacketType.Play.Server.PING) {
            WrapperPlayServerPing wrapperPlayServerPing = new WrapperPlayServerPing(event);
            id = wrapperPlayServerPing.getId();
        }
        if (id == null)
            return;
        MakiScreen.getInstance().getPlayerConnectionManager().addKeepAlive(id, (Player) event.getPlayer());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Object id = null;
        if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            WrapperPlayClientKeepAlive keepAlive = new WrapperPlayClientKeepAlive(event);
            id = keepAlive.getId();
        }
        if (event.getPacketType() == PacketType.Play.Client.PONG) {
            WrapperPlayClientPong clientPong = new WrapperPlayClientPong(event);
            id = clientPong.getId();
        }
        if (id == null)
            return;
        MakiScreen.getInstance().getPlayerConnectionManager().receivedResponse(id, (Player) event.getPlayer());
    }
}
