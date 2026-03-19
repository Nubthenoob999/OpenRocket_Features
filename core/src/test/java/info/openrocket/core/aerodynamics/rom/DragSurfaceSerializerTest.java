package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

public class DragSurfaceSerializerTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void testRoundTripSerialization() {
        double[] mach = new double[] { 0.01, 0.8, 1.2, 2.0 };
        double[] re = new double[] { 4.0, 5.0 };
        double[] alpha = new double[] { 0.0, 5.0, 10.0 };

        double[][][] off = new double[mach.length][re.length][alpha.length];
        double[][][] on = new double[mach.length][re.length][alpha.length];
        for (int im = 0; im < mach.length; im++) {
            for (int ir = 0; ir < re.length; ir++) {
                for (int ia = 0; ia < alpha.length; ia++) {
                    off[im][ir][ia] = 0.2 + im * 0.05 + ir * 0.01 + ia * 0.005;
                    on[im][ir][ia] = off[im][ir][ia] - 0.02;
                }
            }
        }

        DragSurface surface = new DragSurface(mach, re, alpha, off, on, HASH, 1.25);

        String encoded = DragSurfaceSerializer.serializeToBase64Gzip(surface);
        assertNotNull(encoded);

        DragSurface decoded = DragSurfaceSerializer.deserializeFromBase64Gzip(
            encoded, HASH, 1.25, surface.buildTimestampMs);

        assertEquals(HASH, decoded.geometryHash);
        assertEquals(1.25, decoded.looRmsePercent, 0.0);
        assertArrayEquals(surface.machAxis, decoded.machAxis, 0.0);
        assertArrayEquals(surface.logReAxis, decoded.logReAxis, 0.0);
        assertArrayEquals(surface.alphaAxis, decoded.alphaAxis, 0.0);

        assertEquals(surface.machAxis.length, decoded.machAxis.length);
        assertEquals(surface.logReAxis.length, decoded.logReAxis.length);
        assertEquals(surface.alphaAxis.length, decoded.alphaAxis.length);

        for (int im = 0; im < mach.length; im++) {
            for (int ir = 0; ir < re.length; ir++) {
                for (int ia = 0; ia < alpha.length; ia++) {
                    assertEquals(surface.cdPlumeOff[im][ir][ia], decoded.cdPlumeOff[im][ir][ia], 0.0);
                    assertEquals(surface.cdPlumeOn[im][ir][ia], decoded.cdPlumeOn[im][ir][ia], 0.0);
                }
            }
        }
    }

    @Test
    public void testDeserializeRejectsInvalidBase64() {
        assertThrows(IllegalStateException.class, () ->
                DragSurfaceSerializer.deserializeFromBase64Gzip("not-base64", HASH, 0.0, 0L));
    }

    @Test
    public void testDeserializeRejectsInvalidDimensions() {
        String encoded = gzipAndEncode(invalidDimensionBlob());

        assertThrows(IllegalStateException.class, () ->
                DragSurfaceSerializer.deserializeFromBase64Gzip(encoded, HASH, 0.0, 0L));
    }

    @Test
    public void testDeserializeRejectsTruncatedPayload() {
        double[] mach = new double[] { 0.1, 1.0 };
        double[] re = new double[] { 4.0, 6.0 };
        double[] alpha = new double[] { 0.0, 10.0 };
        double[][][] off = new double[mach.length][re.length][alpha.length];
        double[][][] on = new double[mach.length][re.length][alpha.length];
        DragSurface surface = new DragSurface(mach, re, alpha, off, on, HASH, 0.0);

        String valid = DragSurfaceSerializer.serializeToBase64Gzip(surface);
        byte[] gz = Base64.getDecoder().decode(valid);
        byte[] truncated = new byte[gz.length - 8];
        System.arraycopy(gz, 0, truncated, 0, truncated.length);
        String encoded = Base64.getEncoder().encodeToString(truncated);

        assertThrows(IllegalStateException.class, () ->
                DragSurfaceSerializer.deserializeFromBase64Gzip(encoded, HASH, 0.0, 0L));
    }

    private static byte[] invalidDimensionBlob() {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            writeIntLE(raw, 1);
            writeIntLE(raw, 1);
            writeIntLE(raw, 1);
            return raw.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build invalid blob", e);
        }
    }

    private static String gzipAndEncode(byte[] raw) {
        try {
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(gz)) {
                gzip.write(raw);
            }
            return Base64.getEncoder().encodeToString(gz.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build test payload", e);
        }
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }
}
