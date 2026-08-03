package info.openrocket.core.aerodynamics.physicsaero.transonic;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;

/**
 * Sting-free, unpowered blunt-base pressure correlation through the transonic
 * overlap.  The baseline is the faired Configuration-A free-flight curve from
 * Hart, NACA RM L52E06 (1952), Figure 8: an ogive-cylinder body of fineness
 * ratio 11 with a flat, full-caliber base.  Shape-preserving cubic Hermite
 * interpolation retains the measured broad peak without introducing slope
 * discontinuities into the trajectory integrator.
 *
 * <p>The transonic wave-drag excess is deliberately excluded because
 * {@link TransonicDragRiseModel} owns it. The optional displacement ratio is
 * reserved for a resolved boundary-layer state; callers without one must pass
 * zero.  It is an incremental correction, not part of the Hart baseline.
 */
public final class TransonicBaseDragModel {
	public static final String METHOD_ID = "NACA_RM_L52E06_TRANSONIC_BASE_CP_V4";
	private static final double[] MACH = {
			0.70, 0.80, 0.85, 0.90, 0.95, 1.00, 1.05,
			1.08, 1.10, 1.15, 1.20, 1.25, 1.30
	};
	private static final double[] BASE_DRAG = {
			0.150, 0.160, 0.170, 0.180, 0.215, 0.255, 0.265,
			0.267, 0.265, 0.260, 0.255, 0.250, 0.250
	};
	private static final double[] SLOPE = monotoneSlopes(MACH, BASE_DRAG);
	private static final double SUPERSONIC_HANDOFF_MACH = 1.50;
	private static final double SUPERSONIC_HANDOFF_BASE_DRAG =
			0.064 + 0.186 / (SUPERSONIC_HANDOFF_MACH
					* SUPERSONIC_HANDOFF_MACH);
	private static final double SUPERSONIC_HANDOFF_SLOPE =
			-2 * 0.186 / (SUPERSONIC_HANDOFF_MACH
					* SUPERSONIC_HANDOFF_MACH
					* SUPERSONIC_HANDOFF_MACH);

	public double basePressureCoefficient(double mach, double displacementRatio) {
		if (mach < 0.7 || mach > SUPERSONIC_HANDOFF_MACH
				|| displacementRatio < 0
				|| !Double.isFinite(mach + displacementRatio)) {
			throw new IllegalArgumentException();
		}
		double baseline = mach <= MACH[MACH.length - 1]
				? interpolate(mach) : supersonicHandoff(mach);
		return -Math.min(0.45, baseline + 0.30 * displacementRatio);
	}

	/**
	 * Applies the classic cubic base-diameter scaling to Hart's full-caliber
	 * pressure-drag curve.  The cubic law accounts for both pressure area and
	 * the reduced wake-pressure deficit of a smaller aft diameter.
	 */
	public double dragCoefficient(AeroGeometry geometry, double mach,
			double displacementRatio) {
		if (geometry == null) {
			throw new IllegalArgumentException("geometry is required");
		}
		double maximumDiameter = geometry.references().maximumBodyDiameterM();
		double maximumArea = Math.PI * maximumDiameter * maximumDiameter / 4;
		double baseDiameter = 2 * Math.sqrt(
				geometry.references().exposedBaseAreaM2() / Math.PI);
		double diameterRatio = Math.min(1, baseDiameter / maximumDiameter);
		return -basePressureCoefficient(mach, displacementRatio)
				* Math.pow(diameterRatio, 3)
				* maximumArea / geometry.references().referenceAreaM2();
	}

	/**
	 * C1 bridge from the zero-slope Hart M=1.30 endpoint to the value and
	 * slope of the TN-3393/ESDU-form empirical supersonic correlation at M=1.50.
	 */
	private static double supersonicHandoff(double mach) {
		double startMach = MACH[MACH.length - 1];
		double width = SUPERSONIC_HANDOFF_MACH - startMach;
		double t = (mach - startMach) / width;
		double t2 = t * t;
		double t3 = t2 * t;
		return (2 * t3 - 3 * t2 + 1)
				* BASE_DRAG[BASE_DRAG.length - 1]
				+ (-2 * t3 + 3 * t2)
						* SUPERSONIC_HANDOFF_BASE_DRAG
				+ (t3 - t2) * width * SUPERSONIC_HANDOFF_SLOPE;
	}

	private static double interpolate(double mach) {
		int upper = java.util.Arrays.binarySearch(MACH, mach);
		if (upper >= 0) return BASE_DRAG[upper];
		upper = -upper - 1;
		int lower = upper - 1;
		double width = MACH[upper] - MACH[lower];
		double t = (mach - MACH[lower]) / width;
		double t2 = t * t;
		double t3 = t2 * t;
		return (2 * t3 - 3 * t2 + 1) * BASE_DRAG[lower]
				+ (t3 - 2 * t2 + t) * width * SLOPE[lower]
				+ (-2 * t3 + 3 * t2) * BASE_DRAG[upper]
				+ (t3 - t2) * width * SLOPE[upper];
	}

	/**
	 * Fritsch-Carlson/PCHIP node slopes.  Opposite-signed adjacent secants get
	 * a zero slope, so the interpolant cannot invent a second transonic peak.
	 */
	private static double[] monotoneSlopes(double[] x, double[] y) {
		int count = x.length;
		double[] interval = new double[count - 1];
		double[] secant = new double[count - 1];
		for (int index = 0; index < count - 1; index++) {
			interval[index] = x[index + 1] - x[index];
			secant[index] = (y[index + 1] - y[index]) / interval[index];
		}
		double[] slope = new double[count];
		slope[0] = endpointSlope(interval[0], interval[1], secant[0], secant[1]);
		slope[count - 1] = endpointSlope(interval[count - 2], interval[count - 3],
				secant[count - 2], secant[count - 3]);
		for (int index = 1; index < count - 1; index++) {
			if (secant[index - 1] == 0 || secant[index] == 0
					|| Math.signum(secant[index - 1]) != Math.signum(secant[index])) {
				slope[index] = 0;
			} else {
				double firstWeight = 2 * interval[index] + interval[index - 1];
				double secondWeight = interval[index] + 2 * interval[index - 1];
				slope[index] = (firstWeight + secondWeight)
						/ (firstWeight / secant[index - 1]
								+ secondWeight / secant[index]);
			}
		}
		return slope;
	}

	private static double endpointSlope(double endpointWidth, double neighborWidth,
			double endpointSecant, double neighborSecant) {
		double slope = ((2 * endpointWidth + neighborWidth) * endpointSecant
				- endpointWidth * neighborSecant) / (endpointWidth + neighborWidth);
		if (Math.signum(slope) != Math.signum(endpointSecant)) return 0;
		if (Math.signum(endpointSecant) != Math.signum(neighborSecant)
				&& Math.abs(slope) > 3 * Math.abs(endpointSecant)) {
			return 3 * endpointSecant;
		}
		return slope;
	}
}
