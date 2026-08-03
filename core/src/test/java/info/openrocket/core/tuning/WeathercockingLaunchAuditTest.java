package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.simulation.WeathercockingCompensation;
import info.openrocket.core.util.MathUtil;

public class WeathercockingLaunchAuditTest {

	private static final double METERS_TO_FEET = 3.280839895;
	private static final String KAI_ORK_PROPERTY = "weathercockingAudit.kaiOrk";
	private static final String KAI_ORK_ENV = "WEATHERCOCKING_AUDIT_KAI_ORK";
	private static final double TARGET_MATCH_MARGIN_DEG = 10.0;
	private static final int TARGET_MATCH_COARSE_STEPS = 24;
	private static final int TARGET_MATCH_BISECTION_STEPS = 16;
	private static final Path AUDIT_REPORT = Path.of("build", "reports", "weathercocking-audit",
			"WeathercockingLaunchAuditTest.txt");
	private static boolean auditReportInitialized;

	@Test
	public void auditJackpotLaunch1WeathercockingVariants() throws Exception {
		auditCase(new LaunchCase("Jackpot launch 1", "Jackpot_Launch_1", "NASA_26_VDF_Config.ork", 2566.0, null));
	}

	@Test
	public void auditJackpotLaunch3WeathercockingVariants() throws Exception {
		auditCase(new LaunchCase("Jackpot launch 3", "Jackpot_Launch_3", "NASA_26_PDF_Config.ork", 3649.0, 1.0));
	}

	@Test
	public void auditGovernmentWork2WeathercockingVariants() throws Exception {
		auditCase(new LaunchCase("Government work 2", "Government_Work_Launch_2", "NASA_26_Subscale_2.ork", 2378.0, null));
	}

	@Test
	public void auditKaiL1TwentyDegreeWeathercockingVariants() throws Exception {
		Path kaiOrk = configuredKaiOrk();
		Assumptions.assumeTrue(kaiOrk != null && Files.exists(kaiOrk),
				"Set -D" + KAI_ORK_PROPERTY + "=<absolute path to Kai ORK> to run this audit");
		auditCase(new LaunchCase("Kai L1 20deg", kaiOrk, 1481.0, 20.0));
	}

	@Test
	public void auditKaiL1FiveDegreeWeathercockingVariants() throws Exception {
		Path kaiOrk = configuredKaiOrk();
		Assumptions.assumeTrue(kaiOrk != null && Files.exists(kaiOrk),
				"Set -D" + KAI_ORK_PROPERTY + "=<absolute path to Kai ORK> to run this audit");
		auditCase(new LaunchCase("Kai L1 5deg", kaiOrk, 1481.0, 5.0));
	}

	private static void auditCase(LaunchCase launchCase) throws Exception {
		Simulation source = loadSource(launchCase);

		Simulation baseline = source.clone(false);
		baseline.getOptions().setWeathercockingCompensationEnabled(false);
		VariantMetrics baselineMetrics = simulate(baseline);

		Simulation fullWeathercocking = source.clone(false);
		fullWeathercocking.getOptions().setWeathercockingCompensationEnabled(true);
		WeathercockingCompensation.Prediction prediction = WeathercockingCompensation.evaluate(
				fullWeathercocking.getOptions().toSimulationConditions(),
				fullWeathercocking.getActiveConfiguration());
		assertTrue(prediction.isActive(), launchCase.label() + " should activate weathercocking compensation for audit coverage");

		Simulation launchAngleOnly = source.clone(false);
		launchAngleOnly.getOptions().setWeathercockingCompensationEnabled(false);
		launchAngleOnly.getOptions().setLaunchRodAngle(prediction.getAppliedLaunchAngle());
		launchAngleOnly.getOptions().setLaunchRodDirection(prediction.getLaunchRodDirection());
		VariantMetrics launchAngleOnlyMetrics = simulate(launchAngleOnly);

		VariantMetrics fullWeathercockingMetrics = simulate(fullWeathercocking);
		TargetMatch targetMatch = solveTargetLaunchAngle(source, prediction, launchCase.expectedApogeeFeet());
		assertTrue(Math.abs(fullWeathercockingMetrics.apogeeFeet() - baselineMetrics.apogeeFeet()) > 1.0,
				launchCase.label() + " should materially change apogee when weathercocking is enabled");

		String auditLine = String.format(Locale.US,
				"WEATHERCOCK_AUDIT %s target=%.2f ft baseline=%.2f ft angleOnly=%.2f ft full=%.2f ft angleEffect=%.2f ft blendEffect=%.2f ft fullError=%.2f ft matchedAngleDeg=%.3f matchedApogee=%.2f ft matchedError=%.2f ft correctionDeg=%.3f reqAngleDeg=%.3f appAngleDeg=%.3f windMps=%.3f stabilityCal=%.3f ratio=%.3f maxBlend=%.3f%n",
				launchCase.label(),
				launchCase.expectedApogeeFeet(),
				baselineMetrics.apogeeFeet(),
				launchAngleOnlyMetrics.apogeeFeet(),
				fullWeathercockingMetrics.apogeeFeet(),
				launchAngleOnlyMetrics.apogeeFeet() - baselineMetrics.apogeeFeet(),
				fullWeathercockingMetrics.apogeeFeet() - launchAngleOnlyMetrics.apogeeFeet(),
				fullWeathercockingMetrics.apogeeFeet() - launchCase.expectedApogeeFeet(),
				Math.toDegrees(targetMatch.launchAngleRad()),
				targetMatch.apogeeFeet(),
				targetMatch.apogeeFeet() - launchCase.expectedApogeeFeet(),
				Math.toDegrees(lastFinite(fullWeathercockingMetrics.appliedCorrectionAnglesRadians())),
				Math.toDegrees(lastFinite(fullWeathercockingMetrics.requestedLaunchAnglesRadians())),
				Math.toDegrees(lastFinite(fullWeathercockingMetrics.appliedLaunchAnglesRadians())),
				lastFinite(fullWeathercockingMetrics.profileWindMps()),
				lastFinite(fullWeathercockingMetrics.stabilityCalibers()),
				lastFinite(fullWeathercockingMetrics.stabilityToMassRatio()),
				maxFinite(fullWeathercockingMetrics.postRodBlendFactors()));
		System.out.print(auditLine);
		appendAuditLine(auditLine);
	}

