package info.openrocket.core.aerodynamics.physicsaero.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.AttachedFlowHealth;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerHistory;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerMode;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerState;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerStation;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.SurfaceTrack;
import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;
import info.openrocket.core.aerodynamics.physicsaero.roughness.RoughnessRegime;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;
import info.openrocket.core.util.Coordinate;

class SkinFrictionForceIntegratorTest {
	private static final double TOLERANCE = 1e-12;

	@Test
	void axialWallShearIsPositiveDragAndMomentIsTranslatedOnce() {
		BoundaryLayerHistory history = history(BoundaryLayerMode.PLANAR,
				List.of(
						station(0, new Coordinate(1, 2, 0), new Coordinate(1, 0, 0), 4),
						station(2, new Coordinate(3, 2, 0), new Coordinate(1, 0, 0), 4)),
				List.of(state(5), state(5)));

		ForceContribution contribution = new SkinFrictionForceIntegrator().integrate(
				history, new Coordinate(7, -3, 4));

		assertEquals(40, contribution.forceBodyN().x, TOLERANCE);
		assertEquals(0, contribution.forceBodyN().y, TOLERANCE);
		assertEquals(0, contribution.forceBodyN().z, TOLERANCE);
		assertCoordinateEquals(new Coordinate(2, 2, 0), contribution.applicationPointM());
		assertCoordinateEquals(new Coordinate(), contribution.intrinsicMomentBodyNm());

		AerodynamicCoefficients coefficients = assemble(contribution);
		assertEquals(40, coefficients.ca(), TOLERANCE);
		assertEquals(-80, coefficients.cYaw(), TOLERANCE);
	}

	@Test
	void axisymmetricAnnularShearCancelsRadialForceAndMoments() {
		double ds = Math.hypot(1, 0.2);
		Coordinate meridionalTangent = new Coordinate(1, 0.2, 0);
		BoundaryLayerHistory history = history(BoundaryLayerMode.AXISYMMETRIC,
				List.of(
						station(0, new Coordinate(0, 0.2, 0), meridionalTangent, 2 * Math.PI * 0.2),
						station(ds, new Coordinate(1, 0.4, 0), meridionalTangent, 2 * Math.PI * 0.4)),
				List.of(state(10), state(10)));

		ForceContribution contribution = new SkinFrictionForceIntegrator().integrate(
				history, new Coordinate(0, 1, 1));

		assertEquals(6 * Math.PI, contribution.forceBodyN().x, TOLERANCE);
		assertEquals(0, contribution.forceBodyN().y, TOLERANCE);
		assertEquals(0, contribution.forceBodyN().z, TOLERANCE);
		assertCoordinateEquals(new Coordinate(0.5, 0, 0), contribution.applicationPointM());
		assertCoordinateEquals(new Coordinate(), contribution.intrinsicMomentBodyNm());

		AerodynamicCoefficients coefficients = assemble(contribution);
		assertEquals(6 * Math.PI, coefficients.ca(), TOLERANCE);
		assertEquals(0, coefficients.cn(), TOLERANCE);
		assertEquals(0, coefficients.cy(), TOLERANCE);
		assertEquals(0, coefficients.cl(), TOLERANCE);
		assertEquals(0, coefficients.cm(), TOLERANCE);
		assertEquals(0, coefficients.cYaw(), TOLERANCE);
	}

	@Test
	void planarThreeDimensionalTrackRetainsEveryForceAndMomentComponent() {
		Coordinate tangent = new Coordinate(1, 2, 2);
		BoundaryLayerHistory history = history(BoundaryLayerMode.PLANAR,
				List.of(
						station(0, new Coordinate(1, 1, 3), tangent, 2),
						station(3, new Coordinate(2, 3, 5), tangent, 2)),
				List.of(state(5), state(5)));

		ForceContribution contribution = new SkinFrictionForceIntegrator().integrate(history, new Coordinate());
		AerodynamicCoefficients coefficients = assemble(contribution);

		assertCoordinateEquals(new Coordinate(10, 20, 20), contribution.forceBodyN());
		assertCoordinateEquals(new Coordinate(1.5, 2, 4), contribution.applicationPointM());
		assertCoordinateEquals(new Coordinate(), contribution.intrinsicMomentBodyNm());
		assertEquals(10, coefficients.ca(), TOLERANCE);
		assertEquals(20, coefficients.cn(), TOLERANCE);
		assertEquals(20, coefficients.cy(), TOLERANCE);
		assertEquals(-40, coefficients.cl(), TOLERANCE);
		assertEquals(10, coefficients.cm(), TOLERANCE);
		assertEquals(10, coefficients.cYaw(), TOLERANCE);
	}

	private static AerodynamicCoefficients assemble(ForceContribution contribution) {
		ContributionLedger ledger = new ContributionLedger();
		ledger.add(contribution);
		return CoefficientAssembler.assemble(ledger, new ReferenceState(1, 1, 1, new Coordinate()));
	}

	private static BoundaryLayerHistory history(BoundaryLayerMode mode, List<BoundaryLayerStation> stations,
			List<BoundaryLayerState> states) {
		SurfaceTrack track = new SurfaceTrack("component", "region", mode, stations, List.of(), false);
		return new BoundaryLayerHistory(track, states);
	}

	private static BoundaryLayerStation station(double s, Coordinate position, Coordinate tangent, double width) {
		double temperature = 288.15;
		double pressure = 101325;
		double density = 1.225;
		double velocity = 100;
		double speedOfSound = 340;
		GasState gas = new GasState(velocity / speedOfSound, pressure, temperature, density, velocity);
		TotalState total = new TotalState(110000, 294, density);
		SurfaceState edge = new SurfaceState(position.x, Math.hypot(position.y, position.z), gas, total,
				0, 0, speedOfSound, 1.8e-5, "synthetic");
		return new BoundaryLayerStation(s, s, position, edge.radiusM(), 0, 0, velocity, 0, 0, 0,
				temperature, 0, false, "NONE", edge, tangent, width);
	}

	private static BoundaryLayerState state(double wallShearPa) {
		return new BoundaryLayerState(0.001, 0.002, 0.005, 2, 0, 0.002, wallShearPa,
				1e6, 1e3, 0, 0, TransitionState.LAMINAR, AttachedFlowHealth.ATTACHED,
				RoughnessRegime.HYDRAULICALLY_SMOOTH, 288.15, "synthetic");
	}

	private static void assertCoordinateEquals(Coordinate expected, Coordinate actual) {
		assertTrue(Double.isFinite(actual.x + actual.y + actual.z));
		assertEquals(expected.x, actual.x, TOLERANCE);
		assertEquals(expected.y, actual.y, TOLERANCE);
		assertEquals(expected.z, actual.z, TOLERANCE);
	}
}
