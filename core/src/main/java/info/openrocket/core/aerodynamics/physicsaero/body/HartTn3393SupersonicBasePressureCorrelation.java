package info.openrocket.core.aerodynamics.physicsaero.body;

import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicBaseDragModel;

/**
 * Unpowered turbulent blunt-base correlation with a source-consistent
 * transonic-to-supersonic handoff.
 *
 * <p>Hart NACA RM L52E06 owns the measured transonic curve through M=1.30.
 * A C1 bridge reaches the value and slope of the empirical
 * {@code 0.064 + 0.186/M^2} correlation at M=1.50.  That empirical form is
 * independently checked against the turbulent NACA TN 3393 points over
 * M=2.73--4.98 and is consistent with the ESDU 77021 form.  Its use from
 * M=1.50 to 2.73 is explicitly an extrapolation below the direct data range.
 */
public final class HartTn3393SupersonicBasePressureCorrelation
		implements BasePressureCorrelation {
	public static final String METHOD_ID =
			"HART_L52E06_TN3393_TURBULENT_BASE_CP_V1";
	public static final String SOURCE =
			"NACA RM L52E06; NACA TN 3393 turbulent base pressure; "
			+ "ESDU 77021-form empirical correlation";
	private static final double HANDOFF_MACH = 1.50;
	private static final double DIRECT_MINIMUM_MACH = 2.73;
	private static final double MAXIMUM_MACH = 5.0;

	@Override
	public String methodId() {
		return METHOD_ID;
	}

	@Override
	public String source() {
		return SOURCE;
	}

	@Override
	public Validity validity(double mach, boolean powered) {
		if (powered) {
			return new Validity(false,
					"POWERED_BASE_MODEL_NOT_IMPLEMENTED", Double.NaN);
		}
		if (mach < 1.2 || mach > MAXIMUM_MACH) {
			return new Validity(false,
					"OUTSIDE_HART_TN3393_BASE_CORRELATION_RANGE",
					Double.NaN);
		}
		if (mach < HANDOFF_MACH) {
			return new Validity(true,
					"HART_TRANSONIC_AND_C1_HANDOFF", 0.12);
		}
		if (mach < DIRECT_MINIMUM_MACH) {
			return new Validity(true,
					"TN3393_FORM_EXTRAPOLATED_BELOW_DIRECT_MACH_RANGE",
					0.25);
		}
		return new Validity(true,
				"TN3393_TURBULENT_DIRECT_MACH_RANGE", 0.16);
	}

	@Override
	public double basePressureCoefficient(double mach, double gamma) {
		Validity validity = validity(mach, false);
		if (!validity.valid()) {
			throw new IllegalArgumentException(validity.reason());
		}
		if (mach <= HANDOFF_MACH) {
			return new TransonicBaseDragModel()
					.basePressureCoefficient(mach, 0);
		}
		return -(0.064 + 0.186 / (mach * mach));
	}
}
