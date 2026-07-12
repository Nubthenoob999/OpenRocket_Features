package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroColumnMap.Column;

public final class RasaeroAeroPlotReader {
	public List<SourceRow> read(Path path) throws IOException { return read(Files.readString(path, StandardCharsets.UTF_8)); }
	public List<SourceRow> read(String contents) {
		List<List<String>> rows = RasaeroDelimitedText.parse(contents); RasaeroColumnMap columns = RasaeroColumnMap.fromHeaders(rows.get(0));
		List<SourceRow> result = new ArrayList<>();
		for (int line = 1; line < rows.size(); line++) {
			List<String> row = rows.get(line); Map<Column, Double> values = new EnumMap<>(Column.class);
			for (Column column : Column.values()) {
				int index = columns.index(column); if (index >= row.size()) throw new RasaeroParseException(RasaeroParseException.Reason.MALFORMED_ROW, "line " + (line + 1));
				try { values.put(column, Double.parseDouble(row.get(index))); }
				catch (NumberFormatException ex) { throw new RasaeroParseException(RasaeroParseException.Reason.NONNUMERIC_CELL, "line " + (line + 1) + " column " + column); }
			}
			result.add(new SourceRow(values));
		}
		return List.copyOf(result);
	}
	public record SourceRow(Map<Column, Double> values) {
		public SourceRow { values = Map.copyOf(values); }
		public double value(Column column) { return values.get(column); }
	}
}
