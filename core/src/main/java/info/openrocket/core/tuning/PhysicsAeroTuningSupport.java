package info.openrocket.core.tuning;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import info.openrocket.core.aerodynamics.physicsaero.config.NumericalTolerances;
import info.openrocket.core.aerodynamics.physicsaero.config.PhysicsAeroSettingsFingerprint;
import info.openrocket.core.aerodynamics.physicsaero.config.PhysicsConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.config.SamplingConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.SimulationOptions;

/**
 * Builds or reuses the current physics-aero table artifact required by tuning
 * simulations.  Tuning callers select the runtime mode; this class supplies the
 * portable identity that replaced the removed in-memory ROM surfaces.
 */
public final class PhysicsAeroTuningSupport {
	private static final String WALL_MODEL_ID = "ADIABATIC";
	private static final String REFINEMENT_RULE =
			"EXPLICIT_REGIME_HANDOFFS_AND_TRANSONIC_REFINEMENT_V1";
	private static final String FALLBACK_POLICY = "TYPED_GENERATION_FALLBACKS_ONLY";
	private static final double REFERENCE_PRESSURE_PA = 101325;
	private static final double REFERENCE_TEMPERATURE_K = 288.15;

	private PhysicsAeroTuningSupport() {
	}

	public static void prepareTableIfEnabled(Simulation simulation) {
		if (simulation == null) {
			throw new IllegalArgumentException("simulation required");
		}
		PhysicsAeroSettings current = simulation.getOptions().getPhysicsAeroSettings();
		if (!current.isEnabled()) {
			return;
		}
		if (hasCompleteIdentity(current)) {
			try {
				new PhysicsAeroTableResolver().resolve(
						simulation.getActiveConfiguration(), current);
				return;
			} catch (RuntimeException ignored) {
				// Tuning runs rebuild missing or stale identities automatically.
			}
		}
		if (simulation.getActiveConfiguration().getActiveStages().size() != 1
				|| simulation.getActiveConfiguration().getRocket().getStageCount() != 1) {
			throw new IllegalStateException(
					"PHYSICS_AERO_TUNING_REQUIRES_SINGLE_STAGE_CONFIGURATION");
		}

		try {
			SamplingConfiguration sampling = SamplingConfiguration.flightDomainDefaults();
			boolean turbulent = current.isForceTurbulentBoundaryLayer();
			PoweredFlowState[] poweredStates = poweredStatesForTuning(simulation.getOptions());
			PhysicsAeroSettingsFingerprint.Input fingerprintInput = tuningFingerprintInput(
					sampling, turbulent, poweredStates);
			String settingsHash = PhysicsAeroSettingsFingerprint.hash(fingerprintInput);
			AeroGeometry geometry = new GeometryExtractor().extractWithComponentRoughness(
					simulation.getActiveConfiguration(),
					WALL_MODEL_ID,
					settingsHash,
					turbulent);
			PhysicsAeroTableCache cache = new PhysicsAeroTableCache();
			PhysicsAeroTableCache.Key key = new PhysicsAeroTableCache.Key(
					geometry.geometryHash(),
					settingsHash,
					PhysicsAeroValidationGate.CODE_VERSION,
					PhysicsAeroValidationGate.REGISTRY_VERSION);
			AerodynamicTable table;
			try {
				table = cache.load(key, "").orElse(null);
			} catch (IOException | IllegalArgumentException ignored) {
				table = null;
			}
			String contentHash;
			if (table == null) {
				PerfectGasAir air = new PerfectGasAir();
				AtmosphereState atmosphere = new AtmosphereState(
						REFERENCE_PRESSURE_PA,
						REFERENCE_TEMPERATURE_K,
						REFERENCE_PRESSURE_PA
								/ (air.gasConstant() * REFERENCE_TEMPERATURE_K),
						air.viscosity(REFERENCE_TEMPERATURE_K));
				PhysicsAeroTableService.Result built =
						new PhysicsAeroTableService(cache).buildToCache(
								geometry,
								sampling.mach(),
								sampling.alphaRad(),
								sampling.betaRad(),
								poweredStates,
								atmosphere,
								air,
								settingsHash,
								new AtomicBoolean(),
								ignored -> { });
				table = built.table();
				contentHash = built.tableHash();
			} else {
				contentHash = PhysicsAeroTableCache.contentHash(cache.tablePath(key));
			}
			simulation.getOptions().setPhysicsAeroTableIdentity(table, contentHash);
		} catch (IOException exception) {
			throw new IllegalStateException(
					"PHYSICS_AERO_TUNING_TABLE_PREPARATION_FAILED", exception);
		}
	}

	/**
	 * Tuning tables always carry an explicit coast and boost state.  The boost
	 * state is geometry-only when a stage-0 nozzle diameter is available; a
	 * missing nozzle is represented by a named unmodeled state rather than by
	 * dropping the powered axis and forcing a runtime out-of-domain fallback.
	 */
	static PoweredFlowState[] poweredStatesForTuning(SimulationOptions options) {
		if (options == null) {
			throw new IllegalArgumentException("simulation options required");
		}
		double nozzleDiameterM = options.getNozzleExitDiameterForStage(0);
		PoweredFlowState powered = Double.isFinite(nozzleDiameterM) && nozzleDiameterM > 0
				? PoweredFlowState.nozzleGeometryOnly(1,
						Math.PI * nozzleDiameterM * nozzleDiameterM / 4,
						REFERENCE_PRESSURE_PA)
				: PoweredFlowState.unmodeledPoweredBaseline(REFERENCE_PRESSURE_PA);
		return new PoweredFlowState[] {
				PoweredFlowState.coast(REFERENCE_PRESSURE_PA), powered
		};
	}

	static PhysicsAeroSettingsFingerprint.Input tuningFingerprintInput(
			SamplingConfiguration sampling, boolean turbulent,
			PoweredFlowState[] poweredStates) {
		return new PhysicsAeroSettingsFingerprint.Input(
				sampling,
				List.of(REFINEMENT_RULE),
				new PhysicsConfiguration(
						PhysicsConfiguration.allRegisteredMethods(),
						WALL_MODEL_ID,
						0,
						true,
						turbulent),
				NumericalTolerances.defaults(),
				FALLBACK_POLICY,
				PhysicsAeroValidationGate.CODE_VERSION,
				PhysicsAeroValidationGate.REGISTRY_VERSION,
				poweredStates);
	}

	private static boolean hasCompleteIdentity(PhysicsAeroSettings settings) {
		return !settings.getGeometryHash().isBlank()
				&& !settings.getSettingsHash().isBlank()
				&& !settings.getTableContentHash().isBlank();
	}
}
