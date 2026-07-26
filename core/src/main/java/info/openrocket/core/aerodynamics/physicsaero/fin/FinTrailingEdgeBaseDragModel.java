package info.openrocket.core.aerodynamics.physicsaero.fin;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;

/**
 * Blunt fin trailing-edge base area.  For a constant-t/c taper the exposed
 * trailing-edge area is t/c times planform area; otherwise the OpenRocket
 * constant physical thickness is integrated over exposed span.
 */
public final class FinTrailingEdgeBaseDragModel {
	public static final String METHOD_ID = "FIN_BLUNT_TRAILING_EDGE_BASE_PRESSURE_V1";

	public double trailingEdgeAreaPerFinM2(AeroComponent component) {
		if (component.finGeometry() == null) {
			return 0;
		}
		// A symmetric diamond (RASAero "hexagonal") section closes back to zero
		// thickness at the trailing edge.  Its aft wedge owns pressure/wave drag,
		// but there is no exposed blunt base area to load a second time.
		if ("SYMMETRIC_DIAMOND".equals(component.finGeometry().section())) {
			return 0;
		}
		Double thicknessRatio = component.localReferences().get("thicknessRatio");
		if (thicknessRatio != null) {
			if (!Double.isFinite(thicknessRatio) || thicknessRatio < 0) {
				throw new IllegalArgumentException("invalid fin thickness ratio");
			}
			return thicknessRatio * component.finGeometry().planformAreaM2();
		}
		double thickness = component.localReferences().getOrDefault("thicknessM", 0.0);
		if (!Double.isFinite(thickness) || thickness < 0) {
			throw new IllegalArgumentException("invalid fin thickness");
		}
		return thickness * component.finGeometry().spanM();
	}

	public double dragCoefficient(AeroComponent component, double basePressureMagnitude,
			double referenceAreaM2) {
		if (basePressureMagnitude < 0 || referenceAreaM2 <= 0) {
			throw new IllegalArgumentException("invalid fin base-drag state");
		}
		if (component.finGeometry() == null) {
			return 0;
		}
		return basePressureMagnitude * trailingEdgeAreaPerFinM2(component)
				* component.finGeometry().count() / referenceAreaM2;
	}
}
