package cat.maki.MakiScreen;

import com.github.puregero.multilib.MultiLib;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


public class SlavePlayerJoin implements Listener {
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        MultiLib.notify("maki:playerJoin", event.getPlayer().getUniqueId().toString());
    }
}
