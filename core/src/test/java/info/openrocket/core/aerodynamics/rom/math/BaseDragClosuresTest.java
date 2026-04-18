package info.openrocket.core.aerodynamics.rom.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BaseDragClosuresTest {

	@Test
	public void subsonicAndAreaScaledBaseDragMatchClosure() {
		// Tuned constants: SUBSONIC_INTERCEPT=0.11, SUBSONIC_QUADRATIC_COEFFICIENT=0.14
		assertEquals(-(0.11 + 0.14 * 0.25), BaseDragClosures.coastBaseCp(0.5, 1.4), 1.0e-3);
		assertEquals(-0.11, BaseDragClosures.coastBaseCp(0.0, 1.4), 1.0e-3);
		assertEquals(0.5 * Math.abs(BaseDragClosures.coastBaseCp(2.5, 1.4)),
				BaseDragClosures.coastBaseDrag(2.5, 0.5, 1.0, 1.4), 1.0e-8);
	}

	@Test
	public void closureIsReasonablySmoothAcrossBreakpoints() {
		double[] breakpoints = BaseDragClosures.breakpointCheck();
		assertTrue(Math.abs(breakpoints[1] - breakpoints[0]) < 0.05);
		assertTrue(Math.abs(breakpoints[3] - breakpoints[2]) < 0.05);
		// Kayser BRL MR-3353 transonic peak: |Cp_b| ≈ 0.30-0.38 at M=1.0
		assertTrue(Math.abs(BaseDragClosures.coastBaseCp(1.0, 1.4)) > 0.25);
	}

	@Test
	public void herrinDuttonSubsonicRangeMatchesExpectedBand() {
		// Herrin-Dutton (1994) Table 1: |Cp_b| at various subsonic Mach numbers for a flat-base body.
		// Tolerance ±0.03 absolute.
		double tol = 0.03;
		// M=0.0: Hoerner gives ~0.08-0.12 → our formula: 0.11
		double cp0 = Math.abs(BaseDragClosures.coastBaseCp(0.0, 1.4));
		assertTrue(cp0 >= 0.08 && cp0 <= 0.14, "M=0 |Cp_b| out of Hoerner range: " + cp0);
		// M=0.5: ~0.12-0.16 → our formula: 0.145
		double cp05 = Math.abs(BaseDragClosures.coastBaseCp(0.5, 1.4));
		assertTrue(cp05 >= 0.10 && cp05 <= 0.18, "M=0.5 |Cp_b| out of range: " + cp05);
		// M=0.78 (lower break): ~0.18-0.23 → our formula: ~0.195
		double cp078 = Math.abs(BaseDragClosures.coastBaseCp(0.78, 1.4));
		assertTrue(cp078 >= 0.15 && cp078 <= 0.25, "M=0.78 |Cp_b| out of range: " + cp078);
	}

	@Test
	public void transonicPeakIsInKayserRange() {
		// Kayser BRL MR-3353: peak |Cp_b| ≈ 0.30-0.38 at M ≈ 1.0
		double cpPeak = Math.abs(BaseDragClosures.coastBaseCp(1.0, 1.4));
		assertTrue(cpPeak >= 0.28 && cpPeak <= 0.42, "Transonic peak out of Kayser range: " + cpPeak);
	}

	@Test
	public void supersonicDecaysMonotonicallyAboveUpperBreak() {
		// Above M=1.80 (upper break), Cp_b magnitude should decay with increasing Mach
		double cp18 = Math.abs(BaseDragClosures.coastBaseCp(1.80, 1.4));
		double cp25 = Math.abs(BaseDragClosures.coastBaseCp(2.50, 1.4));
		double cp40 = Math.abs(BaseDragClosures.coastBaseCp(4.00, 1.4));
		assertTrue(cp18 > cp25, "Cp_b not decaying from M=1.8 to M=2.5");
		assertTrue(cp25 > cp40, "Cp_b not decaying from M=2.5 to M=4.0");
	}
}
