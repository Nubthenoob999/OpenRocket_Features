package info.openrocket.core.util.ejection;

import java.util.Locale;

import info.openrocket.core.util.ejection.CylinderFrictionForceCalculator.FrictionResult;
import info.openrocket.core.util.ejection.EjectionChargeResult.WarningLevel;

/**
 * Black-powder ejection-charge sizing engine. Combines:
 * <ol>
 *   <li>Shear-pin force from {@link ShearPinLookupTable}</li>
 *   <li>Coupler interference friction from {@link CylinderFrictionForceCalculator}</li>
 *   <li>Required pressure {@code P = F/A}</li>
 *   <li>Ideal-gas-law BP mass: {@code m = SF × P × V_eff / K_BP}</li>
 * </ol>
 *
 * <p>The constant {@code K_BP = R × T_combustion × gas_fraction / MW_gas} bakes
 * in BP combustion chemistry (≈42 g/mol average gas MW, ~44% gas yield by
 * mass, ~1837 K adiabatic flame temperature). See pseudocode header for the
 * full derivation and cross-check against the legacy English formula.
 */
public final class EjectionChargeEngine {

	// --- Physical constants ---
	public static final double R_SI = 8.314;                     // J/(mol·K)
	public static final double MW_GAS_KG_PER_MOL = 0.042;        // 42 g/mol
	public static final double GAS_FRACTION_BP = 0.44;           // mass fraction
	public static final double T_COMBUSTION_K = 1837.2;          // 3307 °R

	/** Combined BP sizing constant: BP_kg = SF × P_pa × V_m3 / K_BP. */
	public static final double K_BP =
			R_SI * T_COMBUSTION_K * GAS_FRACTION_BP / MW_GAS_KG_PER_MOL;

	// --- Conversion factors ---
	public static final double PA_PER_PSI = 6894.757;
	public static final double M_PER_IN = 0.0254;
	public static final double N_PER_LBF = 4.44822;

	/**
	 * Empirical de-rating applied to the Lamé-derived coupler friction force.
	 * Real coupler fits include lubrication from talc/graphite, surface
	 * polish, and slight clearance after assembly cycles, so the textbook
	 * Lamé prediction over-estimates the breakaway friction by roughly 15–20%
	 * compared with bench-pull data. We multiply the friction force by this
	 * factor before combining it with the shear-pin force.
	 */
	public static final double FRICTION_DERATING_FACTOR = 0.6;

	private EjectionChargeEngine() {
	}

