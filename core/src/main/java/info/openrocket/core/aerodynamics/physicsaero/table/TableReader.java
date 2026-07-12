package info.openrocket.core.aerodynamics.physicsaero.table;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.InflaterInputStream;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.util.Coordinate;

public final class TableReader {
	public AerodynamicTable read(Path file) throws IOException {
		try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
			if (!Arrays.equals(in.readNBytes(TableWriter.MAGIC.length), TableWriter.MAGIC)) throw new IOException("invalid table magic");
			int length = in.readInt(); byte[] expected = in.readNBytes(32), compressed = in.readNBytes(length);
			if (compressed.length != length || !Arrays.equals(expected, sha256(compressed))) throw new IOException("table checksum mismatch");
			try (DataInputStream payload = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
				TableAxes axes = new TableAxes(readAxis(payload), readAxis(payload), readAxis(payload)); TableMetadata metadata = readMetadata(payload);
				int count = payload.readInt(); List<TableCell> cells = new ArrayList<>(count); for (int i = 0; i < count; i++) cells.add(readCell(payload));
				return new AerodynamicTable(axes, cells, metadata);
			}
		}
	}
	private static double[] readAxis(DataInputStream in) throws IOException { double[] v = new double[in.readInt()]; for (int i = 0; i < v.length; i++) v[i] = in.readDouble(); return v; }
	private static TableMetadata readMetadata(DataInputStream in) throws IOException {
		String schema = in.readUTF(), geometry = in.readUTF(), settings = in.readUTF(), code = in.readUTF(), registry = in.readUTF();
		String units = in.readUTF(), convention = in.readUTF(); Instant created = Instant.parse(in.readUTF()); String status = in.readUTF();
		return new TableMetadata(schema, geometry, settings, code, registry, units, convention, created, readDoubleMap(in), readDoubleMap(in), status);
	}
	private static TableCell readCell(DataInputStream in) throws IOException {
		AerodynamicCoefficients coefficients = readCoefficients(in); Map<String, AerodynamicCoefficients> components = readCoefficientMap(in), owners = readCoefficientMap(in);
		List<String> methods = readStringList(in); double[] confidence = readSix(in), uncertainty = readSix(in); List<String> validity = readStringList(in);
		ReferenceState reference = new ReferenceState(in.readDouble(), in.readDouble(), in.readDouble(), new Coordinate(in.readDouble(), in.readDouble(), in.readDouble()));
		int flagCount = in.readInt(); Set<DiagnosticFlag> flags = new HashSet<>(); for (int i = 0; i < flagCount; i++) flags.add(DiagnosticFlag.valueOf(in.readUTF()));
		boolean fallback = in.readBoolean(); String method = readNullable(in), reason = readNullable(in); List<String> messages = readStringList(in);
		return new TableCell(coefficients, components, owners, methods, confidence, uncertainty, validity, reference,
				new CellDiagnostics(flags, fallback, method, reason, messages), in.readBoolean());
	}
	private static AerodynamicCoefficients readCoefficients(DataInputStream in) throws IOException { return new AerodynamicCoefficients(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble()); }
	private static Map<String, AerodynamicCoefficients> readCoefficientMap(DataInputStream in) throws IOException { int n = in.readInt(); Map<String, AerodynamicCoefficients> map = new LinkedHashMap<>(); for (int i = 0; i < n; i++) map.put(in.readUTF(), readCoefficients(in)); return map; }
	private static Map<String, Double> readDoubleMap(DataInputStream in) throws IOException { int n = in.readInt(); Map<String, Double> map = new LinkedHashMap<>(); for (int i = 0; i < n; i++) map.put(in.readUTF(), in.readDouble()); return map; }
	private static List<String> readStringList(DataInputStream in) throws IOException { int n = in.readInt(); List<String> list = new ArrayList<>(n); for (int i = 0; i < n; i++) list.add(in.readUTF()); return list; }
	private static double[] readSix(DataInputStream in) throws IOException { double[] v = new double[6]; for (int i = 0; i < 6; i++) v[i] = in.readDouble(); return v; }
	private static String readNullable(DataInputStream in) throws IOException { return in.readBoolean() ? in.readUTF() : null; }
	private static byte[] sha256(byte[] input) { try { return MessageDigest.getInstance("SHA-256").digest(input); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
