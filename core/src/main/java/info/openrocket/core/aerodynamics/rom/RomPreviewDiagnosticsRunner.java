package info.openrocket.core.aerodynamics.rom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerState;
import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.simulation.SimulationConditions;
import info.openrocket.core.simulation.SimulationOptions;

/**
 * Preview-only sweep runner that evaluates ROM/Barrowman coefficients across a
 * 2D Mach/AoA grid for UI diagnostics. It does not mutate the live simulation
 * trajectory or its cached aerodynamic state.
 */
public class RomPreviewDiagnosticsRunner {

	/**
	 * Result of a preview sweep including aggregate summary statistics for the
	 * status strip.
	 */
	public static final class Result {
		private final List<RomPreviewSample> samples;
		private final int requestedRows;
		private final int computedRows;
		private final double worstConfidence;
		private final double maxFallbackPercent;
		private final double maxSeparationPercent;
		private final int singularityCount;
		private final int errorCount;
		private final boolean cancelled;
		private final boolean rowCapExceeded;
		private final String statusMessage;

		Result(List<RomPreviewSample> samples, int requestedRows, double worstConfidence,
				double maxFallbackPercent, double maxSeparationPercent, int singularityCount,
				int errorCount, boolean cancelled, boolean rowCapExceeded, String statusMessage) {
			this.samples = Collections.unmodifiableList(samples);
			this.requestedRows = requestedRows;
			this.computedRows = samples.size();
			this.worstConfidence = worstConfidence;
			this.maxFallbackPercent = maxFallbackPercent;
			this.maxSeparationPercent = maxSeparationPercent;
			this.singularityCount = singularityCount;
			this.errorCount = errorCount;
			this.cancelled = cancelled;
			this.rowCapExceeded = rowCapExceeded;
			this.statusMessage = statusMessage != null ? statusMessage : "";
		}

