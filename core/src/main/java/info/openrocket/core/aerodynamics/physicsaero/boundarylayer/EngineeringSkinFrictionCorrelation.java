package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import info.openrocket.core.aerodynamics.physicsaero.blending.RegimeOverlap;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.thermal.RecoveryTemperatureModel;
import info.openrocket.core.aerodynamics.physicsaero.thermal.VanDriestIITransformation;

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
	public static final String METHOD_ID = "PRANDTL_SCHLICHTING_DATCOM_SKIN_FRICTION_V3";
	public static final double DEFAULT_TRANSITION_REYNOLDS = 500_000;
	private static final double MINIMUM_REYNOLDS = 1_000;
	private static final RegimeOverlap SKIN_FRICTION_HANDOFF = new RegimeOverlap(0.9, 1.1,
			"DATCOM_COMPRESSIBILITY_FACTOR", VanDriestIITransformation.METHOD_ID);

	public Result evaluate(AeroGeometry geometry, FlowCondition flow) {
		double referenceArea = geometry.references().referenceAreaM2();
		double bodyLength = geometry.references().vehicleLengthM();
		double density = flow.atmosphere().densityKgM3();
		double speed = flow.velocityBody().length();
		double viscosity = flow.atmosphere().dynamicViscosityPaS();
		double edgeTemperature = flow.atmosphere().temperatureK();
		double wallTemperature = new RecoveryTemperatureModel().recoveryTemperatureK(
				edgeTemperature, flow.mach(), flow.thermodynamics().gamma(edgeTemperature), 0.72, true);
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
		return new Result(bodyCd, finCd, bodyCf, finCf, bodyReynolds, finReynolds, METHOD_ID);
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
		double datcomFactor = 1 / Math.pow(1 + 0.144 * mach * mach, 0.65);
		if (mach <= SKIN_FRICTION_HANDOFF.startMach()) {
			return mixed * datcomFactor;
		}
		double vanDriestFactor = new VanDriestIITransformation().compressibilityFactor(
				mach, boundedReynolds, edgeTemperatureK, wallTemperatureK);
		if (mach >= SKIN_FRICTION_HANDOFF.endMach()) {
			return mixed * vanDriestFactor;
		}
		double weight = SKIN_FRICTION_HANDOFF.smoothWeight(mach);
		return mixed * (datcomFactor + (vanDriestFactor - datcomFactor) * weight);
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

	public record Result(double bodyCd, double finCd, double bodyCf, double finCf,
			double bodyReynolds, double finReynolds, String methodId) {
		public Result {
			if (!Double.isFinite(bodyCd + finCd + bodyCf + finCf + bodyReynolds + finReynolds)
					|| bodyCd < 0 || finCd < 0 || bodyCf < 0 || finCf < 0) {
				throw new IllegalArgumentException("invalid skin-friction result");
			}
		}

		public double totalCd() {
			return bodyCd + finCd;
		}
	}
}
