package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class RasaeroFlightDatasetReader {
	public List<RasaeroFlightRow> read(Path path) throws IOException { return read(Files.readString(path, StandardCharsets.UTF_8)); }
	public List<RasaeroFlightRow> read(String contents) {
		List<List<String>> parsed = RasaeroDelimitedText.parse(contents); Map<String, Integer> header = new HashMap<>();
		for (int i = 0; i < parsed.get(0).size(); i++) header.put(RasaeroColumnMap.normalize(parsed.get(0).get(i)), i);
		List<RasaeroFlightRow> rows = new ArrayList<>();
		for (int i = 1; i < parsed.size(); i++) {
			List<String> row = parsed.get(i);
			rows.add(new RasaeroFlightRow(integer(row, header, "flight_id"), text(row, header, "vehicle_name"),
					number(row, header, "peak_mach"), number(row, header, "apogee_real_ft"), optional(row, header, "apogee_rasaero_ft"),
					optional(row, header, "err_rasaero_pct"), text(row, header, "flight_data_type"), text(row, header, "data_source")));
		}
		return List.copyOf(rows);
	}
	private static String text(List<String> row, Map<String,Integer> header, String name) { Integer index = header.get(RasaeroColumnMap.normalize(name)); if (index == null || index >= row.size()) throw new RasaeroParseException(RasaeroParseException.Reason.MISSING_COLUMN, name); return row.get(index); }
	private static double number(List<String> row, Map<String,Integer> header, String name) { try { return Double.parseDouble(text(row, header, name)); } catch (NumberFormatException ex) { throw new RasaeroParseException(RasaeroParseException.Reason.NONNUMERIC_CELL, name); } }
	private static int integer(List<String> row, Map<String,Integer> header, String name) { return (int) number(row, header, name); }
	private static Double optional(List<String> row, Map<String,Integer> header, String name) { String value = text(row, header, name); return value.isBlank() ? null : Double.valueOf(value); }
	public record RasaeroFlightRow(int flightId, String vehicle, double peakMach, double measuredApogeeFt,
			Double rasaeroApogeeFt, Double displayedRasaeroErrorPct, String flightDataType, String source) {
		public boolean hasRasaeroPrediction() { return rasaeroApogeeFt != null; }
		public double reconstructedRasaeroErrorPct() { if (!hasRasaeroPrediction()) throw new IllegalStateException("missing RASAero prediction"); return 100 * (rasaeroApogeeFt - measuredApogeeFt) / measuredApogeeFt; }
	}
	public record AggregateError(double meanAbsolutePercentError, int countWithinFivePercent, int countWithinTenPercent,
			double minimumMach, double maximumMach) {
		public static AggregateError fromRasaeroRows(List<RasaeroFlightRow> rows) {
			if (rows.isEmpty() || rows.stream().anyMatch(r -> !r.hasRasaeroPrediction())) throw new IllegalArgumentException("RASAero rows required");
			double sum = 0, min = Double.POSITIVE_INFINITY, max = 0; int five = 0, ten = 0;
			for (RasaeroFlightRow row : rows) { double error = Math.abs(row.reconstructedRasaeroErrorPct()); sum += error; if (error <= 5) five++; if (error <= 10) ten++; min = Math.min(min, row.peakMach()); max = Math.max(max, row.peakMach()); }
			return new AggregateError(sum / rows.size(), five, ten, min, max);
		}
	}
}
