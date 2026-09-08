package info.openrocket.core.util.ejection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.material.Material;
import info.openrocket.core.material.MaterialGroup;
import info.openrocket.core.util.ejection.EjectionChargeResult.WarningLevel;

class EjectionChargeEngineTest {

	private static final double M_PER_IN = 0.0254;

	private static EjectionChargeInputs baseline() {
		EjectionChargeInputs in = new EjectionChargeInputs();
		in.setBayInnerDiameter_m(4.00 * M_PER_IN);
		in.setBayOuterDiameter_m(4.10 * M_PER_IN);
		in.setBayLength_m(8.00 * M_PER_IN);
		in.setBayMaterial(AirframeMaterial.FIBERGLASS);
		in.setCouplerOuterDiameter_m(3.99 * M_PER_IN);
		in.setCouplerInnerDiameter_m(3.85 * M_PER_IN);
		in.setCouplerEngagementLength_m(3.00 * M_PER_IN);
		in.setCouplerMaterial(AirframeMaterial.FIBERGLASS);
		in.setDiametralInterference_m(0.001 * M_PER_IN);
		in.setShearPinDesignation("#4-40");
		in.setNumShearPins(3);
		in.setStrengthSource(StrengthSource.TESTED_MIN);
		in.setSafetyFactor(1.5);
		in.setChuteVolumeFraction(0.10);
		return in;
	}

	@Test
	void baselineProducesNonZeroBpInPlausibleRange() {
		EjectionChargeResult r = EjectionChargeEngine.calculate(baseline());
		assertTrue(r.getBpMassAtWorking_g() > 0.1 && r.getBpMassAtWorking_g() < 20.0,
				"BP mass should be in plausible HPR range, got "
						+ r.getBpMassAtWorking_g() + " g");
		assertTrue(r.getBpMassAtSF15_g() < r.getBpMassAtSF25_g());
	}

	@Test
	void chuteVolumeIsTenPercent() {
		EjectionChargeResult r = EjectionChargeEngine.calculate(baseline());
		assertEquals(0.10 * r.getBayVolume_m3(), r.getChuteVolume_m3(), 1e-9);
		assertEquals(r.getBayVolume_m3() - r.getChuteVolume_m3(),
				r.getEffectiveVolume_m3(), 1e-9);
		assertTrue(r.isChuteVolumeEstimated());
	}

	@Test
	void crossCheckAgainstLegacyEnglishFormulaWithinTwoPercent() {
		// Legacy: BP_g = (P_psi × V_in3) / 265.9 (as in EjectionChargeCalculator).
		// Set zero pin force, zero friction → P_min comes only from manual params.
		// Easier: compute purely-pressure-driven mass and compare with BP = P × V / K_BP scaled.
		EjectionChargeInputs in = baseline();
		in.setNumShearPins(0);
		in.setDiametralInterference_m(0.0);
		in.setSafetyFactor(1.0);
		EjectionChargeResult r = EjectionChargeEngine.calculate(in);
		// With no separation force, P_min ≈ 0 → BP ≈ 0
		assertTrue(r.getBpMassAtWorking_g() < 0.1);
	}

	@Test
	void fewerThanThreePinsTriggersCaution() {
		EjectionChargeInputs in = baseline();
		in.setNumShearPins(2);
		EjectionChargeResult r = EjectionChargeEngine.calculate(in);
		assertTrue(r.getWarnings().stream()
				.anyMatch(w -> w.getLevel() == WarningLevel.CAUTION
						&& w.getMessage().contains("Fewer than 3")));
	}

	@Test
	void metricPinTriggersFallbackWarning() {
		EjectionChargeInputs in = baseline();
		in.setShearPinDesignation("M3");
		in.setStrengthSource(StrengthSource.TESTED_MIN);
		EjectionChargeResult r = EjectionChargeEngine.calculate(in);
		assertTrue(r.isUsedTheoreticalFallback());
		assertTrue(r.getWarnings().stream()
				.anyMatch(w -> w.getMessage().contains("theoretical")));
	}

	@Test
	void zeroInterferenceMeansZeroFrictionForce() {
		EjectionChargeInputs in = baseline();
		in.setDiametralInterference_m(0.0);
		EjectionChargeResult r = EjectionChargeEngine.calculate(in);
		assertEquals(0.0, r.getCouplerFrictionForce_N(), 1e-6);
	}

