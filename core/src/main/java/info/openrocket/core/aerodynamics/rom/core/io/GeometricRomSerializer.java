package info.openrocket.core.aerodynamics.rom.core.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import info.openrocket.core.aerodynamics.rom.core.basis.GeometryParametricRom;
import info.openrocket.core.aerodynamics.rom.core.basis.LocalPodRegion;
import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;

/**
 * Serializer for geometry-parametric ROM artifacts.
 */
public final class GeometricRomSerializer {

	private static final int MAGIC = 0x524F4D34;
	private static final int VERSION = 1;

	private GeometricRomSerializer() {
	}

	public static byte[] serializeToBase64Gzip(GeometryParametricRom rom,
			int totalSnapshotsUsed,
			double achievedMaxReconstructionError) throws IOException {
		ByteArrayOutputStream raw = new ByteArrayOutputStream(1024 * 1024);
		DataOutputStream out = new DataOutputStream(raw);

		List<LocalPodRegion> regions = rom.getRegions();
		int nRegions = regions.size();
		int nMach = rom.machAxis.length;
		int nRe = rom.logReAxis.length;
		int nAlpha = rom.alphaAxis.length;
		int nBeta = rom.betaAxis.length;
		int n = nMach * nRe * nAlpha * nBeta;

		writeIntLE(out, MAGIC);
		writeIntLE(out, VERSION);
		writeIntLE(out, nRegions);
		writeIntLE(out, n);
		writeIntLE(out, nMach);
		writeIntLE(out, nRe);
		writeIntLE(out, nAlpha);
		writeIntLE(out, nBeta);

		writeArrayLE(out, rom.machAxis);
		writeArrayLE(out, rom.logReAxis);
		writeArrayLE(out, rom.alphaAxis);
		writeArrayLE(out, rom.betaAxis);

		int[][] magicOff = rom.getMagicPointsOff();
		int[][] magicOn = rom.getMagicPointsOn();

		for (int r = 0; r < nRegions; r++) {
			LocalPodRegion region = regions.get(r);
			writeArrayLE(out, region.centroidFeatureVector);
			writeDoubleLE(out, region.sigma);
			writeIntLE(out, region.basisOff.rank);
			writeIntLE(out, region.basisOn.rank);
			writeIntLE(out, n);

			writeArrayLE(out, region.basisOff.mean);
			writeModesLE(out, region.basisOff.modes);
			writeArrayLE(out, region.basisOff.singularValues);

			writeArrayLE(out, region.basisOn.mean);
			writeModesLE(out, region.basisOn.modes);
			writeArrayLE(out, region.basisOn.singularValues);

			writeIntLE(out, magicOff[r].length);
			for (int idx : magicOff[r]) {
				writeIntLE(out, idx);
			}
			writeIntLE(out, magicOn[r].length);
			for (int idx : magicOn[r]) {
				writeIntLE(out, idx);
			}
			writeUtf8(out, region.regionLabel);
		}

		writeLongLE(out, System.currentTimeMillis());
		writeIntLE(out, totalSnapshotsUsed);
		writeDoubleLE(out, achievedMaxReconstructionError);
		out.flush();

		ByteArrayOutputStream gz = new ByteArrayOutputStream(raw.size());
		try (GZIPOutputStream gzip = new GZIPOutputStream(gz)) {
			gzip.write(raw.toByteArray());
		}
		return Base64.getEncoder().encode(gz.toByteArray());
	}

