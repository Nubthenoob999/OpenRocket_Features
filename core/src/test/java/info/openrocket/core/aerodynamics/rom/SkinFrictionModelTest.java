package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SkinFrictionModelTest {

	@Test
	public void testCfIncompressibleMatchesBlasiusAtVeryLowRe() {
		double re = 1000.0;
		double expected = 1.328 / Math.sqrt(re);
		double actual = SkinFrictionModel.cfIncompressible(re, 5e5);
		RomTestFixtures.assertRelativeError(actual, expected, 1e-9);
	}

	@Test
	public void testCfIncompressibleIsPositiveAndBelowFullyTurbulentAtHighRe() {
		double re = 1e7;
		double cf = SkinFrictionModel.cfIncompressible(re, 5e5);
		double cfTurbulentOnly = 0.455 / Math.pow(Math.log10(re), 2.58);
		assertTrue(cf > 0.0);
		assertTrue(cf < cfTurbulentOnly);
	}

	@Test
	public void testVanDriestIdentityAtMachZeroAndReductionAtMachTwo() {
		double cfInc = 0.003;
		RomTestFixtures.assertRelativeError(SkinFrictionModel.vanDriestII(cfInc, 0.0, 1.0), cfInc, 1e-6);
		assertTrue(SkinFrictionModel.vanDriestII(cfInc, 2.0, 1.0) < cfInc);
	}

	@Test
	public void testRoughnessCorrectionLimits() {
		double cfSmooth = 0.003;
		assertEquals(cfSmooth, SkinFrictionModel.cfWithRoughness(cfSmooth, 0.5, 0.0), 1e-12);
		assertTrue(SkinFrictionModel.cfWithRoughness(cfSmooth, 0.5, 1e-2) > cfSmooth);
	}

	@Test
	public void testBodyFormFactorSlenderVsBlunt() {
		assertTrue(SkinFrictionModel.bodyFormFactor(0.02, 1.0) < 1.05);
		assertTrue(SkinFrictionModel.bodyFormFactor(0.5, 1.0) > 1.20);
	}

	@Test
	public void testBodyFormFactorAtTenToOneSlenderness() {
		double ff = SkinFrictionModel.bodyFormFactor(0.1, 1.0);
		assertEquals(1.0544, ff, 0.01);
	}

	@Test
	public void testCdFrictionPositiveAndDecreasesWithReynolds() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double cdAt1e6 = SkinFrictionModel.cdFriction(0.5, 1e6, g);
		double cdAt1e7 = SkinFrictionModel.cdFriction(0.5, 1e7, g);
		assertTrue(cdAt1e6 > 0.0);
		assertTrue(cdAt1e6 > cdAt1e7);
	}
}