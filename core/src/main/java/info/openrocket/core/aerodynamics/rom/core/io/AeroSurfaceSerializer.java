package info.openrocket.core.aerodynamics.rom.core.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public final class AeroSurfaceSerializer {

	private AeroSurfaceSerializer() {
	}

	public static byte[] serialize(AeroSurface4D surface) throws IOException {
		ByteArrayOutputStream raw = new ByteArrayOutputStream(512 * 1024);
		DataOutputStream out = new DataOutputStream(raw);

		int nMach = surface.machAxis.length;
		int nRe = surface.logReAxis.length;
		int nAlpha = surface.alphaAxis.length;
		int nBeta = surface.betaAxis.length;

		writeIntLE(out, nMach);
		writeIntLE(out, nRe);
		writeIntLE(out, nAlpha);
		writeIntLE(out, nBeta);

		writeArrayLE(out, surface.machAxis);
		writeArrayLE(out, surface.logReAxis);
		writeArrayLE(out, surface.alphaAxis);
		writeArrayLE(out, surface.betaAxis);

		writeGridLE(out, surface.cdPlumeOff, nMach, nRe, nAlpha, nBeta);
		writeGridLE(out, surface.cdPlumeOn, nMach, nRe, nAlpha, nBeta);
		writeGridLE(out, surface.cdBody, nMach, nRe, nAlpha, nBeta);
		writeGridLE(out, surface.CN, nMach, nRe, nAlpha, nBeta);
		writeGridLE(out, surface.Cm, nMach, nRe, nAlpha, nBeta);
		out.flush();

		ByteArrayOutputStream gz = new ByteArrayOutputStream(raw.size());
		try (GZIPOutputStream gzip = new GZIPOutputStream(gz)) {
			gzip.write(raw.toByteArray());
		}
		return Base64.getEncoder().encode(gz.toByteArray());
	}

	public static AeroSurface4D deserialize(byte[] base64Gzip,
			String geometryHash,
			long buildTimestampMs,
			int finCount) throws IOException {
		byte[] gz = Base64.getDecoder().decode(new String(base64Gzip, StandardCharsets.UTF_8));
		byte[] raw;
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gz))) {
			raw = gzip.readAllBytes();
		}

		ByteArrayInputStream in = new ByteArrayInputStream(raw);
		int nMach = readIntLE(in);
		int nRe = readIntLE(in);
		int nAlpha = readIntLE(in);
		int nBeta = readIntLE(in);

		double[] machAxis = readArrayLE(in, nMach);
		double[] logReAxis = readArrayLE(in, nRe);
		double[] alphaAxis = readArrayLE(in, nAlpha);
		double[] betaAxis = readArrayLE(in, nBeta);

		double[][][][] cdOff = readGridLE(in, nMach, nRe, nAlpha, nBeta);
		double[][][][] cdOn = readGridLE(in, nMach, nRe, nAlpha, nBeta);
		double[][][][] cdBody = readGridLE(in, nMach, nRe, nAlpha, nBeta);
		double[][][][] cn = readGridLE(in, nMach, nRe, nAlpha, nBeta);
		double[][][][] cm = readGridLE(in, nMach, nRe, nAlpha, nBeta);

		AeroSurface4D surface = new AeroSurface4D(
				machAxis,
				logReAxis,
				alphaAxis,
				betaAxis,
				cdOff,
				cdOn,
				cdBody,
				cn,
				cm,
				geometryHash,
				finCount,
				0.0);
		if (buildTimestampMs > 0) {
			// Timestamp is stored on XML attributes and is informational only.
		}
		return surface;
	}

	private static void writeGridLE(DataOutputStream out, double[][][][] g,
			int nMach, int nRe, int nAlpha, int nBeta) throws IOException {
		for (int ib = 0; ib < nBeta; ib++) {
			for (int ia = 0; ia < nAlpha; ia++) {
				for (int ir = 0; ir < nRe; ir++) {
					for (int im = 0; im < nMach; im++) {
						writeDoubleLE(out, g[im][ir][ia][ib]);
					}
				}
			}
		}
	}

	private static double[][][][] readGridLE(ByteArrayInputStream in,
			int nMach, int nRe, int nAlpha, int nBeta) throws IOException {
		double[][][][] g = new double[nMach][nRe][nAlpha][nBeta];
		for (int ib = 0; ib < nBeta; ib++) {
			for (int ia = 0; ia < nAlpha; ia++) {
				for (int ir = 0; ir < nRe; ir++) {
					for (int im = 0; im < nMach; im++) {
						g[im][ir][ia][ib] = readDoubleLE(in);
					}
				}
			}
		}
		return g;
	}

	private static void writeArrayLE(DataOutputStream out, double[] values) throws IOException {
		for (double v : values) {
			writeDoubleLE(out, v);
		}
	}

	private static double[] readArrayLE(ByteArrayInputStream in, int n) throws IOException {
		double[] values = new double[n];
		for (int i = 0; i < n; i++) {
			values[i] = readDoubleLE(in);
		}
		return values;
	}

	private static void writeIntLE(DataOutputStream out, int value) throws IOException {
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

	private static void writeDoubleLE(DataOutputStream out, double value) throws IOException {
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
