package cat.maki.MakiScreen;

import cat.maki.MakiScreen.connection.PlayerConnectionManager;
import com.github.puregero.multilib.MultiLib;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
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

import static cat.maki.MakiScreen.FrameProcessorTask.difference;

public final class MakiScreen extends JavaPlugin implements Listener {

    private final Logger logger = getLogger();

    public static final int BUFFER_MAP_COUNT = 11; // Odd number

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
        if (command.getName().equals("maki") || command.getName().equals("makiauto")) {
            if (!player.hasPermission("makiscreen.admin")) {
                player.sendMessage("You don't have permission!");
                return false;
            }

            int x = 0;
            int y = 0;
            for (int i = 0; i < ConfigFile.getMapSize(); i++) {
                MapView mapView = null;

                // Check for existing maps
                for (ScreenPart screenPart : screens) {
                    if (screenPart.partId == i) {
                        mapView = getServer().getMap(screenPart.mapId);
                    }
                }

                // Create new map if none exists
                if (mapView == null) {
                    mapView = getServer().createMap(player.getWorld());
                    for (int j = 0; j < BUFFER_MAP_COUNT - 1; j++)
                        getServer().createMap(player.getWorld()); // Create extra buffer maps
                    screens.add(new ScreenPart(mapView.getId(), i));
                }

                mapView.setScale(MapView.Scale.CLOSEST);
                mapView.setUnlimitedTracking(true);
                for (MapRenderer renderer : mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
                }

                ItemStack itemStack = new ItemStack(Material.FILLED_MAP);
                MapMeta mapMeta = (MapMeta) itemStack.getItemMeta();
                mapMeta.setMapView(mapView);
                itemStack.setItemMeta(mapMeta);

                ImageManager manager = ImageManager.getInstance();
                manager.saveImage(mapView.getId(), i);

                if (command.getName().equals("makiauto")) {
                    Location location = player.getTargetBlock(10).getLocation();
                    BlockFace facing = player.getTargetBlockFace(10).getOppositeFace();
                    location = location.add(facing.getOppositeFace().getModX(), facing.getOppositeFace().getModY(), facing.getOppositeFace().getModZ());
                    location = location.add(-x * facing.getModZ(), -y, x * facing.getModX());
                    ItemFrame itemFrame = player.getWorld().spawn(location, GlowItemFrame.class);
                    itemFrame.setVisible(false);
                    itemFrame.setFacingDirection(facing.getOppositeFace());
                    itemFrame.setItem(itemStack);
                } else {
                    player.getWorld().dropItem(player.getLocation(), itemStack);
                }

                x++;
                if (x >= ConfigFile.getMapWidth()) {
                    x = 0;
                    y++;
                }
            }
        }

        if (command.getName().equals("makisetdifference")) {
            if (args.length != 1)
                sender.sendMessage("Wrong command usage!");
            difference = Integer.valueOf(args[0]);
            MultiLib.notify("maki:difference", args[0]);
        }

        return true;
    }

    private void init(){
        MultiLib.onString(this, "maki:difference", s -> {
            difference = Integer.valueOf(s);
        });
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
        frameProcessorTask.runTaskAsynchronously(this);
        FramePacketSender framePacketSender =
                new FramePacketSender(this, frameProcessorTask.getFrameBuffers());
        framePacketSender.runTaskAsynchronously(this);
    }

    public static MakiScreen getInstance() {
        return makiScreen;
    }

    public PlayerConnectionManager getPlayerConnectionManager() {
        return playerConnectionManager;
    }
}
