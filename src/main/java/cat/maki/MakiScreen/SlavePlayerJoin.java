package cat.maki.MakiScreen;

import com.github.puregero.multilib.MultiLib;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class SlavePlayerJoin implements Listener {
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        MultiLib.notify("maki:playerJoin", event.getPlayer().getUniqueId().toString());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        MultiLib.notify("maki:playerLeave", event.getPlayer().getUniqueId().toString());
    }
}