		public List<RomPreviewSample> getSamples() { return samples; }
		public int getRequestedRows() { return requestedRows; }
		public int getComputedRows() { return computedRows; }
		public double getWorstConfidence() { return worstConfidence; }
		public double getMaxFallbackPercent() { return maxFallbackPercent; }
		public double getMaxSeparationPercent() { return maxSeparationPercent; }
		public int getSingularityCount() { return singularityCount; }
		public int getErrorCount() { return errorCount; }
		public boolean isCancelled() { return cancelled; }
		public boolean isRowCapExceeded() { return rowCapExceeded; }
		public String getStatusMessage() { return statusMessage; }
	}

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(int computed, int total);
	}

	/**
	 * Compute the deterministic ordered list of Mach values for a sweep
	 * (inclusive of max where step lands).
	 */
	public static List<Double> machGrid(RomSettings settings) {
		RomSettings normalized = settings.normalizedPreviewRanges();
		return linspace(normalized.getPreviewMachMin(), normalized.getPreviewMachMax(),
				normalized.getPreviewMachStep());
	}

	/**
	 * Compute the deterministic ordered list of AoA values for a sweep.
	 */
	public static List<Double> aoaGrid(RomSettings settings) {
		RomSettings normalized = settings.normalizedPreviewRanges();
		return linspace(normalized.getPreviewAoADegMin(), normalized.getPreviewAoADegMax(),
				normalized.getPreviewAoADegStep());
	}

	public static int estimatedRowCount(RomSettings settings) {
		return machGrid(settings).size() * aoaGrid(settings).size();
	}

	/**
	 * Run the preview sweep synchronously. Caller is responsible for executing
	 * off the EDT (e.g. via SwingWorker).
	 */
	public Result run(FlightConfiguration configuration, SimulationOptions options,
			RomSettings sweepSettings, AtomicBoolean cancelToken, ProgressListener progress) {
		RomSettings normalized = sweepSettings.normalizedPreviewRanges();
		List<Double> machList = machGrid(normalized);
		List<Double> aoaList = aoaGrid(normalized);
		int requestedRows = machList.size() * aoaList.size();

		int rowCap = normalized.getPreviewMaxRows();
		if (requestedRows > rowCap) {
			String msg = String.format(Locale.ROOT,
					"Sweep would produce %d rows, exceeding the row cap of %d. "
							+ "Narrow the Mach or AoA range or coarsen the step.",
					requestedRows, rowCap);
			return new Result(new ArrayList<>(), requestedRows, 1.0, 0.0, 0.0, 0, 0,
					false, true, msg);
		}

		// Build a fresh SimulationConditions to obtain a private RomAerodynamicCalculator,
		// preserving the user's live calculator state.
		SimulationConditions prepared = options.toSimulationConditions();
		// Force the calculator to use our sweep ROM settings (pathline counts, theta, etc.
		// come from the supplied settings, not the live options).
		RomAerodynamicCalculator calculator = prepared.getRomAerodynamicCalculator();
		calculator.setSettings(normalized);
		AerodynamicCalculator legacyCalculator = prepared.getAerodynamicCalculator();

		double thetaRad = Math.toRadians(normalized.getPreviewThetaDeg());
		double plume = normalized.getPreviewPlumeState();

		List<RomPreviewSample> samples = new ArrayList<>(requestedRows);
		double worstConfidence = 1.0;
		double maxFallbackPct = 0.0;
		double maxSeparationPct = 0.0;
		int singularityCount = 0;
		int errorCount = 0;
		boolean cancelled = false;

		AtmosphericConditions atmosphere = new AtmosphericConditions(
				options.getLaunchTemperature(),
				options.getLaunchPressure(),
				options.getLaunchRelativeHumidity());

		for (double mach : machList) {
			for (double aoaDeg : aoaList) {
				if (cancelToken != null && cancelToken.get()) {
					cancelled = true;
					break;
				}

				FlightConditions conditions = new FlightConditions(configuration);
				conditions.setAtmosphericConditions(atmosphere);
				conditions.setMach(mach);
				conditions.setAOA(Math.toRadians(aoaDeg));
				conditions.setTheta(thetaRad);
				calculator.setPlumeState(plume);

				WarningSet warnings = new WarningSet();
				RomPreviewSample sample;
				try {
					AerodynamicForces legacyForces = legacyCalculator
							.getAerodynamicForces(configuration, conditions, warnings).clone();

					if (!calculator.isEnabled()) {
						sample = buildDisabledSample(mach, aoaDeg, normalized, legacyForces, warnings);
					} else {
						AerodynamicForces blended = calculator
								.getAerodynamicForces(configuration, conditions, warnings);
						RomResult result = calculator.getLastResult();
						sample = buildSample(mach, aoaDeg, normalized, legacyForces, blended,
								result, warnings);
					}
				} catch (RomMathException ex) {
					singularityCount++;
					sample = buildErrorSample(mach, aoaDeg, normalized, RomPreviewSample.Status.SINGULARITY,
							ex.getMessage(), warnings);
				} catch (RuntimeException ex) {
					errorCount++;
					sample = buildErrorSample(mach, aoaDeg, normalized, RomPreviewSample.Status.ERROR,
							ex.getClass().getSimpleName() + ": " + ex.getMessage(), warnings);
				}

				samples.add(sample);
				worstConfidence = Math.min(worstConfidence, sample.getConfidence());
				maxFallbackPct = Math.max(maxFallbackPct, sample.getFallbackWeight() * 100.0);
				maxSeparationPct = Math.max(maxSeparationPct,
						sample.getSeparationFraction() * 100.0);

				if (progress != null) {
					progress.onProgress(samples.size(), requestedRows);
				}
			}
			if (cancelled) {
				break;
			}
		}

		String msg;
		if (cancelled) {
			msg = String.format(Locale.ROOT, "Cancelled after %d of %d rows.",
					samples.size(), requestedRows);
		} else {
			msg = String.format(Locale.ROOT, "Completed %d of %d rows.",
					samples.size(), requestedRows);
		}

		return new Result(samples, requestedRows, worstConfidence, maxFallbackPct, maxSeparationPct,
				singularityCount, errorCount, cancelled, false, msg);
	}

	private RomPreviewSample buildSample(double mach, double aoaDeg, RomSettings settings,
			AerodynamicForces legacy, AerodynamicForces blended, RomResult result, WarningSet warnings) {
		RomPreviewSample.Builder b = RomPreviewSample.builder()
				.mach(mach)
				.aoaDeg(aoaDeg)
				.thetaDeg(settings.getPreviewThetaDeg())
				.plumeState(settings.getPreviewPlumeState())
				.cdLegacy(legacy.getCD())
				.cnLegacy(legacy.getCN())
				.cmLegacy(legacy.getCm())
				.warnings(extractWarnings(warnings));

		if (result == null) {
			// ROM returned no detailed result (e.g. failed to initialise) — fall back to blended values.
			b.cdRom(blended.getCD())
					.cdFinal(blended.getCD())
					.cnRom(blended.getCN())
					.cnFinal(blended.getCN())
					.cmRom(blended.getCm())
					.cmFinal(blended.getCm())
					.cpxFinalMeters(blended.getCP() != null ? blended.getCP().getX() : 0.0)
					.confidence(1.0)
					.fallbackWeight(1.0)
					.fallbackUsed(true)
					.regime("")
					.status(RomPreviewSample.Status.FALLBACK_USED);
			return b.build();
		}

		AerodynamicForces romForces = result.getRomForces();
		double cpx = result.getBlendedForces().getCP() != null
				? result.getBlendedForces().getCP().getX()
				: 0.0;

		List<EdgeState> edges = result.getEdgeStates();
		List<BoundaryLayerState> bls = result.getBoundaryLayerStates();

		int transitioned = 0;
		int separated = 0;
		double stiffnessSum = 0.0;
		double stiffnessMax = 0.0;
		double cfSum = 0.0;
		int cfCount = 0;
		double minCp = Double.POSITIVE_INFINITY;
		double maxCp = Double.NEGATIVE_INFINITY;
		double minEdgeMach = Double.POSITIVE_INFINITY;
		double maxEdgeMach = Double.NEGATIVE_INFINITY;

		for (BoundaryLayerState s : bls) {
			if (s.isTransitioned()) {
				transitioned++;
			}
			if (s.isSeparated()) {
				separated++;
			}
			double stiff = Math.max(0.0, s.getStiffnessIndicator());
			stiffnessSum += stiff;
			stiffnessMax = Math.max(stiffnessMax, stiff);
			cfSum += s.getSkinFrictionCoefficient();
			cfCount++;
		}
		double meanStiff = bls.isEmpty() ? 0.0 : stiffnessSum / bls.size();
		double meanCf = cfCount == 0 ? 0.0 : cfSum / cfCount;

		for (EdgeState e : edges) {
			minCp = Math.min(minCp, e.getPressureCoefficient());
			maxCp = Math.max(maxCp, e.getPressureCoefficient());
			minEdgeMach = Math.min(minEdgeMach, e.getEdgeMach());
			maxEdgeMach = Math.max(maxEdgeMach, e.getEdgeMach());
		}
		if (!Double.isFinite(minCp)) minCp = 0.0;
		if (!Double.isFinite(maxCp)) maxCp = 0.0;
		if (!Double.isFinite(minEdgeMach)) minEdgeMach = 0.0;
		if (!Double.isFinite(maxEdgeMach)) maxEdgeMach = 0.0;

		double sideslip = result.getFlowState() != null ? result.getFlowState().getSideslipDeg() : 0.0;

		b.betaDeg(sideslip)
				.regime(result.getRegime() != null ? result.getRegime().name() : "")
				.cdRom(romForces.getCD())
				.cdFinal(result.getBlendedForces().getCD())
				.cnRom(romForces.getCN())
				.cnFinal(result.getBlendedForces().getCN())
				.cmRom(romForces.getCm())
				.cmFinal(result.getBlendedForces().getCm())
				.cpxFinalMeters(cpx)
				.confidence(result.getConfidence() != null ? result.getConfidence().getOverallScore() : 0.0)
				.fallbackWeight(result.getFallbackWeight())
				.fallbackUsed(result.isFallbackUsed())
				.separationFraction(result.getSeparationFraction())
				.seedCount(result.getSeeds().size())
				.marchingSteps(result.getMarchingSteps())
				.transitionedCount(transitioned)
				.separatedCount(separated)
				.meanStiffness(meanStiff)
				.maxStiffness(stiffnessMax)
				.meanCf(meanCf)
				.minEdgeCp(minCp)
				.maxEdgeCp(maxCp)
				.minEdgeMach(minEdgeMach)
				.maxEdgeMach(maxEdgeMach)
				.notes(result.getNotes());

		RomPreviewSample.Status status = result.isFallbackUsed()
				? RomPreviewSample.Status.FALLBACK_USED
				: RomPreviewSample.Status.OK;
		b.status(status);
		return b.build();
	}

	private RomPreviewSample buildDisabledSample(double mach, double aoaDeg, RomSettings settings,
			AerodynamicForces legacy, WarningSet warnings) {
		double cpx = legacy.getCP() != null ? legacy.getCP().getX() : 0.0;
		return RomPreviewSample.builder()
				.mach(mach)
				.aoaDeg(aoaDeg)
				.thetaDeg(settings.getPreviewThetaDeg())
				.plumeState(settings.getPreviewPlumeState())
				.regime("")
				.cdLegacy(legacy.getCD())
				.cdRom(legacy.getCD())
				.cdFinal(legacy.getCD())
				.cnLegacy(legacy.getCN())
				.cnRom(legacy.getCN())
				.cnFinal(legacy.getCN())
				.cmLegacy(legacy.getCm())
				.cmRom(legacy.getCm())
				.cmFinal(legacy.getCm())
				.cpxFinalMeters(cpx)
				.confidence(1.0)
				.fallbackWeight(1.0)
				.fallbackUsed(true)
				.notes("ROM disabled — showing Barrowman reference only.")
				.warnings(extractWarnings(warnings))
				.status(RomPreviewSample.Status.DISABLED)
				.build();
	}

	private RomPreviewSample buildErrorSample(double mach, double aoaDeg, RomSettings settings,
			RomPreviewSample.Status status, String message, WarningSet warnings) {
		return RomPreviewSample.builder()
				.mach(mach)
				.aoaDeg(aoaDeg)
				.thetaDeg(settings.getPreviewThetaDeg())
				.plumeState(settings.getPreviewPlumeState())
				.status(status)
				.notes(message != null ? message : "")
				.warnings(extractWarnings(warnings))
				.build();
	}

	private static List<String> extractWarnings(WarningSet warnings) {
		if (warnings == null || warnings.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		warnings.forEach(w -> out.add(w.toString()));
		return out;
	}

	private static List<Double> linspace(double min, double max, double step) {
		List<Double> values = new ArrayList<>();
		double safeStep = Math.max(1e-9, step);
		if (max - min < safeStep * 1e-6) {
			values.add(min);
			return values;
		}
		double tolerance = safeStep * 1e-6;
		for (double v = min; v <= max + tolerance; v += safeStep) {
			values.add(roundTo(Math.min(v, max), 6));
		}
		// guarantee endpoints are present in case of floating roundoff
		if (!values.isEmpty() && Math.abs(values.get(values.size() - 1) - max) > tolerance) {
			values.add(roundTo(max, 6));
		}
		return values;
	}

	private static double roundTo(double v, int decimals) {
		double scale = Math.pow(10.0, decimals);
		return Math.round(v * scale) / scale;
	}
}
