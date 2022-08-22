package cat.maki.MakiScreen;

import com.github.puregero.multilib.MultiLib;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


public class SlavePlayerJoin implements Listener {
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Wait for player to get loaded onto other servers
        Bukkit.getScheduler().runTask(MakiScreen.getInstance(), () -> {
            MultiLib.notify("maki:playerJoin", event.getPlayer().getUniqueId().toString());
        });
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        MultiLib.notify("maki:playerLeave", event.getPlayer().getUniqueId().toString());
    }
}
