package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import info.openrocket.core.aerodynamics.physicsaero.blending.RegimeOverlap;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;

/**
 * Average flat-plate skin-friction closure used where a resolved boundary-layer
 * history is not available.  It keeps body and fin ownership separate so a
 * resolved body march can later replace only the body term.
 *
 * <p>The smooth-wall branch uses the Prandtl-Schlichting average turbulent
 * coefficient with a transition-length correction and the standard DATCOM
 * compressibility factor.  Roughness uses the fully rough Schlichting limit.
 * All areas are actual wetted areas and coefficients are referenced to the
 * vehicle reference area.</p>
 */
public final class EngineeringSkinFrictionCorrelation {
	public static final String METHOD_ID =
			"PRANDTL_SCHLICHTING_DATCOM_RASAERO_COMPRESSIBILITY_SKIN_FRICTION_V6";
	public static final String FIN_INTERFERENCE_METHOD_ID =
			"RASAERO_VIRTUAL_FIN_WETTED_AREA_INTERFERENCE_V1";
	public static final double DEFAULT_TRANSITION_REYNOLDS = 500_000;
	private static final double MINIMUM_REYNOLDS = 1_000;
	private static final RegimeOverlap SKIN_FRICTION_HANDOFF = new RegimeOverlap(0.9, 1.1,
			"DATCOM_SUBSONIC_COMPRESSIBILITY_FACTOR",
			"RASAERO_SUPERSONIC_COMPRESSIBILITY_FACTOR");

	public Result evaluate(AeroGeometry geometry, FlowCondition flow) {
		double referenceArea = geometry.references().referenceAreaM2();
		double bodyLength = geometry.references().vehicleLengthM();
		double density = flow.atmosphere().densityKgM3();
		double speed = flow.velocityBody().length();
		double viscosity = flow.atmosphere().dynamicViscosityPaS();
		double edgeTemperature = flow.atmosphere().temperatureK();
		double wallTemperature = edgeTemperature;
		double bodyReynolds = Math.max(MINIMUM_REYNOLDS, density * speed * bodyLength / viscosity);

		double bodyWettedArea = 0;
		double bodyRoughness = 0;
		double bodyTransitionReynolds = Double.POSITIVE_INFINITY;
		boolean bodyFullyTurbulent = false;
		double finWettedArea = 0;
		double finWeightedChord = 0;
		double finWeightedThicknessRatio = 0;
		double finRoughness = 0;
		double finTransitionReynolds = Double.POSITIVE_INFINITY;
		boolean finFullyTurbulent = false;
		double finInterferenceRatioWeight = 0;
		for (AeroComponent component : geometry.components()) {
			if (component.axisymmetricProfile() != null) {
				bodyWettedArea += component.wettedAreaM2();
				bodyRoughness = Math.max(bodyRoughness, component.roughnessM());
				bodyTransitionReynolds = Math.min(bodyTransitionReynolds,
						transitionReynolds(component, flow.mach()));
				bodyFullyTurbulent |= fullyTurbulent(component);
			}
			FinGeometry fin = component.finGeometry();
			if (fin != null) {
				double meanChord = fin.planformAreaM2() / fin.spanM();
				double thicknessRatio = component.localReferences().getOrDefault("thicknessRatio",
						component.localReferences().getOrDefault("thicknessM", 0.0) / meanChord);
				finWettedArea += component.wettedAreaM2();
				finWeightedChord += component.wettedAreaM2() * meanChord;
				finWeightedThicknessRatio += component.wettedAreaM2() * thicknessRatio;
				finRoughness = Math.max(finRoughness, component.roughnessM());
				finTransitionReynolds = Math.min(finTransitionReynolds,
						transitionReynolds(component, flow.mach()));
				finFullyTurbulent |= fullyTurbulent(component);
				finInterferenceRatioWeight += component.wettedAreaM2()
						* virtualFinOverlapRatio(component);
			}
		}

		if (!Double.isFinite(bodyTransitionReynolds)) {
			bodyTransitionReynolds = DEFAULT_TRANSITION_REYNOLDS;
		}
		if (!Double.isFinite(finTransitionReynolds)) {
			finTransitionReynolds = DEFAULT_TRANSITION_REYNOLDS;
		}
		double bodyCf = compressibleAverageCf(bodyReynolds, bodyTransitionReynolds, flow.mach(),
				edgeTemperature, wallTemperature, bodyFullyTurbulent);
		bodyCf = roughnessLimitedCf(bodyCf, bodyLength, bodyRoughness, flow.mach());
		double diameterToLength = geometry.references().maximumBodyDiameterM() / bodyLength;
		double bodyFormFactor = 1 + 1.5 * Math.pow(diameterToLength, 1.5)
				+ 50 * Math.pow(diameterToLength, 3);
		double bodyCd = bodyCf * bodyFormFactor * bodyWettedArea / referenceArea;

		double meanFinChord = finWettedArea > 0 ? finWeightedChord / finWettedArea : 0;
		double meanThicknessRatio = finWettedArea > 0 ? finWeightedThicknessRatio / finWettedArea : 0;
		double finReynolds = meanFinChord > 0
				? Math.max(MINIMUM_REYNOLDS, density * speed * meanFinChord / viscosity) : MINIMUM_REYNOLDS;
		double finCf = finWettedArea > 0
				? roughnessLimitedCf(compressibleAverageCf(finReynolds, finTransitionReynolds, flow.mach(),
						edgeTemperature, wallTemperature, finFullyTurbulent),
						meanFinChord, finRoughness, flow.mach()) : 0;
		double finFormFactor = 1 + 2 * Math.max(0, meanThicknessRatio);
		double finCd = finCf * finFormFactor * finWettedArea / referenceArea;
		double finInterferenceCd = finWettedArea > 0
				? finCd * finInterferenceRatioWeight / finWettedArea : 0;
		return new Result(bodyCd, finCd, finInterferenceCd, bodyCf, finCf,
				bodyReynolds, finReynolds, METHOD_ID);
	}

