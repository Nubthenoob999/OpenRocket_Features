package info.openrocket.core.aerodynamics.rom;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serialization helper for ROM drag surfaces.
 *
 * Binary format (little-endian):
 * int32 nMach, int32 nRe, int32 nAlpha,
 * double[nMach] machAxis,
 * double[nRe] logReAxis,
 * double[nAlpha] alphaAxis,
 * double[nMach*nRe*nAlpha] cdPlumeOff,
 * double[nMach*nRe*nAlpha] cdPlumeOn.
 */
public final class DragSurfaceSerializer {

    private DragSurfaceSerializer() {
    }

    public static String serializeToBase64Gzip(DragSurface surface) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(256 * 1024);
            writeIntLE(raw, surface.machAxis.length);
            writeIntLE(raw, surface.logReAxis.length);
            writeIntLE(raw, surface.alphaAxis.length);

            writeDoubleArray(raw, surface.machAxis);
            writeDoubleArray(raw, surface.logReAxis);
            writeDoubleArray(raw, surface.alphaAxis);
            writeGrid(raw, surface.cdPlumeOff);
            writeGrid(raw, surface.cdPlumeOn);

            ByteArrayOutputStream gz = new ByteArrayOutputStream(raw.size());
            try (GZIPOutputStream gzip = new GZIPOutputStream(gz)) {
                gzip.write(raw.toByteArray());
            }
            return Base64.getEncoder().encodeToString(gz.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize ROM drag surface", e);
        }
    }

    public static DragSurface deserializeFromBase64Gzip(String base64,
                                                         String geometryHash,
                                                         double looRmsePercent,
                                                         long buildTimestampMs) {
        try {
            byte[] gz = Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
            byte[] raw;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gz))) {
                raw = gzip.readAllBytes();
            }

            ByteArrayInputStream in = new ByteArrayInputStream(raw);
            int nMach = readIntLE(in);
            int nRe = readIntLE(in);
            int nAlpha = readIntLE(in);
            if (nMach <= 1 || nRe <= 1 || nAlpha <= 1) {
                throw new IOException("Invalid ROM surface dimensions");
            }

            double[] machAxis = readDoubleArray(in, nMach);
            double[] logReAxis = readDoubleArray(in, nRe);
            double[] alphaAxis = readDoubleArray(in, nAlpha);
            double[][][] off = readGrid(in, nMach, nRe, nAlpha);
            double[][][] on = readGrid(in, nMach, nRe, nAlpha);

            DragSurface surface = new DragSurface(machAxis, logReAxis, alphaAxis, off, on, geometryHash, looRmsePercent);
            if (buildTimestampMs > 0) {
                // buildTimestampMs in DragSurface is final and set in constructor; metadata field is preserved
                // separately at XML level. We keep constructor timestamp and rely on attribute for display logic.
            }
            return surface;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to deserialize ROM drag surface", e);
        }
    }

    private static void writeGrid(ByteArrayOutputStream out, double[][][] grid) throws IOException {
        for (double[][] plane : grid) {
            for (double[] row : plane) {
                writeDoubleArray(out, row);
            }
        }
    }

    private static double[][][] readGrid(ByteArrayInputStream in, int nMach, int nRe, int nAlpha) throws IOException {
        double[][][] grid = new double[nMach][nRe][nAlpha];
        for (int im = 0; im < nMach; im++) {
            for (int ir = 0; ir < nRe; ir++) {
                for (int ia = 0; ia < nAlpha; ia++) {
                    grid[im][ir][ia] = readDoubleLE(in);
                }
            }
        }
        return grid;
    }

    private static void writeDoubleArray(ByteArrayOutputStream out, double[] values) throws IOException {
        for (double value : values) {
            writeDoubleLE(out, value);
        }
    }

    private static double[] readDoubleArray(ByteArrayInputStream in, int n) throws IOException {
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = readDoubleLE(in);
        }
        return values;
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private static int readIntLE(ByteArrayInputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new EOFException("Unexpected EOF reading int");
        }
        return (b0 & 0xff) | ((b1 & 0xff) << 8) | ((b2 & 0xff) << 16) | ((b3 & 0xff) << 24);
    }

    private static void writeDoubleLE(ByteArrayOutputStream out, double value) throws IOException {
        long bits = Double.doubleToLongBits(value);
        for (int i = 0; i < 8; i++) {
            out.write((int) ((bits >>> (8 * i)) & 0xff));
        }
    }

    private static double readDoubleLE(ByteArrayInputStream in) throws IOException {
        long bits = 0L;
        for (int i = 0; i < 8; i++) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("Unexpected EOF reading double");
            }
            bits |= ((long) (b & 0xff)) << (8 * i);
        }
        return Double.longBitsToDouble(bits);
    }
}
