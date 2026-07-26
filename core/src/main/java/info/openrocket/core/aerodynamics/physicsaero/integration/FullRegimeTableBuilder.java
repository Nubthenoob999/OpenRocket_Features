package info.openrocket.core.aerodynamics.physicsaero.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.blending.ComponentRegimeBlender;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySolver;
import info.openrocket.core.aerodynamics.physicsaero.body.SeparatedBoattailPressureDragModel;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.EngineeringSkinFrictionCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBasePressureClosureModel;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinGeometryAdapter;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinLeadingEdgePressureDragModel;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStripDiscretizer;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinTrailingEdgeBaseDragModel;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import info.openrocket.core.aerodynamics.physicsaero.force.CoefficientAssembler;
import info.openrocket.core.aerodynamics.physicsaero.force.ContributionLedger;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.interaction.SlenderCruciformCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.interaction.CompactFinnedBodyZeroLiftCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.powered.PoweredBaseFlowModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ProtuberanceDragModel;
import info.openrocket.core.aerodynamics.physicsaero.subsonic.BarrowmanLowSpeedAdapter;
import info.openrocket.core.aerodynamics.physicsaero.subsonic.LowSpeedRegimeSelector;
import info.openrocket.core.aerodynamics.physicsaero.subsonic.SubsonicComponentAssembler;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CombinedBodyFinTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;
import info.openrocket.core.aerodynamics.physicsaero.thermodynamics.HighMachThermodynamicSelector;
import info.openrocket.core.aerodynamics.physicsaero.thermodynamics.ThermallyPerfectAir;
import info.openrocket.core.aerodynamics.physicsaero.transonic.BodyCriticalMachEstimator;
import info.openrocket.core.aerodynamics.physicsaero.transonic.FinCriticalMachEstimator;
import info.openrocket.core.aerodynamics.physicsaero.transonic.FinDragDivergenceEstimator;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicBaseDragModel;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicDragRiseHierarchy;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicDragRiseModel;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicFinLiftCorrection;

/**
 * Deterministic Mach-0-to-7 component table. Supersonic Phases 2-5 remain
 * authoritative in their validated range.
 */
public final class FullRegimeTableBuilder {
	private static final double SUBSONIC_OVERLAP_START = 0.85;
	private static final double SUBSONIC_OVERLAP_END = 0.95;
	private static final double DIRECT_BODY_PRESSURE_BRIDGE_START = 1.00;
	private static final String DIRECT_BODY_PRESSURE_BRIDGE_METHOD_ID =
			"MACH_1P2_DIRECT_BODY_PRESSURE_ENDPOINT_BRIDGE_V1";
	/*
	 * NACA RM L9I30 figures 8-12 show the measured zero-lift drag rise of
	 * fin-stabilized bodies becoming predominantly supersonic-pressure drag by
	 * Mach 1.2, with no second handoff delayed to Mach 1.5.  Preserve the
	 * validated Mach-1.2 lower bound of the direct Phase-2 correlations and
	 * complete the handoff at Mach 1.3 with a C1-smooth overlap through the
	 * shock-attachment topology change.
	 */
	private static final double SUPERSONIC_OVERLAP_START = 1.20;
	private static final double SUPERSONIC_OVERLAP_END = 1.30;
	/** Mach nodes in this closed range need a supersonic Reynolds stencil pre-solve. */
	public static final double SUPERSONIC_STENCIL_MIN_MACH = 1.20;
	public static final double SUPERSONIC_STENCIL_MAX_MACH = 5.0;
	private static final double THREE_DECADE_REYNOLDS_MAXIMUM_MACH = 1.30;
	private static final double HIGH_MACH_OVERLAP_START = 4.0;
	private static final double HIGH_MACH_OVERLAP_END = 5.0;
	private static final double FIN_DRAG_DIVERGENCE_CALIBRATION = 0.92;
	/*
	 * The table is generated at a sea-level reference atmosphere, while flight
	 * queries begin at field elevations that are commonly already 10-15% below
	 * the reference Reynolds number.  A +/-5% envelope therefore forced an
	 * immediate Barrowman fallback even though the stored log-Re sensitivity is
	 * specifically intended to account for atmospheric variation.
	 *
	 * Keep the upper limit modest (dense/cold launch sites), but cover the
	 * lower-Re portion of ascent where the continuous engineering correlations
	 * retain the same physical ownership. The established one-decade correction
	 * retains its direct Re/Re_ref=0.10 anchor. A broader two-decade correction is
	 * fitted through direct 0.10 and 0.01 solutions and is admitted only when it
	 * independently reproduces direct solutions at 0.25 and sqrt(0.001). This
	 * avoids using a local derivative as a two-decade extrapolation. At Mach 1.3
	 * and below, a three-decade cubic is fitted through direct 0.10, 0.01, and
	 * 0.001 anchors and independently checked at each log-midpoint plus the local
	 * stencil. Cells whose ownership or separation topology changes retain the
	 * conservative narrower envelope.
	 */
	private static final double LOCAL_MINIMUM_RUNTIME_REYNOLDS_RATIO = 0.50;
	private static final double ONE_DECADE_VALIDATION_REYNOLDS_RATIO = 0.25;
	private static final double ONE_DECADE_ANCHOR_REYNOLDS_RATIO = 0.10;
	private static final double TWO_DECADE_VALIDATION_REYNOLDS_RATIO = 0.03162277660168379;
	private static final double TWO_DECADE_ANCHOR_REYNOLDS_RATIO = 0.01;
	private static final double THREE_DECADE_VALIDATION_REYNOLDS_RATIO = 0.0031622776601683794;
	private static final double THREE_DECADE_ANCHOR_REYNOLDS_RATIO = 0.001;
	private static final double MAXIMUM_RUNTIME_REYNOLDS_RATIO = 1.25;
	private static final String RUNTIME_REYNOLDS_METHOD_ID = "ANCHORED_CUBIC_LOG_RE_V5";
	private final Map<BodyPressureAnchorKey, BodyPressureAnchor> bodyPressureAnchors =
			new ConcurrentHashMap<>();
	private final Map<String, GeometryMetrics> geometryMetricsCache =
			new ConcurrentHashMap<>();
	private final int workerCount;

	public FullRegimeTableBuilder() {
		this(recommendedWorkerCount(Runtime.getRuntime().availableProcessors()));
	}

	FullRegimeTableBuilder(int workerCount) {
		if (workerCount < 1) {
			throw new IllegalArgumentException("worker count must be positive");
		}
		this.workerCount = workerCount;
	}

	/**
	 * Leaves enough CPU capacity for the UI and other simulations while keeping
	 * the table build compute-bound.  Rounding 55% gives 50-60% for every
	 * multi-core count except three logical processors, where two workers are the
	 * closest useful choice.
	 */
	static int recommendedWorkerCount(int availableProcessors) {
		if (availableProcessors < 1) {
			throw new IllegalArgumentException("available processor count must be positive");
		}
		return Math.max(1, (int) Math.round(availableProcessors * 0.55));
	}

	public AerodynamicTable build(AeroGeometry geometry, double[] mach, double[] alpha,
			double[] beta, AtmosphereState atmosphere, ThermodynamicModel gas,
			TableMetadata metadata) {
		return build(geometry, mach, alpha, beta, atmosphere, gas, metadata, () -> false, ignored -> { });
	}

	public AerodynamicTable build(AeroGeometry geometry, double[] mach, double[] alpha,
			double[] beta, AtmosphereState atmosphere, ThermodynamicModel gas,
			TableMetadata metadata, BooleanSupplier cancelled, IntConsumer progress) {
		validateAxes(mach, alpha, beta);
		TableAxes axes = new TableAxes(mach, alpha, beta);
		ExecutorService executor = newBuildExecutor();
		try {
			Map<Double, SupersonicStencil> supersonic = supersonicTables(
					geometry, mach, alpha, beta, atmosphere, gas, metadata, cancelled, progress, executor);
			TableCell[] cells = parallelIndexed(executor, axes.cellCount(), cancelled, flatIndex -> {
				int betaIndex = flatIndex % beta.length;
				int alphaIndex = (flatIndex / beta.length) % alpha.length;
				int machIndex = flatIndex / (alpha.length * beta.length);
				return cellWithRuntimeCorrection(geometry, mach[machIndex], alpha[alphaIndex], beta[betaIndex],
						alphaIndex, betaIndex, atmosphere, gas, supersonic.get(mach[machIndex]));
			}, completed -> progress.accept(5 + (80 * completed / axes.cellCount())),
					TableCell[]::new);
			return new AerodynamicTable(axes, Arrays.asList(cells), metadata);
		} finally {
			shutdown(executor);
		}
	}

	/** Builds coast and jet-on variants on an explicit powered-state axis. */
	public AerodynamicTable build(AeroGeometry geometry, double[] mach, double[] alpha,
			double[] beta, PoweredFlowState[] poweredStates, AtmosphereState atmosphere,
			ThermodynamicModel gas, TableMetadata metadata) {
		return build(geometry, mach, alpha, beta, poweredStates, atmosphere, gas, metadata,
				() -> false, ignored -> { });
	}

