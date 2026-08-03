package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBasePressureClosureModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ProtuberanceGeometry;
import info.openrocket.core.util.Coordinate;

class FinnedBasePressureClosureModelTest {
	private final FinnedBasePressureClosureModel model =
			new FinnedBasePressureClosureModel();

	@Test
	void basicFinnerTopologyTargetsTn3393BaselineTimesFourFinWakeScale() {
		double mach = 3;
		double smoothBody = 0.064 + 0.186 / (mach * mach);
		var result = model.evaluate(genericFlatBase(4), mach, 0.25 / mach);

		assertEquals(smoothBody * 1.55,
				result.targetPressureMagnitude(), 1e-12);
		assertTrue(result.pressureMagnitudeIncrement() > 0);
		assertFalse(result.expandingFinCanSleeve());
		assertTrue(result.validityFlags().contains(
				"ADA636861_BASIC_FINNER_WAKE_SCALE"));
		assertTrue(result.validityFlags().contains(
				"TN3393_TURBULENT_BASELINE_DIRECT_MACH_RANGE"));
	}

	@Test
	void threeFinTopologyIsNotExtrapolatedFromFourFinSource() {
		var result = model.evaluate(genericFlatBase(3), 2, 0.125);

		assertEquals(0, result.pressureMagnitudeIncrement(), 0);
		assertTrue(result.validityFlags().contains(
				"FOUR_FIN_SOURCE_TOPOLOGY_NOT_MET"));
	}

	@Test
	void roundedFinSectionIsOutsideBasicFinnerTopology() {
		var result = model.evaluate(genericFlatBase(
				4, "ROUNDED_LEADING_EDGE"), 2, 0.125);

		assertEquals(0, result.pressureMagnitudeIncrement(), 0);
		assertEquals(0, result.finCount());
		assertTrue(result.validityFlags().contains(
				"FOUR_FIN_SOURCE_TOPOLOGY_NOT_MET"));
	}

	@Test
	void threeFinExpandingSleeveIsNotExtrapolatedFromFourFinSource() {
		var result = model.evaluate(expandingSleeve(false, 3),
				2, 0.125);

		assertTrue(result.expandingFinCanSleeve());
		assertEquals(0, result.pressureMagnitudeIncrement(), 0);
		assertTrue(result.validityFlags().contains(
				"FOUR_FIN_SOURCE_TOPOLOGY_NOT_MET"));
	}

	@Test
	void terminalExpandingSleeveIsOutsideBasicFinnerSourceTopology() {
		var sleeve = model.evaluate(expandingSleeve(false, 4), 2, 0.125);

		assertTrue(sleeve.expandingFinCanSleeve());
		assertEquals(0, sleeve.pressureMagnitudeIncrement(), 0);
		assertTrue(sleeve.validityFlags().contains(
				"EXPANDING_FIN_CAN_SLEEVE_OUTSIDE_BASIC_FINNER_SOURCE_TOPOLOGY"));
		assertEquals(0, sleeve.confidence(), 0);
	}

	@Test
	void aftProtuberanceDoesNotMoveTheAxisymmetricBaseOrDisableClosure() {
		AeroGeometry baseline = genericFlatBase(4);
		AeroGeometry extended = withAftProtuberance(baseline);
		var baselineResult = model.evaluate(baseline, 2, 0.125);
		var extendedResult = model.evaluate(extended, 2, 0.125);

		assertEquals(baselineResult.finCount(), extendedResult.finCount());
		assertEquals(baselineResult.targetPressureMagnitude(),
				extendedResult.targetPressureMagnitude(), 1e-15);
		assertEquals(baselineResult.pressureMagnitudeIncrement(),
				extendedResult.pressureMagnitudeIncrement(), 1e-15);
	}

	@Test
	void terminalBoattailIsOutsideBasicFinnerSourceTopology() {
		var boattail = model.evaluate(expandingSleeve(true, 4), 2, 0.125);

		assertFalse(boattail.expandingFinCanSleeve());
		assertEquals(0, boattail.pressureMagnitudeIncrement(), 0);
		assertTrue(boattail.validityFlags().contains(
				"TERMINAL_NONCYLINDRICAL_AFTERBODY_OUTSIDE_BASIC_FINNER_SOURCE_TOPOLOGY"));
	}

	@Test
	void hartEndpointJoinsSmoothlyToSupersonicHandoff() {
		double below = FinnedBasePressureClosureModelAccessor
				.smoothBodyPressureMagnitude(1.3);
		var result = model.evaluate(genericFlatBase(4), 1.3, 0.25);

		assertEquals(0.25, below, 1e-12);
		assertEquals(0.25 * 1.55,
				result.targetPressureMagnitude(), 1e-12);
		assertTrue(result.validityFlags().contains(
				"TN3393_BASELINE_EXTRAPOLATED_BELOW_DIRECT_MACH_RANGE"));
	}

	private static AeroGeometry genericFlatBase(int finCount) {
		return genericFlatBase(finCount, "FLAT_PLATE");
	}

