package info.openrocket.core.simulation;

import java.util.Locale;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.masscalc.RigidBody;
import info.openrocket.core.models.wind.WindModel;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;

/**
 * Beta weathercocking compensation helper for ROM-backed launch simulations.
 */
public final class WeathercockingCompensation {

	private static final double MIN_STABILITY_CALIBERS = 1.8;
	private static final double MIN_PROFILE_WIND = 1.998;
	private static final double MPS_TO_MPH = 2.2369362920544;
	private static final double LAUNCH_ANALYSIS_MACH = 0.3;
	private static final double MIN_STABILITY_TO_MASS_RATIO = 0.05;
	private static final double MAX_STABILITY_TO_MASS_RATIO = 1.20;
	private static final double MAX_SIGNED_LAUNCH_ANGLE_DEG = Math.toDegrees(SimulationOptions.MAX_LAUNCH_ROD_ANGLE);

	private static final double DELTA_COEFF_RATIO_WIND = 2.5369;
	private static final double DELTA_COEFF_WIND = -2.6440;
	private static final double DELTA_COEFF_INITIAL = 0.4871;
	private static final double DELTA_OFFSET = 16.1043;

	private static final double BASE_BLEND_DURATION_S = 0.16;
	private static final double BLEND_DURATION_CORRECTION_GAIN = 0.012;
	private static final double BLEND_DURATION_WIND_GAIN = 0.003;
	private static final double MIN_BLEND_DURATION_S = 0.12;
	private static final double MAX_BLEND_DURATION_S = 0.40;
	private static final double BASE_BLEND_FACTOR = 0.10;
	private static final double BLEND_FACTOR_CORRECTION_GAIN = 0.010;
	private static final double MAX_BLEND_FACTOR = 0.32;

	private static final double[] PROFILE_SAMPLE_ALTITUDES_M = { 0.0, 15.0, 35.0, 60.0, 90.0, 125.0, 160.0 };
	private static final double[] PROFILE_SAMPLE_WEIGHTS = { 0.24, 0.20, 0.16, 0.14, 0.11, 0.09, 0.06 };

	public static final FlightDataType TYPE_REQUESTED_EFFECTIVE_ANGLE = FlightDataType.getType(
			"Weathercocking requested rail angle", "wcReq", UnitGroup.UNITS_ANGLE);
	public static final FlightDataType TYPE_APPLIED_EFFECTIVE_ANGLE = FlightDataType.getType(
			"Weathercocking applied rail angle", "wcApp", UnitGroup.UNITS_ANGLE);
	public static final FlightDataType TYPE_APPLIED_CORRECTION_ANGLE = FlightDataType.getType(
			"Weathercocking applied correction", "wcDel", UnitGroup.UNITS_ANGLE);
	public static final FlightDataType TYPE_POST_ROD_BLEND = FlightDataType.getType(
			"Weathercocking post-rod blend", "wcBld", UnitGroup.UNITS_RELATIVE);
	public static final FlightDataType TYPE_PROFILE_WIND_SPEED = FlightDataType.getType(
			"Weathercocking sampled wind speed", "wcWnd", UnitGroup.UNITS_VELOCITY);
	public static final FlightDataType TYPE_STABILITY_CALIBERS = FlightDataType.getType(
			"Weathercocking sampled stability", "wcStb", UnitGroup.UNITS_STABILITY);
	public static final FlightDataType TYPE_STABILITY_TO_MASS_RATIO = FlightDataType.getType(
			"Weathercocking stability-to-mass ratio", "wcRat", UnitGroup.UNITS_NONE);

	private static final String TUMBLE_WARNING =
			"Weathercocking compensation encountered tumbling during the post-rod correction window.";

	private WeathercockingCompensation() {
	}