	private static double virtualFinOverlapRatio(AeroComponent component) {
		FinGeometry fin = component.finGeometry();
		if (fin == null || !(component.rootRadiusM() > 0)
				|| !(fin.spanM() > 0) || !(fin.planformAreaM2() > 0)
				|| !("TRAPEZOIDAL".equals(fin.planform())
						|| "RECTANGULAR".equals(fin.planform()))) {
			return 0;
		}
		double tolerance = Math.max(1.0e-9, 1.0e-6 * fin.spanM());
		double minimumTipX = Double.POSITIVE_INFINITY;
		double maximumTipX = Double.NEGATIVE_INFINITY;
		for (var point : fin.outline()) {
			if (Math.abs(point.radiusM() - fin.spanM()) <= tolerance) {
				minimumTipX = Math.min(minimumTipX, point.xM());
				maximumTipX = Math.max(maximumTipX, point.xM());
			}
		}
		if (!Double.isFinite(minimumTipX + maximumTipX)
				|| maximumTipX < minimumTipX) {
			return 0;
		}
		double tipChord = maximumTipX - minimumTipX;
		double rootChord = fin.rootChordM();
		double centerlineChord = rootChord
				+ (rootChord - tipChord) * component.rootRadiusM() / fin.spanM();
		double virtualOverlapArea = 0.5 * component.rootRadiusM()
				* (rootChord + centerlineChord);
		return Math.max(0, virtualOverlapArea / fin.planformAreaM2());
	}

	private static double transitionReynolds(AeroComponent component, double mach) {
		double minimumMach = component.localReferences().getOrDefault(
				"transitionMachMinimum", Double.NEGATIVE_INFINITY);
		double specified = component.localReferences().getOrDefault(
				"transitionReynolds", DEFAULT_TRANSITION_REYNOLDS);
		if (Double.isNaN(minimumMach) || !Double.isFinite(specified) || specified <= 0) {
			throw new IllegalArgumentException("invalid component transition metadata");
		}
		return mach >= minimumMach ? specified : DEFAULT_TRANSITION_REYNOLDS;
	}

	private static boolean fullyTurbulent(AeroComponent component) {
		double value = component.localReferences().getOrDefault("forceFullyTurbulent", 0.0);
		if (value != 0.0 && value != 1.0) {
			throw new IllegalArgumentException("invalid fully turbulent boundary-layer metadata");
		}
		return value == 1.0;
	}

	public double compressibleAverageCf(double reynolds, double transitionReynolds, double mach,
			double edgeTemperatureK, double wallTemperatureK) {
		return compressibleAverageCf(reynolds, transitionReynolds, mach,
				edgeTemperatureK, wallTemperatureK, false);
	}

