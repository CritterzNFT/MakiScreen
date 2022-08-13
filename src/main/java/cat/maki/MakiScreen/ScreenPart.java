package cat.maki.MakiScreen;

public class ScreenPart {

    public final int mapId;
    public final int partId;
    public byte[] lastFrameBuffer;
    public boolean modified;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ScreenPart)) {
            return false;
        }
        return this.mapId == ((ScreenPart) obj).mapId;
    }

    public ScreenPart(int mapId, int partId) {
        this.mapId = mapId;
        this.partId = partId;
    }
}
