package cat.maki.MakiScreen;

import cat.maki.MakiScreen.connection.PlayerConnectionManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

public final class MakiScreen extends JavaPlugin implements Listener {

    private final Logger logger = getLogger();

    public static final Set<ScreenPart> screens = new TreeSet<>(
            Comparator.comparingInt(to -> to.mapId));
    private VideoCapture videoCapture;
    private static MakiScreen makiScreen;
    private PlayerConnectionManager playerConnectionManager;

    @Override
    public void onLoad() {
        makiScreen = this;
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        //Are all listeners read only?
        PacketEvents.getAPI().getSettings().readOnlyListeners(true)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        init();
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        logger.info("Bye!");
        videoCapture.cleanup();
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        Player player = (Player) sender;
        if (command.getName().equals("maki")) {
            if (!player.hasPermission("makiscreen.admin")) {
                player.sendMessage("You don't have permission!");
                return false;
            }

            for (int i = 0; i < ConfigFile.getMapSize(); i++) {
                MapView mapView = getServer().createMap(player.getWorld());
                mapView.setScale(MapView.Scale.CLOSEST);
                mapView.setUnlimitedTracking(true);
                for (MapRenderer renderer : mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
                }

                ItemStack itemStack = new ItemStack(Material.FILLED_MAP);

                MapMeta mapMeta = (MapMeta) itemStack.getItemMeta();
                mapMeta.setMapView(mapView);

                itemStack.setItemMeta(mapMeta);
                player.getWorld().dropItem(player.getLocation(), itemStack);
                screens.add(new ScreenPart(mapView.getId(), i));
                ImageManager manager = ImageManager.getInstance();
                manager.saveImage(mapView.getId(), i);
            }
        }

        if (command.getName().equals("setMakiMaster")) {
            if (!player.hasPermission("makiscreen.admin")) {
                player.sendMessage("You don't have permission!");
                return false;
            }
            getConfig().set("isMaster", true);
            saveConfig();
            init();
        }

        if (command.getName().equals("setMakiSlave")) {
            if (!player.hasPermission("makiscreen.admin")) {
                player.sendMessage("You don't have permission!");
                return false;
            }
            getConfig().set("isMaster", false);
            saveConfig();
            init();
        }

        return true;
    }

    private void init(){
        playerConnectionManager = new PlayerConnectionManager();
        ConfigFile configFile = new ConfigFile(this);
        configFile.run();

        ImageManager manager = ImageManager.getInstance();
        manager.init();

        logger.info("Hi!");
        getServer().getPluginManager().registerEvents(this, this);

        logger.info("Config file loaded \n" +
                "Map Size: " + ConfigFile.getMapSize() + "\n" +
                "Map Width: " + ConfigFile.getMapWidth() + "\n" +
                "Width: " + ConfigFile.getVCWidth() + "\n" +
                "Height: " + ConfigFile.getVCHeight()

        );

        int mapSize = ConfigFile.getMapSize();
        int mapWidth = ConfigFile.getMapWidth();

        videoCapture = new VideoCapture(this,
                ConfigFile.getVCWidth(),
                ConfigFile.getVCHeight()
        );
        videoCapture.start();

        FrameProcessorTask frameProcessorTask = new FrameProcessorTask(mapSize, mapWidth);
        frameProcessorTask.runTaskTimerAsynchronously(this, 0, 1);
        FramePacketSender framePacketSender =
                new FramePacketSender(this, frameProcessorTask.getFrameBuffers());
        framePacketSender.runTaskTimerAsynchronously(this, 0, 1);
    }

    public static MakiScreen getInstance() {
        return makiScreen;
    }

    public PlayerConnectionManager getPlayerConnectionManager() {
        return playerConnectionManager;
    }
}
