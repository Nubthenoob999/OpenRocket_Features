package info.openrocket.core.tuning;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class TelemetryParsers {
	private static final double STANDARD_GRAVITY = 9.80665;
	private static final double FEET_TO_METERS = 0.3048;
	private static final double FLUCTUS_ALTITUDE_MAX_M = 200_000.0;
	private static final double FLUCTUS_VELOCITY_MAX_MPS = 5_000.0;
	private static final double FLUCTUS_ACCEL_MAX_MPS2 = 2_000.0;
	private static final double FLUCTUS_TEMPERATURE_MIN_C = -150.0;
	private static final double FLUCTUS_TEMPERATURE_MAX_C = 200.0;
	private static final double ALTITUDE_MIN_M = -1_000.0;
	private static final double ALTITUDE_MAX_M = 200_000.0;
	private static final double VELOCITY_MAX_MPS = 5_000.0;
	private static final double ACCEL_MAX_MPS2 = 2_000.0;
	private static final double TEMPERATURE_MIN_C = -150.0;
	private static final double TEMPERATURE_MAX_C = 200.0;

	private TelemetryParsers() {
	}

	public static TelemetrySeries parse(Path path) throws IOException {
		TelemetrySchema schema = detectSchema(path);
		switch (schema) {
			case FLUCTUS_SEMICOLON:
				return parseFluctus(path);
			case EASYMINI_ALTIMETER:
				return parseEasyMini(path);
			case STRATOLOGGER_COMMA:
				return parseStratologgerComma(path);
			case TAB_DELIMITED_ALTIMETER:
				return parseTabDelimited(path);
			case AB_IMU_INTERLEAVED:
				return parseAbInterleaved(path, schema);
			case AB_EXTENDED:
				return parseAbInterleaved(path, schema);
			default:
				throw new IllegalStateException("Unsupported schema: " + schema);
		}
	}

	public static TelemetrySchema detectSchema(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String first = reader.readLine();
			if (first == null) {
				throw new IllegalArgumentException("Telemetry file is empty: " + path);
			}
			if (first.startsWith("sep=;")) {
				return TelemetrySchema.FLUCTUS_SEMICOLON;
			}
			if (first.startsWith("#version")) {
				return TelemetrySchema.EASYMINI_ALTIMETER;
			}
			if (first.contains("Time (s)") && first.contains("Altitude (ft)")) {
				return TelemetrySchema.STRATOLOGGER_COMMA;
			}
			if (first.contains("state_letter") && first.contains("set_extension")) {
				if (first.contains("timestamp_seconds") || first.contains("est_position_z_meters")) {
					return TelemetrySchema.AB_EXTENDED;
				}
				return TelemetrySchema.AB_IMU_INTERLEAVED;
			}
			if (first.contains("\t")) {
				return TelemetrySchema.TAB_DELIMITED_ALTIMETER;
			}
			throw new IllegalArgumentException("Unable to detect telemetry schema for file: " + path);
		}
	}

	private static TelemetrySeries parseEasyMini(Path path) throws IOException {
		TelemetrySeries series = new TelemetrySeries(TelemetrySchema.EASYMINI_ALTIMETER);
		TelemetryParserDiagnostics.Mutable diagnostics = new TelemetryParserDiagnostics.Mutable(TelemetrySchema.EASYMINI_ALTIMETER.name());
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line = reader.readLine();
			if (line == null) {
				return series;
			}
			while ((line = reader.readLine()) != null) {
				diagnostics.incRowsRead();
				String[] tokens = splitCsv(line);
				if (tokens.length < 12) {
					diagnostics.incOutlierDrops();
					continue;
				}
				Double time = parseDouble(tokens[3]);
				Double altitude = parseDouble(tokens[9]);
				Double velocity = parseDouble(tokens[10]);
				Double accelZ = parseDouble(tokens[6]);
				Double pressure = parseDouble(tokens[7]);
				Double temperature = parseDouble(tokens[11]);

				altitude = sanitizeRange(altitude, ALTITUDE_MIN_M, ALTITUDE_MAX_M, diagnostics);
				velocity = sanitizeRange(velocity, -VELOCITY_MAX_MPS, VELOCITY_MAX_MPS, diagnostics);
				accelZ = sanitizeRange(accelZ, -ACCEL_MAX_MPS2, ACCEL_MAX_MPS2, diagnostics);
				temperature = sanitizeRange(temperature, TEMPERATURE_MIN_C, TEMPERATURE_MAX_C, diagnostics);
				if (time == null) {
					diagnostics.incOutlierDrops();
					continue;
				}
				series.addPoint(time, altitude, velocity, null, null, accelZ, pressure, temperature);
				diagnostics.incRowsAccepted();
			}
		}
		series.setParserDiagnostics(diagnostics.freeze());
		return series;
	}

	private static TelemetrySeries parseFluctus(Path path) throws IOException {
		TelemetrySeries series = new TelemetrySeries(TelemetrySchema.FLUCTUS_SEMICOLON);
		TelemetryParserDiagnostics.Mutable diagnostics = new TelemetryParserDiagnostics.Mutable(TelemetrySchema.FLUCTUS_SEMICOLON.name());
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String separatorLine = reader.readLine();
			if (separatorLine == null) {
				return series;
			}
			String header = reader.readLine();
			if (header == null) {
				return series;
			}

			Map<String, Integer> columns = parseColumns(header, ";");
			String line;
			Double firstTimeSec = null;
			while ((line = reader.readLine()) != null) {
				diagnostics.incRowsRead();
				String[] tokens = line.split(";", -1);
				Double timeMillis = readByNames(tokens, columns, "time (ms)");
				timeMillis = dropSentinel(timeMillis, diagnostics);
				if (timeMillis == null) {
					diagnostics.incOutlierDrops();
					continue;
				}
				double timeSec = timeMillis / 1000.0;
				if (firstTimeSec == null) {
					firstTimeSec = timeSec;
				}
				timeSec -= firstTimeSec;

				Double altitude = readByNames(tokens, columns, "dedrck-alti (m)", "baro-altitude (m)");
				Double velocity = readByNames(tokens, columns, "dedrck-v-speed (m/s)", "baro-speed (m/s)");
				Double accelZ = readByNames(tokens, columns, "vert-accel (m/s2)");
				Double temperature = readByNames(tokens, columns, "amb-temp (deg c)");

				altitude = sanitizeRange(dropSentinel(altitude, diagnostics), ALTITUDE_MIN_M, FLUCTUS_ALTITUDE_MAX_M, diagnostics);
				velocity = sanitizeRange(dropSentinel(velocity, diagnostics), -FLUCTUS_VELOCITY_MAX_MPS, FLUCTUS_VELOCITY_MAX_MPS, diagnostics);
				accelZ = sanitizeRange(dropSentinel(accelZ, diagnostics), -FLUCTUS_ACCEL_MAX_MPS2, FLUCTUS_ACCEL_MAX_MPS2, diagnostics);
				temperature = sanitizeRange(dropSentinel(temperature, diagnostics), FLUCTUS_TEMPERATURE_MIN_C, FLUCTUS_TEMPERATURE_MAX_C, diagnostics);

				series.addPoint(timeSec, altitude, velocity, null, null, accelZ, null, temperature);
				diagnostics.incRowsAccepted();
			}
		}
		series.setParserDiagnostics(diagnostics.freeze());
		return series;
	}

	private static TelemetrySeries parseTabDelimited(Path path) throws IOException {
		TelemetrySeries series = new TelemetrySeries(TelemetrySchema.TAB_DELIMITED_ALTIMETER);
		TelemetryParserDiagnostics.Mutable diagnostics = new TelemetryParserDiagnostics.Mutable(TelemetrySchema.TAB_DELIMITED_ALTIMETER.name());
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				diagnostics.incRowsRead();
				String trimmed = stripWrappingQuotes(line.trim());
				if (trimmed.isEmpty()) {
					diagnostics.incOutlierDrops();
					continue;
				}
				String[] tokens = trimmed.split("\\t");
				if (tokens.length < 4) {
					diagnostics.incOutlierDrops();
					continue;
				}
				Double time = parseDouble(tokens[0]);
				Double altitudeFeet = parseDouble(tokens[1]);
				Double altitudeMeters = altitudeFeet == null ? null : altitudeFeet * FEET_TO_METERS;
				if (altitudeFeet != null) {
					diagnostics.incUnitCorrections();
				}
				Double temperatureC = parseFahrenheit(tokens[2]);
				if (temperatureC != null) {
					diagnostics.incUnitCorrections();
				}
				altitudeMeters = sanitizeRange(altitudeMeters, ALTITUDE_MIN_M, ALTITUDE_MAX_M, diagnostics);
				temperatureC = sanitizeRange(temperatureC, TEMPERATURE_MIN_C, TEMPERATURE_MAX_C, diagnostics);
				if (time == null) {
					diagnostics.incOutlierDrops();
					continue;
				}
				series.addPoint(time, altitudeMeters, null, null, null, null, null, temperatureC);
				diagnostics.incRowsAccepted();
			}
		}
		series.setParserDiagnostics(diagnostics.freeze());
		return series;
	}

	private static String stripWrappingQuotes(String value) {
		if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static TelemetrySeries parseStratologgerComma(Path path) throws IOException {
		TelemetrySeries series = new TelemetrySeries(TelemetrySchema.STRATOLOGGER_COMMA);
		TelemetryParserDiagnostics.Mutable diagnostics = new TelemetryParserDiagnostics.Mutable(TelemetrySchema.STRATOLOGGER_COMMA.name());
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String header = reader.readLine();
			if (header == null) {
				return series;
			}
			Map<String, Integer> columns = parseColumns(header, ",");
			String line;
			while ((line = reader.readLine()) != null) {
				diagnostics.incRowsRead();
				String[] tokens = splitCsv(line);
				Double time = readByNames(tokens, columns, "Time (s)", "time (s)");
				Double altitudeFeet = readByNames(tokens, columns, "Altitude (ft)", "altitude (ft)");
				Double altitudeMeters = altitudeFeet == null ? null : altitudeFeet * FEET_TO_METERS;
				if (altitudeFeet != null) {
					diagnostics.incUnitCorrections();
				}
				Double temperatureC = parseFahrenheitByName(tokens, columns, "Temperature (F)", "temperature (f)");
				if (temperatureC != null) {
					diagnostics.incUnitCorrections();
				}
				altitudeMeters = sanitizeRange(altitudeMeters, ALTITUDE_MIN_M, ALTITUDE_MAX_M, diagnostics);
				temperatureC = sanitizeRange(temperatureC, TEMPERATURE_MIN_C, TEMPERATURE_MAX_C, diagnostics);
				if (time == null) {
					diagnostics.incOutlierDrops();
					continue;
				}
				series.addPoint(time, altitudeMeters, null, null, null, null, null, temperatureC);
				diagnostics.incRowsAccepted();
			}
		}
		series.setParserDiagnostics(diagnostics.freeze());
		return series;
	}

	private static TelemetrySeries parseAbInterleaved(Path path, TelemetrySchema schema) throws IOException {
		TelemetrySeries series = new TelemetrySeries(schema);
		TelemetryParserDiagnostics.Mutable diagnostics = new TelemetryParserDiagnostics.Mutable(schema.name());
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String header = reader.readLine();
			if (header == null) {
				return series;
			}
			Map<String, Integer> columns = parseColumns(header);

			String line;
			Double firstTimestampSeconds = null;
			while ((line = reader.readLine()) != null) {
				diagnostics.incRowsRead();
				String[] tokens = splitCsv(line);
				Double time = extractTimeSec(tokens, columns);
				if (time == null) {
					diagnostics.incOutlierDrops();
					continue;
				}
				if (time > 1e10) {
					if (firstTimestampSeconds == null) {
						firstTimestampSeconds = time / 1_000_000_000.0;
					}
					time = (time / 1_000_000_000.0) - firstTimestampSeconds;
				}

				Double altitude = readByNames(tokens, columns,
						"current_altitude",
						"est_position_z_meters",
						"estPressureAlt");
				Double velocityZ = readByNames(tokens, columns,
						"vertical_velocity",
						"est_velocity_z_meters_per_s",
						"est_velocity_z");
				Double accelX = readByNames(tokens, columns,
						"estLinearAccelX",
						"est_acceleration_x_gs",
						"scaledAccelX");
				Double accelY = readByNames(tokens, columns,
						"estLinearAccelY",
						"est_acceleration_y_gs",
						"scaledAccelY");
				Double accelZ = readByNames(tokens, columns,
						"vertical_acceleration",
						"estLinearAccelZ",
						"est_acceleration_z_gs",
						"scaledAccelZ");
				Double pressure = readByNames(tokens, columns,
						"pressure_pascals",
						"scaledAmbientPressure",
						"pressure");
				Double temperature = readByNames(tokens, columns,
						"temperature_celsius",
						"temperature");

				accelX = maybeGsToMetersPerSecond2(accelX, columns, "est_acceleration_x_gs", diagnostics);
				accelY = maybeGsToMetersPerSecond2(accelY, columns, "est_acceleration_y_gs", diagnostics);
				accelZ = maybeGsToMetersPerSecond2(accelZ, columns, "est_acceleration_z_gs", diagnostics);

				altitude = sanitizeRange(altitude, ALTITUDE_MIN_M, ALTITUDE_MAX_M, diagnostics);
				velocityZ = sanitizeRange(velocityZ, -VELOCITY_MAX_MPS, VELOCITY_MAX_MPS, diagnostics);
				accelX = sanitizeRange(accelX, -ACCEL_MAX_MPS2, ACCEL_MAX_MPS2, diagnostics);
				accelY = sanitizeRange(accelY, -ACCEL_MAX_MPS2, ACCEL_MAX_MPS2, diagnostics);
				accelZ = sanitizeRange(accelZ, -ACCEL_MAX_MPS2, ACCEL_MAX_MPS2, diagnostics);
				temperature = sanitizeRange(temperature, TEMPERATURE_MIN_C, TEMPERATURE_MAX_C, diagnostics);

				if (allNull(altitude, velocityZ, accelX, accelY, accelZ, pressure, temperature)) {
					diagnostics.incOutlierDrops();
					continue;
				}
				series.addPoint(time, altitude, velocityZ, accelX, accelY, accelZ, pressure, temperature);
				diagnostics.incRowsAccepted();
			}
		}
		series.setParserDiagnostics(diagnostics.freeze());
		return series;
	}

	private static Double maybeGsToMetersPerSecond2(Double value,
													  Map<String, Integer> columns,
													  String fieldName,
													  TelemetryParserDiagnostics.Mutable diagnostics) {
		if (value == null) {
			return null;
		}
		if (columns.containsKey(fieldName)) {
			diagnostics.incUnitCorrections();
			return value * STANDARD_GRAVITY;
		}
		return value;
	}

	private static Double extractTimeSec(String[] tokens, Map<String, Integer> columns) {
		Double value = readByNames(tokens, columns,
				"timestamp_seconds",
				"time",
				"timestamp");
		return value;
	}

	private static boolean allNull(Double... values) {
		for (Double value : values) {
			if (value != null) {
				return false;
			}
		}
		return true;
	}

	private static Map<String, Integer> parseColumns(String headerLine) {
		return parseColumns(headerLine, ",");
	}

	private static Map<String, Integer> parseColumns(String headerLine, String delimiter) {
		String[] headers = headerLine.split(delimiter, -1);
		Map<String, Integer> columns = new HashMap<>();
		for (int i = 0; i < headers.length; i++) {
			columns.put(headers[i].trim(), i);
		}
		return columns;
	}

	private static Double readByNames(String[] tokens, Map<String, Integer> columns, String... names) {
		for (String name : names) {
			Integer index = columns.get(name);
			if (index == null || index >= tokens.length) {
				continue;
			}
			Double value = parseDouble(tokens[index]);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String[] splitCsv(String line) {
		return line.split(",", -1);
	}

	private static Double parseDouble(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		trimmed = trimmed.replace(',', '.');
		try {
			return Double.parseDouble(trimmed);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static Double parseFahrenheit(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.endsWith("F") || trimmed.endsWith("f")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		Double fahrenheit = parseDouble(trimmed);
		if (fahrenheit == null) {
			return null;
		}
		return (fahrenheit - 32.0) * (5.0 / 9.0);
	}

	private static Double parseFahrenheitByName(String[] tokens, Map<String, Integer> columns, String... names) {
		for (String name : names) {
			Integer index = columns.get(name);
			if (index == null || index >= tokens.length) {
				continue;
			}
			Double value = parseFahrenheit(tokens[index]);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static Double sanitizeRange(Double value,
										   double min,
										   double max,
										   TelemetryParserDiagnostics.Mutable diagnostics) {
		if (value == null || !Double.isFinite(value)) {
			return null;
		}
		if (value < min || value > max) {
			diagnostics.incRangeDrops();
			return null;
		}
		return value;
	}

	private static Double dropSentinel(Double value, TelemetryParserDiagnostics.Mutable diagnostics) {
		if (value == null || !Double.isFinite(value)) {
			return null;
		}
		if (Math.abs(value - Integer.MAX_VALUE) < 1e-6 || Math.abs(value - Integer.MIN_VALUE) < 1e-6) {
			diagnostics.incSentinelDrops();
			return null;
		}
		return value;
	}
}
