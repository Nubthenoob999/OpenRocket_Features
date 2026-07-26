package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.PhysicsAeroAerodynamicCalculator;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.CenterOfPressureDiagnostic;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBasePressureClosureModel;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySolver;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.startup.OpenRocketCore;

@Tag("benchmark")
class HuntsvillePhysicsAeroRegressionTest {
	private static final Path ROCKET_FILE = Path.of("src", "test", "java", "info", "openrocket", "core",
			"Rockets_NewStuff", "NASA_26_Huntsville_DOL_ROM.ork");
	private static final String SETTINGS = "phase6-default-adiabatic-smooth-unpowered";

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void compoundNoseBodyPressureRemainsComponentwisePhysical() throws Exception {
		OpenRocketDocument document = new GeneralRocketLoader(
				ROCKET_FILE.toFile()).load();
		AeroGeometry geometry = new GeometryExtractor().extract(
				document.getRocket(), 0, "ADIABATIC", SETTINGS);
		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = 101253.0311;
		double temperatureK = 299.55;
		AtmosphereState atmosphere = new AtmosphereState(pressurePa,
				temperatureK,
				pressurePa / (air.gasConstant() * temperatureK),
				air.viscosity(temperatureK));
		java.util.Map<Double, Double> nosePressure = new java.util.LinkedHashMap<>();
		java.util.Map<Double, String> noseMethods = new java.util.LinkedHashMap<>();
		for (double mach : new double[] {1.5, 2.0, 3.0}) {
			FlowCondition flow = FlowCondition.fromAngles(mach, 0, 0,
					atmosphere, air, false, geometry.geometryHash());
			var result = new AxisymmetricBodySolver().evaluate(geometry, flow);
			for (var contribution : result.contributions()) {
				if (contribution.owner().term()
						!= PhysicalTerm.BODY_PRESSURE_FOREBODY
						&& contribution.owner().term()
								!= PhysicalTerm.BODY_PRESSURE_TRANSITION) {
					continue;
				}
				double coefficient = contribution.forceBodyN().x
						/ (flow.dynamicPressurePa()
								* geometry.references().referenceAreaM2());
				String sourcePath = geometry.components().stream()
						.filter(component -> component.id().equals(
								contribution.componentId()))
						.map(component -> component.sourcePath())
						.findFirst().orElse(contribution.componentId());
				assertTrue(coefficient >= -0.05 && coefficient <= 0.45,
						"nonphysical component pressure Cd " + coefficient
								+ " on " + sourcePath + "; method="
								+ contribution.methodId().value());
				if (sourcePath.endsWith("/Nose cone body")) {
					nosePressure.put(mach, coefficient);
					noseMethods.put(mach,
							contribution.methodId().value());
				}
			}
		}
		assertTrue(nosePressure.get(2.0)
				<= nosePressure.get(1.5) + 0.10,
				"compound-nose pressure topology jump: " + nosePressure
						+ "; methods=" + noseMethods);
	}

