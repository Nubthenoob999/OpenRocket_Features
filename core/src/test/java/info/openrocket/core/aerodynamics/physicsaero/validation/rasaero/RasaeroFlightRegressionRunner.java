package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroFlightDatasetReader.RasaeroFlightRow;

public final class RasaeroFlightRegressionRunner {
	public List<FlightResult> run(List<RasaeroFlightRow> rows, Map<Integer, Double> physicsApogeeFt,
			String geometryHash, String tableHash) {
		List<FlightResult> results = new ArrayList<>();
		for (RasaeroFlightRow row : rows) {
			Double physics = physicsApogeeFt.get(row.flightId()); boolean supported = physics != null;
			String reason = supported ? null : isExpectedMultistageSkip(row.flightId())
					? "EXPECTED_MULTI_STAGE_SKIP" : "MISSING_LOCAL_VEHICLE_MODEL";
			results.add(new FlightResult(row.flightId(), row.vehicle(), row.peakMach(), row.measuredApogeeFt(),
					row.rasaeroApogeeFt(), physics, row.hasRasaeroPrediction() ? row.reconstructedRasaeroErrorPct() : null,
					physics == null ? null : 100 * (physics - row.measuredApogeeFt()) / row.measuredApogeeFt(),
					geometryHash, tableHash, supported, reason));
		}
		return List.copyOf(results);
	}
	private static boolean isExpectedMultistageSkip(int id) { return id == 22 || id == 25; }
	public record FlightResult(int flightId, String vehicle, double peakMachReference, double measuredApogeeFt,
			Double rasaeroApogeeFt, Double physicsAeroApogeeFt, Double rasaeroErrorPct, Double physicsAeroErrorPct,
			String geometryHash, String tableHash, boolean supported, String reasonCode) {}
}
