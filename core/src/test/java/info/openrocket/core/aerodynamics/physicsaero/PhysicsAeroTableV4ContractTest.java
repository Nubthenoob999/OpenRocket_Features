package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.TableQueryEngine;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.aerodynamics.physicsaero.table.TableReader;
import info.openrocket.core.aerodynamics.physicsaero.table.TableWriter;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;
import info.openrocket.core.util.Coordinate;

class PhysicsAeroTableV4ContractTest {
	@TempDir Path temporary;

	@Test
	void schemaV5RoundTripIsDeterministicAndPreservesPoweredOwnershipRatesAndCorrections() throws Exception {
		AerodynamicTable table = poweredTable();
		Path first = temporary.resolve("first.aero");
		Path second = temporary.resolve("second.aero");
		new TableWriter().write(table, first, temporary.resolve("first.json"));
		new TableWriter().write(table, second, temporary.resolve("second.json"));
		assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));

		AerodynamicTable loaded = new TableReader().read(first);
		assertEquals(TableMetadata.CURRENT_SCHEMA, loaded.metadata().schemaVersion());
		assertArrayEquals(new double[] {0, 1}, loaded.axes().poweredFraction());
		assertArrayEquals(new double[] {0.002, 0, 0, 0, 0, 0},
				loaded.cell(0, 0, 0, 0).runtimeCorrection()
						.dCoefficientDLogReSquared(), 0);
		assertArrayEquals(new double[] {0.0003, 0, 0, 0, 0, 0},
				loaded.cell(0, 0, 0, 0).runtimeCorrection()
						.dCoefficientDLogReCubed(), 0);
		TableCell powered = loaded.cell(0, 0, 0, 1);
		assertEquals(0.20, powered.ownerTotals().get("POWERED_PLUME_INSTALLATION_DRAG").ca(), 0);
		assertEquals(new AerodynamicDerivatives(-0.1, -1.2, -0.7), powered.derivatives());
		assertTrue(powered.runtimeCorrection().requiresRebuild());
		assertTrue(powered.diagnostics().reasonCodes().contains(FailureReason.PRESCRIBED_TRANSITION));
	}

	@Test
	void checksumAndSchemaV1FailWithRebuildDiagnostics() throws Exception {
		Path binary = temporary.resolve("table.aero");
		new TableWriter().write(poweredTable(), binary, temporary.resolve("table.json"));
		byte[] corrupt = Files.readAllBytes(binary);
		corrupt[corrupt.length - 1] ^= 1;
		Files.write(binary, corrupt);
		assertTrue(assertThrows(IOException.class, () -> new TableReader().read(binary))
				.getMessage().contains("checksum"));

		Path v1 = temporary.resolve("v1.aero");
		Files.write(v1, "OPRAERO1".getBytes(StandardCharsets.US_ASCII));
		assertTrue(assertThrows(IOException.class, () -> new TableReader().read(v1))
				.getMessage().contains("rebuild as " + TableMetadata.CURRENT_SCHEMA));
	}

	@Test
	void poweredAndTransitionEventsCannotBeInterpolated() {
		AerodynamicTable powered = poweredTable();
		assertEquals(0.30, new TableQueryEngine().query(powered, 1.075, 0, 0, 1)
				.coefficients().ca(), 0);
		assertThrows(IllegalStateException.class,
				() -> new TableQueryEngine().query(powered, 1.075, 0, 0, 0.5));

		AerodynamicTable transition = new AerodynamicTable(
				new TableAxes(new double[] {0.9, 1.1}, new double[] {0}, new double[] {0}),
				List.of(cell(0.1, "METHOD", List.of("LAMINAR_TRANSITION_STATE"), "OWNER"),
						cell(0.2, "METHOD", List.of("TURBULENT_TRANSITION_STATE"), "OWNER")),
				metadata());
		assertThrows(IllegalStateException.class,
				() -> new TableQueryEngine().query(transition, 1.0, 0, 0));
	}

	@Test
	void smoothMethodAndOwnershipHandoffsInterpolateWithUnionProvenance() {
		AerodynamicTable smooth = new AerodynamicTable(
				new TableAxes(new double[] {0.9, 1.1}, new double[] {0}, new double[] {0}),
				List.of(cell(0.1, "SUBSONIC_METHOD", List.of("SMOOTH_OVERLAP"), "SUBSONIC_OWNER"),
						cell(0.3, "SUPERSONIC_METHOD", List.of("SMOOTH_OVERLAP"), "SUPERSONIC_OWNER")),
				metadata());

		var result = new TableQueryEngine().query(smooth, 1.0, 0, 0);
		assertEquals(0.2, result.coefficients().ca(), 1.0e-12);
		assertTrue(result.methodIds().containsAll(List.of("SUBSONIC_METHOD", "SUPERSONIC_METHOD")));
		assertEquals(0.05, result.ownerTotals().get("SUBSONIC_OWNER").ca(), 1.0e-12);
		assertEquals(0.15, result.ownerTotals().get("SUPERSONIC_OWNER").ca(), 1.0e-12);
	}

	@Test
	void cacheKeyIncludesAllFourIdentitiesAndRejectsContentMismatch() throws Exception {
		AerodynamicTable table = poweredTable();
		PhysicsAeroTableCache cache = new PhysicsAeroTableCache(temporary.resolve("cache"));
		PhysicsAeroTableCache.Key key = new PhysicsAeroTableCache.Key("geometry", "settings", "code", "registry");
		new TableWriter().write(table, cache.tablePath(key), cache.manifestPath(key));
		String hash = PhysicsAeroTableCache.contentHash(cache.tablePath(key));
		assertEquals(table.metadata(), cache.load(key, hash).orElseThrow().metadata());
		assertThrows(IOException.class, () -> cache.load(key, "stale-content-hash"));
	}

	private static AerodynamicTable poweredTable() {
		return new AerodynamicTable(new TableAxes(new double[] {1.075}, new double[] {0},
				new double[] {0}, new double[] {0, 1}),
				List.of(cell(0.1, "COAST_METHOD", List.of("COAST_STATE"), "BASE_PRESSURE_DRAG"),
						cell(0.3, "POWERED_METHOD", List.of("POWERED_STATE"),
								"POWERED_PLUME_INSTALLATION_DRAG")), metadata());
	}

	private static TableCell cell(double ca, String method, List<String> validity, String owner) {
		AerodynamicCoefficients coefficients = new AerodynamicCoefficients(ca, 0.4, 0.1, 0, -0.2, 0.05);
		AerodynamicCoefficients owned = new AerodynamicCoefficients(
				owner.startsWith("POWERED") ? 0.2 : ca, 0, 0, 0, 0, 0);
		RuntimeCorrectionData correction = validity.contains("COAST_STATE")
				? new RuntimeCorrectionData(1_000_000, 0.1, 1.25,
						new double[] {0.01, 0, 0, 0, 0, 0},
						new double[] {0.002, 0, 0, 0, 0, 0},
						new double[] {0.0003, 0, 0, 0, 0, 0},
						false, "ANCHORED_QUADRATIC_LOG_RE_TEST")
				: RuntimeCorrectionData.rebuildRequired(1_000_000);
		return new TableCell(coefficients, Map.of("vehicle", coefficients), Map.of(owner, owned),
				List.of(method), new double[] {0.8, 0.8, 0.8, 0.8, 0.8, 0.8},
				new double[] {0.2, 0.2, 0.2, 0.2, 0.2, 0.2}, validity,
				new ReferenceState(1000, 0.01, 1, new Coordinate()),
				new CellDiagnostics(Set.of(DiagnosticFlag.DIRECT_GENERATION),
						Set.of(FailureReason.PRESCRIBED_TRANSITION), false, null, null, List.of()),
				true, new AerodynamicDerivatives(-0.1, -1.2, -0.7), correction);
	}

	private static TableMetadata metadata() {
		return new TableMetadata(TableMetadata.CURRENT_SCHEMA, "geometry", "settings", "code", "registry",
				TableMetadata.REQUIRED_UNITS, TableMetadata.REQUIRED_AXIS_CONVENTION,
				Map.of(), Map.of(), CertificationState.EXPERIMENTAL_FLIGHT_PENDING);
	}
}
