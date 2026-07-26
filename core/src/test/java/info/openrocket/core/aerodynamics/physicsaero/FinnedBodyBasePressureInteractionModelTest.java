package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBodyBasePressureInteractionModel;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBodyBasePressureInteractionModel.BaseState;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBodyBasePressureInteractionModel.FinSet;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBodyBasePressureInteractionModel.Result;

class FinnedBodyBasePressureInteractionModelTest {
	private final FinnedBodyBasePressureInteractionModel model =
			new FinnedBodyBasePressureInteractionModel();

	@Test
	void a53D02GeometryRecoversTheBroadFigureNineLowSupersonicBasePressureScale() {
		double diameter = 0.25;
		double bodyBaseArea = Math.PI * diameter * diameter / 4;
		double planformAreaPerFin = 0.5 * (0.75 + 0.375) * 0.125;
		BaseState base = new BaseState(2.0, false, -0.25 / 2.0,
				bodyBaseArea, diameter);
		Result result = model.evaluate(base,
				List.of(new FinSet(4, planformAreaPerFin, 0.04, 0)));

		assertEquals(FinnedBodyBasePressureInteractionModel.REFERENCE_FIN_SOLIDITY,
				result.effectiveFinSolidity(), 1e-15);
		assertEquals(1, result.geometryInfluence(), 1e-15);
		assertTrue(result.correctedBodyBasePressureCoefficient() < -0.17);
		assertTrue(result.correctedBodyBasePressureCoefficient() > -0.20);
		assertEquals(result.correctedBodyBasePressureCoefficient(),
				result.finBasePressureCoefficient(), 0);
		assertTrue(result.validityFlags().contains("BODY_AND_FIN_BASE_PRESSURE_EQUAL"));
		assertTrue(result.confidence() <= 0.20);
	}

	@Test
	void interactionIsMonotoneInCountPlanformThicknessAndBaseProximity() {
		BaseState base = base(1.9, -0.15);
		FinSet nominal = new FinSet(1, 0.0010, 0.02, 0.01);
		double nominalIncrement = suctionIncrement(base, nominal);

		assertTrue(suctionIncrement(base, new FinSet(2, 0.0010, 0.02, 0.01))
				> nominalIncrement);
		assertTrue(suctionIncrement(base, new FinSet(1, 0.0020, 0.02, 0.01))
				> nominalIncrement);
		assertTrue(suctionIncrement(base, new FinSet(1, 0.0010, 0.04, 0.01))
				> nominalIncrement);
		assertTrue(suctionIncrement(base, new FinSet(1, 0.0010, 0.02, 0.0))
				> nominalIncrement);
		assertTrue(suctionIncrement(base, new FinSet(1, 0.0010, 0.02, 0.05))
				< nominalIncrement);
	}

	@Test
	void correctionIsBoundedAbsolutelyRelativelyAndByGeometrySaturation() {
		BaseState smallPrimaryPressure = base(1.9, -0.02);
		Result result = model.evaluate(smallPrimaryPressure,
				List.of(new FinSet(100, 10, 0.5, 0)));

		assertEquals(1, result.geometryInfluence(), 0);
		assertEquals(-0.01, result.pressureCoefficientIncrement(), 1e-15);
		assertTrue(-result.pressureCoefficientIncrement()
				<= FinnedBodyBasePressureInteractionModel.MAX_ABSOLUTE_PRESSURE_INCREMENT);
		assertTrue(-result.pressureCoefficientIncrement()
				<= FinnedBodyBasePressureInteractionModel.MAX_RELATIVE_PRESSURE_INCREMENT
						* Math.abs(result.primaryBodyBasePressureCoefficient()));
		assertEquals(0.01 * 0.003 / 0.005,
				result.pressureDragCoefficientIncrement(0.003, 0.005), 1e-15);
		assertThrows(IllegalArgumentException.class,
				() -> result.pressureDragCoefficientIncrement(1, 0));
	}

	@Test
	void noFinSolidityProducesNoCorrectionAndMachEnvelopeDecays() {
		BaseState lowSupersonic = base(1.9, -0.15);
		Result noFins = model.evaluate(lowSupersonic, List.of());
		assertEquals(0, noFins.pressureCoefficientIncrement(), 0);
		assertEquals(noFins.primaryBodyBasePressureCoefficient(),
				noFins.correctedBodyBasePressureCoefficient(), 0);
		assertTrue(noFins.validityFlags().contains("NO_FIN_SOLIDITY_INTERACTION"));

		FinSet finSet = new FinSet(4, 0.003, 0.04, 0);
		double lowMachIncrement = suctionIncrement(lowSupersonic, finSet);
		double highMachIncrement = suctionIncrement(base(4.5, -0.25 / 4.5), finSet);
		assertTrue(lowMachIncrement > highMachIncrement * 100);
	}

	@Test
	void interactionDevelopsOnlyAsFinMachLinesSweepTheBase() {
		FinSet finSet = new FinSet(4, 0.003, 0.04, 0);
		double onset = suctionIncrement(base(1.2, -0.25 / 1.2), finSet);
		double early = suctionIncrement(base(1.5, -0.25 / 1.5), finSet);
		double developing = suctionIncrement(base(1.7, -0.25 / 1.7), finSet);
		double developed = suctionIncrement(base(1.9, -0.25 / 1.9), finSet);

		assertEquals(0, onset, 0);
		assertTrue(early > onset);
		assertTrue(developing > early);
		assertTrue(developed > developing);
	}

	@Test
	void unsupportedPoweredAndMachStatesFailExplicitly() {
		assertThrows(IllegalArgumentException.class, () -> model.evaluate(
				new BaseState(2, true, -0.1, 0.002, 0.05), List.of()));
		assertThrows(IllegalArgumentException.class, () -> model.evaluate(
				base(1.19, -0.1), List.of()));
		assertThrows(IllegalArgumentException.class, () -> model.evaluate(
				base(6.51, -0.1), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new FinSet(4, 0.003, -0.01, 0));
	}

	private static BaseState base(double mach, double pressureCoefficient) {
		return new BaseState(mach, false, pressureCoefficient, 0.002, 0.05);
	}

	private double suctionIncrement(BaseState base, FinSet finSet) {
		return -model.evaluate(base, List.of(finSet)).pressureCoefficientIncrement();
	}
}
