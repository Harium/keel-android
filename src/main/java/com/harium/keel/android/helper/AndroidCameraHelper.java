package com.harium.keel.android.helper;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;

import java.nio.ByteBuffer;

public class AndroidCameraHelper {

    private static final int YCBCR_MAX_VALUE = 262143;

    public static int[] getRGB(ImageReader reader) {
        int width = reader.getWidth();
        int height = reader.getHeight();

        int[] rgb = new int[width * height * 3];

        return getRGB(reader, rgb);
    }

    public static int[] getRGB(ImageReader reader, int[] out) {
        Image image = reader.acquireNextImage();
        int width = image.getWidth();
        int height = image.getHeight();

        if (reader.getImageFormat() == ImageFormat.YUV_420_888) {
            getRGBFromPlanes(width, height, image.getPlanes(), out);
        }

        image.close();
        return out;
    }

    /**
     * Convert YUV420_888 to RGB_888
     * Code found at: http://werner-dittmann.blogspot.com.br/2016/03/solving-some-mysteries-about-androids.html
     *
     * @param width  camera width
     * @param height camera height
     * @param planes Android Camera2 API's planes
     * @param out    int array of rgb channels
     * @return int array of rgb channels
     */
    private static int[] getRGBFromPlanes(int width, int height, Image.Plane[] planes, int[] out) {
        ByteBuffer yPlane = planes[0].getBuffer();
        ByteBuffer uPlane = planes[1].getBuffer();
        ByteBuffer vPlane = planes[2].getBuffer();

        int bufferIndex = 0;
        final int total = yPlane.capacity();
        final int uvCapacity = uPlane.capacity();
        // final int width = planes[0].getRowStride();

        int yPos = 0;
        for (int i = 0; i < height; i++) {
            int uvPos = (i >> 1) * width;

            for (int j = 0; j < width; j++) {
                if (uvPos >= uvCapacity - 1)
                    break;
                if (yPos >= total)
                    break;

                final int y1 = yPlane.get(yPos++) & 0xff;

                /*
                  The ordering of the u (Cb) and v (Cr) bytes inside the planes is a
                  bit strange. The _first_ byte of the u-plane and the _second_ byte
                  of the v-plane build the u/v pair and belong to the first two pixels
                  (y-bytes), thus usual YUV 420 behavior. What the Android devs did
                  here (IMHO): just copy the interleaved NV21 U/V data to two planes
                  but keep the offset of the interleaving.
                */

                final int u = (uPlane.get(uvPos) & 0xff) - 128;
                final int v = (vPlane.get(uvPos + 1) & 0xff) - 128;
                if ((j & 1) == 1) {
                    uvPos += 2;
                }

                // This is the integer variant to convert YCbCr to RGB, NTSC values.
                // formulae found at
                // https://software.intel.com/en-us/android/articles/trusted-tools-in-the-new-android-world-optimization-techniques-from-intel-sse-intrinsics-to
                // and on StackOverflow etc.
                final int y1192 = 1192 * y1;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = clampYCbCr(r);
                g = clampYCbCr(g);
                b = clampYCbCr(b);

                out[bufferIndex++] = ((r << 6) & 0xff0000) |
                        ((g >> 2) & 0xff00) |
                        ((b >> 10) & 0xff);
            }
        }

        return out;
    }

    private static int clampYCbCr(int channel) {
        return (channel < 0) ? 0 : ((channel > YCBCR_MAX_VALUE) ? YCBCR_MAX_VALUE : channel);
    }
}
