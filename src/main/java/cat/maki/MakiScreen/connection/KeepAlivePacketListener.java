package cat.maki.MakiScreen.connection;

import cat.maki.MakiScreen.MakiScreen;
import com.github.puregero.multilib.MultiLib;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import org.bukkit.entity.Player;

public class KeepAlivePacketListener implements PacketListener {
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.KEEP_ALIVE) {
            return;
        }
        WrapperPlayServerKeepAlive keepAlive = new WrapperPlayServerKeepAlive(event);
        MakiScreen.getInstance().getPlayerConnectionManager().addKeepAlive(keepAlive.getId(), (Player) event.getPlayer());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.KEEP_ALIVE) {
            return;
        }
        WrapperPlayClientKeepAlive keepAlive = new WrapperPlayClientKeepAlive(event);
        MakiScreen.getInstance().getPlayerConnectionManager().receivedResponse(keepAlive.getId(), (Player) event.getPlayer());
    }
}
