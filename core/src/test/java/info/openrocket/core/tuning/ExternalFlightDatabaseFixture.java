package info.openrocket.core.tuning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pinned, redistributable summary of the Aidan Yu Rocket Flight Database v1.2.
 *
 * <p>This is an apogee-validation fixture, not a vehicle model.  The upstream
 * project intentionally does not redistribute the RASAero CDX1 definitions or
 * per-flight meteorology, so a row may be scored only after a caller supplies
 * an independently obtained as-flown ORK/CDX1 conversion.  Keeping that
 * distinction explicit prevents a tuned drag table from being credited for a
 * replay whose geometry, motor or atmosphere is unknown.</p>
 */
final class ExternalFlightDatabaseFixture {
	static final String UPSTREAM_URL = "https://github.com/AidanSYu/rocket-flight-database";
	static final String SOURCE_REVISION = "cfbfc37d0d4db9a88de8f525041ccdfe458c904c";
	static final double FEET_TO_METERS = 0.3048;

	private static final String HEADER = "flight_id|vehicle_name|motor|diameter_in|peak_mach|"
			+ "launch_site_alt_ft|apogee_real_ft|apogee_openrocket_plus_ft|measurement_type|data_source|"
			+ "vehicle_definition_status";

	private ExternalFlightDatabaseFixture() {
	}

	static List<Flight> load(Path manifest) throws IOException {
		Objects.requireNonNull(manifest, "manifest");
		List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
		if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
			throw new IOException("Unexpected external-flight manifest schema: " + manifest);
		}

		List<Flight> flights = new ArrayList<>();
		for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
			String line = lines.get(lineNumber - 1).trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] columns = line.split("\\|", -1);
			if (columns.length != 11) {
				throw new IOException("Expected 11 fields at " + manifest + ":" + lineNumber);
			}
			try {
				flights.add(new Flight(
						parsePositiveInt(columns[0], "flight_id", lineNumber),
						columns[1], columns[2], columns[3],
						parsePositiveDouble(columns[4], "peak_mach", lineNumber),
						parseNonNegativeDouble(columns[5], "launch_site_alt_ft", lineNumber),
						parsePositiveDouble(columns[6], "apogee_real_ft", lineNumber),
						parsePositiveDouble(columns[7], "apogee_openrocket_plus_ft", lineNumber),
						columns[8], columns[9], DefinitionStatus.valueOf(columns[10])));
			} catch (IllegalArgumentException exception) {
				throw new IOException("Invalid external-flight manifest value at " + manifest + ":" + lineNumber,
						exception);
			}
		}
		return List.copyOf(flights);
	}

	private static int parsePositiveInt(String value, String field, int lineNumber) {
		int parsed = Integer.parseInt(value);
		if (parsed <= 0) {
			throw new IllegalArgumentException(field + " must be positive at line " + lineNumber);
		}
		return parsed;
	}

	private static double parsePositiveDouble(String value, String field, int lineNumber) {
		double parsed = Double.parseDouble(value);
		if (!Double.isFinite(parsed) || parsed <= 0.0) {
			throw new IllegalArgumentException(field + " must be finite and positive at line " + lineNumber);
		}
		return parsed;
	}

	private static double parseNonNegativeDouble(String value, String field, int lineNumber) {
		double parsed = Double.parseDouble(value);
		if (!Double.isFinite(parsed) || parsed < 0.0) {
			throw new IllegalArgumentException(field + " must be finite and non-negative at line " + lineNumber);
		}
		return parsed;
	}

	enum DefinitionStatus {
		/** A geometry/mass/motor definition must be supplied before replaying this row. */
		NOT_REDISTRIBUTED_BY_UPSTREAM
	}

	record Flight(int id, String vehicleName, String motor, String diameterInches, double peakMach,
				  double launchSiteAltitudeFeet, double measuredApogeeFeet,
				  double publishedOpenRocketPlusApogeeFeet, String measurementType,
				  String dataSource, DefinitionStatus definitionStatus) {
		Flight {
			Objects.requireNonNull(vehicleName, "vehicleName");
			Objects.requireNonNull(motor, "motor");
			Objects.requireNonNull(diameterInches, "diameterInches");
			Objects.requireNonNull(measurementType, "measurementType");
			Objects.requireNonNull(dataSource, "dataSource");
			Objects.requireNonNull(definitionStatus, "definitionStatus");
		}

		double measuredApogeeMeters() {
			return measuredApogeeFeet * FEET_TO_METERS;
		}

		double launchSiteAltitudeMetersMsl() {
			return launchSiteAltitudeFeet * FEET_TO_METERS;
		}

		/** Signed apogee error: 100 * (prediction - measured) / measured. */
		double signedApogeeErrorPercent(double predictedApogeeFeet) {
			if (!Double.isFinite(predictedApogeeFeet) || predictedApogeeFeet <= 0.0) {
				throw new IllegalArgumentException("Predicted apogee must be finite and positive");
			}
			return 100.0 * (predictedApogeeFeet - measuredApogeeFeet) / measuredApogeeFeet;
		}

		boolean isWithinApogeeTolerance(double predictedApogeeFeet, double fractionalTolerance) {
			if (!Double.isFinite(fractionalTolerance) || fractionalTolerance < 0.0) {
				throw new IllegalArgumentException("Tolerance must be finite and non-negative");
			}
			return Math.abs(signedApogeeErrorPercent(predictedApogeeFeet)) <= 100.0 * fractionalTolerance;
		}
	}
}
