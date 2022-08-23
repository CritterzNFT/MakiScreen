package cat.maki.MakiScreen.connection;

import cat.maki.MakiScreen.MakiScreen;
import com.github.puregero.multilib.MultiLib;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import org.bukkit.entity.Player;

public class KeepAlivePacketListener implements PacketListener {
    @Override
    public void onPacketSend(PacketSendEvent event) {
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
