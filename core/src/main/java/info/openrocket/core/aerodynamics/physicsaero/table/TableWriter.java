package info.openrocket.core.aerodynamics.physicsaero.table;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;

/** Deterministic binary writer with compressed payload and SHA-256 checksum. */
public final class TableWriter {
	static final byte[] MAGIC = "OPRAERO1".getBytes(StandardCharsets.US_ASCII);
	public void write(AerodynamicTable table, Path binary, Path manifest) throws IOException {
		byte[] payload = payload(table), compressed = compress(payload), digest = sha256(compressed);
		Path parent = binary.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, binary.getFileName().toString(), ".tmp");
		try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
			out.write(MAGIC); out.writeInt(compressed.length); out.write(digest); out.write(compressed);
		}
		moveAtomic(temp, binary);
		String json = new GsonBuilder().registerTypeAdapter(Instant.class,
				(com.google.gson.JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
				.setPrettyPrinting().disableHtmlEscaping().create().toJson(table.metadata()) + System.lineSeparator();
		Path manifestParent = manifest.toAbsolutePath().getParent(); if (manifestParent != null) Files.createDirectories(manifestParent);
		Path manifestTemp = Files.createTempFile(manifestParent, manifest.getFileName().toString(), ".tmp");
		Files.writeString(manifestTemp, json, StandardCharsets.UTF_8); moveAtomic(manifestTemp, manifest);
	}
	private static byte[] payload(AerodynamicTable t) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			writeAxis(out, t.axes().mach()); writeAxis(out, t.axes().alphaRad()); writeAxis(out, t.axes().betaRad());
			writeMetadata(out, t.metadata()); out.writeInt(t.cells().size());
			for (TableCell c : t.cells()) writeCell(out, c);
		}
		return bytes.toByteArray();
	}
	private static void writeAxis(DataOutputStream out, double[] axis) throws IOException { out.writeInt(axis.length); for (double v : axis) out.writeLong(Double.doubleToLongBits(v == 0 ? 0 : v)); }
	private static void writeMetadata(DataOutputStream out, TableMetadata m) throws IOException {
		writeStrings(out, m.schemaVersion(), m.geometryHash(), m.settingsHash(), m.codeVersion(), m.correlationRegistryVersion(),
				m.units(), m.axisConvention(), m.createdAt().toString(), m.validationStatus());
		writeDoubleMap(out, m.references()); writeDoubleMap(out, m.tolerances());
	}
	private static void writeCell(DataOutputStream out, TableCell c) throws IOException {
		writeCoefficients(out, c.coefficients()); writeCoefficientMap(out, c.componentTotals()); writeCoefficientMap(out, c.ownerTotals());
		writeStringList(out, c.methodIds()); for (double v : c.confidence()) out.writeDouble(v); for (double v : c.uncertainty()) out.writeDouble(v);
		writeStringList(out, c.validityFlags()); ReferenceState r = c.referenceState();
		out.writeDouble(r.dynamicPressurePa()); out.writeDouble(r.referenceAreaM2()); out.writeDouble(r.referenceLengthM());
		out.writeDouble(r.momentOriginM().x); out.writeDouble(r.momentOriginM().y); out.writeDouble(r.momentOriginM().z);
		out.writeInt(c.diagnostics().flags().size()); for (var f : c.diagnostics().flags().stream().sorted().toList()) out.writeUTF(f.name());
		out.writeBoolean(c.diagnostics().fallback()); writeNullable(out, c.diagnostics().fallbackMethodId()); writeNullable(out, c.diagnostics().fallbackReasonCode());
		writeStringList(out, c.diagnostics().messages()); out.writeBoolean(c.directlyGenerated());
	}
	private static void writeCoefficients(DataOutputStream out, AerodynamicCoefficients c) throws IOException { for (double v : c.toArray()) out.writeDouble(v); }
	private static void writeCoefficientMap(DataOutputStream out, Map<String, AerodynamicCoefficients> map) throws IOException {
		out.writeInt(map.size()); for (var e : map.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { out.writeUTF(e.getKey()); writeCoefficients(out, e.getValue()); }
	}
	private static void writeDoubleMap(DataOutputStream out, Map<String, Double> map) throws IOException {
		out.writeInt(map.size()); for (var e : map.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { out.writeUTF(e.getKey()); out.writeDouble(e.getValue()); }
	}
	private static void writeStringList(DataOutputStream out, List<String> list) throws IOException { out.writeInt(list.size()); for (String v : list) out.writeUTF(v); }
	private static void writeStrings(DataOutputStream out, String... values) throws IOException { for (String v : values) out.writeUTF(v); }
	private static void writeNullable(DataOutputStream out, String v) throws IOException { out.writeBoolean(v != null); if (v != null) out.writeUTF(v); }
	private static byte[] compress(byte[] input) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); try (DeflaterOutputStream deflater = new DeflaterOutputStream(out)) { deflater.write(input); } return out.toByteArray(); }
	private static byte[] sha256(byte[] input) { try { return MessageDigest.getInstance("SHA-256").digest(input); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
	private static void moveAtomic(Path source, Path target) throws IOException { try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); } }
}
