package info.openrocket.core.aerodynamics.physicsaero.interpolation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;

class TableQueryEngineReynoldsCorrectionTest {
	private static final String METHOD = "TEST_LOG_RE_SENSITIVITY";

	@Test
	void blendsDimensionlessDomainsWhenReferenceReynoldsChangesWithMach() {
		RuntimeCorrectionData lower = correction(1_000_000, 0.8, 1.2,
				new double[] {0.01, 0, 0, 0, 0, 0},
				new double[] {0.002, 0, 0, 0, 0, 0});
		RuntimeCorrectionData upper = correction(2_000_000, 0.8, 1.2,
				new double[] {0.03, 0, 0, 0, 0, 0},
				new double[] {0.006, 0, 0, 0, 0, 0});

		RuntimeCorrectionData blended = TableQueryEngine.blendCorrection(lower, upper, 0.5);

		assertFalse(blended.requiresRebuild(),
				"changing Mach-node reference Reynolds must not invalidate a shared Re/Re_ref domain");
		assertEquals(1_500_000, blended.referenceReynolds(), 0);
		assertEquals(0.8, blended.minimumRatio(), 0);
		assertEquals(1.2, blended.maximumRatio(), 0);
		assertArrayEquals(new double[] {0.02, 0, 0, 0, 0, 0},
				blended.dCoefficientDLogRe(), 1e-15);
		assertArrayEquals(new double[] {0.004, 0, 0, 0, 0, 0},
				blended.dCoefficientDLogReSquared(), 1e-15);
		assertArrayEquals(new double[] {0, 0, 0, 0, 0, 0},
				blended.dCoefficientDLogReCubed(), 1e-15);
		assertTrue(blended.supportsRatio(1));
	}

	@Test
	void intersectsEndpointDomainsAsDimensionlessRatios() {
		RuntimeCorrectionData lower = correction(1_000_000, 0.7, 1.15,
				new double[] {0.01, 0, 0, 0, 0, 0});
		RuntimeCorrectionData upper = correction(3_000_000, 0.9, 1.4,
				new double[] {0.03, 0, 0, 0, 0, 0});

		RuntimeCorrectionData blended = TableQueryEngine.blendCorrection(lower, upper, 0.25);

		assertFalse(blended.requiresRebuild());
		assertEquals(1_500_000, blended.referenceReynolds(), 0);
		assertEquals(0.9, blended.minimumRatio(), 0);
		assertEquals(1.15, blended.maximumRatio(), 0);
		assertArrayEquals(new double[] {0.015, 0, 0, 0, 0, 0},
				blended.dCoefficientDLogRe(), 1e-15);
	}

	@Test
	void retainsExplicitRebuildRequirement() {
		RuntimeCorrectionData valid = correction(1_000_000, 0.8, 1.2,
				new double[] {0.01, 0, 0, 0, 0, 0});

		RuntimeCorrectionData blended = TableQueryEngine.blendCorrection(
				valid, RuntimeCorrectionData.rebuildRequired(2_000_000), 0.5);

		assertTrue(blended.requiresRebuild());
		assertEquals(1_500_000, blended.referenceReynolds(), 0);
		assertTrue(blended.supportsRatio(1 + 5.0e-4),
				"the directly generated reference state remains valid despite roundoff");
		assertFalse(blended.supportsRatio(1.002),
				"a topology-sensitive cell cannot correct away from its reference state");
	}

	@Test
	void negligibleRemoteCornerCannotInvalidateDominantCorrectionTopology() {
		RuntimeCorrectionData valid = correction(1_000_000, 0.5, 1.25,
				new double[] {0.01, 0, 0, 0, 0, 0});
		RuntimeCorrectionData rebuild = RuntimeCorrectionData.rebuildRequired(1_000_000);
		List<RuntimeCorrectionData> corners = List.of(
				valid, valid, rebuild, rebuild, valid, valid, rebuild, rebuild);

		RuntimeCorrectionData nearCentral = EventAwareInterpolator.interpolateCorrection(
				corners, new double[] {0.9995, 0, 0.0005, 0, 0, 0, 0, 0});
		RuntimeCorrectionData materiallyBlended = EventAwareInterpolator.interpolateCorrection(
				corners, new double[] {0.99, 0, 0.01, 0, 0, 0, 0, 0});

		assertFalse(nearCentral.requiresRebuild());
		assertTrue(nearCentral.supportsRatio(0.85));
		assertTrue(materiallyBlended.requiresRebuild());
	}

	@Test
	void tinyPositiveMachWeightEstablishesReferenceReynoldsFromMachZeroLimit() {
		RuntimeCorrectionData zero = correction(0, 0.5, 1.25,
				new double[] {0, 0, 0, 0, 0, 0});
		RuntimeCorrectionData positive = correction(1_000_000, 0.5, 1.25,
				new double[] {0.01, 0, 0, 0, 0, 0});
		List<RuntimeCorrectionData> corners = List.of(
				zero, zero, zero, zero, positive, positive, positive, positive);

		RuntimeCorrectionData result = EventAwareInterpolator.interpolateCorrection(
				corners, new double[] {0.9995, 0, 0, 0, 0.0005, 0, 0, 0});

		assertFalse(result.requiresRebuild());
		assertEquals(500, result.referenceReynolds(), 1.0e-12);
	}

	@Test
	void angularBlendAtMachZeroDoesNotPoisonSmallPositiveMachReference() {
		RuntimeCorrectionData zero = correction(0, 0.5, 1.25,
				new double[] {0, 0, 0, 0, 0, 0});
		RuntimeCorrectionData positive = correction(1_000_000, 0.1, 1.25,
				new double[] {0.01, 0, 0, 0, 0, 0});
		List<RuntimeCorrectionData> corners = List.of(
				zero, zero, zero, zero, positive, positive, positive, positive);

		RuntimeCorrectionData result = EventAwareInterpolator.interpolateCorrection(
				corners, new double[] {0.90, 0, 0.095, 0, 0.0045, 0, 0.0005, 0});

		assertFalse(result.requiresRebuild(),
				"valid angular Mach-zero limits must remain valid before the positive-Mach blend");
		assertEquals(5_000, result.referenceReynolds(), 1.0e-12,
				"all positive-Mach angular corners must retain their numerical Reynolds weight");
		assertTrue(result.supportsRatio(0.85));
		assertEquals(0.5, result.minimumRatio(), 0,
				"the common validated domain is the conservative intersection");
		assertEquals(1.25, result.maximumRatio(), 0);
	}

	private static RuntimeCorrectionData correction(double reference, double minimum,
			double maximum, double[] sensitivity) {
		return new RuntimeCorrectionData(reference, minimum, maximum,
				sensitivity, false, METHOD);
	}

	private static RuntimeCorrectionData correction(double reference, double minimum,
			double maximum, double[] sensitivity, double[] curvature) {
		return new RuntimeCorrectionData(reference, minimum, maximum,
				sensitivity, curvature, false, METHOD);
	}
}