	public AerodynamicTable build(AeroGeometry geometry, double[] mach, double[] alpha,
			double[] beta, PoweredFlowState[] poweredStates, AtmosphereState atmosphere,
			ThermodynamicModel gas, TableMetadata metadata, BooleanSupplier cancelled,
			IntConsumer progress) {
		if (poweredStates == null || poweredStates.length == 0) {
			throw new IllegalArgumentException("powered states required");
		}
		double[] poweredAxis = new double[poweredStates.length];
		for (int index = 0; index < poweredStates.length; index++) {
			poweredAxis[index] = poweredStates[index].poweredFraction();
			if (index > 0 && poweredAxis[index] <= poweredAxis[index - 1]) {
				throw new IllegalArgumentException("powered states must be strictly increasing");
			}
		}
		validateAxes(mach, alpha, beta);
		TableAxes axes = new TableAxes(mach, alpha, beta, poweredAxis);
		int coastCellCount = mach.length * alpha.length * beta.length;
		ExecutorService executor = newBuildExecutor();
		try {
			Map<Double, SupersonicStencil> supersonic = supersonicTables(
					geometry, mach, alpha, beta, atmosphere, gas, metadata, cancelled, progress, executor);
			TableCell[][] poweredCells = parallelIndexed(executor, coastCellCount, cancelled, flatIndex -> {
				int betaIndex = flatIndex % beta.length;
				int alphaIndex = (flatIndex / beta.length) % alpha.length;
				int machIndex = flatIndex / (alpha.length * beta.length);
				TableCell coast = cellWithRuntimeCorrection(geometry, mach[machIndex], alpha[alphaIndex],
						beta[betaIndex], alphaIndex, betaIndex, atmosphere, gas,
						supersonic.get(mach[machIndex]));
				TableCell[] states = new TableCell[poweredStates.length];
				for (int stateIndex = 0; stateIndex < poweredStates.length; stateIndex++) {
					states[stateIndex] = applyPoweredState(
							geometry, mach[machIndex], coast, poweredStates[stateIndex]);
				}
				return states;
			}, completed -> progress.accept(5 + (80 * completed / coastCellCount)),
					TableCell[][]::new);
			List<TableCell> cells = new ArrayList<>(axes.cellCount());
			for (TableCell[] states : poweredCells) {
				cells.addAll(Arrays.asList(states));
			}
			return new AerodynamicTable(axes, cells, metadata);
		} finally {
			shutdown(executor);
		}
	}

	/**
	 * Progress is reported over 1..4 so that the caller can distinguish this
	 * pre-solve phase from the cell sweep, which starts at 5.
	 */
	private Map<Double, SupersonicStencil> supersonicTables(AeroGeometry geometry, double[] mach,
			double[] alpha, double[] beta, AtmosphereState atmosphere, ThermodynamicModel gas,
			TableMetadata metadata, BooleanSupplier cancelled, IntConsumer progress,
			ExecutorService executor) {
		Map<Double, SupersonicStencil> supersonic = new HashMap<>();
		final double logStep = 0.05;
		AtmosphereState lowerReAtmosphere = withDensityScale(atmosphere, Math.exp(-logStep));
		AtmosphereState upperReAtmosphere = withDensityScale(atmosphere, Math.exp(logStep));
		AtmosphereState oneDecadeAnchorAtmosphere = withDensityScale(atmosphere,
				ONE_DECADE_ANCHOR_REYNOLDS_RATIO);
		AtmosphereState oneDecadeValidationAtmosphere = withDensityScale(atmosphere,
				ONE_DECADE_VALIDATION_REYNOLDS_RATIO);
		AtmosphereState twoDecadeAnchorAtmosphere = withDensityScale(atmosphere,
				TWO_DECADE_ANCHOR_REYNOLDS_RATIO);
		AtmosphereState twoDecadeValidationAtmosphere = withDensityScale(atmosphere,
				TWO_DECADE_VALIDATION_REYNOLDS_RATIO);
		AtmosphereState threeDecadeAnchorAtmosphere = withDensityScale(atmosphere,
				THREE_DECADE_ANCHOR_REYNOLDS_RATIO);
		AtmosphereState threeDecadeValidationAtmosphere = withDensityScale(atmosphere,
				THREE_DECADE_VALIDATION_REYNOLDS_RATIO);
		double[] stencilMach = Arrays.stream(mach)
				.filter(currentMach -> currentMach >= SUPERSONIC_STENCIL_MIN_MACH
						&& currentMach <= SUPERSONIC_STENCIL_MAX_MACH)
				.toArray();
		SupersonicStencil[] stencils = parallelIndexed(executor, stencilMach.length, cancelled, index -> {
			double currentMach = stencilMach[index];
			CombinedBodyFinTableBuilder builder = new CombinedBodyFinTableBuilder(true, true);
			return new SupersonicStencil(
					builder.build(geometry, new double[] {currentMach}, alpha, beta,
							atmosphere, gas, metadata),
					builder.build(geometry, new double[] {currentMach}, alpha, beta,
							lowerReAtmosphere, gas, metadata),
					builder.build(geometry, new double[] {currentMach}, alpha, beta,
							upperReAtmosphere, gas, metadata),
					builder.build(geometry, new double[] {currentMach}, alpha, beta,
							oneDecadeAnchorAtmosphere, gas, metadata),
					builder.build(geometry, new double[] {currentMach}, alpha, beta,
							oneDecadeValidationAtmosphere, gas, metadata),
					builder.build(geometry, new double[] {currentMach}, alpha, beta,
							twoDecadeAnchorAtmosphere, gas, metadata),
					builder.build(geometry, new double[] {currentMach}, alpha, beta,
							twoDecadeValidationAtmosphere, gas, metadata),
					currentMach <= THREE_DECADE_REYNOLDS_MAXIMUM_MACH
							? builder.build(geometry, new double[] {currentMach}, alpha, beta,
									threeDecadeAnchorAtmosphere, gas, metadata) : null,
					currentMach <= THREE_DECADE_REYNOLDS_MAXIMUM_MACH
							? builder.build(geometry, new double[] {currentMach}, alpha, beta,
									threeDecadeValidationAtmosphere, gas, metadata) : null);
		}, completed -> progress.accept(1 + (3 * completed / Math.max(1, stencilMach.length))),
				SupersonicStencil[]::new);
		for (int index = 0; index < stencilMach.length; index++) {
			supersonic.put(stencilMach[index], stencils[index]);
		}
		return supersonic;
	}

	private ExecutorService newBuildExecutor() {
		return Executors.newFixedThreadPool(workerCount, runnable -> {
			Thread thread = new Thread(runnable, "physics-aero-table-worker");
			thread.setDaemon(true);
			return thread;
		});
	}

