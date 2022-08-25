package cat.maki.MakiScreen;

import com.github.puregero.multilib.MultiLib;
import com.google.common.collect.EvictingQueue;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static cat.maki.MakiScreen.dither.DitherLookupUtil.COLOR_MAP;
import static cat.maki.MakiScreen.dither.DitherLookupUtil.FULL_COLOR_MAP;

class FrameProcessorTask extends BukkitRunnable {

    private final Object lock = new Object();
    private final AtomicInteger threadsInLock = new AtomicInteger(0);
    private final Queue<byte[][]> frameBuffers = EvictingQueue.create(450);
    private final int mapSize;

    private final byte[] ditheredFrameData;
    private final int[][] ditherBuffer;
    private final byte[][] cachedMapData;
    private final int frameWidth;
    private byte[] frameData;

    FrameProcessorTask(int mapSize, int mapWidth) {
        this.mapSize = mapSize;
        this.frameWidth = mapWidth * 128;
        this.ditheredFrameData = new byte[mapSize * 128 * 128];
        this.ditherBuffer = new int[2][frameWidth << 2];
        this.cachedMapData = new byte[mapSize][];
    }

    public Queue<byte[][]> getFrameBuffers() {
        return frameBuffers;
    }

    private int[] r;
    private int[] g;
    private int[] b;
    private void dontDitherFrame() {
        //   |  Y
        // X | -> -> -> ->
        //   | -> -> -> ->
        //   | -> -> -> ->
        //   | -> -> -> ->

        int[] ditherBuffer = new int[frameData.length];

        int width = this.frameWidth;
        int height = this.ditheredFrameData.length / width;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pos = pos(x, y, width);
                int rgb = 0;

                int blue = (int) frameData[pos] & 0xFF;
                int green = (int) frameData[pos + 1] & 0xFF;
                int red = (int) frameData[pos + 2] & 0xFF;

                red = (int) Math.max(Math.min(255, red + ditherBuffer[pos]), 0);
                green = (int) Math.max(Math.min(255, green + ditherBuffer[pos + 1]), 0);
                blue = (int) Math.max(Math.min(255, blue + ditherBuffer[pos + 2]), 0);

                rgb |= blue;
                rgb |= green << 8;
                rgb |= red << 16;

                ditheredFrameData[y * width + x] = getColor(rgb);

                int newRgb = getBestFullColor(red, green, blue);

                if (x != width - 1) {
                    dither(ditherBuffer, pos(x + 1, y, width), rgb, newRgb, 7.0 / 16);
                    if (y != height - 1){
                        dither(ditherBuffer, pos(x + 1, y + 1, width), rgb, newRgb, 1.0 / 16);
                    }
                }
                if (y != height - 1){
                    dither(ditherBuffer, pos(x, y + 1, width), rgb, newRgb, 5.0 / 16);
                    if (x != 0) {
                        dither(ditherBuffer, pos(x - 1, y + 1, width), rgb, newRgb, 3.0 / 16);
                    }
                }
            }
        }
    }

    private void dither(int[] ditherBuffer, int pos, int rgb, int newRgb, double scalar) {
        int blueDiff = (rgb & 0xFF) - (newRgb & 0xFF);
        int greenDiff = ((rgb >> 8) & 0xFF) - ((newRgb >> 8) & 0xFF);
        int redDiff = ((rgb >> 16) & 0xFF) - ((newRgb >> 16) & 0xFF);

        ditherBuffer[pos] += blueDiff * scalar;
        ditherBuffer[pos + 1] += greenDiff * scalar;
        ditherBuffer[pos + 2] += redDiff * scalar;

    }

    private byte addNoOverflow(byte theByte, double v) {
        int theInt = (int) Math.round(v);
        if (theInt + (int) theByte >= Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        } else if (theInt + (int) theByte <= Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        } else {
            return (byte) (theInt + (int) theByte);
        }
    }

