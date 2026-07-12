package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.util.ArrayList;
import java.util.List;

final class RasaeroDelimitedText {
	private RasaeroDelimitedText() {}
	static List<List<String>> parse(String text) {
		if (text == null || text.isBlank()) throw new RasaeroParseException(RasaeroParseException.Reason.EMPTY_INPUT, "no rows");
		text = text.charAt(0) == '\ufeff' ? text.substring(1) : text;
		char delimiter = delimiter(text); List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder(); boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"') {
				if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
				else quoted = !quoted;
			} else if (c == delimiter && !quoted) { row.add(cell.toString().trim()); cell.setLength(0); }
			else if ((c == '\n' || c == '\r') && !quoted) {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
				row.add(cell.toString().trim()); cell.setLength(0); if (!row.stream().allMatch(String::isBlank)) rows.add(List.copyOf(row)); row.clear();
			} else cell.append(c);
		}
		if (quoted) throw new RasaeroParseException(RasaeroParseException.Reason.MALFORMED_ROW, "unclosed quote");
		if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString().trim()); if (!row.stream().allMatch(String::isBlank)) rows.add(List.copyOf(row)); }
		return List.copyOf(rows);
	}
	private static char delimiter(String text) {
		int lineEnd = text.indexOf('\n'); String header = lineEnd < 0 ? text : text.substring(0, lineEnd);
		int commas = countOutsideQuotes(header, ','), tabs = countOutsideQuotes(header, '\t'); return tabs > commas ? '\t' : ',';
	}
	private static int countOutsideQuotes(String value, char target) {
		boolean quoted = false; int count = 0; for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '"') quoted = !quoted; else if (!quoted && c == target) count++; } return count;
	}
}