	public static Prediction evaluate(SimulationConditions conditions, FlightConfiguration configuration) {
		double originalAngle = conditions != null ? conditions.getLaunchRodAngle() : 0.0;
		double launchDirection = conditions != null ? conditions.getLaunchRodDirection() : 0.0;

		if (conditions == null || configuration == null) {
			return Prediction.disabled(originalAngle, launchDirection, Double.NaN, Double.NaN, Double.NaN);
		}

		if (!conditions.isWeathercockingCompensationEnabled()) {
			return Prediction.disabled(originalAngle, launchDirection, Double.NaN, Double.NaN, Double.NaN);
		}

		try {
			double launchAltitudeMsl = conditions.getLaunchSite() != null ? conditions.getLaunchSite().getAltitude() : 0.0;
			double profileWindSpeed = sampleProfileWindSpeed(conditions.getWindModel(), launchAltitudeMsl);
			RigidBody launchMass = MassCalculator.calculateLaunch(configuration);
			AerodynamicCalculator stabilityCalculator = conditions.getBaselineAerodynamicCalculator();
			if (stabilityCalculator == null) {
				stabilityCalculator = conditions.getAerodynamicCalculator();
			}

			double stabilityCalibers = computeLaunchStabilityCalibers(configuration, stabilityCalculator, launchMass);

			double wetMassKg = launchMass.getMass();
			double stabilityToMassRatio = (Double.isFinite(stabilityCalibers) && Double.isFinite(wetMassKg)
					&& wetMassKg > MathUtil.EPSILON) ? stabilityCalibers / wetMassKg : Double.NaN;

			if (!Double.isFinite(stabilityCalibers) || !Double.isFinite(wetMassKg) || wetMassKg <= MathUtil.EPSILON) {
				return Prediction.disabled(originalAngle, launchDirection, profileWindSpeed, stabilityCalibers,
						stabilityToMassRatio);
			}

			if (stabilityCalibers < MIN_STABILITY_CALIBERS || profileWindSpeed < MIN_PROFILE_WIND) {
				return Prediction.disabled(originalAngle, launchDirection, profileWindSpeed, stabilityCalibers,
						stabilityToMassRatio);
			}

			return predictForInputs(originalAngle, launchDirection, profileWindSpeed, stabilityCalibers, wetMassKg);
		} catch (RuntimeException ex) {
			return Prediction.disabled(originalAngle, launchDirection, Double.NaN, Double.NaN, Double.NaN);
		}
	}

	static Prediction predictForInputs(double originalAngleRad, double launchDirectionRad, double profileWindSpeedMps, double stabilityCalibers, double wetMassKg) {
		double windMph = profileWindSpeedMps * MPS_TO_MPH;
		double initialAngleDeg = Math.toDegrees(originalAngleRad);
		double stabilityToMassRatio = stabilityCalibers / wetMassKg;
		double boundedRatio = MathUtil.clamp(stabilityToMassRatio,
				MIN_STABILITY_TO_MASS_RATIO,
				MAX_STABILITY_TO_MASS_RATIO);

		double correctionDeg = DELTA_COEFF_RATIO_WIND * boundedRatio * windMph
				+ DELTA_COEFF_WIND * windMph
				+ DELTA_COEFF_INITIAL * initialAngleDeg
				+ DELTA_OFFSET;
		double requestedAngleDeg = initialAngleDeg + correctionDeg;
		double appliedAngleDeg = MathUtil.clamp(requestedAngleDeg,
				-MAX_SIGNED_LAUNCH_ANGLE_DEG,
				MAX_SIGNED_LAUNCH_ANGLE_DEG);

		boolean clamped = Math.abs(requestedAngleDeg - appliedAngleDeg) > 1.0e-9;
		double correctionMagnitudeDeg = Math.abs(appliedAngleDeg - initialAngleDeg);
		double postRodBlendDurationS = MathUtil.clamp(
				BASE_BLEND_DURATION_S
						+ BLEND_DURATION_CORRECTION_GAIN * correctionMagnitudeDeg
						+ BLEND_DURATION_WIND_GAIN * windMph,
				MIN_BLEND_DURATION_S,
				MAX_BLEND_DURATION_S);
		double peakPostRodBlend = MathUtil.clamp(
				BASE_BLEND_FACTOR + BLEND_FACTOR_CORRECTION_GAIN * correctionMagnitudeDeg,
				BASE_BLEND_FACTOR,
				MAX_BLEND_FACTOR);

		String clampWarning = null;
		if (clamped) {
			clampWarning = String.format(Locale.US,
					"Weathercocking compensation clamped the requested signed rail angle from %.1f deg to %.1f deg.",
					requestedAngleDeg,
					appliedAngleDeg);
		}

		return new Prediction(true,
				originalAngleRad,
				launchDirectionRad,
				Math.toRadians(requestedAngleDeg),
				Math.toRadians(appliedAngleDeg),
				profileWindSpeedMps,
				stabilityCalibers,
				stabilityToMassRatio,
				postRodBlendDurationS,
				peakPostRodBlend,
				clamped,
				clampWarning);
	}

	public static CoordinateIF toLaunchRodDirectionVector(double launchRodAngle, double launchRodDirection) {
		return new Coordinate(
				Math.sin(launchRodAngle) * Math.cos(Math.PI / 2.0 - launchRodDirection),
				Math.sin(launchRodAngle) * Math.sin(Math.PI / 2.0 - launchRodDirection),
				Math.cos(launchRodAngle));
	}

	public static String tumbleWarningMessage() {
		return TUMBLE_WARNING;
	}