	public static GeometryParametricRom deserializeFromBase64Gzip(byte[] base64Gzip) throws IOException {
		byte[] gz = Base64.getDecoder().decode(new String(base64Gzip, StandardCharsets.UTF_8));
		byte[] raw;
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gz))) {
			raw = gzip.readAllBytes();
		}
		ByteArrayInputStream in = new ByteArrayInputStream(raw);

		int magic = readIntLE(in);
		int version = readIntLE(in);
		if (magic != MAGIC) {
			throw new IOException("Invalid Geometric ROM magic");
		}
		if (version != VERSION) {
			throw new IOException("Unsupported Geometric ROM version: " + version);
		}

		int nRegions = readIntLE(in);
		int n = readIntLE(in);
		int nMach = readIntLE(in);
		int nRe = readIntLE(in);
		int nAlpha = readIntLE(in);
		int nBeta = readIntLE(in);

		double[] machAxis = readArrayLE(in, nMach);
		double[] logReAxis = readArrayLE(in, nRe);
		double[] alphaAxis = readArrayLE(in, nAlpha);
		double[] betaAxis = readArrayLE(in, nBeta);

		List<LocalPodRegion> regions = new ArrayList<>(nRegions);
		int[][] magicOff = new int[nRegions][];
		int[][] magicOn = new int[nRegions][];

		for (int r = 0; r < nRegions; r++) {
			double[] centroid = readArrayLE(in, 10);
			double sigma = readDoubleLE(in);
			int rankOff = readIntLE(in);
			int rankOn = readIntLE(in);
			int nCheck = readIntLE(in);
			if (nCheck != n) {
				throw new IOException("Snapshot length mismatch in region " + r);
			}

			double[] meanOff = readArrayLE(in, n);
			double[][] modesOff = readModesLE(in, rankOff, n);
			double[] svOff = readArrayLE(in, rankOff);

			double[] meanOn = readArrayLE(in, n);
			double[][] modesOn = readModesLE(in, rankOn, n);
			double[] svOn = readArrayLE(in, rankOn);

			int nMagicOff = readIntLE(in);
			magicOff[r] = new int[nMagicOff];
			for (int i = 0; i < nMagicOff; i++) {
				magicOff[r][i] = readIntLE(in);
			}
			int nMagicOn = readIntLE(in);
			magicOn[r] = new int[nMagicOn];
			for (int i = 0; i < nMagicOn; i++) {
				magicOn[r][i] = readIntLE(in);
			}
			String label = readUtf8(in);

			PodBasis off = new PodBasis(modesOff, meanOff, svOff, machAxis, logReAxis, alphaAxis, betaAxis, 1.0);
			PodBasis on = new PodBasis(modesOn, meanOn, svOn, machAxis, logReAxis, alphaAxis, betaAxis, 1.0);
			regions.add(new LocalPodRegion(centroid, sigma, off, on, label));
		}

		// Footer values are currently informational only.
		readLongLE(in);
		readIntLE(in);
		readDoubleLE(in);

		return new GeometryParametricRom(regions, machAxis, logReAxis, alphaAxis, betaAxis, magicOff, magicOn);
	}

	private static void writeModesLE(DataOutputStream out, double[][] modes) throws IOException {
		for (double[] mode : modes) {
			writeArrayLE(out, mode);
		}
	}

	private static double[][] readModesLE(ByteArrayInputStream in, int rows, int cols) throws IOException {
		double[][] out = new double[rows][cols];
		for (int i = 0; i < rows; i++) {
			out[i] = readArrayLE(in, cols);
		}
		return out;
	}

	private static void writeUtf8(DataOutputStream out, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		writeIntLE(out, bytes.length);
		out.write(bytes);
	}

	private static String readUtf8(ByteArrayInputStream in) throws IOException {
		int len = readIntLE(in);
		byte[] bytes = in.readNBytes(len);
		if (bytes.length != len) {
			throw new EOFException("Unexpected EOF reading UTF-8 string");
		}
		return new String(bytes, StandardCharsets.UTF_8);
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

	private static void writeLongLE(DataOutputStream out, long value) throws IOException {
		for (int i = 0; i < 8; i++) {
			out.write((int) ((value >>> (8 * i)) & 0xff));
		}
	}

	private static long readLongLE(ByteArrayInputStream in) throws IOException {
		long bits = 0L;
		for (int i = 0; i < 8; i++) {
			int b = in.read();
			if (b < 0) {
				throw new EOFException("Unexpected EOF reading long");
			}
			bits |= ((long) (b & 0xff)) << (8 * i);
		}
		return bits;
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