	@Test
	void roundedHuntsvilleFinsBuildAndQueryAcrossTheFullRegime(@TempDir Path output) throws Exception {
		OpenRocketDocument document = new GeneralRocketLoader(ROCKET_FILE.toFile()).load();
		AeroGeometry geometry = new GeometryExtractor().extract(document.getRocket(), 0, "ADIABATIC", SETTINGS);
		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = 101253.0311;
		double temperatureK = 299.55;
		AtmosphereState atmosphere = new AtmosphereState(pressurePa, temperatureK,
				pressurePa / (air.gasConstant() * temperatureK), air.viscosity(temperatureK));
		double[] mach = {0, .25, .3, .35, .7, .85, .9, .95, 1, 1.05, 1.1, 1.2, 1.3, 1.5, 2, 3, 4, 5, 6, 7};
		double[] alpha = degrees(-15, -10, -5, 0, 5, 10, 15);
		double[] beta = degrees(-5, 0, 5);
		var request = new PhysicsAeroTableService.Request(geometry, mach, alpha, beta, atmosphere, air, SETTINGS,
				output.resolve("huntsville.aero"), output.resolve("huntsville.json"));

		PhysicsAeroTableService.Result result = assertDoesNotThrow(() ->
				new PhysicsAeroTableService().build(request, new AtomicBoolean(), ignored -> { }));

		assertTrue(result.table().cells().stream()
				.allMatch(cell -> Arrays.stream(cell.coefficients().toArray()).allMatch(Double::isFinite)));
		assertTrue(result.table().cells().stream()
				.allMatch(cell -> recovers(cell.ownerTotals().values().toArray(AerodynamicCoefficients[]::new),
						cell.coefficients())));
		assertTrue(result.table().cells().stream()
				.allMatch(cell -> recovers(cell.componentTotals().values().toArray(AerodynamicCoefficients[]::new),
						cell.coefficients())));

		int zeroAlphaIndex = 3;
		int zeroBetaIndex = 1;
		double[] zeroLiftCd = IntStream.range(0, mach.length)
				.mapToDouble(index -> result.table().cell(index, zeroAlphaIndex, zeroBetaIndex)
						.coefficients().ca())
				.toArray();
		assertFalse(result.table().cell(indexOf(mach, 2.0), zeroAlphaIndex,
				zeroBetaIndex).methodIds().contains(
						FinnedBasePressureClosureModel.METHOD_ID),
				"rounded-fin Huntsville geometry is outside the Basic Finner closure");
		int peakIndex = IntStream.range(0, mach.length)
				.filter(index -> mach[index] >= 0.9 && mach[index] <= 1.3)
				.reduce((first, second) -> zeroLiftCd[first] >= zeroLiftCd[second] ? first : second)
				.orElseThrow();
		assertTrue(mach[peakIndex] >= 1.0 && mach[peakIndex] <= 1.3,
				"transonic drag peak at Mach " + mach[peakIndex]);
		for (int index = peakIndex + 1; index < mach.length; index++) {
			assertTrue(zeroLiftCd[index] <= zeroLiftCd[index - 1] + 0.01,
					"post-peak Cd rise at Mach " + mach[index] + ": "
							+ zeroLiftCd[index - 1] + " -> " + zeroLiftCd[index]
							+ "; owners=" + result.table().cell(index,
									zeroAlphaIndex, zeroBetaIndex).ownerTotals());
		}
		for (int index = 1; index < mach.length; index++) {
			assertTrue(zeroLiftCd[index] >= 0.10 && zeroLiftCd[index] <= 0.80,
					"Huntsville zero-lift Cd outside broad physical band at Mach " + mach[index]);
		}

		double[] cpX = IntStream.range(0, mach.length)
				.mapToDouble(index -> CenterOfPressureDiagnostic.derive(
						result.table().cell(index, 4, zeroBetaIndex).coefficients(),
						result.table().cell(index, 4, zeroBetaIndex).referenceState(), 1.0e-9)
						.pitchXM().orElse(Double.NaN))
				.toArray();
		for (int index = 1; index < cpX.length; index++) {
			assertTrue(Double.isFinite(cpX[index])
					&& cpX[index] >= 0 && cpX[index] <= geometry.references().vehicleLengthM(),
					"invalid center of pressure at Mach " + mach[index]);
		}
		int machOne = indexOf(mach, 1.0);
		int machOnePointOne = indexOf(mach, 1.1);
		assertTrue(cpX[machOnePointOne] > cpX[machOne],
				"center of pressure should move aft through the transonic peak");
		int supersonicOverlapEnd = indexOf(mach, 1.5);
		assertTrue(cpX[supersonicOverlapEnd] > cpX[machOnePointOne],
				"center of pressure should continue aft through the transonic/supersonic overlap");
		for (int index = supersonicOverlapEnd + 1; index <= indexOf(mach, 4.0); index++) {
			assertTrue(cpX[index] <= cpX[index - 1] + 1.0e-8,
					"supersonic CP recovery must remain smooth at Mach " + mach[index]
							+ ": " + cpX[index - 1] + " -> " + cpX[index]
							+ "; owners=" + result.table().cell(index,
									4, zeroBetaIndex).ownerTotals());
		}

		Simulation simulation = document.getSimulations().get(0).clone(false);
		simulation.getOptions().setPhysicsAeroTableIdentity(result.table(), result.tableHash());
		simulation.getOptions().setPhysicsAeroMode(PhysicsAeroMode.STRICT);
		var conditions = simulation.getOptions().toSimulationConditions(result.table());
		assertTrue(conditions.getAerodynamicCalculator() instanceof PhysicsAeroAerodynamicCalculator);
		FlightConditions flight = new FlightConditions(simulation.getActiveConfiguration());
		flight.setMach(0);
		flight.setAOA(0);
		AerodynamicForces forces = conditions.getAerodynamicCalculator().getAerodynamicForces(
				simulation.getActiveConfiguration(), flight, new WarningSet());
		assertTrue(Double.isFinite(forces.getCDaxial()));
		var runtime = ((PhysicsAeroAerodynamicCalculator) conditions.getAerodynamicCalculator()).getRuntimeReport();
		assertTrue(runtime.successfulTableQueries() > 0);
		assertEquals(0, runtime.fallbackCount());
	}

	private static double[] degrees(double... values) {
		return Arrays.stream(values).map(Math::toRadians).toArray();
	}

	private static int indexOf(double[] values, double target) {
		for (int index = 0; index < values.length; index++) {
			if (values[index] == target) return index;
		}
		throw new IllegalArgumentException("missing axis value " + target);
	}

	private static boolean recovers(AerodynamicCoefficients[] values,
			AerodynamicCoefficients expected) {
		double[] sum = new double[6];
		for (AerodynamicCoefficients value : values) {
			double[] coefficients = value.toArray();
			for (int axis = 0; axis < sum.length; axis++) sum[axis] += coefficients[axis];
		}
		double[] target = expected.toArray();
		for (int axis = 0; axis < sum.length; axis++) {
			if (Math.abs(sum[axis] - target[axis])
					> 1.0e-9 * Math.max(1, Math.abs(target[axis]))) return false;
		}
		return true;
	}
}
