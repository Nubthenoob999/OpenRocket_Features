package info.openrocket.core.aerodynamics.physicsaero.interaction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Versioned NACA 1307 table with bounded interpolation and no extrapolation. */
public final class PnkFactorTable {
	public static final String RESOURCE = "/datafiles/physicsaero/pnk-naca-1307-v1.csv";
	private final double[] x, kwb, kbw;
	public PnkFactorTable() { this(readResource()); }
	PnkFactorTable(List<double[]> rows) {
		x = new double[rows.size()]; kwb = new double[rows.size()]; kbw = new double[rows.size()];
		for (int i = 0; i < rows.size(); i++) { x[i] = rows.get(i)[0]; kwb[i] = rows.get(i)[1]; kbw[i] = rows.get(i)[2]; if (i > 0 && x[i] <= x[i - 1]) throw new IllegalArgumentException("PNK axis must increase"); }
	}
	public Factors interpolate(double radiusOverSemispan) {
		if (radiusOverSemispan < x[0] || radiusOverSemispan > x[x.length - 1]) throw new IllegalArgumentException("PNK_EXTRAPOLATION_FORBIDDEN");
		int i = 0; while (i + 1 < x.length && x[i + 1] < radiusOverSemispan) i++;
		if (i == x.length - 1 || radiusOverSemispan == x[i]) return new Factors(kwb[i], kbw[i], radiusOverSemispan);
		double f = (radiusOverSemispan - x[i]) / (x[i + 1] - x[i]);
		return new Factors(kwb[i] + f * (kwb[i + 1] - kwb[i]), kbw[i] + f * (kbw[i + 1] - kbw[i]), radiusOverSemispan);
	}
	private static List<double[]> readResource() {
		InputStream stream = PnkFactorTable.class.getResourceAsStream(RESOURCE);
		if (stream == null) throw new IllegalStateException("missing PNK data resource");
		List<double[]> rows = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null;) {
				if (line.isBlank() || line.startsWith("#")) continue; String[] values = line.split(",");
				rows.add(new double[] { Double.parseDouble(values[0]), Double.parseDouble(values[1]), Double.parseDouble(values[2]) });
			}
		} catch (IOException ex) { throw new UncheckedIOException(ex); }
		if (rows.size() < 2) throw new IllegalStateException("empty PNK data resource"); return rows;
	}
	public record Factors(double wingBodyFactor, double bodyWingFactor, double radiusOverSemispan) {}
}