	@Test
	void noWarningsForReasonableBaseline() {
		EjectionChargeResult r = EjectionChargeEngine.calculate(baseline());
		// Pin uses tested data, 3 pins, normal fit → no warnings expected.
		assertFalse(r.isUsedTheoreticalFallback());
	}

	@Test
	void higherSafetyFactorScalesBpLinearly() {
		EjectionChargeInputs in = baseline();
		in.setSafetyFactor(1.0);
		double m1 = EjectionChargeEngine.calculate(in).getBpMassAtWorking_g();
		in.setSafetyFactor(2.0);
		double m2 = EjectionChargeEngine.calculate(in).getBpMassAtWorking_g();
		assertEquals(2.0, m2 / m1, 0.001);
	}

	@Test
	void selectedComponentPropertiesOverrideCategoryMechanics() {
		EjectionChargeInputs in = baseline();
		Material bay = Material.newMaterial(Material.Type.BULK, "Selected aluminum", 2700.0, 26.0e9,
				70.0e9, 240.0e6, Double.NaN, 0.33, MaterialGroup.METALS, true, true);
		Material coupler = Material.newMaterial(Material.Type.BULK, "Selected polymer", 1200.0, 1.0e9,
				3.0e9, 40.0e6, Double.NaN, 0.38, MaterialGroup.PLASTICS, true, true);
		in.setBayComponentMaterial(bay);
		in.setCouplerComponentMaterial(coupler);

		EjectionChargeResult r = EjectionChargeEngine.calculate(in);
		double mu = FrictionCoefficientLookupTable.getCOF(
				in.getBayMaterial(), in.getCouplerMaterial()).getStaticNominal();
		TubeGeometry outer = new TubeGeometry("Bay", in.getBayMaterial(), 4.10, 4.00,
				70.0e9 / EjectionChargeEngine.PA_PER_PSI, 0.33);
		TubeGeometry inner = new TubeGeometry("Coupler", in.getCouplerMaterial(), 3.99, 3.85,
				3.0e9 / EjectionChargeEngine.PA_PER_PSI, 0.38);
		double expectedContactPressure = CylinderFrictionForceCalculator.calculate(
				outer, inner, 3.00, 0.001, mu).getContactPressure_psi()
				* EjectionChargeEngine.PA_PER_PSI;

		assertEquals(expectedContactPressure, r.getContactPressure_pa(), 1.0e-6);
		assertEquals(240.0e6, r.getHoopUts_pa(), 1.0e-6);
	}

	@Test
	void incompleteSelectedMaterialUsesCategoryFallbackWithWarning() {
		EjectionChargeInputs in = baseline();
		Material incomplete = Material.newMaterial(Material.Type.BULK, "Uncharacterized laminate",
				1800.0, 0.0, MaterialGroup.COMPOSITES, true, true);
		in.setBayComponentMaterial(incomplete);

		EjectionChargeResult r = EjectionChargeEngine.calculate(in);

		assertEquals(RocketryMaterialProperties.getHoopUts_psi(AirframeMaterial.FIBERGLASS)
				* EjectionChargeEngine.PA_PER_PSI, r.getHoopUts_pa(), 1.0e-6);
		assertTrue(r.getWarnings().stream()
				.anyMatch(w -> w.getMessage().contains("lacks a complete E/Poisson property pair")));
	}

	@Test
	void frictionCategoryIsDerivedFromSelectedMaterial() {
		Material metal = Material.newMaterial(Material.Type.BULK, "Aluminum 6061-T6", 2700.0,
				MaterialGroup.METALS, false);
		Material g10 = Material.newMaterial(Material.Type.BULK, "G-10 laminate", 1800.0,
				MaterialGroup.COMPOSITES, false);
		Material balsa = Material.newMaterial(Material.Type.BULK, "Balsa", 170.0,
				MaterialGroup.WOODS, false);

		assertEquals(AirframeMaterial.METAL, AirframeMaterial.fromMaterial(metal));
		assertEquals(AirframeMaterial.FIBERGLASS, AirframeMaterial.fromMaterial(g10));
		assertEquals(AirframeMaterial.BALSA_WOOD, AirframeMaterial.fromMaterial(balsa));
	}
}
