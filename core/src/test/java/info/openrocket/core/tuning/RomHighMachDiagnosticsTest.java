package info.openrocket.core.tuning;

import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.OpenRocketCore;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RomHighMachDiagnosticsTest {

	@Test
	public void pelencatorFourDImprovesOverDerivedThreeDWithoutOvershootingBaseline() throws Exception {
		VariantResults results = loadVariants("Pelencator_Launch_1", "VDF_Launch_1.ork");
		assertNotNull(results.threeD);
		assertNotNull(results.fourD);
		assertNotNull(results.baseline);
		assertTrue(results.fourD.apogeeFeet > results.threeD.apogeeFeet + 10.0,
				"Expected 4D taper to recover some over-drag on Pelencator. 3D=" + results.threeD.apogeeFeet
						+ " 4D=" + results.fourD.apogeeFeet);
		assertTrue(results.fourD.apogeeFeet < results.baseline.apogeeFeet,
				"4D should remain more conservative than the pure baseline on Pelencator. baseline="
						+ results.baseline.apogeeFeet + " 4D=" + results.fourD.apogeeFeet);
	}

	@Test
	public void jackpotFourDImprovesOverDerivedThreeDWithoutOvershootingBaseline() throws Exception {
		VariantResults results = loadVariants("Jackpot_Launch_2", "NASA_26_PDF_Config.ork");
		assertNotNull(results.threeD);
		assertNotNull(results.fourD);
		assertNotNull(results.baseline);
		assertTrue(results.fourD.apogeeFeet > results.threeD.apogeeFeet + 0.1,
				"Expected 4D taper to recover some over-drag on Jackpot. 3D=" + results.threeD.apogeeFeet
						+ " 4D=" + results.fourD.apogeeFeet);
		assertTrue(results.fourD.apogeeFeet < results.baseline.apogeeFeet,
				"4D should remain more conservative than the pure baseline on Jackpot. baseline="
						+ results.baseline.apogeeFeet + " 4D=" + results.fourD.apogeeFeet);
	}

	@Test
	public void governmentWorkLaunch2StillLoadsAndSimulatesBaseline() throws Exception {
		VariantResults results = loadVariants("Government_Work_Launch_2", "NASA_26_Subscale_2.ork");
		assertNotNull(results.baseline);
		assertTrue(results.baseline.apogeeFeet > 2000.0);
	}

	private static VariantResults loadVariants(String folder, String file) throws Exception {
		ensureApplicationInjector();
		File orkFile = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning", folder, file).toFile();
		GeneralRocketLoader loader = new GeneralRocketLoader(orkFile);
		OpenRocketDocument document = loader.load();
		Simulation source = document.getSimulations().get(0);

		return new VariantResults(
				simulate(source.clone(false)),
				simulate(baselineVariant(source)),
				simulate(threeDVariant(source)),
				simulate(fourDVariant(source)));
	}

	private static Simulation baselineVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		simulation.getOptions().setRomSurfaceMode(RomSurfaceMode.THREE_D);
		simulation.getOptions().setRomDragSurface(null);
		simulation.getOptions().setRomAeroSurface4D(null);
		return simulation;
	}

	private static Simulation threeDVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		simulation.getOptions().setRomSurfaceMode(RomSurfaceMode.THREE_D);
		if (simulation.getOptions().getRomDragSurface() == null && simulation.getOptions().getRomAeroSurface4D() != null) {
			simulation.getOptions().setRomDragSurface(
					SurfaceAdapter.toBetaZeroDragSurface(simulation.getOptions().getRomAeroSurface4D()));
		}
		simulation.getOptions().setRomAeroSurface4D(null);
		return simulation;
	}

	private static Simulation fourDVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		simulation.getOptions().setRomSurfaceMode(RomSurfaceMode.FOUR_D);
		return simulation;
	}

	private static SimulationResult simulate(Simulation simulation) throws Exception {
		boolean available = simulation.getOptions().getRomSurfaceMode() != RomSurfaceMode.FOUR_D
				? (simulation.getOptions().hasRomDragSurface() || simulation.getOptions().getRomAeroSurface4D() == null)
				: simulation.getOptions().hasRomAeroSurface4D();
		if (!available) {
			return null;
		}

		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		double apogeeMeters = data.getMaxAltitude();
		double apogeeFeet = apogeeMeters * 3.280839895;
		double maxMach = data.getMaxMachNumber();
		return new SimulationResult(apogeeFeet, apogeeMeters, maxMach);
	}

	private record SimulationResult(double apogeeFeet, double apogeeMeters, double maxMach) {
	}

	private record VariantResults(
			SimulationResult saved,
			SimulationResult baseline,
			SimulationResult threeD,
			SimulationResult fourD) {
	}

	private static synchronized void ensureApplicationInjector() {
		if (Application.getInjector() != null && OpenRocketCore.isInitialized()) {
			return;
		}
		OpenRocketCore.initialize();
	}
}