	private static Simulation loadSource(LaunchCase launchCase) throws Exception {
		ensureApplicationInjector();
		File file = resolveOrkFile(launchCase);
		GeneralRocketLoader loader = new GeneralRocketLoader(file);
		OpenRocketDocument document = loader.load();
		if (document.getSimulations().isEmpty()) {
			Simulation simulation = new Simulation(document.getRocket());
			FlightConfigurationId id = document.getRocket().getSelectedConfiguration().getFlightConfigurationID();
			simulation.setFlightConfigurationId(id);
			return simulation;
		}
		if (launchCase.preferredLaunchRodAngleDeg() != null) {
			for (Simulation simulation : document.getSimulations()) {
				double launchRodAngleDeg = Math.toDegrees(simulation.getOptions().getLaunchRodAngle());
				if (Math.abs(launchRodAngleDeg - launchCase.preferredLaunchRodAngleDeg()) < 1.0e-6) {
					return simulation;
				}
			}
		}
		return document.getSimulations().get(0);
	}

	private static File resolveOrkFile(LaunchCase launchCase) {
		if (launchCase.absoluteOrkPath() != null) {
			return launchCase.absoluteOrkPath().toFile();
		}
		return Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				launchCase.folder(), launchCase.orkFile()).toFile();
	}

	private static Path configuredKaiOrk() {
		String value = System.getProperty(KAI_ORK_PROPERTY);
		if (value == null || value.isBlank()) {
			value = System.getenv(KAI_ORK_ENV);
		}
		if (value == null || value.isBlank()) {
			return null;
		}
		return Path.of(value).toAbsolutePath().normalize();
	}

	private static synchronized void ensureApplicationInjector() {
		try {
			TuningTestInfrastructure.ensureApplicationInjector();
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to initialize tuning test services", ex);
		}
	}

	private static synchronized void appendAuditLine(String auditLine) throws Exception {
		Files.createDirectories(AUDIT_REPORT.getParent());
		if (!auditReportInitialized) {
			Files.deleteIfExists(AUDIT_REPORT);
			auditReportInitialized = true;
		}
		Files.writeString(AUDIT_REPORT, auditLine, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	private static VariantMetrics simulate(Simulation simulation) throws SimulationException {
		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		FlightDataBranch branch = data.getBranch(0);
		return new VariantMetrics(
				data.getMaxAltitude() * METERS_TO_FEET,
				branch.get(WeathercockingCompensation.TYPE_REQUESTED_EFFECTIVE_ANGLE),
				branch.get(WeathercockingCompensation.TYPE_APPLIED_EFFECTIVE_ANGLE),
				branch.get(WeathercockingCompensation.TYPE_APPLIED_CORRECTION_ANGLE),
				branch.get(WeathercockingCompensation.TYPE_PROFILE_WIND_SPEED),
				branch.get(WeathercockingCompensation.TYPE_STABILITY_CALIBERS),
				branch.get(WeathercockingCompensation.TYPE_STABILITY_TO_MASS_RATIO),
				branch.get(WeathercockingCompensation.TYPE_POST_ROD_BLEND));
	}

	private static TargetMatch solveTargetLaunchAngle(Simulation source,
			WeathercockingCompensation.Prediction prediction,
			double targetApogeeFeet) throws SimulationException {
		double originalAngleRad = source.getOptions().getLaunchRodAngle();
		double predictedAngleRad = prediction.getAppliedLaunchAngle();
		double marginRad = Math.toRadians(TARGET_MATCH_MARGIN_DEG);
		double minAngleRad = MathUtil.clamp(
				Math.min(originalAngleRad, predictedAngleRad) - marginRad,
				-SimulationOptions.MAX_LAUNCH_ROD_ANGLE,
				SimulationOptions.MAX_LAUNCH_ROD_ANGLE);
		double maxAngleRad = MathUtil.clamp(
				Math.max(originalAngleRad, predictedAngleRad) + marginRad,
				-SimulationOptions.MAX_LAUNCH_ROD_ANGLE,
				SimulationOptions.MAX_LAUNCH_ROD_ANGLE);
		double directionRad = source.getOptions().getLaunchRodDirection();

		TargetMatch previous = simulateAtAngle(source, minAngleRad, directionRad);
		TargetMatch best = previous;
		double previousDiff = previous.apogeeFeet() - targetApogeeFeet;

		for (int step = 1; step <= TARGET_MATCH_COARSE_STEPS; step++) {
			double fraction = (double) step / TARGET_MATCH_COARSE_STEPS;
			double angleRad = minAngleRad + (maxAngleRad - minAngleRad) * fraction;
			TargetMatch current = simulateAtAngle(source, angleRad, directionRad);
			double currentDiff = current.apogeeFeet() - targetApogeeFeet;
			if (Math.abs(currentDiff) < Math.abs(best.apogeeFeet() - targetApogeeFeet)) {
				best = current;
			}
			if (Math.signum(previousDiff) != Math.signum(currentDiff)) {
				return bisectTargetLaunchAngle(source, directionRad, targetApogeeFeet, previous, current);
			}
			previous = current;
			previousDiff = currentDiff;
		}

		return best;
	}

	private static TargetMatch bisectTargetLaunchAngle(Simulation source, double directionRad,
			double targetApogeeFeet, TargetMatch lower, TargetMatch upper) throws SimulationException {
		TargetMatch left = lower;
		TargetMatch right = upper;
		TargetMatch best = Math.abs(left.apogeeFeet() - targetApogeeFeet) <= Math.abs(right.apogeeFeet() - targetApogeeFeet)
				? left : right;

		for (int step = 0; step < TARGET_MATCH_BISECTION_STEPS; step++) {
			double middleAngle = 0.5 * (left.launchAngleRad() + right.launchAngleRad());
			TargetMatch middle = simulateAtAngle(source, middleAngle, directionRad);
			double middleDiff = middle.apogeeFeet() - targetApogeeFeet;
			if (Math.abs(middleDiff) < Math.abs(best.apogeeFeet() - targetApogeeFeet)) {
				best = middle;
			}
			if (Math.signum(left.apogeeFeet() - targetApogeeFeet) == Math.signum(middleDiff)) {
				left = middle;
			} else {
				right = middle;
			}
		}

		return best;
	}

	private static TargetMatch simulateAtAngle(Simulation source, double launchAngleRad, double launchDirectionRad)
			throws SimulationException {
		Simulation simulation = source.clone(false);
		simulation.getOptions().setWeathercockingCompensationEnabled(false);
		simulation.getOptions().setLaunchRodAngle(launchAngleRad);
		simulation.getOptions().setLaunchRodDirection(launchDirectionRad);
		VariantMetrics metrics = simulate(simulation);
		return new TargetMatch(launchAngleRad, metrics.apogeeFeet());
	}

	private static double lastFinite(List<Double> values) {
		if (values == null) {
			return Double.NaN;
		}
		for (int i = values.size() - 1; i >= 0; i--) {
			Double value = values.get(i);
			if (value != null && Double.isFinite(value)) {
				return value;
			}
		}
		return Double.NaN;
	}

	private static double maxFinite(List<Double> values) {
		double max = Double.NaN;
		if (values == null) {
			return max;
		}
		for (Double value : values) {
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			if (Double.isNaN(max) || value > max) {
				max = value;
			}
		}
		return max;
	}

	private record LaunchCase(String label, String folder, String orkFile, double expectedApogeeFeet,
			Double preferredLaunchRodAngleDeg, Path absoluteOrkPath) {
		private LaunchCase(String label, String folder, String orkFile, double expectedApogeeFeet,
				Double preferredLaunchRodAngleDeg) {
			this(label, folder, orkFile, expectedApogeeFeet, preferredLaunchRodAngleDeg, null);
		}

		private LaunchCase(String label, Path absoluteOrkPath, double expectedApogeeFeet,
				Double preferredLaunchRodAngleDeg) {
			this(label, null, null, expectedApogeeFeet, preferredLaunchRodAngleDeg, absoluteOrkPath);
		}
	}

	private record VariantMetrics(
			double apogeeFeet,
			List<Double> requestedLaunchAnglesRadians,
			List<Double> appliedLaunchAnglesRadians,
			List<Double> appliedCorrectionAnglesRadians,
			List<Double> profileWindMps,
			List<Double> stabilityCalibers,
			List<Double> stabilityToMassRatio,
			List<Double> postRodBlendFactors) {
	}

	private record TargetMatch(double launchAngleRad, double apogeeFeet) {
	}
}
