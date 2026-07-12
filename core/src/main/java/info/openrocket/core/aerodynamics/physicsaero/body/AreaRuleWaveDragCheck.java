package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;

/** Slender area-slope diagnostic only; never enters the force ledger. */
public final class AreaRuleWaveDragCheck {
	public Result evaluate(List<AxisymmetricBodySegment> segments, double referenceAreaM2,
			double referenceLengthM, double authoritativePressureCa) {
		double integral = 0;
		for (AxisymmetricBodySegment segment : segments) {
			List<GeometryStation> s = segment.quadratureStations();
			for (int i = 1; i < s.size(); i++) {
				double dx = s.get(i).xM() - s.get(i - 1).xM(); if (dx <= 0) continue;
				double derivative = (s.get(i).areaM2() - s.get(i - 1).areaM2()) / dx;
				integral += derivative * derivative * dx;
			}
		}
		double diagnostic = integral / (2 * Math.PI * referenceAreaM2 * referenceLengthM);
		return new Result("SLENDER_AREA_SLOPE_DIAGNOSTIC_V1", diagnostic, authoritativePressureCa,
				diagnostic - authoritativePressureCa, false);
	}
	public record Result(String methodId, double diagnosticCa, double pressureCa, double disagreement,
			boolean authoritative) {}
}