//    private void ditherFrame() {
//        int width = this.frameWidth;
//        int height = this.ditheredFrameData.length / width;
//        int widthMinus = width - 1;
//        int heightMinus = height - 1;
//
//        //   |  Y
//        // X | -> -> -> ->
//        //   | <- <- <- <-
//        //   | -> -> -> ->
//        //   | <- <- <- <-
//        for (int y = 0; y < height; y++) {
//            boolean hasNextY = y < heightMinus;
//            int yIndex = y * width;
//            if ((y & 0x1) == 0) { // Forward
//                int bufferIndex = 0;
//                final int[] buf1 = ditherBuffer[0];
//                final int[] buf2 = ditherBuffer[1];
//
//                for (int x = 0; x < width; ++x) {
//                    int pos = pos(x, y, width);
//                    int blue = (int) frameData[pos++] & 0xff;
//                    int green = ((int) frameData[pos++] & 0xff);
//                    int red = ((int) frameData[pos] & 0xff);
//
//                    red = Math.max(Math.min(255, red + buf1[bufferIndex++]), 0);
//                    green = Math.max(Math.min(255, green + buf1[bufferIndex++]), 0);
//                    blue = Math.max(Math.min(255, blue + buf1[bufferIndex++]), 0);
//
//                    final int closest = getBestFullColor(red, green, blue);
//                    final int delta_r = red - (closest >> 16 & 0xFF);
//                    final int delta_g = green - (closest >> 8 & 0xFF);
//                    final int delta_b = blue - (closest & 0xFF);
//
//                    if (x < widthMinus) {
//                        buf1[bufferIndex] = delta_r >> 1;
//                        buf1[bufferIndex + 1] = delta_g >> 1;
//                        buf1[bufferIndex + 2] = delta_b >> 1;
//                    }
//                    if (hasNextY) {
//                        if (x > 0) {
//                            buf2[bufferIndex - 6] = delta_r >> 2;
//                            buf2[bufferIndex - 5] = delta_g >> 2;
//                            buf2[bufferIndex - 4] = delta_b >> 2;
//                        }
//                        buf2[bufferIndex - 3] = delta_r >> 2;
//                        buf2[bufferIndex - 2] = delta_g >> 2;
//                        buf2[bufferIndex - 1] = delta_b >> 2;
//                    }
//                    ditheredFrameData[yIndex + x] = getColor(closest);
//                }
//            } else { // Backward
//                int bufferIndex = width + (width << 1) - 1;
//                final int[] buf1 = ditherBuffer[1];
//                final int[] buf2 = ditherBuffer[0];
//                for (int x = width - 1; x >= 0; --x) {
//                    int pos = pos(x, y, width);
//                    int blue = (int) frameData[pos++] & 0xff;
//                    int green = ((int) frameData[pos++] & 0xff);
//                    int red = ((int) frameData[pos] & 0xff);
//
//                    red = Math.max(Math.min(255, red + buf1[bufferIndex--]), 0);
//                    green = Math.max(Math.min(255, green + buf1[bufferIndex--]), 0);
//                    blue = Math.max(Math.min(255, blue + buf1[bufferIndex--]), 0);
//
//                    int closest = getBestFullColor(red, green, blue);
//                    int delta_r = red - (closest >> 16 & 0xFF);
//                    int delta_g = green - (closest >> 8 & 0xFF);
//                    int delta_b = blue - (closest & 0xFF);
//
//                    if (x > 0) {
//                        buf1[bufferIndex] = delta_b >> 1;
//                        buf1[bufferIndex - 1] = delta_g >> 1;
//                        buf1[bufferIndex - 2] = delta_r >> 1;
//                    }
//                    if (hasNextY) {
//                        if (x < widthMinus) {
//                            buf2[bufferIndex + 6] = delta_b >> 2;
//                            buf2[bufferIndex + 5] = delta_g >> 2;
//                            buf2[bufferIndex + 4] = delta_r >> 2;
//                        }
//                        buf2[bufferIndex + 3] = delta_b >> 2;
//                        buf2[bufferIndex + 2] = delta_g >> 2;
//                        buf2[bufferIndex + 1] = delta_r >> 2;
//                    }
//                    ditheredFrameData[yIndex + x] = getColor(closest);
//                }
//            }
//        }
//    }

    private static int pos(int x, int y, int width) {
        return (y * 3 * width) + (x * 3);
    }

    private byte getColor(int rgb) {
        byte result = COLOR_MAP[(rgb >> 16 & 0xFF) >> 1 << 14 | (rgb >> 8 & 0xFF) >> 1 << 7
                | (rgb & 0xFF) >> 1];
        return result;
    }

    private int getBestFullColor(final int red, final int green, final int blue) {
        return FULL_COLOR_MAP[red >> 1 << 14 | green >> 1 << 7 | blue >> 1];
    }

    @Override
    public void run() {
        try {
            if (threadsInLock.getAndIncrement() < 3) {
                synchronized (lock) {
                    BufferedImage frame = VideoCapture.currentFrame;
                    if (frame == null) {
                        return;
                    }
                    frameData = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();
                    dontDitherFrame();
//      System.out.println("DitherTime: " + diff + "ns");

                    byte[][] buffers = new byte[mapSize][];

                    for (int partId = 0; partId < buffers.length; partId++) {
                        buffers[partId] = getMapData(partId, frameWidth);
                    }

                    frameBuffers.offer(buffers);

                    notifyNewFrameBuffer(buffers);
                }
            }
        } finally {
            threadsInLock.decrementAndGet();
        }
    }

    private void notifyNewFrameBuffer(byte[][] buffers) {
        int size = 4;
        for (int i = 0; i < buffers.length; i ++) {
            size += 4 + (buffers[i] == null ? 0 : buffers[i].length);
        }

        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(buffers.length);
        for (int i = 0; i < buffers.length; i ++) {
            if (buffers[i] == null) {
                buffer.putInt(0);
            } else {
                buffer.putInt(buffers[i].length);
                buffer.put(buffers[i]);
            }
        }

        MultiLib.notify("maki:frameBuffer", buffer.array());
    }

    private byte[] getMapData(int partId, int width) {
        int offset = 0;
        int startX = ((partId % ConfigFile.getMapWidth()) * 128);
        int startY = ((partId / ConfigFile.getMapWidth()) * 128);
        int maxY = startY + 128;
        int maxX = startX + 128;

        boolean modified = false;
        byte[] bytes = this.cachedMapData[partId];
        if (bytes == null) {
            bytes = new byte[128 * 128];
            modified = true;
        }
        byte[] oldBytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, oldBytes, 0, bytes.length);
        for (int y = startY; y < maxY; y++) {
            int yIndex = y * width;
            for (int x = startX; x < maxX; x++) {
                byte newColor = ditheredFrameData[yIndex + x];
                if (modified) {
                    bytes[offset] = newColor;
                } else {
                    if (bytes[offset] != newColor) {
                        bytes[offset] = newColor;
                        modified = true;
                    }
                }
                offset++;
            }
        }

        double[] response = getDifference(oldBytes, bytes);
        if (response[0] < difference && response[1] < difference*1.5){
            return null;
        }

        if (modified) {
            this.cachedMapData[partId] = bytes;
            byte[] result = new byte[bytes.length];
            System.arraycopy(bytes, 0, result, 0, bytes.length);
            return result;
        }
        return null;
    }

    public static int difference = 20;
    private double[] getDifference(byte[] first, byte[] second) {
        double[] response = new double[2];
        double largestDifference = 0;
        double difference = 0;
        for (int i = 0; i < first.length; i++){
            int change = Math.abs(first[i] - second[i]);
            difference += change;
            if (change > largestDifference)
                largestDifference = change;
        }
        response[0] = difference / first.length;
        response[1] = largestDifference;
        return response;
    }
}