	public double compressibleAverageCf(double reynolds, double transitionReynolds, double mach,
			double edgeTemperatureK, double wallTemperatureK, boolean fullyTurbulent) {
		if (!(reynolds > 0) || !(transitionReynolds > 0) || mach < 0
				|| !(edgeTemperatureK > 0) || !(wallTemperatureK > 0)) {
			throw new IllegalArgumentException("invalid skin-friction state");
		}
		double boundedReynolds = Math.max(MINIMUM_REYNOLDS, reynolds);
		double boundedTransition = Math.max(MINIMUM_REYNOLDS, transitionReynolds);
		double turbulent = 0.455 / Math.pow(Math.log10(boundedReynolds), 2.58);
		double mixed;
		if (fullyTurbulent) {
			mixed = turbulent;
		} else {
			double transitionOffset = boundedTransition *
					(0.455 / Math.pow(Math.log10(boundedTransition), 2.58)
							- 1.328 / Math.sqrt(boundedTransition));
			double laminar = 1.328 / Math.sqrt(boundedReynolds);
			mixed = Math.max(laminar, turbulent - transitionOffset / boundedReynolds);
		}
		return mixed * engineeringCompressibilityFactor(mach);
	}

	/** Smooth named overlap between the established subsonic DATCOM branch and
	 * the RASAero supersonic branch. */
	public static double engineeringCompressibilityFactor(double mach) {
		if (!Double.isFinite(mach) || mach < 0) {
			throw new IllegalArgumentException("invalid Mach number");
		}
		double datcom = 1 / Math.pow(1 + 0.144 * mach * mach, 0.65);
		if (mach <= SKIN_FRICTION_HANDOFF.startMach()) return datcom;
		double rasaero = rasaeroCompressibilityFactor(mach);
		if (mach >= SKIN_FRICTION_HANDOFF.endMach()) return rasaero;
		double weight = SKIN_FRICTION_HANDOFF.smoothWeight(mach);
		return datcom + weight * (rasaero - datcom);
	}

	/**
	 * RASAero II v1.0.2 engineering compressibility correction.  The Mach
	 * 1.05--2 branch is the program's linear fit; above Mach 2 it uses the
	 * standard DATCOM factor and holds its Mach-10 endpoint at still higher
	 * Mach numbers.  This belongs only to the unresolved engineering closure;
	 * resolved boundary-layer marches retain their wall-temperature models.
	 */
	public static double rasaeroCompressibilityFactor(double mach) {
		if (!Double.isFinite(mach) || mach < 0) {
			throw new IllegalArgumentException("invalid Mach number");
		}
		if (mach < 1.05) return 1;
		if (mach <= 2) return 1 - 0.256 * (mach - 1);
		if (mach <= 10) return 1 / Math.pow(1 + 0.144 * mach * mach, 0.65);
		return 0.1691;
	}

	public double roughnessLimitedCf(double smoothCf, double lengthM, double roughnessM) {
		return roughnessLimitedCf(smoothCf, lengthM, roughnessM, 0);
	}

	public double roughnessLimitedCf(double smoothCf, double lengthM, double roughnessM,
			double mach) {
		if (!(smoothCf >= 0) || !(lengthM > 0) || roughnessM < 0) {
			throw new IllegalArgumentException("invalid roughness state");
		}
		if (mach < 0 || !Double.isFinite(mach)) {
			throw new IllegalArgumentException("invalid roughness Mach number");
		}
		if (roughnessM == 0) {
			return smoothCf;
		}
		double roughnessLimited = 0.032 * Math.pow(roughnessM / lengthM, 0.2);
		double correctedRoughness = roughnessLimited * roughnessCompressibilityFactor(mach);
		return Math.max(smoothCf, correctedRoughness);
	}

	private static double roughnessCompressibilityFactor(double mach) {
		if (mach <= 0.9) {
			return 1 - 0.1 * mach * mach;
		}
		if (mach >= 1.1) {
			return 1 / (1 + 0.18 * mach * mach);
		}
		double subsonic = 1 - 0.1 * 0.9 * 0.9;
		double supersonic = 1 / (1 + 0.18 * 1.1 * 1.1);
		double weight = (mach - 0.9) / 0.2;
		return subsonic + (supersonic - subsonic) * weight;
	}

	public record Result(double bodyCd, double finCd, double finInterferenceCd,
			double bodyCf, double finCf,
			double bodyReynolds, double finReynolds, String methodId) {
		public Result {
			if (!Double.isFinite(bodyCd + finCd + finInterferenceCd + bodyCf
					+ finCf + bodyReynolds + finReynolds)
					|| bodyCd < 0 || finCd < 0 || finInterferenceCd < 0
					|| bodyCf < 0 || finCf < 0) {
				throw new IllegalArgumentException("invalid skin-friction result");
			}
		}

		public double totalCd() {
			return bodyCd + finCd + finInterferenceCd;
		}
	}
}
