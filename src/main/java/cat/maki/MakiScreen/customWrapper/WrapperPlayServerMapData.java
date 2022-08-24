package cat.maki.MakiScreen.customWrapper;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;

public class WrapperPlayServerMapData extends PacketWrapper<WrapperPlayServerMapData> {

    private int data;
    private int scale;
    private boolean locked;
    private Collection<MapIcon> icons;
    private byte[] colors;
    private int x;
    private int z;
    private int width;
    private int height;

    public WrapperPlayServerMapData(int data, int scale, boolean locked, @Nullable Collection<MapIcon> icons, byte[] colors, int x, int z, int width, int height) {
        super(PacketType.Play.Server.MAP_DATA);
        this.data = data;
        this.scale = scale;
        this.locked = locked;
        this.icons = icons;
        this.colors = colors;
        this.x = x;
        this.z = z;
        this.width = width;
        this.height = height;
    }

    public WrapperPlayServerMapData() {
        super(PacketType.Play.Server.MAP_DATA);
    }

    @Override
    public void read() {
        this.data = this.readVarInt();
        this.scale = this.readByte();

        boolean readIcons = true;
        if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_9)) {
            if (this.serverVersion.isOlderThan(ServerVersion.V_1_17)) {
                readIcons = this.readBoolean();
                this.locked = this.readBoolean();
            } else {
                this.locked = this.readBoolean();
                readIcons = this.readBoolean();
            }
        }

        if (readIcons || this.serverVersion.isOlderThan(ServerVersion.V_1_17)) {
            int iconCount = this.readVarInt();
            this.icons = new ArrayList<>(iconCount);
            for (int i = 0; i < iconCount; i++) {
                this.icons.add(new MapIcon(this));
            }
        }

        this.width = this.readUnsignedByte();
        if (this.width > 0) {
            this.height = this.readUnsignedByte();
            this.x = this.readUnsignedByte();
            this.z = this.readUnsignedByte();
            this.buffer = this.readByteArray();
        }
    }

    @Override
    public void write() {
        this.writeVarInt(this.data);
        this.writeByte(this.scale);

        boolean writeIcons = true;
        if (this.serverVersion.isNewerThanOrEquals(ServerVersion.V_1_9)) {
            writeIcons = this.icons != null;
            if (this.serverVersion.isOlderThan(ServerVersion.V_1_17)) {
                this.writeBoolean(writeIcons);
                this.writeBoolean(this.locked);
            } else {
                this.writeBoolean(this.locked);
                this.writeBoolean(writeIcons);
            }
        }

        if (writeIcons || this.serverVersion.isOlderThan(ServerVersion.V_1_17)) {
            this.writeVarInt(this.icons.size());
            this.icons.forEach(icon -> {
                this.writeByte((icon.getType().getId() & 15) << 4 | icon.getRotation() & 15);
                this.writeByte(icon.getX());
                this.writeByte(icon.getY());
            });
        }

        this.writeByte(this.width);
        if (this.width > 0) {
            this.writeByte(this.height);
            this.writeByte(this.x);
            this.writeByte(this.z);
            this.writeByteArray(this.colors);
        }
    }

    @Override
    public void copy(WrapperPlayServerMapData wrapper) {
        this.data = wrapper.data;
        this.scale = wrapper.scale;
        this.locked = wrapper.locked;
        if (wrapper.icons != null) {
            this.icons = new ArrayList<>(wrapper.icons.size());
            wrapper.icons.forEach(icon -> this.icons.add(icon.clone()));
        } else {
            this.icons = null;
        }
        this.colors = new byte[wrapper.colors.length];
        System.arraycopy(wrapper.colors, 0, this.colors, 0, wrapper.colors.length);
        this.width = wrapper.width;
        this.height = wrapper.height;
        this.x = wrapper.x;
        this.z = wrapper.z;
    }
}