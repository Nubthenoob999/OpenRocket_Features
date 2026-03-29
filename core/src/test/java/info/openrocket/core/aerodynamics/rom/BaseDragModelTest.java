package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BaseDragModelTest {

	@Test
	public void testCdBaseSubsonicAlwaysPositive() {
		double[] cfs = new double[] { 0.001, 0.003, 0.005 };
		double[] loOverD = new double[] { 0.0, 0.5, 2.0, 5.0 };
		for (double cf : cfs) {
			for (double loD : loOverD) {
				assertTrue(BaseDragModel.cdBaseSubsonic(cf, loD) > 0.0);
			}
		}
	}

	@Test
	public void testTransonicMultiplierBoundariesAndPeak() {
		assertEquals(1.0, BaseDragModel.transonicMultiplier(0.0), 1e-12);
		assertEquals(1.0, BaseDragModel.transonicMultiplier(0.6), 1e-12);

		double peak = 0.0;
		for (double m = 0.6; m <= 2.0; m += 0.01) {
			peak = Math.max(peak, BaseDragModel.transonicMultiplier(m));
		}
		assertTrue(peak > 1.8);
	}

	@Test
	public void testTransonicMultiplierDecaysMonotonicallyAboveMachTwo() {
		double prev = BaseDragModel.transonicMultiplier(2.01);
		for (double m = 2.1; m <= 4.0; m += 0.1) {
			double curr = BaseDragModel.transonicMultiplier(m);
			assertTrue(curr <= prev + 1e-9);
			prev = curr;
		}
	}

	@Test
	public void testPlumeOnAlwaysLowerThanOrEqualToPlumeOff() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double[] machValues = new double[] { 0.3, 0.8, 1.5, 3.0 };
		for (double mach : machValues) {
			double off = BaseDragModel.cdBasePlumeOff(mach, 0.003, g);
			double on = BaseDragModel.cdBasePlumeOn(mach, 0.003, g);
			assertTrue(on <= off, "Plume-on must be <= plume-off at M=" + mach);
		}
	}

	@Test
	public void testPlumeOnFallsBackToPlumeOffWhenNoExitArea() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		g.motorExitArea = 0.0;
		g.motorExitDiameter = 0.0;
		double off = BaseDragModel.cdBasePlumeOff(0.9, 0.003, g);
		double on = BaseDragModel.cdBasePlumeOn(0.9, 0.003, g);
		assertEquals(off, on, 1e-12);
	}
}