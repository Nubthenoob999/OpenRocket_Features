package info.openrocket.core.aerodynamics.physicsaero.runtime;

import info.openrocket.core.aerodynamics.physicsaero.interpolation.QueryResult;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.TableQueryEngine;
import info.openrocket.core.aerodynamics.physicsaero.table.*;

/** Runtime is intentionally a validated lookup only: it contains no hidden physics fallback. */
public final class PhysicsAeroTableCalculator {
	private final AerodynamicTable table;
	public PhysicsAeroTableCalculator(AerodynamicTable table, String geometryHash, String settingsHash) {
		if (!TableMetadata.CURRENT_SCHEMA.equals(table.metadata().schemaVersion())) throw new IllegalArgumentException("SCHEMA_MISMATCH");
		if (!"SI;radians".equals(table.metadata().units())) throw new IllegalArgumentException("UNITS_MISMATCH");
		if (!"OPENROCKET_BODY_AXES_V1".equals(table.metadata().axisConvention())) throw new IllegalArgumentException("CONVENTION_MISMATCH");
		if (!table.metadata().geometryHash().equals(geometryHash) || !table.metadata().settingsHash().equals(settingsHash)) throw new IllegalArgumentException("HASH_MISMATCH");
		this.table = table;
	}
	public QueryResult query(double mach, double alphaRad, double betaRad) { return new TableQueryEngine().query(table, mach, alphaRad, betaRad); }
}
