package info.openrocket.core.aerodynamics.physicsaero.table;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.InflaterInputStream;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.util.Coordinate;

public final class TableReader {
	public AerodynamicTable read(Path file) throws IOException {
		try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
			byte[] magic = in.readNBytes(TableWriter.MAGIC.length);
			if (Arrays.equals(magic, "OPRAERO1".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
					|| Arrays.equals(magic, "OPRAERO2".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
					|| Arrays.equals(magic, "OPRAERO3".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
					|| Arrays.equals(magic, "OPRAERO4".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
				throw new IOException("obsolete physics-aero table; rebuild as " + TableMetadata.CURRENT_SCHEMA);
			}
			if (!Arrays.equals(magic, TableWriter.MAGIC)) throw new IOException("invalid table magic");
			int length = in.readInt();
			if (length < 0 || length > Files.size(file)) throw new IOException("invalid table payload length");
			byte[] expected = in.readNBytes(32), compressed = in.readNBytes(length);
			if (compressed.length != length || !Arrays.equals(expected, sha256(compressed))) throw new IOException("table checksum mismatch");
			if (in.read() != -1) throw new IOException("unexpected data after table payload");
			try (DataInputStream payload = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
				TableAxes axes = new TableAxes(readAxis(payload), readAxis(payload), readAxis(payload), readAxis(payload)); TableMetadata metadata = readMetadata(payload);
				int count = payload.readInt(); List<TableCell> cells = new ArrayList<>(count); for (int i = 0; i < count; i++) cells.add(readCell(payload));
				if (payload.read() != -1) throw new IOException("unexpected data after decoded table");
				if (!TableMetadata.CURRENT_SCHEMA.equals(metadata.schemaVersion())) {
					throw new IOException("table schema mismatch; rebuild required");
				}
				return new AerodynamicTable(axes, cells, metadata);
			}
		}
	}
	private static double[] readAxis(DataInputStream in) throws IOException { double[] v = new double[in.readInt()]; for (int i = 0; i < v.length; i++) v[i] = in.readDouble(); return v; }
	private static TableMetadata readMetadata(DataInputStream in) throws IOException {
		String schema = in.readUTF(), geometry = in.readUTF(), settings = in.readUTF(), code = in.readUTF(), registry = in.readUTF();
		String units = in.readUTF(), convention = in.readUTF(), status = in.readUTF();
		CertificationState certification;
		try { certification = CertificationState.valueOf(status); }
		catch (IllegalArgumentException exception) { throw new IOException("invalid table certification state", exception); }
		return new TableMetadata(schema, geometry, settings, code, registry, units, convention,
				readDoubleMap(in), readDoubleMap(in), certification);
	}
	private static TableCell readCell(DataInputStream in) throws IOException {
		AerodynamicCoefficients coefficients = readCoefficients(in); Map<String, AerodynamicCoefficients> components = readCoefficientMap(in), owners = readCoefficientMap(in);
		List<String> methods = readStringList(in); double[] confidence = readSix(in), uncertainty = readSix(in); List<String> validity = readStringList(in);
		ReferenceState reference = new ReferenceState(in.readDouble(), in.readDouble(), in.readDouble(), new Coordinate(in.readDouble(), in.readDouble(), in.readDouble()));
		int flagCount = in.readInt(); Set<DiagnosticFlag> flags = new HashSet<>(); for (int i = 0; i < flagCount; i++) flags.add(DiagnosticFlag.valueOf(in.readUTF()));
		int reasonCount = in.readInt(); Set<FailureReason> reasons = new HashSet<>(); for (int i = 0; i < reasonCount; i++) reasons.add(FailureReason.valueOf(in.readUTF()));
		boolean fallback = in.readBoolean(); String method = readNullable(in), reason = readNullable(in); List<String> messages = readStringList(in);
		boolean directlyGenerated = in.readBoolean();
		AerodynamicDerivatives derivatives = new AerodynamicDerivatives(in.readDouble(), in.readDouble(), in.readDouble());
		RuntimeCorrectionData correction = new RuntimeCorrectionData(in.readDouble(), in.readDouble(), in.readDouble(),
				readSix(in), readSix(in), readSix(in), in.readBoolean(), in.readUTF());
		return new TableCell(coefficients, components, owners, methods, confidence, uncertainty, validity, reference,
				new CellDiagnostics(flags, reasons, fallback, method, reason, messages), directlyGenerated, derivatives,
				correction);
	}
	private static AerodynamicCoefficients readCoefficients(DataInputStream in) throws IOException { return new AerodynamicCoefficients(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble()); }
	private static Map<String, AerodynamicCoefficients> readCoefficientMap(DataInputStream in) throws IOException { int n = in.readInt(); Map<String, AerodynamicCoefficients> map = new LinkedHashMap<>(); for (int i = 0; i < n; i++) map.put(in.readUTF(), readCoefficients(in)); return map; }
	private static Map<String, Double> readDoubleMap(DataInputStream in) throws IOException { int n = in.readInt(); Map<String, Double> map = new LinkedHashMap<>(); for (int i = 0; i < n; i++) map.put(in.readUTF(), in.readDouble()); return map; }
	private static List<String> readStringList(DataInputStream in) throws IOException { int n = in.readInt(); List<String> list = new ArrayList<>(n); for (int i = 0; i < n; i++) list.add(in.readUTF()); return list; }
	private static double[] readSix(DataInputStream in) throws IOException { double[] v = new double[6]; for (int i = 0; i < 6; i++) v[i] = in.readDouble(); return v; }
	private static String readNullable(DataInputStream in) throws IOException { return in.readBoolean() ? in.readUTF() : null; }
	private static byte[] sha256(byte[] input) { try { return MessageDigest.getInstance("SHA-256").digest(input); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