	/**
	 * Run the full ejection-charge calculation for a given input set.
	 */
	public static EjectionChargeResult calculate(EjectionChargeInputs inputs) {
		if (inputs == null) {
			throw new IllegalArgumentException("Inputs must not be null");
		}

		EjectionChargeResult result = new EjectionChargeResult();
		result.setInputs(inputs);

		// =====================================================================
		// STEP 1 — Volumes
		// =====================================================================
		double bayInnerD_m = inputs.getBayInnerDiameter_m();
		double bayLen_m = inputs.getBayLength_m();
		if (bayInnerD_m <= 0.0 || bayLen_m <= 0.0) {
			throw new IllegalArgumentException(
					"Bay inner diameter and length must be > 0");
		}
		double A_m2 = Math.PI * (bayInnerD_m * 0.5) * (bayInnerD_m * 0.5);
		double V_bay = A_m2 * bayLen_m;

		double V_chute;
		boolean chuteEstimated;
		double absChuteVol = inputs.getChutePackedVolume_m3();
		if (absChuteVol > 0.0) {
			// Absolute packed volume from the rocket model takes precedence.
			if (absChuteVol >= V_bay) {
				result.addWarning(WarningLevel.WARNING,
						"Packed chute volume meets or exceeds bay volume; "
								+ "clamping to 95% of bay volume.");
				V_chute = 0.95 * V_bay;
			} else {
				V_chute = absChuteVol;
			}
			chuteEstimated = false;
		} else {
			double chuteFraction = inputs.getChuteVolumeFraction();
			if (chuteFraction < 0.0 || chuteFraction >= 1.0) {
				throw new IllegalArgumentException(
						"Chute volume fraction must be in [0, 1)");
			}
			V_chute = V_bay * chuteFraction;
			chuteEstimated = true;
		}
		double V_eff = V_bay - V_chute;

		result.setBayVolume_m3(V_bay);
		result.setChuteVolume_m3(V_chute);
		result.setEffectiveVolume_m3(V_eff);
		result.setChuteVolumeEstimated(chuteEstimated);
		result.setCrossSectionArea_m2(A_m2);

		// =====================================================================
		// STEP 2a — Shear pin force
		// =====================================================================
		ShearPinSpec spec = ShearPinLookupTable.getSpec(inputs.getShearPinDesignation());
		StrengthSource source = inputs.getStrengthSource();
		boolean wantsTested = (source == StrengthSource.TESTED_MIN
				|| source == StrengthSource.TESTED_MAX);
		boolean fellBack = wantsTested && !spec.hasTestedData();

		double shearForce_lbs = ShearPinLookupTable.calcTotalShearForce(
				inputs.getShearPinDesignation(),
				inputs.getNumShearPins(),
				source);
		double shearForce_N = shearForce_lbs * N_PER_LBF;
		result.setShearPinForce_N(shearForce_N);
		result.setUsedTheoreticalFallback(fellBack);

		if (fellBack) {
			result.addWarning(WarningLevel.CAUTION,
					"No tested-strength data available for " + spec.getDesignation()
							+ "; using theoretical values. Verify with a ground test.");
		}
		if (inputs.getNumShearPins() < 3) {
			result.addWarning(WarningLevel.CAUTION,
					"Fewer than 3 shear pins. Minimum recommended is 3, evenly "
							+ "spaced. Asymmetric loading can cause coupler cocking.");
		}

		// =====================================================================
		// STEP 2b — Coupler interference friction (Lamé)
		// =====================================================================
		double fricForce_N = 0.0;
		double P_contact_psi = 0.0;
		double C_outer = 0.0;
		double C_inner = 0.0;

		double couplerOD_m = inputs.getCouplerOuterDiameter_m();
		double couplerID_m = inputs.getCouplerInnerDiameter_m();
		double couplerL_m = inputs.getCouplerEngagementLength_m();
		double bayOD_m = inputs.getBayOuterDiameter_m();
		double delta_m = inputs.getDiametralInterference_m();

		boolean haveCouplerGeometry = couplerOD_m > 0.0
				&& couplerID_m >= 0.0
				&& couplerL_m > 0.0
				&& bayOD_m > bayInnerD_m;

		if (haveCouplerGeometry) {
			double mu_s = FrictionCoefficientLookupTable.getCOF(
					inputs.getBayMaterial(), inputs.getCouplerMaterial())
					.getStaticNominal();

			FrictionResult fr = CylinderFrictionForceCalculator.calculate(
					inputs.getBayMaterial(),
					inputs.getCouplerMaterial(),
					bayInnerD_m / M_PER_IN,
					bayOD_m / M_PER_IN,
					couplerID_m / M_PER_IN,
					couplerOD_m / M_PER_IN,
					couplerL_m / M_PER_IN,
					delta_m / M_PER_IN,
					mu_s);

			fricForce_N = fr.getFrictionForce_lbs() * N_PER_LBF * FRICTION_DERATING_FACTOR;
			P_contact_psi = fr.getContactPressure_psi();
			C_outer = fr.getLameFactorOuter();
			C_inner = fr.getLameFactorInner();

			double fricForce_lbs_derated = fr.getFrictionForce_lbs() * FRICTION_DERATING_FACTOR;
			if (fricForce_lbs_derated > shearForce_lbs && shearForce_lbs > 0.0) {
				result.addWarning(WarningLevel.CAUTION, String.format(Locale.ROOT,
						"Coupler interference friction (%.1f lbs) exceeds shear pin "
								+ "force (%.1f lbs). Consider sanding the shoulder to "
								+ "reduce interference toward 0.000\u20130.002\".",
						fricForce_lbs_derated, shearForce_lbs));
			}
		}

		result.setCouplerFrictionForce_N(fricForce_N);
		result.setContactPressure_pa(P_contact_psi * PA_PER_PSI);
		result.setLameFactorOuter(C_outer);
		result.setLameFactorInner(C_inner);

		// =====================================================================
		// STEP 3 — Required pressure
		// =====================================================================
		double F_total_N = shearForce_N + fricForce_N;
		double P_min_pa = F_total_N / A_m2;
		result.setTotalSeparationForce_N(F_total_N);
		result.setMinRequiredPressure_pa(P_min_pa);

		if (P_min_pa < 5.0 * PA_PER_PSI) {
			result.addWarning(WarningLevel.WARNING,
					"Minimum required pressure is below 5 psi. Verify shear pin "
							+ "count and interference value.");
		}
		if (P_min_pa > 30.0 * PA_PER_PSI) {
			result.addWarning(WarningLevel.WARNING,
					"Minimum required pressure exceeds 30 psi. Verify fit geometry "
							+ "and number of shear pins.");
		}

		// =====================================================================
		// STEP 4 — BP mass
		// =====================================================================
		double SF = inputs.getSafetyFactor();
		if (SF <= 0.0) {
			throw new IllegalArgumentException("Safety factor must be > 0");
		}
		double P_working_pa = SF * P_min_pa;
		result.setWorkingPressure_pa(P_working_pa);

		double n_mol = (P_working_pa * V_eff) / (R_SI * T_COMBUSTION_K);
		result.setMolesGasRequired_mol(n_mol);

		double bpAtWorking_g = bpMassGrams(SF, P_min_pa, V_eff);
		double bpAtSF15_g = bpMassGrams(1.5, P_min_pa, V_eff);
		double bpAtSF25_g = bpMassGrams(2.5, P_min_pa, V_eff);
		result.setBpMassAtWorking_g(bpAtWorking_g);
		result.setBpMassAtSF15_g(bpAtSF15_g);
		result.setBpMassAtSF25_g(bpAtSF25_g);

		if (bpAtWorking_g < 0.1) {
			result.addWarning(WarningLevel.WARNING,
					"Calculated BP mass is very small (< 0.1 g). May not reliably "
							+ "ignite; consider a minimum charge of 0.3 g.");
		}
		if (bpAtWorking_g > 20.0) {
			result.addWarning(WarningLevel.WARNING,
					"Calculated BP mass exceeds 20 g. Verify bay volume, shear pin "
							+ "selection, and interference. Charges this large can "
							+ "damage the airframe or chute on deployment.");
		}

		// =====================================================================
		// STEP 5 — Airframe burst check (thin-wall hoop stress)
		// =====================================================================
		double bayOD = inputs.getBayOuterDiameter_m();
		double wallThk_m = (bayOD - bayInnerD_m) * 0.5;
		if (wallThk_m > 0.0) {
			double r_m = bayInnerD_m * 0.5;
			double hoopStress_pa = P_working_pa * r_m / wallThk_m;
			double uts_pa = RocketryMaterialProperties.getHoopUts_psi(
					inputs.getBayMaterial()) * PA_PER_PSI;
			double margin = uts_pa > 0.0 ? hoopStress_pa / uts_pa : 0.0;
			result.setHoopStress_pa(hoopStress_pa);
			result.setHoopUts_pa(uts_pa);
			result.setHoopStressRatio(margin);
			if (margin >= 1.0) {
				result.addWarning(WarningLevel.WARNING, String.format(Locale.ROOT,
						"BURST RISK: at the working pressure (%.1f psi) the bay "
								+ "hoop stress (%.0f psi) meets or exceeds the %s "
								+ "airframe's ultimate tensile strength (%.0f psi). "
								+ "This charge has a real chance of bursting the bay. "
								+ "Reduce the BP mass, add vent holes, or use a stronger tube.",
						P_working_pa * PSI_PER_PA(),
						hoopStress_pa * PSI_PER_PA(),
						inputs.getBayMaterial().getDisplayName(),
						uts_pa * PSI_PER_PA()));
			} else if (margin >= 0.5) {
				result.addWarning(WarningLevel.CAUTION, String.format(Locale.ROOT,
						"Bay hoop stress (%.0f psi) is %.0f%% of the %s ultimate "
								+ "tensile strength (%.0f psi). Margin is below 2\u00d7; "
								+ "verify wall thickness or reduce the charge.",
						hoopStress_pa * PSI_PER_PA(),
						margin * 100.0,
						inputs.getBayMaterial().getDisplayName(),
						uts_pa * PSI_PER_PA()));
			}
		}

		return result;
	}

	private static double PSI_PER_PA() {
		return 1.0 / PA_PER_PSI;
	}

	/** BP mass (grams) for a given safety factor, minimum required pressure (Pa) and effective volume (m³). */
	public static double bpMassGrams(double safetyFactor,
									 double minRequiredPressure_pa,
									 double effectiveVolume_m3) {
		double bp_kg = (safetyFactor * minRequiredPressure_pa * effectiveVolume_m3) / K_BP;
		return bp_kg * 1000.0;
	}
}
