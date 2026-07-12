package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class GeometryHasher {
	private GeometryHasher() {}
	public static String hash(AeroGeometry geometry, String settingsFingerprint) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream out = new DataOutputStream(bytes)) {
				out.writeUTF(settingsFingerprint);
				for (AeroComponent c : geometry.components().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList()) {
					out.writeUTF(c.id()); out.writeUTF(c.sourcePath()); out.writeUTF(c.type()); out.writeUTF(c.classification());
					writeDouble(out, c.axialStartM()); writeDouble(out, c.axialEndM()); writeDouble(out, c.rootRadiusM());
					writeDouble(out, c.wettedAreaM2()); writeDouble(out, c.projectedAreaM2()); writeDouble(out, c.baseAreaM2());
					writeDouble(out, c.roughnessM()); out.writeUTF(c.wallTemperatureModelId());
					writeDouble(out, c.originM().x); writeDouble(out, c.originM().y); writeDouble(out, c.originM().z);
					out.writeUTF(c.parentStageId()); out.writeInt(c.axialOrder());
					c.localReferences().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> {
						try { out.writeUTF(e.getKey()); writeDouble(out, e.getValue()); } catch (IOException ex) { throw new HashFailure(ex); }
					});
					out.writeInt(c.eligibleCorrelationIds().size());
					for (String id : c.eligibleCorrelationIds().stream().sorted().toList()) out.writeUTF(id);
					writeProfile(out, c.axisymmetricProfile());
					writeFin(out, c.finGeometry());
					writeProtuberance(out, c.protuberanceGeometry());
				}
				ReferenceGeometry r = geometry.references();
				writeDouble(out, r.referenceAreaM2()); writeDouble(out, r.exposedBaseAreaM2());
				writeDouble(out, r.vehicleLengthM()); writeDouble(out, r.maximumBodyDiameterM()); writeDouble(out, r.referenceLengthM());
				writeDouble(out, r.momentOriginM().x); writeDouble(out, r.momentOriginM().y); writeDouble(out, r.momentOriginM().z);
				for (var e : r.wettedAreaByComponentM2().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
					out.writeUTF(e.getKey()); writeDouble(out, e.getValue());
				}
			}
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
		} catch (IOException | NoSuchAlgorithmException | HashFailure e) { throw new IllegalStateException("cannot hash geometry", e); }
	}
	private static void writeDouble(DataOutputStream out, double value) throws IOException {
		double canonical = value == 0 ? 0 : value;
		if (!Double.isFinite(canonical)) throw new IllegalArgumentException("non-finite hash input");
		out.writeLong(Double.doubleToLongBits(canonical));
	}
	private static void writeProfile(DataOutputStream out, AxisymmetricProfile profile) throws IOException {
		out.writeBoolean(profile != null); if (profile == null) return;
		out.writeUTF(profile.smoothingAlgorithm()); writeDouble(out, profile.smoothingToleranceM()); out.writeInt(profile.stations().size());
		for (GeometryStation s : profile.stations()) { writeDouble(out, s.xM()); writeDouble(out, s.radiusM()); writeDouble(out, s.slope()); writeDouble(out, s.secondDerivative()); }
		out.writeInt(profile.events().size()); for (GeometryEvent event : profile.events()) { writeDouble(out, event.xM()); out.writeUTF(event.type().name()); out.writeUTF(event.componentId()); }
	}
	private static void writeFin(DataOutputStream out, FinGeometry fin) throws IOException {
		out.writeBoolean(fin != null); if (fin == null) return;
		out.writeUTF(fin.planform()); out.writeUTF(fin.section()); out.writeInt(fin.count()); writeDouble(out, fin.rootChordM());
		writeDouble(out, fin.spanM()); writeDouble(out, fin.planformAreaM2()); writeDouble(out, fin.cantRad());
	}
	private static void writeProtuberance(DataOutputStream out, ProtuberanceGeometry p) throws IOException {
		out.writeBoolean(p != null); if (p == null) return;
		out.writeUTF(p.type()); writeDouble(out, p.lengthM()); writeDouble(out, p.projectedAreaM2()); writeDouble(out, p.frontalAreaM2());
	}
	private static final class HashFailure extends RuntimeException { HashFailure(Throwable cause) { super(cause); } }
}
