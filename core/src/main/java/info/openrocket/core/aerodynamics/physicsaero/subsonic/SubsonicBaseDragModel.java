package info.openrocket.core.aerodynamics.physicsaero.subsonic;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;

/**
 * RASAero/Hoerner friction-coupled subsonic base-drag correlation.
 *
 * <p>The correlation couples the wake pressure deficit to the vehicle skin
 * friction and scales a reduced base by the cube of its diameter ratio:</p>
 *
 * <pre>CD_base = 0.029 / sqrt(CD_friction) * (d_base/d_max)^3
 *           * (A_max/A_reference)</pre>
 *
 * <p>This replaces the geometry-independent {@code 0.12 + 0.13 M^2}
 * pressure coefficient, which overstates the low-speed base drag of long,
 * low-friction rockets and cannot respond to base diameter.</p>
 */
public final class SubsonicBaseDragModel {
	public static final String METHOD_ID =
			"RASAERO_HOERNER_FRICTION_COUPLED_SUBSONIC_BASE_DRAG_V1";

	public double dragCoefficient(AeroGeometry geometry, double skinFrictionCd) {
		if (geometry == null || !(skinFrictionCd > 0)
				|| !Double.isFinite(skinFrictionCd)) {
			throw new IllegalArgumentException("invalid subsonic base-drag state");
		}
		double maximumDiameter = geometry.references().maximumBodyDiameterM();
		double maximumArea = Math.PI * maximumDiameter * maximumDiameter / 4;
		double baseDiameter = 2 * Math.sqrt(
				geometry.references().exposedBaseAreaM2() / Math.PI);
		double diameterRatio = Math.min(1, baseDiameter / maximumDiameter);
		return 0.029 / Math.sqrt(skinFrictionCd)
				* Math.pow(diameterRatio, 3)
				* maximumArea / geometry.references().referenceAreaM2();
	}
}