	private static AeroGeometry genericFlatBase(int finCount,
			String finSection) {
		return geometry(List.of(body("body", "CYLINDER", 0, 1,
				0.05, 0.05)), finCount, 0.8, 1, 0.05, 0.10,
				finSection);
	}

	private static AeroGeometry expandingSleeve(boolean terminalBoattail,
			int finCount) {
		List<AeroComponent> bodies = new ArrayList<>();
		bodies.add(body("body", "CYLINDER", 0, 0.70,
				0.04, 0.04));
		bodies.add(body("shoulder", "TRANSITION", 0.70, 0.72,
				0.04, 0.05));
		bodies.add(body("sleeve", "CYLINDER", 0.72,
				terminalBoattail ? 0.90 : 1.0, 0.05, 0.05));
		if (terminalBoattail) {
			bodies.add(body("boattail", "BOATTAIL", 0.90, 1.0,
					0.05, 0.04));
		}
		return geometry(bodies, finCount, 0.78,
				terminalBoattail ? 0.95 : 1.0, 0.05, 0.10,
				"FLAT_PLATE");
	}

	private static AeroGeometry geometry(List<AeroComponent> bodies,
			int finCount, double finStart, double finEnd,
			double finRootRadius, double span, String finSection) {
		List<AeroComponent> components = new ArrayList<>(bodies);
		double rootChord = finEnd - finStart;
		double planformArea = rootChord * span;
		FinGeometry fin = new FinGeometry("RECTANGULAR", finSection,
				finCount, rootChord, span, planformArea, 0,
				List.of(new GeometryStation(0, 0, 0, 0),
						new GeometryStation(0, span, 0, 0),
						new GeometryStation(rootChord, span, 0, 0),
						new GeometryStation(rootChord, 0, 0, 0)));
		components.add(new AeroComponent("fins", "/fins", "test",
				"FIN_TRAPEZOIDAL", "stage", bodies.size(),
				new Coordinate(finStart, 0, 0), finStart, finEnd,
				finRootRadius, 2 * planformArea * finCount,
				planformArea * finCount, 0, 0, "ADIABATIC",
				Map.of("thicknessM", 0.004), List.of(),
				null, fin, null));

		AeroComponent last = bodies.get(bodies.size() - 1);
		double baseRadius = last.axisymmetricProfile().stations()
				.get(last.axisymmetricProfile().stations().size() - 1)
				.radiusM();
		double maximumRadius = bodies.stream()
				.flatMap(component -> component.axisymmetricProfile()
						.stations().stream())
				.mapToDouble(GeometryStation::radiusM).max().orElseThrow();
		double referenceArea = Math.PI * maximumRadius * maximumRadius;
		double baseArea = Math.PI * baseRadius * baseRadius;
		return new AeroGeometry(components,
				new ReferenceGeometry(referenceArea, baseArea, 1,
						2 * maximumRadius, Map.of(), new Coordinate(),
						2 * maximumRadius), "finned-base-test");
	}

	private static AeroGeometry withAftProtuberance(
			AeroGeometry geometry) {
		List<AeroComponent> components = new ArrayList<>(
				geometry.components());
		components.add(new AeroComponent("aft-button", "/aft-button",
				"test", "PROTUBERANCE", "stage", components.size(),
				new Coordinate(1.2, 0, 0), 1.2, 1.21, 0.05,
				0.001, 0.001, 0, 0, "ADIABATIC", Map.of(),
				List.of(), null, null,
				new ProtuberanceGeometry("RAIL_BUTTON", 1, 1.2,
						0.01, 0.005, 0.001, 0.0001)));
		ReferenceGeometry original = geometry.references();
		return new AeroGeometry(components, new ReferenceGeometry(
				original.referenceAreaM2(),
				original.exposedBaseAreaM2(), 1.21,
				original.maximumBodyDiameterM(),
				original.wettedAreaByComponentM2(),
				original.momentOriginM(),
				original.referenceLengthM()),
				geometry.geometryHash() + "-aft-protuberance");
	}

	private static AeroComponent body(String id, String classification,
			double start, double end, double foreRadius,
			double aftRadius) {
		AxisymmetricProfile profile = new AxisymmetricProfile(List.of(
				new GeometryStation(start, foreRadius,
						(aftRadius - foreRadius) / (end - start), 0),
				new GeometryStation(end, aftRadius,
						(aftRadius - foreRadius) / (end - start), 0)),
				List.of(), "TEST", 0);
		return new AeroComponent(id, "/" + id, "test", classification,
				"stage", 0, new Coordinate(start, 0, 0), start, end,
				Math.max(foreRadius, aftRadius), 0, 0,
				Math.PI * aftRadius * aftRadius, 0, "ADIABATIC",
				Map.of(), List.of(), profile, null, null);
	}

	/**
	 * Keeps the production interpolation package-private while allowing this
	 * package-level regression to verify its Hart endpoint through evaluation.
	 */
	private static final class FinnedBasePressureClosureModelAccessor {
		static double smoothBodyPressureMagnitude(double mach) {
			return new FinnedBasePressureClosureModel()
					.evaluate(genericFlatBase(3), mach, 0)
					.smoothBodyPressureMagnitude();
		}
	}
}
