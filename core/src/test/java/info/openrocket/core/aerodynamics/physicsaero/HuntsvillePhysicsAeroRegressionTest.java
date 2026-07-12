package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.models.wind.WindModelType;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.startup.OpenRocketCore;

class HuntsvillePhysicsAeroRegressionTest {
	private static final Path ROCKET_FILE = Path.of("src", "test", "java", "info", "openrocket", "core",
			"Rockets_NewStuff", "NASA_26_Huntsville_DOL_ROM.ork");
	private static final String SETTINGS = "phase6-default-adiabatic-smooth-unpowered";

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void roundedHuntsvilleFinsBuildAcrossTheFullRegime(@TempDir Path output) throws Exception {
		OpenRocketDocument document = new GeneralRocketLoader(ROCKET_FILE.toFile()).load();
		AeroGeometry geometry = new GeometryExtractor().extract(document.getRocket(), 0, "ADIABATIC", SETTINGS);
		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = 101253.0311;
		double temperatureK = 299.55;
		AtmosphereState atmosphere = new AtmosphereState(pressurePa, temperatureK,
				pressurePa / (air.gasConstant() * temperatureK), air.viscosity(temperatureK));
		double[] mach = {0, .25, .3, .35, .7, .85, .9, .95, 1, 1.05, 1.1, 1.2, 1.5, 2, 3, 4, 5, 6, 7};
		double[] alpha = degrees(-14, -10, -5, 0, 5, 10, 14);
		double[] beta = degrees(-5, 0, 5);
		var request = new PhysicsAeroTableService.Request(geometry, mach, alpha, beta, atmosphere, air, SETTINGS,
				output.resolve("huntsville.aero"), output.resolve("huntsville.json"));

		PhysicsAeroTableService.Result result = assertDoesNotThrow(() ->
				new PhysicsAeroTableService().build(request, new AtomicBoolean(), ignored -> { }));

		assertTrue(result.table().cells().stream()
				.allMatch(cell -> Arrays.stream(cell.coefficients().toArray()).allMatch(Double::isFinite)));
	}

	@Test
	void screenshotLaunchRunsHeadlesslyWithRomAndWithoutWeathercockingCompensation() throws Exception {
		OpenRocketDocument document = new GeneralRocketLoader(ROCKET_FILE.toFile()).load();
		Simulation compensated = document.getSimulations().get(0).clone(false);
		applyScreenshotSettings(compensated, false, true);
		assertDoesNotThrow(() -> compensated.simulate());
		double compensatedApogeeFeet = compensated.getSimulatedData().getMaxAltitude() / 0.3048;

		Simulation baseline = document.getSimulations().get(0).clone(false);
		applyScreenshotSettings(baseline, false, false);
		assertDoesNotThrow(() -> baseline.simulate());
		double baselineApogeeFeet = baseline.getSimulatedData().getMaxAltitude() / 0.3048;

		Simulation simulation = document.getSimulations().get(0).clone(false);
		applyScreenshotSettings(simulation, true, false);
		assertDoesNotThrow(() -> simulation.simulate());
		double romApogeeFeet = simulation.getSimulatedData().getMaxAltitude() / 0.3048;
		System.out.printf("Huntsville compensated apogee: %.2f ft; uncompensated baseline: %.2f ft; "
				+ "uncompensated ROM raw-correlation: %.2f ft%n", compensatedApogeeFeet, baselineApogeeFeet,
				romApogeeFeet);
		assertTrue(Math.abs(compensatedApogeeFeet - 4606) < 10,
				"The saved compensated screenshot case should remain close to 4606 ft");
		assertTrue(Double.isFinite(baselineApogeeFeet));
		assertTrue(Double.isFinite(romApogeeFeet));
	}

	private static void applyScreenshotSettings(Simulation simulation, boolean romEnabled,
			boolean weathercockingCompensationEnabled) {
		var options = simulation.getOptions();
		options.setWindModelType(WindModelType.MULTI_LEVEL);
		options.setLiveWeatherDataSelected(true);
		options.setLiveWeatherLaunchDate("2026-04-24");
		options.setLiveWeatherLaunchTime("00:00");
		options.setLaunchLatitude(34.89461);
		options.setLaunchLongitude(-86.61642);
		options.setLaunchAltitude(794 * 0.3048);
		options.setLaunchRodLength(144 * 0.0254);
		options.setLaunchIntoWind(false);
		options.setLaunchRodAngle(Math.toRadians(3));
		options.setLaunchRodDirection(Math.toRadians(90));
		options.setISAAtmosphere(false);
		options.setLaunchTemperature(26.4 + 273.15);
		options.setLaunchPressure(1012.53 * 100);
		options.setLaunchRelativeHumidity(.5);
		options.setWeathercockingCompensationEnabled(weathercockingCompensationEnabled);
		options.setRomEnabled(romEnabled);
		options.setRomMode(RomMode.STANDARD);
	}

	private static double[] degrees(double... values) {
		return Arrays.stream(values).map(Math::toRadians).toArray();
	}
}