	private static double computeLaunchStabilityCalibers(FlightConfiguration configuration,
			AerodynamicCalculator aerodynamicCalculator, RigidBody launchMass) {
		AerodynamicCalculator calculator = aerodynamicCalculator != null ? aerodynamicCalculator : new BarrowmanCalculator();
		FlightConditions launchConditions = new FlightConditions(configuration);
		launchConditions.setMach(LAUNCH_ANALYSIS_MACH);
		launchConditions.setAOA(0.0);
		launchConditions.setRollRate(0.0);

		CoordinateIF cp = calculator.getWorstCP(configuration, launchConditions, new WarningSet());
		CoordinateIF cg = launchMass.getCM();
		if (cp == null || cp.getWeight() <= 1.0e-6 || cg.getWeight() <= 1.0e-6) {
			return Double.NaN;
		}

		return (cp.getX() - cg.getX()) / launchConditions.getRefLength();
	}

	private static double sampleProfileWindSpeed(WindModel windModel, double launchAltitudeMsl) {
		if (windModel == null) {
			return 0.0;
		}

		double weightedSpeed = 0.0;
		double totalWeight = 0.0;
		for (int i = 0; i < PROFILE_SAMPLE_ALTITUDES_M.length; i++) {
			double altitudeAgl = PROFILE_SAMPLE_ALTITUDES_M[i];
			double weight = PROFILE_SAMPLE_WEIGHTS[i];
			CoordinateIF windVelocity = windModel.getWindVelocity(0.0, launchAltitudeMsl + altitudeAgl, altitudeAgl);
			if (windVelocity == null) {
				continue;
			}
			double speed = windVelocity.length();
			if (!Double.isFinite(speed)) {
				continue;
			}
			weightedSpeed += speed * weight;
			totalWeight += weight;
		}

		return totalWeight > 0.0 ? weightedSpeed / totalWeight : 0.0;
	}

	public static final class Prediction {
		private final boolean active;
		private final double originalLaunchAngle;
		private final double launchRodDirection;
		private final double requestedLaunchAngle;
		private final double appliedLaunchAngle;
		private final double profileWindSpeedMps;
		private final double stabilityCalibers;
		private final double stabilityToMassRatio;
		private final double postRodBlendDurationS;
		private final double peakPostRodBlend;
		private final boolean clamped;
		private final String clampWarningText;

		private Prediction(boolean active,
				double originalLaunchAngle,
				double launchRodDirection,
				double requestedLaunchAngle,
				double appliedLaunchAngle,
				double profileWindSpeedMps,
				double stabilityCalibers,
				double stabilityToMassRatio,
				double postRodBlendDurationS,
				double peakPostRodBlend,
				boolean clamped,
				String clampWarningText) {
			this.active = active;
			this.originalLaunchAngle = originalLaunchAngle;
			this.launchRodDirection = launchRodDirection;
			this.requestedLaunchAngle = requestedLaunchAngle;
			this.appliedLaunchAngle = appliedLaunchAngle;
			this.profileWindSpeedMps = profileWindSpeedMps;
			this.stabilityCalibers = stabilityCalibers;
			this.stabilityToMassRatio = stabilityToMassRatio;
			this.postRodBlendDurationS = postRodBlendDurationS;
			this.peakPostRodBlend = peakPostRodBlend;
			this.clamped = clamped;
			this.clampWarningText = clampWarningText;
		}

		static Prediction disabled(double originalLaunchAngle, double launchRodDirection, double profileWindSpeedMps,
				double stabilityCalibers, double stabilityToMassRatio) {
			return new Prediction(false,
					originalLaunchAngle,
					launchRodDirection,
					originalLaunchAngle,
					originalLaunchAngle,
					profileWindSpeedMps,
					stabilityCalibers,
					stabilityToMassRatio,
					0.0,
					0.0,
					false,
					null);
		}

		public boolean isActive() {
			return active;
		}

		public double getRequestedLaunchAngle() {
			return requestedLaunchAngle;
		}

		public double getAppliedLaunchAngle() {
			return appliedLaunchAngle;
		}

		public double getLaunchRodDirection() {
			return launchRodDirection;
		}

		public double getAppliedCorrectionAngle() {
			return appliedLaunchAngle - originalLaunchAngle;
		}

		public double getProfileWindSpeedMps() {
			return profileWindSpeedMps;
		}

		public double getStabilityCalibers() {
			return stabilityCalibers;
		}

		public double getStabilityToMassRatio() {
			return stabilityToMassRatio;
		}

		public boolean isClamped() {
			return clamped;
		}

		public String getClampWarningText() {
			return clampWarningText;
		}

		public double getBlendFactor(double timeSinceRodClear) {
			if (!active || timeSinceRodClear < 0.0 || timeSinceRodClear >= postRodBlendDurationS
					|| postRodBlendDurationS <= 0.0 || peakPostRodBlend <= 0.0) {
				return 0.0;
			}

			double normalized = 1.0 - timeSinceRodClear / postRodBlendDurationS;
			return peakPostRodBlend * normalized * normalized;
		}
	}
}