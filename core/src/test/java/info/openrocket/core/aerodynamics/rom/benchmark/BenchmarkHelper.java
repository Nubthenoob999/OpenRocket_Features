package info.openrocket.core.aerodynamics.rom.benchmark;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomResult;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;

/**
 * Shared helpers for the ROM dataset-backed benchmark suite.
 *
 * <p>Provides labeled assertion formatting, FORCE_ROM calculator creation,
 * and condition-building utilities used across all benchmark test classes.
 */
public final class BenchmarkHelper {

	private BenchmarkHelper() {}

	/** Standard sea-level ISA conditions. */
	public static final double ISA_TEMP = 288.15;
	public static final double ISA_PRESSURE = 101325.0;
	public static final double GAMMA_AIR = 1.4;

	/**
	 * Build a FORCE_ROM calculator with diagnostics enabled.
	 */
	public static RomAerodynamicCalculator forceRom() {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setFallbackMode(RomFallbackMode.FORCE_ROM);
		settings.setDiagnosticsEnabled(true);
		return new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
	}

	/**
	 * Build a STANDARD (blended) ROM calculator with diagnostics enabled.
	 */
	public static RomAerodynamicCalculator standardRom() {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setDiagnosticsEnabled(true);
		return new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
	}

	/**
	 * Create flight conditions at given Mach and AoA (degrees).
	 */
	public static FlightConditions conditions(FlightConfiguration config, double mach, double aoaDeg) {
		FlightConditions cond = new FlightConditions(config);
		cond.setMach(mach);
		cond.setAOA(Math.toRadians(aoaDeg));
		cond.setTheta(0.0);
		cond.setAtmosphericConditions(new AtmosphericConditions(ISA_TEMP, ISA_PRESSURE));
		return cond;
	}

	/**
	 * Evaluate FORCE_ROM and return forces.
	 */
	public static AerodynamicForces evaluate(RomAerodynamicCalculator rom,
			FlightConfiguration config, double mach, double aoaDeg) {
		FlightConditions cond = conditions(config, mach, aoaDeg);
		return rom.getAerodynamicForces(config, cond, new WarningSet());
	}

	/**
	 * Format a benchmark label for diagnostic assertion messages.
	 *
	 * @param datasetId  e.g. "A06", "B12"
	 * @param source     e.g. "Jorgensen CN", "Eckert T-star/Te"
	 * @param condition  e.g. "M=0.6, α=10°"
	 * @param expected   expected value
	 * @param actual     actual computed value
	 * @return formatted diagnostic string
	 */
	public static String label(String datasetId, String source, String condition,
			double expected, double actual) {
		return String.format("[%s/%s] %s: expected=%.6f, actual=%.6f, delta=%.6f (%.2f%%)",
				datasetId, source, condition,
				expected, actual,
				actual - expected,
				expected != 0.0 ? 100.0 * (actual - expected) / expected : Double.NaN);
	}

	/**
	 * Format a short label without expected/actual (for monotonicity checks).
	 */
	public static String label(String datasetId, String source, String condition) {
		return String.format("[%s/%s] %s", datasetId, source, condition);
	}

	/**
	 * Dump ROM diagnostics to stderr on assertion failure.
	 */
	public static void dumpOnFailure(RomAerodynamicCalculator rom, String context) {
		RomResult result = rom.getLastResult();
		if (result != null) {
			System.err.println("=== ROM Debug Dump: " + context + " ===");
			System.err.println("  Confidence: " + result.getConfidence().getOverallScore());
			System.err.println("  Fallback weight: " + result.getFallbackWeight());
			System.err.println("  Separation fraction: " + result.getSeparationFraction());
			System.err.println("  Notes: " + result.getNotes());
			System.err.println("  Seeds: " + result.getSeeds());
			System.err.println("=== End Debug Dump ===");
		}
	}
}
