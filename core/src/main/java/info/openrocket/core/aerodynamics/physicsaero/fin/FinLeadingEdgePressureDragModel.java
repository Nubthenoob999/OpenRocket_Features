package info.openrocket.core.aerodynamics.physicsaero.fin;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;

/**
 * Engineering fore-pressure drag for square and rounded fin leading edges.
 *
 * <p>The Mach correlations are the established OpenRocket/Hoerner-style
 * stagnation and rounded-edge closures. Drag is applied to actual projected
 * leading-edge frontal area, with swept-edge cosine-squared relief. Skin
 * friction and trailing-edge base pressure remain separately owned.</p>
 */
public final class FinLeadingEdgePressureDragModel {
	public static final String METHOD_ID = "FIN_LEADING_EDGE_PRESSURE_DRAG_V1";

	public double dragCoefficient(AeroComponent component, double mach, double referenceAreaM2) {
		FinGeometry fin = component.finGeometry();
		if (fin == null || !(referenceAreaM2 > 0) || !Double.isFinite(mach) || mach < 0) {
			throw new IllegalArgumentException("invalid fin leading-edge drag input");
		}
		double pressureFactor = switch (fin.section()) {
			case "FLAT_PLATE", "SQUARE" -> stagnationCoefficient(mach);
			case "ROUNDED_LEADING_EDGE", "ROUNDED" -> roundedCoefficient(mach);
			default -> 0;
		};
		if (pressureFactor == 0) return 0;
		double meanChord = fin.planformAreaM2() / fin.spanM();
		double thickness = component.localReferences().getOrDefault("thicknessM",
				component.localReferences().getOrDefault("thicknessRatio", 0.0) * meanChord);
		if (!(thickness > 0)) return 0;
		return pressureFactor * leadingEdgeCosineSquared(fin) * fin.spanM() * thickness
				* fin.count() / referenceAreaM2;
	}

	public double dragCoefficientPerFin(AeroComponent component, double mach, double referenceAreaM2) {
		return dragCoefficient(component, mach, referenceAreaM2) / component.finGeometry().count();
	}

	double stagnationCoefficient(double mach) {
		double m2 = mach * mach;
		double pressure = mach <= 1
				? 1 + m2 / 4 + m2 * m2 / 40
				: 1.84 - 0.76 / m2 + 0.166 / (m2 * m2) + 0.035 / (m2 * m2 * m2);
		return 0.85 * pressure;
	}

	double roundedCoefficient(double mach) {
		if (mach < 0.9) return Math.pow(1 - mach * mach, -0.417) - 1;
		if (mach < 1) return 1 - 1.785 * (mach - 0.9);
		double m2 = mach * mach;
		return 1.214 - 0.502 / m2 + 0.1095 / (m2 * m2);
	}

	private static double leadingEdgeCosineSquared(FinGeometry fin) {
		double span = fin.spanM();
		double rootLeadingX = fin.outline().stream()
				.filter(point -> Math.abs(point.radiusM()) <= 1e-12)
				.mapToDouble(point -> point.xM()).min().orElse(0);
		double tipLeadingX = fin.outline().stream()
				.filter(point -> Math.abs(point.radiusM() - span)
						<= 1e-9 * Math.max(1, span))
				.mapToDouble(point -> point.xM()).min().orElse(rootLeadingX);
		double sweep = tipLeadingX - rootLeadingX;
		return span * span / (span * span + sweep * sweep);
	}
}
