package info.openrocket.core.montecarlo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroFailureOccurrence;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroQueryException.QueryCoordinates;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeFlag;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.montecarlo.MonteCarloMetric;
import info.openrocket.core.simulation.montecarlo.MonteCarloParameter;
import info.openrocket.core.util.Config;

/** Stable, non-Java-serialization codec for the compact .ork Monte Carlo payload. */
public final class MonteCarloAnalysisCodec {
	private static final int MAGIC = 0x4d434131; // MCA1
	private static final int MAX_COLLECTION = 1_000_000;

	private MonteCarloAnalysisCodec() { }

	public static String encode(MonteCarloAnalysis analysis) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
				DataOutputStream out = new DataOutputStream(gzip)) {
			out.writeInt(MAGIC);
			out.writeInt(MonteCarloAnalysis.FORMAT_VERSION);
			out.writeLong(analysis.getCreatedAtEpochMillis());
			writeString(out, analysis.getSourceSimulationName());
			writeString(out, analysis.getSourceConfigurationId());
			out.writeDouble(analysis.getLaunchLatitudeDeg());
			out.writeDouble(analysis.getLaunchLongitudeDeg());
			out.writeDouble(analysis.getLaunchAltitudeM());
			out.writeBoolean(analysis.isUsedPhysicsTable());
			writeString(out, analysis.getPhysicsAeroMode().name());
			writeString(out, analysis.getTableGeometryHash());
			writeString(out, analysis.getTableSettingsHash());
			writeString(out, analysis.getTableContentHash());
			out.writeBoolean(analysis.isStale());
			writeString(out, analysis.getStaleReason());
			writeConfig(out, analysis.getSettings());
			out.writeInt(analysis.getRecords().size());
			for (MonteCarloRunRecord record : analysis.getRecords()) writeRecord(out, record);
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	public static MonteCarloAnalysis decode(String encoded) throws IOException {
		if (encoded == null || encoded.isBlank()) throw new IOException("empty Monte Carlo analysis");
		byte[] bytes;
		try {
			bytes = Base64.getMimeDecoder().decode(encoded.trim());
		} catch (IllegalArgumentException exception) {
			throw new IOException("invalid Monte Carlo analysis encoding", exception);
		}
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
			if (in.readInt() != MAGIC) throw new IOException("unknown Monte Carlo analysis payload");
			int version = in.readInt();
			if (version != MonteCarloAnalysis.FORMAT_VERSION) {
				throw new IOException("unsupported Monte Carlo analysis version " + version);
			}
			long created = in.readLong();
			String name = readString(in);
			String configuration = readString(in);
			double latitude = in.readDouble();
			double longitude = in.readDouble();
			double altitude = in.readDouble();
			boolean useTable = in.readBoolean();
			PhysicsAeroMode mode = enumValue(PhysicsAeroMode.class, readString(in), PhysicsAeroMode.OFF);
			String geometryHash = readString(in);
			String settingsHash = readString(in);
			String contentHash = readString(in);
			boolean stale = in.readBoolean();
			String staleReason = readString(in);
			Config settings = readConfig(in);
			int count = readCount(in, "run count");
			List<MonteCarloRunRecord> records = new ArrayList<>(count);
			for (int i = 0; i < count; i++) records.add(readRecord(in));
			return new MonteCarloAnalysis(created, name, configuration, latitude, longitude,
					altitude, useTable, mode, geometryHash, settingsHash, contentHash,
					settings, records, stale, staleReason);
		} catch (EOFException exception) {
			throw new IOException("truncated Monte Carlo analysis", exception);
		}
	}

	private static void writeRecord(DataOutputStream out, MonteCarloRunRecord r) throws IOException {
		out.writeBoolean(r.nominal);
		out.writeInt(r.runIndex);
		writeString(out, r.simulationName);
		out.writeBoolean(r.deterministicSeed);
		out.writeLong(r.seedUsed);
		out.writeInt(r.simulationSeed);
		out.writeInt(r.masterSeed);
		writeNullableString(out, r.failureMessage);
		writeString(out, r.windDisturbanceSample);

		out.writeDouble(r.windSpeedAverageSigma_mps);
		out.writeDouble(r.windSpeedTurbulenceSigma_mps);
		out.writeBoolean(r.gustEventsEnabled);
		out.writeBoolean(r.shearLayerEnabled);
		out.writeInt(r.gustEventCountConfigured);
		for (double value : new double[] { r.gustWindowStart_s, r.gustWindowEnd_s,
				r.gustDurationMean_s, r.gustDurationSigma_s, r.gustPeakDeltaMean_mps,
				r.gustPeakDeltaSigma_mps, r.shearCenterAlt_m, r.shearThickness_m,
				r.shearDeltaMean_mps, r.shearDeltaSigma_mps }) out.writeDouble(value);
		out.writeInt(r.gustEventCountRealized);
		for (double value : new double[] { r.gustMaxDeltaWind_mps, r.shearDeltaApplied_mps,
				r.deltaWindImpulse_mps_s, r.maxTilt_deg, r.maxAoA_deg,
				r.cdMultiplierSigma, r.thrustMultiplierSigma, r.massMultiplierSigma,
				r.cdMultiplierUsed, r.thrustMultiplierUsed, r.massMultiplierUsed }) out.writeDouble(value);

		writeString(out, r.windModelType);
		out.writeInt(r.windLevels.size());
		for (MonteCarloRunRecord.WindLevel level : r.windLevels) {
			out.writeDouble(level.altitudeM); out.writeDouble(level.speedMps);
			out.writeDouble(level.directionRad); out.writeDouble(level.stdDevMps);
		}
		for (double value : new double[] { r.apogee_m, r.maxVelocity_mps,
				r.maxAcceleration_mps2, r.flightTime_s, r.landingEast_m, r.landingNorth_m,
				r.landingLat_deg, r.landingLon_deg, r.launchLatitudeDeg,
				r.launchLongitudeDeg, r.launchAltitudeM, r.launchRodAngleRad,
				r.launchRodDirectionRad, r.launchTemperatureK, r.launchPressurePa }) out.writeDouble(value);

		SimulationData data = r.results == null ? new SimulationData() : r.results;
		for (double value : new double[] { data.apogee_m, data.apogeeTime_s, data.landingTime_s,
				data.landingEast_m, data.landingNorth_m, data.landingLat_deg, data.landingLon_deg,
				data.landingDownrange_m, data.landingCrossrange_m, data.maxVelocity_mps,
				data.maxAcceleration_mps2, data.flightTime_s, data.launchLat_deg,
				data.launchLon_deg, data.launchRodDirection_deg }) out.writeDouble(value);
		out.writeBoolean(data.hasLanding); out.writeBoolean(data.hasApogee);
		writeString(out, data.simulationName);

		writeParameterDoubles(out, r.sampledVariations);
		out.writeInt(r.uncertaintySettings.size());
		for (var entry : r.uncertaintySettings.entrySet()) {
			writeString(out, entry.getKey().name()); writeString(out, entry.getValue());
		}
		out.writeInt(r.bodyResults.size());
		for (MonteCarloRunRecord.BodyResult body : r.bodyResults) {
			writeString(out, body.bodyId); out.writeInt(body.branchIndex);
			writeString(out, body.branchName); out.writeBoolean(body.groundHit);
			out.writeDouble(body.eastM); out.writeDouble(body.northM);
			out.writeDouble(body.latitudeDeg); out.writeDouble(body.longitudeDeg);
			writeNullableString(out, body.failureMessage);
			out.writeInt(body.metrics.size());
			for (var entry : body.metrics.entrySet()) {
				writeString(out, entry.getKey().name()); out.writeDouble(entry.getValue());
			}
		}
		writeRuntimeReport(out, r.physicsAeroRuntimeReport);
	}

	private static MonteCarloRunRecord readRecord(DataInputStream in) throws IOException {
		boolean nominal = in.readBoolean();
		int runIndex = in.readInt();
		String simulationName = readString(in);
		boolean deterministic = in.readBoolean();
		long seed = in.readLong();
		int simulationSeed = in.readInt();
		int masterSeed = in.readInt();
		String failure = readNullableString(in);
		String windAudit = readString(in);
		double windSigma = in.readDouble();
		double turbulenceSigma = in.readDouble();
		boolean gustEnabled = in.readBoolean();
		boolean shearEnabled = in.readBoolean();
		int gustCountConfigured = in.readInt();
		double[] config = readDoubles(in, 10);
		int gustCountRealized = in.readInt();
		double[] realized = readDoubles(in, 11);
		String windModelType = readString(in);
		int windCount = readCount(in, "wind level count");
		List<MonteCarloRunRecord.WindLevel> windLevels = new ArrayList<>(windCount);
		for (int i = 0; i < windCount; i++) {
			windLevels.add(new MonteCarloRunRecord.WindLevel(in.readDouble(), in.readDouble(),
					in.readDouble(), in.readDouble()));
		}
		double[] scalar = readDoubles(in, 15);
		double[] storedData = readDoubles(in, 15);
		SimulationData data = new SimulationData();
		data.apogee_m = storedData[0]; data.apogeeTime_s = storedData[1];
		data.landingTime_s = storedData[2]; data.landingEast_m = storedData[3];
		data.landingNorth_m = storedData[4]; data.landingLat_deg = storedData[5];
		data.landingLon_deg = storedData[6]; data.landingDownrange_m = storedData[7];
		data.landingCrossrange_m = storedData[8]; data.maxVelocity_mps = storedData[9];
		data.maxAcceleration_mps2 = storedData[10]; data.flightTime_s = storedData[11];
		data.launchLat_deg = storedData[12]; data.launchLon_deg = storedData[13];
		data.launchRodDirection_deg = storedData[14];
		data.hasLanding = in.readBoolean(); data.hasApogee = in.readBoolean();
		data.simulationName = readString(in);

		SimulationOptions options = new SimulationOptions();
		options.setLaunchLatitude(scalar[8]); options.setLaunchLongitude(scalar[9]);
		options.setLaunchAltitude(scalar[10]); options.setLaunchRodAngle(scalar[11]);
		options.setLaunchRodDirection(scalar[12]); options.setLaunchTemperature(scalar[13]);
		options.setLaunchPressure(scalar[14]);
		if (!windLevels.isEmpty()) {
			var first = windLevels.get(0);
			options.getAverageWindModel().setAverage(Math.max(0, first.speedMps));
			options.getAverageWindModel().setDirection(first.directionRad);
			options.getAverageWindModel().setStandardDeviation(Math.max(0, first.stdDevMps));
		}
		MonteCarloRunRecord r = new MonteCarloRunRecord(runIndex, simulationName, deterministic,
				seed, windSigma, turbulenceSigma, gustEnabled, shearEnabled, gustCountConfigured,
				config[0], config[1], config[2], config[3], config[4], config[5], config[6],
				config[7], config[8], config[9], gustCountRealized, realized[0], realized[1],
				realized[2], realized[3], realized[4], realized[5], realized[6], realized[7],
				realized[8], realized[9], realized[10], options, data);
		r.nominal = nominal; r.simulationSeed = simulationSeed; r.masterSeed = masterSeed;
		r.failureMessage = failure; r.windDisturbanceSample = windAudit;
		r.windModelType = windModelType;
		r.windLevels.clear(); r.windLevels.addAll(windLevels);
		r.sampledVariations = readParameterDoubles(in);
		int uncertaintyCount = readCount(in, "uncertainty count");
		EnumMap<MonteCarloParameter, String> uncertainty = new EnumMap<>(MonteCarloParameter.class);
		for (int i = 0; i < uncertaintyCount; i++) {
			MonteCarloParameter key = enumValue(MonteCarloParameter.class, readString(in), null);
			String value = readString(in); if (key != null) uncertainty.put(key, value);
		}
		r.uncertaintySettings = Map.copyOf(uncertainty);
		int bodyCount = readCount(in, "body result count");
		List<MonteCarloRunRecord.BodyResult> bodies = new ArrayList<>(bodyCount);
		for (int i = 0; i < bodyCount; i++) {
			String id = readString(in); int branchIndex = in.readInt(); String branchName = readString(in);
			boolean groundHit = in.readBoolean(); double east = in.readDouble(); double north = in.readDouble();
			double lat = in.readDouble(); double lon = in.readDouble(); String bodyFailure = readNullableString(in);
			int metricCount = readCount(in, "metric count");
			EnumMap<MonteCarloMetric, Double> metrics = new EnumMap<>(MonteCarloMetric.class);
			for (int j = 0; j < metricCount; j++) {
				MonteCarloMetric metric = enumValue(MonteCarloMetric.class, readString(in), null);
				double value = in.readDouble(); if (metric != null) metrics.put(metric, value);
			}
			bodies.add(new MonteCarloRunRecord.BodyResult(id, branchIndex, branchName, groundHit,
					east, north, lat, lon, bodyFailure, metrics));
		}
		r.bodyResults = List.copyOf(bodies);
		r.physicsAeroRuntimeReport = readRuntimeReport(in);
		return r;
	}

	private static void writeRuntimeReport(DataOutputStream out, PhysicsAeroRuntimeReport report) throws IOException {
		PhysicsAeroRuntimeReport r = report == null ? PhysicsAeroRuntimeReport.disabled() : report;
		out.writeBoolean(r.tableValid()); writeString(out, r.schemaVersion());
		writeString(out, r.geometryHash()); writeString(out, r.settingsHash());
		writeString(out, r.contentHash()); writeString(out, r.certificationState());
		out.writeLong(r.totalQueries()); out.writeLong(r.successfulTableQueries());
		out.writeLong(r.interpolatedQueries()); out.writeLong(r.reynoldsCorrectedQueries());
		out.writeLong(r.fallbackCount()); out.writeDouble(r.lowestConfidence());
		out.writeInt(r.failureCounts().size());
		for (var entry : r.failureCounts().entrySet()) {
			writeString(out, entry.getKey().name()); out.writeLong(entry.getValue());
		}
		out.writeInt(r.runtimeFlags().size());
		for (PhysicsAeroRuntimeFlag flag : r.runtimeFlags()) writeString(out, flag.name());
		out.writeInt(r.firstOccurrences().size());
		for (var entry : r.firstOccurrences().entrySet()) {
			writeString(out, entry.getKey().name());
			PhysicsAeroFailureOccurrence occurrence = entry.getValue();
			out.writeDouble(occurrence.simulationTimeSeconds());
			QueryCoordinates c = occurrence.coordinates();
			out.writeDouble(c.mach()); out.writeDouble(c.alphaRad()); out.writeDouble(c.betaRad());
			out.writeDouble(c.poweredFraction()); out.writeDouble(c.reynoldsNumber());
			writeString(out, occurrence.detail());
		}
	}

	private static PhysicsAeroRuntimeReport readRuntimeReport(DataInputStream in) throws IOException {
		boolean valid = in.readBoolean(); String schema = readString(in); String geometry = readString(in);
		String settings = readString(in); String content = readString(in); String certification = readString(in);
		long total = in.readLong(); long success = in.readLong(); long interpolated = in.readLong();
		long reynolds = in.readLong(); long fallback = in.readLong(); double confidence = in.readDouble();
		EnumMap<FailureReason, Long> failures = new EnumMap<>(FailureReason.class);
		int failureCount = readCount(in, "runtime failure count");
		for (int i = 0; i < failureCount; i++) {
			FailureReason reason = enumValue(FailureReason.class, readString(in), null);
			long count = in.readLong(); if (reason != null) failures.put(reason, count);
		}
		Set<PhysicsAeroRuntimeFlag> flags = EnumSet.noneOf(PhysicsAeroRuntimeFlag.class);
		int flagCount = readCount(in, "runtime flag count");
		for (int i = 0; i < flagCount; i++) {
			PhysicsAeroRuntimeFlag flag = enumValue(PhysicsAeroRuntimeFlag.class, readString(in), null);
			if (flag != null) flags.add(flag);
		}
		EnumMap<FailureReason, PhysicsAeroFailureOccurrence> occurrences = new EnumMap<>(FailureReason.class);
		int occurrenceCount = readCount(in, "runtime occurrence count");
		for (int i = 0; i < occurrenceCount; i++) {
			FailureReason reason = enumValue(FailureReason.class, readString(in), null);
			double time = in.readDouble(); QueryCoordinates coordinates = new QueryCoordinates(
					in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
			String detail = readString(in);
			if (reason != null) occurrences.put(reason, new PhysicsAeroFailureOccurrence(time, coordinates, detail));
		}
		return new PhysicsAeroRuntimeReport(valid, schema, geometry, settings, content, certification,
				total, success, interpolated, reynolds, fallback, failures, confidence, flags, occurrences);
	}

	private static void writeParameterDoubles(DataOutputStream out,
			Map<MonteCarloParameter, Double> values) throws IOException {
		out.writeInt(values.size());
		for (var entry : values.entrySet()) {
			writeString(out, entry.getKey().name()); out.writeDouble(entry.getValue());
		}
	}

	private static Map<MonteCarloParameter, Double> readParameterDoubles(DataInputStream in) throws IOException {
		int count = readCount(in, "variation count");
		EnumMap<MonteCarloParameter, Double> values = new EnumMap<>(MonteCarloParameter.class);
		for (int i = 0; i < count; i++) {
			MonteCarloParameter key = enumValue(MonteCarloParameter.class, readString(in), null);
			double value = in.readDouble(); if (key != null) values.put(key, value);
		}
		return Map.copyOf(values);
	}

	private static void writeConfig(DataOutputStream out, Config config) throws IOException {
		out.writeInt(config.keySet().size());
		for (String key : config.keySet()) {
			writeString(out, key); writeConfigValue(out, config.get(key, ""));
		}
	}

	private static Config readConfig(DataInputStream in) throws IOException {
		Config config = new Config(); int count = readCount(in, "settings count");
		for (int i = 0; i < count; i++) config.put(readString(in), readConfigValue(in));
		return config;
	}

	private static void writeConfigValue(DataOutputStream out, Object value) throws IOException {
		if (value instanceof Boolean b) { out.writeByte(1); out.writeBoolean(b); }
		else if (value instanceof Number n) { out.writeByte(2); writeString(out, n.toString()); }
		else if (value instanceof String s) { out.writeByte(3); writeString(out, s); }
		else if (value instanceof List<?> list) {
			out.writeByte(4); out.writeInt(list.size());
			for (Object item : list) writeConfigValue(out, item);
		} else throw new IOException("unsupported Monte Carlo setting type " + value.getClass());
	}

	private static Object readConfigValue(DataInputStream in) throws IOException {
		return switch (in.readUnsignedByte()) {
			case 1 -> in.readBoolean();
			case 2 -> new java.math.BigDecimal(readString(in));
			case 3 -> readString(in);
			case 4 -> {
				int count = readCount(in, "settings list count"); List<Object> values = new ArrayList<>(count);
				for (int i = 0; i < count; i++) values.add(readConfigValue(in)); yield values;
			}
			default -> throw new IOException("unknown Monte Carlo setting value type");
		};
	}

	private static void writeNullableString(DataOutputStream out, String value) throws IOException {
		out.writeBoolean(value != null); if (value != null) writeString(out, value);
	}

	private static String readNullableString(DataInputStream in) throws IOException {
		return in.readBoolean() ? readString(in) : null;
	}

	private static void writeString(DataOutputStream out, String value) throws IOException {
		byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length); out.write(bytes);
	}

	private static String readString(DataInputStream in) throws IOException {
		int length = in.readInt();
		if (length < 0 || length > 64 * 1024 * 1024) throw new IOException("invalid string length " + length);
		return new String(in.readNBytes(length), StandardCharsets.UTF_8);
	}

	private static int readCount(DataInputStream in, String label) throws IOException {
		int count = in.readInt();
		if (count < 0 || count > MAX_COLLECTION) throw new IOException("invalid " + label + " " + count);
		return count;
	}

	private static double[] readDoubles(DataInputStream in, int count) throws IOException {
		double[] values = new double[count]; for (int i = 0; i < count; i++) values[i] = in.readDouble();
		return values;
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
		try { return Enum.valueOf(type, name); } catch (RuntimeException exception) { return fallback; }
	}
}
