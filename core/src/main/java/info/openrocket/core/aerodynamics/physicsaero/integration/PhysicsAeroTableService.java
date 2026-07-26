package info.openrocket.core.aerodynamics.physicsaero.integration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.aerodynamics.physicsaero.table.TableWriter;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.config.PhysicsAeroSettingsFingerprint;

public final class PhysicsAeroTableService {
	private final PhysicsAeroTableCache cache;

	public PhysicsAeroTableService() { this(new PhysicsAeroTableCache()); }
	public PhysicsAeroTableService(PhysicsAeroTableCache cache) {
		if (cache == null) throw new IllegalArgumentException("physics-aero cache required");
		this.cache = cache;
	}
	public record Request(AeroGeometry geometry, double[] mach, double[] alphaRad, double[] betaRad,
			PoweredFlowState[] poweredStates, AtmosphereState atmosphere, ThermodynamicModel gas,
			String settingsHash, Path tableFile, Path manifestFile) {
		public Request(AeroGeometry geometry, double[] mach, double[] alphaRad, double[] betaRad,
				AtmosphereState atmosphere, ThermodynamicModel gas, String settingsHash,
				Path tableFile, Path manifestFile) {
			this(geometry, mach, alphaRad, betaRad, new PoweredFlowState[0], atmosphere, gas,
					settingsHash, tableFile, manifestFile);
		}
		public Request(AeroGeometry geometry, double[] mach, double[] alphaRad, double[] betaRad,
				AtmosphereState atmosphere, ThermodynamicModel gas,
				PhysicsAeroSettingsFingerprint.Input settings, Path tableFile, Path manifestFile) {
			this(geometry, mach, alphaRad, betaRad, settings.poweredStates(), atmosphere, gas,
					PhysicsAeroSettingsFingerprint.hash(settings), tableFile, manifestFile);
		}
		public Request {
			if (geometry == null || atmosphere == null || gas == null || settingsHash == null
					|| settingsHash.isBlank() || tableFile == null || manifestFile == null) {
				throw new IllegalArgumentException("invalid physics-aero table request");
			}
			mach = mach.clone(); alphaRad = alphaRad.clone(); betaRad = betaRad.clone();
			poweredStates = poweredStates == null ? new PoweredFlowState[0] : poweredStates.clone();
		}
		@Override public double[] mach() { return mach.clone(); }
		@Override public double[] alphaRad() { return alphaRad.clone(); }
		@Override public double[] betaRad() { return betaRad.clone(); }
		@Override public PoweredFlowState[] poweredStates() { return poweredStates.clone(); }
	}

	public record Result(AerodynamicTable table, Path tableFile, Path manifestFile,
			PhysicsAeroValidationGate.Report validation, String tableHash) { }

	public Result buildToCache(AeroGeometry geometry, double[] mach, double[] alphaRad,
			double[] betaRad, PoweredFlowState[] poweredStates, AtmosphereState atmosphere,
			ThermodynamicModel gas, String settingsHash, AtomicBoolean cancelled,
			IntConsumer progress) throws IOException {
		PhysicsAeroTableCache.Key key = new PhysicsAeroTableCache.Key(geometry.geometryHash(),
				settingsHash, PhysicsAeroValidationGate.CODE_VERSION, PhysicsAeroValidationGate.REGISTRY_VERSION);
		return build(new Request(geometry, mach, alphaRad, betaRad, poweredStates, atmosphere, gas,
				settingsHash, cache.tablePath(key), cache.manifestPath(key)), cancelled, progress);
	}

	public Result buildToCache(AeroGeometry geometry, double[] mach, double[] alphaRad,
			double[] betaRad, AtmosphereState atmosphere, ThermodynamicModel gas,
			PhysicsAeroSettingsFingerprint.Input settings, AtomicBoolean cancelled,
			IntConsumer progress) throws IOException {
		String settingsHash = PhysicsAeroSettingsFingerprint.hash(settings);
		return buildToCache(geometry, mach, alphaRad, betaRad, settings.poweredStates(), atmosphere,
				gas, settingsHash, cancelled, progress);
	}

	public Result build(Request request, AtomicBoolean cancelled, IntConsumer progress) throws IOException {
		if (cancelled.get()) throw new IllegalStateException("BUILD_CANCELLED");
		progress.accept(1);
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				request.geometry().geometryHash(), request.settingsHash(),
				PhysicsAeroValidationGate.CODE_VERSION, PhysicsAeroValidationGate.REGISTRY_VERSION,
				TableMetadata.REQUIRED_UNITS, TableMetadata.REQUIRED_AXIS_CONVENTION,
				Map.of("referenceAreaM2", request.geometry().references().referenceAreaM2()),
				Map.of("joinJump", 0.01, "interpolationRelative", 0.02),
				CertificationState.EXPERIMENTAL_FLIGHT_PENDING);
		FullRegimeTableBuilder builder = new FullRegimeTableBuilder();
		AerodynamicTable table = request.poweredStates().length == 0
				? builder.build(request.geometry(), request.mach(), request.alphaRad(), request.betaRad(),
						request.atmosphere(), request.gas(), metadata, cancelled::get, progress)
				: builder.build(request.geometry(), request.mach(), request.alphaRad(), request.betaRad(),
						request.poweredStates(), request.atmosphere(), request.gas(), metadata,
						cancelled::get, progress);
		if (cancelled.get()) throw new IllegalStateException("BUILD_CANCELLED_LAST_VALID_TABLE_UNCHANGED");
		progress.accept(90);
		var validation = new PhysicsAeroValidationGate().evaluate(table);
		if (validation.status() != PhysicsAeroValidationGate.Status.PASS) {
			throw new IllegalStateException("TABLE_ARTIFACT_VALIDATION_FAILED:"
					+ String.join(",", validation.failures()));
		}
		new TableWriter().write(table, request.tableFile(), request.manifestFile());
		progress.accept(100);
		return new Result(table, request.tableFile(), request.manifestFile(), validation,
				PhysicsAeroTableCache.contentHash(request.tableFile()));
	}
}
