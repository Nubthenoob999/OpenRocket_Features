package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RasaeroColumnMap {
	private final EnumMap<Column, Integer> indices;
	private RasaeroColumnMap(EnumMap<Column, Integer> indices) { this.indices = indices; }
	public static RasaeroColumnMap fromHeaders(List<String> headers) {
		Map<String, Integer> normalized = new HashMap<>();
		for (int i = 0; i < headers.size(); i++) {
			String key = normalize(headers.get(i)); Integer previous = normalized.putIfAbsent(key, i);
			if (previous != null) throw new RasaeroParseException(RasaeroParseException.Reason.DUPLICATE_COLUMN, headers.get(i));
		}
		EnumMap<Column, Integer> result = new EnumMap<>(Column.class);
		for (Column column : Column.values()) {
			Integer index = normalized.get(column.key); if (index == null) throw new RasaeroParseException(RasaeroParseException.Reason.MISSING_COLUMN, column.display);
			result.put(column, index);
		}
		return new RasaeroColumnMap(result);
	}
	public int index(Column column) { return indices.get(column); }
	public int size() { return indices.size(); }
	static String normalize(String value) { return value.replace("\ufeff", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
	public enum Column {
		MACH("Mach"), ALPHA("Alpha"), CD("CD"), CD_OFF("CD Power-Off"), CD_ON("CD Power-On"),
		CA_OFF("CA Power-Off"), CA_ON("CA Power-On"), CL("CL"), CN("CN"), CN_POTENTIAL("CN Potential"),
		CN_VISCOUS("CN Viscous"), CN_ALPHA("CNalpha (0 to 4 deg) (per rad)"), CP("CP"),
		CP_0_TO_4("CP (0 to 4 deg)"), REYNOLDS("Reynolds Number");
		private final String display, key;
		Column(String display) { this.display = display; this.key = normalize(display); }
	}
}
