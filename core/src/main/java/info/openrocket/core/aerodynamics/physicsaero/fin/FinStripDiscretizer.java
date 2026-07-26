package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinGeometryAdapter.PhysicalFin;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.util.Coordinate;

/** Area-conservative midpoint strips for polygonal OpenRocket planforms. */
public final class FinStripDiscretizer {
	public List<FinStrip> discretize(PhysicalFin fin, int stripCount) {
		if (stripCount < 2) throw new IllegalArgumentException("at least two strips required");
		Double constantThicknessRatio = fin.component().localReferences().get("thicknessRatio");
		if (constantThicknessRatio != null
				&& (!Double.isFinite(constantThicknessRatio) || constantThicknessRatio < 0)) {
			throw new IllegalArgumentException("thicknessRatio must be finite and nonnegative");
		}
		double span = fin.geometry().spanM(), dy = span / stripCount;
		List<Raw> raw = new ArrayList<>(); double rawArea = 0;
		for (int i = 0; i < stripCount; i++) {
			double y = (i + 0.5) * dy; Edge edge = edgeAt(fin.geometry().outline(), y, fin.geometry().rootChordM(), span);
			double chord = edge.trailing - edge.leading; if (chord <= 0) continue;
			raw.add(new Raw(y, edge.leading, edge.trailing, chord)); rawArea += chord * dy;
		}
		if (raw.isEmpty() || rawArea <= 0) throw new IllegalArgumentException("fin outline has no exposed area");
		double areaScale = fin.geometry().planformAreaM2() / rawArea;
		List<FinStrip> strips = new ArrayList<>();
		for (int i = 0; i < raw.size(); i++) {
			Raw r = raw.get(i); Raw before = raw.get(Math.max(0, i - 1)), after = raw.get(Math.min(raw.size() - 1, i + 1));
			double deltaY = Math.max(dy, after.y - before.y);
			double leSweep = Math.atan2(after.leading - before.leading, deltaY);
			double halfSweep = Math.atan2((after.leading + after.trailing - before.leading - before.trailing) * 0.5, deltaY);
			double x = fin.component().axialStartM() + (r.leading + r.trailing) * 0.5;
			Coordinate radial = fin.frame().spanwise();
			Coordinate centroid = new Coordinate(x, radial.y * (fin.component().rootRadiusM() + r.y), radial.z * (fin.component().rootRadiusM() + r.y));
			// A physical OpenRocket fin normally has one constant absolute thickness.  Imported
			// correlation fixtures may instead specify a geometrically similar taper with a
			// constant t/c.  The explicit ratio owns that representation when present; the
			// historical thicknessM/chord behavior remains unchanged otherwise.
			double tc = constantThicknessRatio != null ? constantThicknessRatio : fin.thicknessM() / r.chord;
			strips.add(new FinStrip(r.y, dy, r.leading, r.trailing, r.chord, leSweep, halfSweep, tc,
					family(fin.geometry().section()), centroid, r.chord * dy * areaScale, fin.frame(), fin.component().rootRadiusM()));
		}
		return List.copyOf(strips);
	}
	private static Edge edgeAt(List<GeometryStation> outline, double y, double rootChord, double span) {
		if (outline.size() < 3) return new Edge(0, rootChord * (1 - y / span));
		List<Double> intersections = new ArrayList<>();
		for (int i = 0; i < outline.size(); i++) {
			GeometryStation a = outline.get(i), b = outline.get((i + 1) % outline.size());
			double ya = a.radiusM(), yb = b.radiusM();
			if ((ya <= y && y < yb) || (yb <= y && y < ya)) intersections.add(a.xM() + (y - ya) * (b.xM() - a.xM()) / (yb - ya));
		}
		intersections.sort(Comparator.naturalOrder());
		if (intersections.size() < 2) throw new IllegalArgumentException("invalid or non-closed fin outline at span station " + y);
		return new Edge(intersections.get(0), intersections.get(intersections.size() - 1));
	}
	private static FinSectionFamily family(String section) {
		try { return FinSectionFamily.valueOf(section); } catch (RuntimeException ex) { return FinSectionFamily.FLAT_PLATE; }
	}
	private record Edge(double leading, double trailing) {}
	private record Raw(double y, double leading, double trailing, double chord) {}
}