	private static <T> T[] parallelIndexed(ExecutorService executor, int count,
			BooleanSupplier cancelled, IntFunction<T> evaluator, IntConsumer progress,
			IntFunction<T[]> arrayFactory) {
		T[] results = arrayFactory.apply(count);
		if (count == 0) {
			return results;
		}
		CompletionService<IndexedResult<T>> completion = new ExecutorCompletionService<>(executor);
		List<Future<IndexedResult<T>>> futures = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			final int taskIndex = index;
			futures.add(completion.submit(() -> {
				checkCancelled(cancelled);
				return new IndexedResult<>(taskIndex, evaluator.apply(taskIndex));
			}));
		}
		try {
			for (int completed = 1; completed <= count; completed++) {
				checkCancelled(cancelled);
				IndexedResult<T> result = completion.take().get();
				results[result.index()] = result.value();
				progress.accept(completed);
			}
			return results;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			futures.forEach(future -> future.cancel(true));
			throw new IllegalStateException("BUILD_INTERRUPTED", exception);
		} catch (ExecutionException exception) {
			futures.forEach(future -> future.cancel(true));
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new IllegalStateException("TABLE_CELL_GENERATION_FAILED", cause);
		} catch (RuntimeException exception) {
			futures.forEach(future -> future.cancel(true));
			throw exception;
		}
	}

	private static void shutdown(ExecutorService executor) {
		executor.shutdownNow();
		try {
			executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private static AtmosphereState withDensityScale(AtmosphereState atmosphere, double scale) {
		return new AtmosphereState(atmosphere.pressurePa() * scale, atmosphere.temperatureK(),
				atmosphere.densityKgM3() * scale, atmosphere.dynamicViscosityPaS());
	}

	private static void checkCancelled(BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean()) {
			throw new IllegalStateException("BUILD_CANCELLED_LAST_VALID_TABLE_UNCHANGED");
		}
	}

	private TableCell applyPoweredState(AeroGeometry geometry, double mach, TableCell coast,
			PoweredFlowState state) {
		List<String> validity = new ArrayList<>(coast.validityFlags());
		if (!state.powered()) {
			validity.add("COAST_STATE");
			return new TableCell(coast.coefficients(), coast.componentTotals(), coast.ownerTotals(),
					coast.methodIds(), coast.confidence(), coast.uncertainty(), validity,
					coast.referenceState(), coast.diagnostics(), coast.directlyGenerated(), coast.derivatives(),
					coast.runtimeCorrection());
		}
		if (!state.hasPoweredClosureState()) {
			validity.add("POWERED_STATE");
			validity.add("POWERED_INCREMENT_UNMODELED");
			return new TableCell(coast.coefficients(), coast.componentTotals(), coast.ownerTotals(),
					coast.methodIds(), coast.confidence(), coast.uncertainty(), validity,
					coast.referenceState(), coast.diagnostics(), coast.directlyGenerated(),
					coast.derivatives(), coast.runtimeCorrection());
		}
		PoweredBaseFlowModel model = new PoweredBaseFlowModel();
		if (!model.supportsMach(mach)) {
			validity.add("POWERED_STATE");
			validity.add("POWERED_INCREMENT_OUTSIDE_SOURCE_RANGE");
			return new TableCell(coast.coefficients(), coast.componentTotals(), coast.ownerTotals(),
					coast.methodIds(), coast.confidence(), coast.uncertainty(), validity,
					coast.referenceState(), coast.diagnostics(), coast.directlyGenerated(),
					coast.derivatives(), coast.runtimeCorrection());
		}
		var correction = model.evaluate(geometry, mach, state);
		AerodynamicCoefficients poweredDelta = axial(correction.totalDeltaCd());
		AerodynamicCoefficients coefficients = add(coast.coefficients(), poweredDelta);
		Map<String, AerodynamicCoefficients> components = new HashMap<>(coast.componentTotals());
		components.put("powered-flow", poweredDelta);
		Map<String, AerodynamicCoefficients> owners = new HashMap<>(coast.ownerTotals());
		owners.put("POWERED_BASE_PRESSURE_DRAG", axial(correction.baseDeltaCd()));
		owners.put("POWERED_BOATTAIL_PRESSURE_DRAG", axial(correction.boattailDeltaCd()));
		owners.put("POWERED_PLUME_INSTALLATION_DRAG", axial(correction.plumeAndInstallationDeltaCd()));
		validity.add("POWERED_STATE");
		validity.addAll(correction.validityFlags());
		return new TableCell(coefficients, components, owners,
				union(coast.methodIds(), correction.methodIds()), coast.confidence(), coast.uncertainty(),
				validity, coast.referenceState(), coast.diagnostics(), coast.directlyGenerated(),
				coast.derivatives(), coast.runtimeCorrection());
	}

	private static AerodynamicCoefficients axial(double coefficient) {
		return new AerodynamicCoefficients(coefficient, 0, 0, 0, 0, 0);
	}

	private static AerodynamicCoefficients add(AerodynamicCoefficients first,
			AerodynamicCoefficients second) {
		double[] a = first.toArray();
		double[] b = second.toArray();
		for (int index = 0; index < a.length; index++) a[index] += b[index];
		return AerodynamicCoefficients.fromArray(a);
	}

	private TableCell cellWithRuntimeCorrection(AeroGeometry geometry, double mach, double alpha, double beta,
			int alphaIndex, int betaIndex, AtmosphereState atmosphere,
			ThermodynamicModel gas, SupersonicStencil supersonic) {
		TableCell center = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
				atmosphere, gas, supersonic == null ? null : supersonic.center());
		double speed = mach * gas.speedOfSound(atmosphere.temperatureK());
		double viscosity = atmosphere.dynamicViscosityPaS();
		double reynolds = Double.isFinite(viscosity) && viscosity > 0
				? atmosphere.densityKgM3() * speed * geometry.references().referenceLengthM() / viscosity : 0;
		if (!(reynolds > 0)) {
			/*
			 * Mach zero is a coefficient limit, not missing Reynolds data.  A
			 * valid zero-reference endpoint lets interpolation toward the first
			 * positive Mach node produce the correct proportional Re reference.
			 */
			if (mach == 0) {
				return withCorrection(center, new RuntimeCorrectionData(0,
						THREE_DECADE_ANCHOR_REYNOLDS_RATIO, MAXIMUM_RUNTIME_REYNOLDS_RATIO,
						new double[6], false, RUNTIME_REYNOLDS_METHOD_ID));
			}
			return withCorrection(center, RuntimeCorrectionData.rebuildRequired(reynolds));
		}
		final double logStep = 0.05;
		AtmosphereState lowerReAtmosphere = withDensityScale(atmosphere, Math.exp(-logStep));
		AtmosphereState upperReAtmosphere = withDensityScale(atmosphere, Math.exp(logStep));
		TableCell lower = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
				lowerReAtmosphere, gas, supersonic == null ? null : supersonic.lower());
		TableCell upper = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
				upperReAtmosphere, gas, supersonic == null ? null : supersonic.upper());
		boolean localTopologyStable = sameCorrectionTopology(center, lower)
				&& sameCorrectionTopology(center, upper);
		double[] low = lower.coefficients().toArray();
		double[] high = upper.coefficients().toArray();
		double[] sensitivity = new double[6];
		for (int index = 0; index < sensitivity.length; index++) {
			sensitivity[index] = (high[index] - low[index]) / (2 * logStep);
		}
		AtmosphereState oneDecadeAnchorAtmosphere = withDensityScale(atmosphere,
				ONE_DECADE_ANCHOR_REYNOLDS_RATIO);
		TableCell oneDecadeAnchor = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
				oneDecadeAnchorAtmosphere, gas,
				supersonic == null ? null : supersonic.oneDecadeAnchor());
		AtmosphereState oneDecadeValidationAtmosphere = withDensityScale(atmosphere,
				ONE_DECADE_VALIDATION_REYNOLDS_RATIO);
		TableCell oneDecadeValidation = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
				oneDecadeValidationAtmosphere, gas,
				supersonic == null ? null : supersonic.oneDecadeValidation());
		double oneDecadeLogRatio = Math.log(ONE_DECADE_ANCHOR_REYNOLDS_RATIO);
		double oneDecadeLogRatioSquared = oneDecadeLogRatio * oneDecadeLogRatio;
		double[] oneDecadeAnchorValues = oneDecadeAnchor.coefficients().toArray();
		double[] centerValues = center.coefficients().toArray();
		double[] oneDecadeCurvature = new double[6];
		for (int index = 0; index < oneDecadeCurvature.length; index++) {
			oneDecadeCurvature[index] = (oneDecadeAnchorValues[index] - centerValues[index]
					- sensitivity[index] * oneDecadeLogRatio) / oneDecadeLogRatioSquared;
		}

		AtmosphereState twoDecadeAnchorAtmosphere = withDensityScale(atmosphere,
				TWO_DECADE_ANCHOR_REYNOLDS_RATIO);
		TableCell twoDecadeAnchor = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
				twoDecadeAnchorAtmosphere, gas,
				supersonic == null ? null : supersonic.twoDecadeAnchor());
		double[] twoDecadeAnchorValues = twoDecadeAnchor.coefficients().toArray();
		double[] broadLinear = new double[6];
		double[] broadQuadratic = new double[6];
		fitQuadraticThroughAnchors(centerValues,
				oneDecadeAnchorValues, ONE_DECADE_ANCHOR_REYNOLDS_RATIO,
				twoDecadeAnchorValues, TWO_DECADE_ANCHOR_REYNOLDS_RATIO,
				broadLinear, broadQuadratic);

		AtmosphereState twoDecadeValidationAtmosphere = withDensityScale(atmosphere,
				TWO_DECADE_VALIDATION_REYNOLDS_RATIO);
		TableCell twoDecadeValidation = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
				twoDecadeValidationAtmosphere, gas,
				supersonic == null ? null : supersonic.twoDecadeValidation());
		boolean broadTopologyStable = localTopologyStable
				&& sameCorrectionTopology(center, oneDecadeAnchor)
				&& sameCorrectionTopology(center, oneDecadeValidation)
				&& sameCorrectionTopology(center, twoDecadeAnchor)
				&& sameCorrectionTopology(center, twoDecadeValidation);
		boolean broadValidated = correctionMatches(centerValues, broadLinear, broadQuadratic,
						oneDecadeValidation.coefficients().toArray(),
						ONE_DECADE_VALIDATION_REYNOLDS_RATIO)
				&& correctionMatches(centerValues, broadLinear, broadQuadratic,
						twoDecadeValidation.coefficients().toArray(),
						TWO_DECADE_VALIDATION_REYNOLDS_RATIO)
				&& correctionMatches(centerValues, broadLinear, broadQuadratic,
						lower.coefficients().toArray(), Math.exp(-logStep))
				&& correctionMatches(centerValues, broadLinear, broadQuadratic,
						upper.coefficients().toArray(), Math.exp(logStep));
		if (mach <= THREE_DECADE_REYNOLDS_MAXIMUM_MACH) {
			AtmosphereState threeDecadeAnchorAtmosphere = withDensityScale(atmosphere,
					THREE_DECADE_ANCHOR_REYNOLDS_RATIO);
			TableCell threeDecadeAnchor = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
					threeDecadeAnchorAtmosphere, gas,
					supersonic == null ? null : supersonic.threeDecadeAnchor());
			AtmosphereState threeDecadeValidationAtmosphere = withDensityScale(atmosphere,
					THREE_DECADE_VALIDATION_REYNOLDS_RATIO);
			TableCell threeDecadeValidation = cell(geometry, mach, alpha, beta, alphaIndex, betaIndex,
					threeDecadeValidationAtmosphere, gas,
					supersonic == null ? null : supersonic.threeDecadeValidation());
			double[] cubicLinear = new double[6];
			double[] cubicQuadratic = new double[6];
			double[] cubic = new double[6];
			fitCubicThroughDecadeAnchors(centerValues,
					oneDecadeAnchorValues, twoDecadeAnchorValues,
					threeDecadeAnchor.coefficients().toArray(),
					cubicLinear, cubicQuadratic, cubic);
			boolean threeDecadeTopologyStable = broadTopologyStable
					&& sameCorrectionTopology(center, threeDecadeAnchor)
					&& sameCorrectionTopology(center, threeDecadeValidation);
			boolean threeDecadeValidated =
					correctionMatches(centerValues, cubicLinear, cubicQuadratic, cubic,
							oneDecadeValidation.coefficients().toArray(),
							ONE_DECADE_VALIDATION_REYNOLDS_RATIO)
					&& correctionMatches(centerValues, cubicLinear, cubicQuadratic, cubic,
							twoDecadeValidation.coefficients().toArray(),
							TWO_DECADE_VALIDATION_REYNOLDS_RATIO)
					&& correctionMatches(centerValues, cubicLinear, cubicQuadratic, cubic,
							threeDecadeValidation.coefficients().toArray(),
							THREE_DECADE_VALIDATION_REYNOLDS_RATIO)
					&& correctionMatches(centerValues, cubicLinear, cubicQuadratic, cubic,
							threeDecadeAnchor.coefficients().toArray(),
							THREE_DECADE_ANCHOR_REYNOLDS_RATIO)
					&& correctionMatches(centerValues, cubicLinear, cubicQuadratic, cubic,
							lower.coefficients().toArray(), Math.exp(-logStep))
					&& correctionMatches(centerValues, cubicLinear, cubicQuadratic, cubic,
							upper.coefficients().toArray(), Math.exp(logStep));
			if (threeDecadeTopologyStable && threeDecadeValidated) {
				return withCorrection(center, new RuntimeCorrectionData(reynolds,
						THREE_DECADE_ANCHOR_REYNOLDS_RATIO, MAXIMUM_RUNTIME_REYNOLDS_RATIO,
						cubicLinear, cubicQuadratic, cubic, false,
						RUNTIME_REYNOLDS_METHOD_ID));
			}
		}
		if (broadTopologyStable && broadValidated) {
			return withCorrection(center, new RuntimeCorrectionData(reynolds,
					TWO_DECADE_ANCHOR_REYNOLDS_RATIO, MAXIMUM_RUNTIME_REYNOLDS_RATIO,
					broadLinear, broadQuadratic, false, RUNTIME_REYNOLDS_METHOD_ID));
		}

		boolean oneDecadeValidated = correctionMatches(centerValues, sensitivity,
				oneDecadeCurvature, oneDecadeValidation.coefficients().toArray(),
				ONE_DECADE_VALIDATION_REYNOLDS_RATIO);
		if ((localTopologyStable && sameCorrectionTopology(center, oneDecadeAnchor))
				|| oneDecadeValidated) {
			return withCorrection(center, new RuntimeCorrectionData(reynolds,
					ONE_DECADE_ANCHOR_REYNOLDS_RATIO, MAXIMUM_RUNTIME_REYNOLDS_RATIO,
					sensitivity, oneDecadeCurvature, false, RUNTIME_REYNOLDS_METHOD_ID));
		}
		if (!localTopologyStable) {
			return withCorrection(center, RuntimeCorrectionData.rebuildRequired(reynolds));
		}
		return withCorrection(center, new RuntimeCorrectionData(reynolds,
				LOCAL_MINIMUM_RUNTIME_REYNOLDS_RATIO, MAXIMUM_RUNTIME_REYNOLDS_RATIO,
				sensitivity, new double[6], false, RUNTIME_REYNOLDS_METHOD_ID));
	}

	private static void fitQuadraticThroughAnchors(double[] center,
			double[] firstAnchor, double firstRatio,
			double[] secondAnchor, double secondRatio,
			double[] linear, double[] quadratic) {
		double firstLog = Math.log(firstRatio);
		double secondLog = Math.log(secondRatio);
		double determinant = firstLog * secondLog * secondLog
				- secondLog * firstLog * firstLog;
		for (int index = 0; index < linear.length; index++) {
			double firstDelta = firstAnchor[index] - center[index];
			double secondDelta = secondAnchor[index] - center[index];
			linear[index] = (firstDelta * secondLog * secondLog
					- secondDelta * firstLog * firstLog) / determinant;
			quadratic[index] = (firstLog * secondDelta
					- secondLog * firstDelta) / determinant;
		}
	}

	private static void fitCubicThroughDecadeAnchors(double[] center,
			double[] oneDecadeAnchor, double[] twoDecadeAnchor,
			double[] threeDecadeAnchor, double[] linear, double[] quadratic,
			double[] cubic) {
		double decade = Math.log(10);
		for (int index = 0; index < linear.length; index++) {
			double first = oneDecadeAnchor[index] - center[index];
			double second = twoDecadeAnchor[index] - center[index];
			double third = threeDecadeAnchor[index] - center[index];
			// In u=-log(Re/Re_ref)/log(10), p(0)=0 and the direct
			// anchors are p(1), p(2), and p(3).  Finite differences
			// give the unique cubic without a numerically fragile matrix solve.
			double scaledCubic = (third - 3 * second + 3 * first) / 6;
			double scaledQuadratic = (second - 2 * first - 6 * scaledCubic) / 2;
			double scaledLinear = first - scaledQuadratic - scaledCubic;
			linear[index] = -scaledLinear / decade;
			quadratic[index] = scaledQuadratic / (decade * decade);
			cubic[index] = -scaledCubic / (decade * decade * decade);
		}
	}

	private static boolean correctionMatches(double[] center, double[] sensitivity,
			double[] curvature, double[] direct, double ratio) {
		return correctionMatches(center, sensitivity, curvature, new double[6],
				direct, ratio);
	}

	private static boolean correctionMatches(double[] center, double[] sensitivity,
			double[] curvature, double[] cubic, double[] direct, double ratio) {
		double logRatio = Math.log(ratio);
		for (int index = 0; index < direct.length; index++) {
			double predicted = center[index] + sensitivity[index] * logRatio
					+ curvature[index] * logRatio * logRatio
					+ cubic[index] * logRatio * logRatio * logRatio;
			double tolerance = 0.002 + 0.03 * Math.max(Math.abs(center[index]),
					Math.abs(direct[index]));
			if (Math.abs(predicted - direct[index]) > tolerance) return false;
		}
		return true;
	}

	private static boolean sameCorrectionTopology(TableCell expected, TableCell perturbed) {
		return expected.methodIds().equals(perturbed.methodIds())
				&& expected.componentTotals().keySet().equals(perturbed.componentTotals().keySet())
				&& expected.ownerTotals().keySet().equals(perturbed.ownerTotals().keySet())
				&& topologyFlags(expected.validityFlags()).equals(topologyFlags(perturbed.validityFlags()))
				&& expected.diagnostics().reasonCodes().equals(perturbed.diagnostics().reasonCodes());
	}

	private static Set<String> topologyFlags(List<String> flags) {
		Set<String> result = new java.util.TreeSet<>();
		for (String flag : flags) {
			String normalized = flag.toUpperCase(java.util.Locale.ROOT);
			if (normalized.contains("TOPOLOGY") || normalized.contains("SEPARAT")
					|| normalized.contains("TRANSITION") || normalized.contains("OWNED")) result.add(flag);
		}
		return result;
	}

	private static TableCell withCorrection(TableCell cell, RuntimeCorrectionData correction) {
		return new TableCell(cell.coefficients(), cell.componentTotals(), cell.ownerTotals(), cell.methodIds(),
				cell.confidence(), cell.uncertainty(), cell.validityFlags(), cell.referenceState(),
				cell.diagnostics(), cell.directlyGenerated(), cell.derivatives(), correction);
	}

	private TableCell cell(AeroGeometry geometry, double mach, double alpha, double beta,
			int alphaIndex, int betaIndex, AtmosphereState atmosphere,
			ThermodynamicModel gas, AerodynamicTable supersonicTable) {
		double speed = mach * gas.speedOfSound(atmosphere.temperatureK());
		double dynamicPressure = Math.max(1, 0.5 * atmosphere.densityKgM3() * speed * speed);
		ReferenceState reference = new ReferenceState(dynamicPressure,
				geometry.references().referenceAreaM2(), geometry.references().referenceLengthM(),
				geometry.references().momentOriginM());

		BranchResult branch;
		if (mach < SUBSONIC_OVERLAP_START) {
			branch = subsonic(geometry, mach, alpha, beta, atmosphere, gas, reference);
		} else if (mach < SUBSONIC_OVERLAP_END) {
			BranchResult subsonic = subsonic(geometry, mach, alpha, beta, atmosphere, gas, reference);
			BranchResult transonic = transonic(geometry, mach, alpha, beta, atmosphere, gas);
			branch = blend(subsonic, transonic,
					smoothOverlap(mach, SUBSONIC_OVERLAP_START, SUBSONIC_OVERLAP_END),
					"SUBSONIC_TRANSONIC_SMOOTH_OVERLAP");
		} else if (mach < SUPERSONIC_OVERLAP_START) {
			branch = transonic(geometry, mach, alpha, beta, atmosphere, gas);
		} else if (mach <= SUPERSONIC_OVERLAP_END) {
			BranchResult transonic = transonic(geometry, mach, alpha, beta, atmosphere, gas);
			BranchResult supersonic = supersonic(supersonicTable, alphaIndex, betaIndex);
			branch = blend(transonic, supersonic,
					smoothOverlap(mach, SUPERSONIC_OVERLAP_START, SUPERSONIC_OVERLAP_END),
					"TRANSONIC_SUPERSONIC_SMOOTH_OVERLAP");
		} else if (mach < HIGH_MACH_OVERLAP_START) {
			branch = supersonic(supersonicTable, alphaIndex, betaIndex);
		} else if (mach <= HIGH_MACH_OVERLAP_END) {
			BranchResult supersonic = supersonic(supersonicTable, alphaIndex, betaIndex);
			BranchResult highMach = highMach(geometry, mach, alpha, beta, atmosphere, gas);
			branch = blend(supersonic, highMach,
					smoothOverlap(mach, HIGH_MACH_OVERLAP_START, HIGH_MACH_OVERLAP_END),
					"SUPERSONIC_HIGH_MACH_SMOOTH_OVERLAP");
		} else {
			branch = highMach(geometry, mach, alpha, beta, atmosphere, gas);
		}
		branch = applySlenderCruciformClosure(geometry, mach, branch);
		branch = applyCompactFinnedBodyClosure(geometry, mach, alpha, beta, branch);
		branch = applyProtuberanceDrag(geometry, mach, alpha, beta, atmosphere, gas, branch);

		double[] confidence = new double[6];
		double[] uncertainty = new double[6];
		Arrays.fill(confidence, branch.confidence());
		Arrays.fill(uncertainty, 1 - branch.confidence());
		return new TableCell(branch.coefficients(), branch.componentTotals(), branch.ownerTotals(),
				branch.methods(), confidence, uncertainty, branch.validity(), reference,
				new CellDiagnostics(Set.of(DiagnosticFlag.DIRECT_GENERATION), reasons(branch), false, null, null,
						branch.messages()), true, staticRateDerivatives(geometry, mach));
	}

	private BranchResult applyProtuberanceDrag(AeroGeometry geometry, double mach,
			double alpha, double beta, AtmosphereState atmosphere,
			ThermodynamicModel gas, BranchResult branch) {
		if (geometry.components().stream().noneMatch(
				component -> component.protuberanceGeometry() != null)) {
			return branch;
		}
		double evaluationMach = Math.max(1.0e-9, mach);
		FlowCondition flow = FlowCondition.fromAngles(evaluationMach, alpha, beta,
				atmosphere, gas, false, geometry.geometryHash());
		ProtuberanceDragModel.Result result =
				new ProtuberanceDragModel().evaluate(geometry, flow);
		if (!(result.totalCd() > 0)) {
			return branch;
		}
		AerodynamicCoefficients delta = axial(result.totalCd());
		Map<String, AerodynamicCoefficients> components =
				new HashMap<>(branch.componentTotals());
		for (var component : result.components()) {
			components.merge(component.componentId(), axial(component.totalCd()),
					FullRegimeTableBuilder::add);
		}
		Map<String, AerodynamicCoefficients> owners =
				new HashMap<>(branch.ownerTotals());
		if (result.pressureCd() > 0) {
			owners.merge("PROTUBERANCE_PRESSURE_DRAG",
					axial(result.pressureCd()), FullRegimeTableBuilder::add);
		}
		if (result.internalFlowCd() > 0) {
			owners.merge("PROTUBERANCE_INTERNAL_FLOW_DRAG",
					axial(result.internalFlowCd()), FullRegimeTableBuilder::add);
		}
		List<String> methods = new ArrayList<>(branch.methods());
		List<String> validity = new ArrayList<>(branch.validity());
		double confidence = branch.confidence();
		for (var component : result.components()) {
			methods.add(component.methodId());
			validity.addAll(component.validityFlags());
			confidence = Math.min(confidence, component.confidence());
		}
		validity.add("PROTUBERANCE_DRAG_OWNED");
		return new BranchResult(add(branch.coefficients(), delta),
				List.copyOf(new LinkedHashSet<>(methods)),
				List.copyOf(new LinkedHashSet<>(validity)), branch.messages(),
				confidence, components, owners);
	}

	private AerodynamicDerivatives staticRateDerivatives(AeroGeometry geometry, double mach) {
		GeometryMetrics metrics = geometryMetrics(geometry);
		double finAuthority = metrics.finPlanformAreaRatio()
				/ (1.0 + metrics.finPlanformAreaRatio());
		double compressibilityScale = 1.0 / Math.sqrt(1.0 + 0.25 * mach * mach);
		double clp = -0.12 * finAuthority * compressibilityScale;
		double cmq = -(0.35 + 1.65 * finAuthority) * compressibilityScale;
		double cnr = -(0.20 + 0.80 * finAuthority) * compressibilityScale;
		return new AerodynamicDerivatives(clp, cmq, cnr);
	}

	private BranchResult applyCompactFinnedBodyClosure(AeroGeometry geometry, double mach,
			double alpha, double beta, BranchResult branch) {
		var value = new CompactFinnedBodyZeroLiftCorrelation().evaluate(geometry, mach,
				Math.atan(Math.hypot(Math.tan(alpha), Math.tan(beta))));
		if (value.isEmpty()) return branch;
		AerodynamicCoefficients original = branch.coefficients();
		AerodynamicCoefficients replacement = new AerodynamicCoefficients(value.getAsDouble(), original.cn(),
				original.cy(), original.cl(), original.cm(), original.cYaw());
		return withVehicleClosure(branch, replacement,
				union(branch.methods(), List.of(CompactFinnedBodyZeroLiftCorrelation.METHOD_ID)),
				branch.validity(), branch.messages(), Math.max(branch.confidence(), 0.82),
				"COMPACT_FINNED_BODY_SOURCE_CLOSURE");
	}

	private static Set<FailureReason> reasons(BranchResult branch) {
		Set<FailureReason> result = new LinkedHashSet<>();
		if (branch.validity().contains("TRANSONIC_CORRELATION_DOMINANT")) result.add(FailureReason.TRANSONIC_EMPIRICAL_CLOSURE);
		if (branch.validity().stream().anyMatch(value -> value.contains("ENGINEERING") && value.contains("SKIN_FRICTION"))) result.add(FailureReason.ENGINEERING_SKIN_FRICTION);
		if (branch.validity().contains("PHASE5_VISCOUS_COUPLING_INVALID")) result.add(FailureReason.VISCOUS_COUPLING_FALLBACK);
		if (branch.validity().contains("PNK_DISABLED_ISOLATED_VALIDATION_GATE")) result.add(FailureReason.PNK_INTERFERENCE_DISABLED);
		if (branch.validity().contains("BODY_NONZERO_INCIDENCE_UNOWNED_PHASE3")) result.add(FailureReason.BODY_INCIDENCE_UNOWNED);
		if (branch.validity().contains("PRESCRIBED_BODY_TRANSITION")) result.add(FailureReason.PRESCRIBED_TRANSITION);
		if (branch.validity().contains("PRESCRIBED_BODY_WALL_TEMPERATURE")) result.add(FailureReason.PRESCRIBED_WALL_TEMPERATURE);
		if (branch.methods().stream().anyMatch(value -> value.contains("PROFILE_DRAG_FALLBACK"))) result.add(FailureReason.FIN_PROFILE_FALLBACK);
		return Set.copyOf(result);
	}

	private BranchResult applySlenderCruciformClosure(AeroGeometry geometry, double mach,
			BranchResult branch) {
		SlenderCruciformCorrelation correlation = new SlenderCruciformCorrelation();
		double dragIncrement = correlation.axialDragIncrement(geometry, mach);
		AerodynamicCoefficients original = branch.coefficients();
		double ca = correlation.axialCoefficient(geometry, mach).orElse(original.ca() + dragIncrement);
		double cm = original.cm();
		double yaw = original.cYaw();
		List<String> validity = new ArrayList<>(branch.validity());
		if (correlation.eligible(geometry)) {
			double cp = correlation.centerOfPressureFraction(geometry, mach)
					* geometry.references().vehicleLengthM();
			double arm = (cp - geometry.references().momentOriginM().x)
					/ geometry.references().referenceLengthM();
			cm = -original.cn() * arm;
			yaw = original.cy() * arm;
			validity.remove("BODY_NONZERO_INCIDENCE_UNOWNED_PHASE3");
			validity.add("SLENDER_CRUCIFORM_BODY_FIN_INCIDENCE_OWNED");
		}
		if (ca == original.ca() && cm == original.cm() && yaw == original.cYaw()) return branch;
		List<String> methods = union(branch.methods(), List.of(SlenderCruciformCorrelation.METHOD_ID));
		return withVehicleClosure(branch, new AerodynamicCoefficients(ca,
				original.cn(), original.cy(), original.cl(), cm, yaw), methods, validity,
				branch.messages(), Math.max(branch.confidence(), 0.72),
				"SLENDER_CRUCIFORM_SOURCE_CLOSURE");
	}

	private BranchResult subsonic(AeroGeometry geometry, double mach, double alpha, double beta,
			AtmosphereState atmosphere, ThermodynamicModel gas, ReferenceState reference) {
		double evaluationMach = mach == 0 ? 1e-9 : mach;
		FlowCondition flow = FlowCondition.fromAngles(evaluationMach, alpha, beta, atmosphere,
				gas, false, geometry.geometryHash());
		SubsonicComponentAssembler.Result result = new SubsonicComponentAssembler().evaluate(geometry, flow);
		AerodynamicCoefficients coefficients = result.coefficients();
		AerodynamicCoefficients directCoefficients = coefficients;
		List<String> methods = new ArrayList<>(result.methods());
		List<String> validity = new ArrayList<>();
		if (mach == 0) {
			validity.add("MACH_ZERO_COEFFICIENT_LIMIT");
		}
		if (mach < 0.35) {
			ContributionLedger ledger = new ContributionLedger();
			new BarrowmanLowSpeedAdapter().evaluate(geometry, flow).forEach(ledger::add);
			AerodynamicCoefficients barrowman = ledger.entries().isEmpty()
					? new AerodynamicCoefficients(coefficients.ca(), 0, 0, 0, 0, 0)
					: CoefficientAssembler.assemble(ledger, reference);
			double correlationWeight = new LowSpeedRegimeSelector().correlationWeight(mach);
			coefficients = new ComponentRegimeBlender().blend(barrowman, 1 - correlationWeight,
					coefficients, correlationWeight == 0 ? 1e-12 : correlationWeight);
			methods.add("BARROWMAN_LOW_SPEED_ADAPTER_V1");
			validity.add(mach >= 0.25 ? "MACH_0P3_SMOOTH_OVERLAP" : "BARROWMAN_OWNER");
		}
		Map<String, AerodynamicCoefficients> components = new HashMap<>(result.componentTotals());
		Map<String, AerodynamicCoefficients> owners = new HashMap<>(result.ownerTotals());
		if (!coefficients.equals(directCoefficients)) {
			AerodynamicCoefficients adjustment = difference(coefficients, directCoefficients);
			components.put("low-speed-correlation-adjustment", adjustment);
			owners.put("LOW_SPEED_BARROWMAN_CORRECTION", adjustment);
		}
		return new BranchResult(coefficients, methods, validity, result.diagnostics(),
				result.confidence(), components, owners);
	}

	private BranchResult transonic(AeroGeometry geometry, double mach, double alpha, double beta,
			AtmosphereState atmosphere, ThermodynamicModel gas) {
		FlowCondition flow = FlowCondition.fromAngles(mach, alpha, beta, atmosphere, gas,
				false, geometry.geometryHash());
		GeometryMetrics metrics = geometryMetrics(geometry);
		double incidence = Math.atan(Math.hypot(Math.tan(alpha), Math.tan(beta)));

		BodyCriticalMachEstimator.Estimate bodyCritical = new BodyCriticalMachEstimator().estimate(
				1 / metrics.finenessRatio(), metrics.bodyCurvatureMetric(), incidence);
		double criticalMach = bodyCritical.criticalMach();
		FinCriticalMachEstimator.Estimate finCritical = null;
		FinDragDivergenceEstimator.Estimate finDivergence = null;
		if (metrics.finPlanformAreaRatio() > 0) {
			finCritical = new FinCriticalMachEstimator().estimate(metrics.finThicknessRatio(),
					metrics.halfChordSweepRad(), incidence);
			criticalMach = Math.min(criticalMach, finCritical.criticalMach());
			double sectionLiftCoefficient = 2 * Math.PI * metrics.aspectRatio()
					/ (metrics.aspectRatio() + 2) * incidence;
			finDivergence = new FinDragDivergenceEstimator().estimate(metrics.finThicknessRatio(),
					metrics.halfChordSweepRad(), sectionLiftCoefficient,
					FIN_DRAG_DIVERGENCE_CALIBRATION);
		}
		// The first component to diverge owns onset.  A fin with a later onset must
		// not delay a body drag rise, which was the old max(...) behavior.
		double bodyDivergenceMach = bodyCritical.criticalMach() + 0.08;
		double dragDivergenceMach = finDivergence == null ? bodyDivergenceMach
				: Math.min(bodyDivergenceMach, finDivergence.dragDivergenceMach());
		dragDivergenceMach = Math.max(criticalMach + 0.02, dragDivergenceMach);

		TransonicDragRiseHierarchy.GeometryInputs hierarchyGeometry =
				new TransonicDragRiseHierarchy.GeometryInputs(metrics.finenessRatio(),
						metrics.bodyFrontalAreaRatio(), metrics.bodyCurvatureMetric(),
						metrics.finThicknessRatio(), metrics.finPlanformAreaRatio(),
						metrics.halfChordSweepRad());
		TransonicDragRiseHierarchy.Decomposition rise = new TransonicDragRiseHierarchy().decompose(
				hierarchyGeometry, criticalMach, dragDivergenceMach);
		double sonicFraction = unitInterval((mach - criticalMach) / 0.15);
		TransonicDragRiseModel riseModel = new TransonicDragRiseModel();
		double bodyDragRise = riseModel.deltaCd(mach, sonicFraction, rise.body());
		double finDragRise = riseModel.deltaCd(mach, sonicFraction, rise.fin());
		double interferenceDragRise = riseModel.deltaCd(mach, sonicFraction, rise.interference());
		BodyPressureAnchor bodyPressureAnchor = null;
		double bodyPressureBridgeWeight = 0;
		if (mach >= DIRECT_BODY_PRESSURE_BRIDGE_START) {
			/*
			 * The generic transonic body-rise closure owns onset, but the direct
			 * pressure solver is authoritative at Mach 1.2.  NACA RM L9I30
			 * figures 8-12 show the measured rise reaching its supersonic
			 * plateau by this endpoint.  Bridge only the body-wave owner to the
			 * direct forebody/transition pressure value; base, viscous, fin, and
			 * interference terms retain their existing independent ownership.
			 */
			bodyPressureAnchor = directBodyPressureAnchor(geometry, atmosphere, gas);
			bodyPressureBridgeWeight = smoothOverlap(mach,
					DIRECT_BODY_PRESSURE_BRIDGE_START, SUPERSONIC_OVERLAP_START);
			bodyDragRise = (1 - bodyPressureBridgeWeight) * bodyDragRise
					+ bodyPressureBridgeWeight * bodyPressureAnchor.cd();
		}
		double dragRise = bodyDragRise + finDragRise + interferenceDragRise;

		EngineeringSkinFrictionCorrelation.Result friction =
				new EngineeringSkinFrictionCorrelation().evaluate(geometry, flow);
		// No resolved displacement-thickness state is available in this branch, so
		// the base correlation's explicit boundary-layer correction remains zero.
		double basePressureMagnitude = -new TransonicBaseDragModel()
				.basePressureCoefficient(mach, 0);
		double referenceArea = geometry.references().referenceAreaM2();
		double bodyBaseCd = basePressureMagnitude
				* geometry.references().exposedBaseAreaM2() / referenceArea;
		FinnedBasePressureClosureModel.Result finnedBaseClosure =
				new FinnedBasePressureClosureModel().evaluate(
						geometry, mach, basePressureMagnitude);
		double bodyBaseClosureCd =
				finnedBaseClosure.pressureMagnitudeIncrement()
				* geometry.references().exposedBaseAreaM2() / referenceArea;
		double boattailCd = new SeparatedBoattailPressureDragModel()
				.dragCoefficient(geometry, basePressureMagnitude);
		FinTrailingEdgeBaseDragModel finBaseModel = new FinTrailingEdgeBaseDragModel();
		double finBaseCd = geometry.components().stream()
				.filter(component -> component.finGeometry() != null)
				.mapToDouble(component -> finBaseModel.dragCoefficient(component,
						basePressureMagnitude, referenceArea))
				.sum();
		double finBaseClosureCd = geometry.components().stream()
				.filter(component -> component.finGeometry() != null)
				.mapToDouble(component -> finBaseModel.dragCoefficient(component,
						finnedBaseClosure.pressureMagnitudeIncrement(),
						referenceArea))
				.sum();
		FinLeadingEdgePressureDragModel finLeadingEdgeModel =
				new FinLeadingEdgePressureDragModel();
		double finLeadingEdgeCd = geometry.components().stream()
				.filter(component -> component.finGeometry() != null)
				.mapToDouble(component -> finLeadingEdgeModel.dragCoefficient(
						component, mach, referenceArea))
				.sum();

		double axialCoefficient = bodyBaseCd + bodyBaseClosureCd + boattailCd
				+ finBaseCd + finBaseClosureCd + finLeadingEdgeCd
				+ friction.totalCd() + dragRise;
		double liftCorrection = new TransonicFinLiftCorrection().factor(mach,
				metrics.finThicknessRatio(), metrics.aspectRatio());
		double slope = 2.5 * liftCorrection;
		double normalCoefficient = slope * alpha;
		double sideCoefficient = slope * beta;
		double aerodynamicCenter = transonicApplicationPointM(geometry);
		double arm = (aerodynamicCenter - geometry.references().momentOriginM().x)
				/ geometry.references().referenceLengthM();
		AerodynamicCoefficients coefficients = new AerodynamicCoefficients(axialCoefficient,
				normalCoefficient, sideCoefficient, 0, -normalCoefficient * arm,
				sideCoefficient * arm);

		List<String> methods = new ArrayList<>(List.of(
				"DEDICATED_TRANSONIC_ROCKET_PEAK_V1",
				TransonicBaseDragModel.METHOD_ID,
				EngineeringSkinFrictionCorrelation.METHOD_ID,
				bodyCritical.methodId(),
				rise.total().sourceId(), rise.body().sourceId(), rise.fin().sourceId(),
				rise.interference().sourceId()));
		if (finCritical != null) {
			methods.add(finCritical.methodId());
			methods.add(finDivergence.methodId());
		}
		if (finBaseCd > 0) {
			methods.add(FinTrailingEdgeBaseDragModel.METHOD_ID);
		}
		if (finLeadingEdgeCd > 0) {
			methods.add(FinLeadingEdgePressureDragModel.METHOD_ID);
		}
		if (boattailCd > 0) {
			methods.add(SeparatedBoattailPressureDragModel.METHOD_ID);
		}
		if (bodyPressureBridgeWeight > 0) {
			methods.add(DIRECT_BODY_PRESSURE_BRIDGE_METHOD_ID);
			methods.addAll(bodyPressureAnchor.methodIds());
		}
		if (bodyBaseClosureCd + finBaseClosureCd > 0) {
			methods.add(FinnedBasePressureClosureModel.METHOD_ID);
		}
		AerodynamicCoefficients incidenceLoads = new AerodynamicCoefficients(0,
				normalCoefficient, sideCoefficient, 0, -normalCoefficient * arm,
				sideCoefficient * arm);
		Map<String, AerodynamicCoefficients> components = Map.of(
				"axisymmetric-body", axial(bodyBaseCd + bodyBaseClosureCd
						+ boattailCd
						+ friction.bodyCd() + bodyDragRise),
				"fins", axial(finBaseCd + finBaseClosureCd
						+ finLeadingEdgeCd
						+ friction.finCd() + finDragRise),
				"body-fin-interference", axial(interferenceDragRise),
				"vehicle-incidence", incidenceLoads);
		Map<String, AerodynamicCoefficients> owners = new HashMap<>();
		owners.put("BODY_TRANSONIC_DRAG_RISE", axial(bodyDragRise));
		owners.put("FIN_TRANSONIC_DRAG_RISE", axial(finDragRise));
		owners.put("BODY_FIN_TRANSONIC_INTERFERENCE",
				axial(interferenceDragRise));
		owners.put("BODY_BASE_PRESSURE_DRAG", axial(bodyBaseCd));
		owners.put("FINNED_BODY_BASE_PRESSURE_CLOSURE",
				axial(bodyBaseClosureCd + finBaseClosureCd));
		owners.put("BOATTAIL_PRESSURE_DRAG", axial(boattailCd));
		owners.put("FIN_BASE_PRESSURE_DRAG", axial(finBaseCd));
		owners.put("FIN_LEADING_EDGE_PRESSURE_DRAG",
				axial(finLeadingEdgeCd));
		owners.put("BODY_SKIN_FRICTION", axial(friction.bodyCd()));
		owners.put("FIN_SKIN_FRICTION", axial(friction.finCd()));
		owners.put("TRANSONIC_INCIDENCE_LOADS", incidenceLoads);
		List<String> validity = new ArrayList<>(
				List.of("NEAR_SONIC", "TRANSONIC_CORRELATION_DOMINANT"));
		if (bodyPressureBridgeWeight > 0) {
			validity.add("DIRECT_MACH_1P2_BODY_PRESSURE_ENDPOINT_BRIDGE");
		}
		List<String> diagnostics = new ArrayList<>();
		double confidence = 0.45;
		if (bodyBaseClosureCd + finBaseClosureCd > 0) {
			validity.add("FINNED_BASE_PRESSURE_CLOSURE");
			diagnostics.addAll(finnedBaseClosure.validityFlags());
			confidence = Math.min(confidence,
					finnedBaseClosure.confidence());
		}
		return new BranchResult(coefficients, methods,
				validity, diagnostics, confidence,
				components, owners);
	}

	private BodyPressureAnchor directBodyPressureAnchor(AeroGeometry geometry,
			AtmosphereState atmosphere, ThermodynamicModel gas) {
		double gamma = gas.gamma(atmosphere.temperatureK());
		BodyPressureAnchorKey key = new BodyPressureAnchorKey(geometry.geometryHash(),
				gas.getClass().getName(), atmosphere.temperatureK(), gamma);
		return bodyPressureAnchors.computeIfAbsent(key, ignored -> {
			FlowCondition endpointFlow = FlowCondition.fromAngles(
					SUPERSONIC_OVERLAP_START, 0, 0, atmosphere, gas, false,
					geometry.geometryHash());
			var result = new AxisymmetricBodySolver().evaluate(geometry, endpointFlow);
			double denominator = endpointFlow.dynamicPressurePa()
					* geometry.references().referenceAreaM2();
			double cd = result.contributions().stream()
					.filter(contribution ->
							contribution.owner().term() == PhysicalTerm.BODY_PRESSURE_FOREBODY
									|| contribution.owner().term()
											== PhysicalTerm.BODY_PRESSURE_TRANSITION)
					.mapToDouble(contribution -> contribution.forceBodyN().x / denominator)
					.sum();
			List<String> methods = result.contributions().stream()
					.filter(contribution ->
							contribution.owner().term() == PhysicalTerm.BODY_PRESSURE_FOREBODY
									|| contribution.owner().term()
											== PhysicalTerm.BODY_PRESSURE_TRANSITION)
					.map(contribution -> contribution.methodId().value())
					.distinct().sorted().toList();
			return new BodyPressureAnchor(Math.max(0, cd), methods);
		});
	}

	/** Area-weighted component application point; no Mach-fraction shortcut. */
	private double transonicApplicationPointM(AeroGeometry geometry) {
		double weightedX = 0;
		double weight = 0;
		for (AeroComponent component : geometry.components()) {
			if (component.axisymmetricProfile() != null) {
				double authority = Math.max(component.projectedAreaM2(), component.baseAreaM2());
				weightedX += authority * 0.5 * (component.axialStartM() + component.axialEndM());
				weight += authority;
			}
			if (component.finGeometry() != null) {
				double authority = component.finGeometry().planformAreaM2()
						* component.finGeometry().count();
				double localCentroid = component.finGeometry().outline().stream()
						.mapToDouble(GeometryStation::xM).average()
						.orElse(0.5 * component.finGeometry().rootChordM());
				weightedX += authority * (component.axialStartM() + localCentroid);
				weight += authority;
			}
		}
		if (weight > 0) return weightedX / weight;
		return geometry.references().momentOriginM().x
				+ 0.5 * geometry.references().referenceLengthM();
	}

	private BranchResult supersonic(AerodynamicTable table, int alphaIndex, int betaIndex) {
		if (table == null) {
			throw new IllegalStateException("missing supersonic overlap table");
		}
		TableCell cell = table.cell(0, alphaIndex, betaIndex);
		return new BranchResult(cell.coefficients(), cell.methodIds(), cell.validityFlags(),
				cell.diagnostics().messages(), 0.70, cell.componentTotals(), cell.ownerTotals());
	}

	private BranchResult highMach(AeroGeometry geometry, double mach, double alpha, double beta,
			AtmosphereState atmosphere, ThermodynamicModel gas) {
		HighMachThermodynamicSelector.Selection selection = new HighMachThermodynamicSelector().select(
				mach, atmosphere.temperatureK(), mach * gas.speedOfSound(atmosphere.temperatureK()));
		if (selection.model() == HighMachThermodynamicSelector.Model.INVALID_IONIZATION) {
			throw new IllegalArgumentException(selection.reason());
		}
		double gamma = selection.model() == HighMachThermodynamicSelector.Model.PERFECT_GAS
				? gas.gamma(atmosphere.temperatureK())
				: new ThermallyPerfectAir().gamma(Math.min(5999, selection.stagnationTemperatureK()));
		// Keep the selected thermodynamic state explicit even though the present
		// high-Mach coefficient closure only consumes Mach.
		if (!Double.isFinite(gamma)) {
			throw new IllegalArgumentException("invalid high-Mach gamma");
		}
		GeometryMetrics metrics = geometryMetrics(geometry);
		// Geometry-scaled continuation of pressure/base/profile drag.  The Mach
		// decay follows the A53D02 high-Mach development set while fin area and
		// body fineness retain the required vehicle dependence.
		double geometryScale = Math.max(0.70, Math.min(1.40,
				0.85 + 0.02 * Math.sqrt(Math.max(0, metrics.finPlanformAreaRatio()))
						+ 0.5 / metrics.finenessRatio()));
		double axialCoefficient = geometryScale * (0.055 + 0.60 / mach);
		double slope = 4 / Math.sqrt(Math.max(0.1, mach * mach - 1));
		double normalCoefficient = slope * alpha;
		double sideCoefficient = slope * beta;
		AerodynamicCoefficients coefficients = new AerodynamicCoefficients(axialCoefficient,
				normalCoefficient, sideCoefficient, 0, -0.55 * normalCoefficient,
				0.55 * sideCoefficient);
		double confidence = selection.model() == HighMachThermodynamicSelector.Model.EQUILIBRIUM_AIR
				? 0.5 : 0.65;
		return new BranchResult(coefficients,
				List.of(selection.model().name() + "_GEOMETRY_SCALED_HIGH_MACH_PRESSURE_V2"),
				List.of(selection.reason(), "GEOMETRY_SCALED_HIGH_MACH_CLOSURE"), List.of(), confidence);
	}

	private GeometryMetrics geometryMetrics(AeroGeometry geometry) {
		return geometryMetricsCache.computeIfAbsent(geometry.geometryHash(),
				ignored -> computeGeometryMetrics(geometry));
	}

	private static GeometryMetrics computeGeometryMetrics(AeroGeometry geometry) {
		double diameter = geometry.references().maximumBodyDiameterM();
		double finenessRatio = geometry.references().vehicleLengthM() / diameter;
		double frontalArea = Math.PI * diameter * diameter / 4;
		double bodyFrontalAreaRatio = frontalArea / geometry.references().referenceAreaM2();
		double bodyCurvatureMetric = bodyCurvatureMetric(geometry, diameter);

		double totalFinArea = 0;
		double thicknessAreaIntegral = 0;
		double sweepAreaIntegral = 0;
		double aspectAreaIntegral = 0;
		FinGeometryAdapter adapter = new FinGeometryAdapter();
		FinStripDiscretizer discretizer = new FinStripDiscretizer();
		for (AeroComponent component : geometry.components()) {
			FinGeometry fin = component.finGeometry();
			if (fin == null) {
				continue;
			}
			List<FinStrip> strips = discretizer.discretize(adapter.expand(component).get(0), 20);
			double representedArea = fin.planformAreaM2() * fin.count();
			double stripArea = strips.stream().mapToDouble(FinStrip::areaM2).sum();
			double meanThicknessRatio = strips.stream()
					.mapToDouble(strip -> strip.thicknessToChord() * strip.areaM2()).sum() / stripArea;
			double meanSweep = strips.stream()
					.mapToDouble(strip -> strip.halfChordSweepRad() * strip.areaM2()).sum() / stripArea;
			double aspectRatio = fin.spanM() * fin.spanM() / fin.planformAreaM2();
			totalFinArea += representedArea;
			thicknessAreaIntegral += meanThicknessRatio * representedArea;
			sweepAreaIntegral += meanSweep * representedArea;
			aspectAreaIntegral += aspectRatio * representedArea;
		}
		double meanThicknessRatio = totalFinArea > 0 ? thicknessAreaIntegral / totalFinArea : 0;
		double meanSweep = totalFinArea > 0 ? sweepAreaIntegral / totalFinArea : 0;
		double meanAspectRatio = totalFinArea > 0 ? aspectAreaIntegral / totalFinArea : 1;
		return new GeometryMetrics(finenessRatio, bodyFrontalAreaRatio, bodyCurvatureMetric,
				meanThicknessRatio, totalFinArea / geometry.references().referenceAreaM2(),
				meanSweep, meanAspectRatio);
	}

	private static double bodyCurvatureMetric(AeroGeometry geometry, double diameter) {
		double curvatureIntegral = 0;
		double length = 0;
		for (AeroComponent component : geometry.components()) {
			AxisymmetricProfile profile = component.axisymmetricProfile();
			if (profile == null) {
				continue;
			}
			List<GeometryStation> stations = profile.stations();
			for (int index = 1; index < stations.size(); index++) {
				GeometryStation before = stations.get(index - 1);
				GeometryStation after = stations.get(index);
				double increment = after.xM() - before.xM();
				curvatureIntegral += 0.5 * increment
						* (Math.abs(before.meridionalCurvature())
								+ Math.abs(after.meridionalCurvature()));
				length += increment;
			}
		}
		return length > 0 ? Math.min(1, diameter * curvatureIntegral / length) : 0;
	}

	private BranchResult blend(BranchResult lower, BranchResult upper, double upperWeight,
			String overlapFlag) {
		AerodynamicCoefficients coefficients = new ComponentRegimeBlender().blend(
				lower.coefficients(), 1 - upperWeight, upper.coefficients(), upperWeight);
		List<String> methods = union(lower.methods(), upper.methods());
		List<String> validity = union(lower.validity(), upper.validity());
		validity = union(validity, List.of(overlapFlag));
		List<String> messages = union(lower.messages(), upper.messages());
		double confidence = (1 - upperWeight) * lower.confidence()
				+ upperWeight * upper.confidence();
		Map<String, AerodynamicCoefficients> components = blendTotals(
				lower.componentTotals(), upper.componentTotals(), coefficients, upperWeight);
		Map<String, AerodynamicCoefficients> owners = blendTotals(
				lower.ownerTotals(), upper.ownerTotals(), coefficients, upperWeight);
		return new BranchResult(coefficients, methods, validity, messages, confidence, components, owners);
	}

	private BranchResult withVehicleClosure(BranchResult branch, AerodynamicCoefficients replacement,
			List<String> methods, List<String> validity, List<String> messages, double confidence,
			String closureOwner) {
		AerodynamicCoefficients delta = difference(replacement, branch.coefficients());
		Map<String, AerodynamicCoefficients> components = new HashMap<>(branch.componentTotals());
		Map<String, AerodynamicCoefficients> owners = new HashMap<>(branch.ownerTotals());
		if (components.size() == 1 && components.containsKey("vehicle")) components.put("vehicle", replacement);
		else components.put("vehicle-correlation-closure", delta);
		if (owners.size() == 1 && owners.containsKey("FULL_REGIME_OWNER")) owners.put("FULL_REGIME_OWNER", replacement);
		else owners.put(closureOwner, delta);
		return new BranchResult(replacement, methods, validity, messages, confidence, components, owners);
	}

	private static Map<String, AerodynamicCoefficients> blendTotals(
			Map<String, AerodynamicCoefficients> lower, Map<String, AerodynamicCoefficients> upper,
			AerodynamicCoefficients total, double upperWeight) {
		if (lower.containsKey("vehicle") || upper.containsKey("vehicle")) return Map.of("vehicle", total);
		if (lower.containsKey("FULL_REGIME_OWNER") || upper.containsKey("FULL_REGIME_OWNER")) {
			return Map.of("FULL_REGIME_OWNER", total);
		}
		Map<String, AerodynamicCoefficients> result = new HashMap<>();
		LinkedHashSet<String> keys = new LinkedHashSet<>(lower.keySet());
		keys.addAll(upper.keySet());
		AerodynamicCoefficients zero = axial(0);
		for (String key : keys) {
			result.put(key, blendCoefficients(lower.getOrDefault(key, zero),
					upper.getOrDefault(key, zero), upperWeight));
		}
		return Map.copyOf(result);
	}

	private static AerodynamicCoefficients blendCoefficients(AerodynamicCoefficients lower,
			AerodynamicCoefficients upper, double upperWeight) {
		double[] first = lower.toArray();
		double[] second = upper.toArray();
		for (int index = 0; index < first.length; index++) {
			first[index] = (1 - upperWeight) * first[index] + upperWeight * second[index];
		}
		return AerodynamicCoefficients.fromArray(first);
	}

	private static AerodynamicCoefficients difference(AerodynamicCoefficients value,
			AerodynamicCoefficients baseline) {
		double[] result = value.toArray();
		double[] other = baseline.toArray();
		for (int index = 0; index < result.length; index++) result[index] -= other[index];
		return AerodynamicCoefficients.fromArray(result);
	}

	private static List<String> union(List<String> first, List<String> second) {
		LinkedHashSet<String> result = new LinkedHashSet<>(first);
		result.addAll(second);
		return List.copyOf(result);
	}

	private static double smoothOverlap(double value, double start, double end) {
		double fraction = unitInterval((value - start) / (end - start));
		return fraction * fraction * (3 - 2 * fraction);
	}

	private static double unitInterval(double value) {
		return Math.max(0, Math.min(1, value));
	}

	private void validateAxes(double[] mach, double[] alpha, double[] beta) {
		if (mach[0] < 0 || mach[mach.length - 1] > 10
				|| alpha[0] < -Math.toRadians(15)
				|| alpha[alpha.length - 1] > Math.toRadians(15)
				|| beta[0] < -Math.toRadians(5)
				|| beta[beta.length - 1] > Math.toRadians(5)) {
			throw new IllegalArgumentException("PHASE6_DOMAIN_EXCEEDED");
		}
		// Alpha and beta are independent signed body-axis coordinates.  The
		// rectangular validated envelope intentionally includes its corner states.
	}

	private record BranchResult(AerodynamicCoefficients coefficients, List<String> methods,
			List<String> validity, List<String> messages, double confidence,
			Map<String, AerodynamicCoefficients> componentTotals,
			Map<String, AerodynamicCoefficients> ownerTotals) {
		private BranchResult(AerodynamicCoefficients coefficients, List<String> methods,
				List<String> validity, List<String> messages, double confidence) {
			this(coefficients, methods, validity, messages, confidence,
					Map.of("vehicle", coefficients), Map.of("FULL_REGIME_OWNER", coefficients));
		}
		private BranchResult {
			methods = List.copyOf(methods);
			validity = List.copyOf(validity);
			messages = List.copyOf(messages);
			componentTotals = Map.copyOf(componentTotals);
			ownerTotals = Map.copyOf(ownerTotals);
		}
	}

	private record SupersonicStencil(AerodynamicTable center, AerodynamicTable lower,
			AerodynamicTable upper, AerodynamicTable oneDecadeAnchor,
			AerodynamicTable oneDecadeValidation, AerodynamicTable twoDecadeAnchor,
			AerodynamicTable twoDecadeValidation, AerodynamicTable threeDecadeAnchor,
			AerodynamicTable threeDecadeValidation) { }

	private record GeometryMetrics(double finenessRatio, double bodyFrontalAreaRatio,
			double bodyCurvatureMetric, double finThicknessRatio,
			double finPlanformAreaRatio, double halfChordSweepRad, double aspectRatio) { }

	private record BodyPressureAnchorKey(String geometryHash, String thermodynamicModel,
			double temperatureK, double gamma) { }

	private record BodyPressureAnchor(double cd, List<String> methodIds) {
		private BodyPressureAnchor {
			methodIds = List.copyOf(methodIds);
		}
	}

	private record IndexedResult<T>(int index, T value) { }
}
