package info.openrocket.core.montecarlo;

import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.util.Config;

/**
 * Immutable, document-owned snapshot of the most recently completed Monte Carlo batch.
 * The aerodynamic table itself remains in the machine-local cache; only its portable
 * identity and the per-run summary results are stored here.
 */
public final class MonteCarloAnalysis {
	public static final int FORMAT_VERSION = 1;

	private final long createdAtEpochMillis;
	private final String sourceSimulationName;
	private final String sourceConfigurationId;
	private final double launchLatitudeDeg;
	private final double launchLongitudeDeg;
	private final double launchAltitudeM;
	private final boolean usedPhysicsTable;
	private final PhysicsAeroMode physicsAeroMode;
	private final String tableGeometryHash;
	private final String tableSettingsHash;
	private final String tableContentHash;
	private final Config settings;
	private final List<MonteCarloRunRecord> records;
	private final boolean stale;
	private final String staleReason;

	public MonteCarloAnalysis(long createdAtEpochMillis, String sourceSimulationName,
			String sourceConfigurationId, double launchLatitudeDeg, double launchLongitudeDeg,
			double launchAltitudeM, boolean usedPhysicsTable, PhysicsAeroMode physicsAeroMode,
			String tableGeometryHash, String tableSettingsHash, String tableContentHash,
			Config settings, List<MonteCarloRunRecord> records, boolean stale, String staleReason) {
		this.createdAtEpochMillis = createdAtEpochMillis;
		this.sourceSimulationName = value(sourceSimulationName);
		this.sourceConfigurationId = value(sourceConfigurationId);
		this.launchLatitudeDeg = launchLatitudeDeg;
		this.launchLongitudeDeg = launchLongitudeDeg;
		this.launchAltitudeM = launchAltitudeM;
		this.usedPhysicsTable = usedPhysicsTable;
		this.physicsAeroMode = physicsAeroMode == null ? PhysicsAeroMode.OFF : physicsAeroMode;
		this.tableGeometryHash = value(tableGeometryHash);
		this.tableSettingsHash = value(tableSettingsHash);
		this.tableContentHash = value(tableContentHash);
		this.settings = settings == null ? new Config() : settings.clone();
		this.records = records == null ? List.of() : List.copyOf(records);
		this.stale = stale;
		this.staleReason = value(staleReason);
	}

	public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
	public String getSourceSimulationName() { return sourceSimulationName; }
	public String getSourceConfigurationId() { return sourceConfigurationId; }
	public double getLaunchLatitudeDeg() { return launchLatitudeDeg; }
	public double getLaunchLongitudeDeg() { return launchLongitudeDeg; }
	public double getLaunchAltitudeM() { return launchAltitudeM; }
	public boolean isUsedPhysicsTable() { return usedPhysicsTable; }
	public PhysicsAeroMode getPhysicsAeroMode() { return physicsAeroMode; }
	public String getTableGeometryHash() { return tableGeometryHash; }
	public String getTableSettingsHash() { return tableSettingsHash; }
	public String getTableContentHash() { return tableContentHash; }
	public Config getSettings() { return settings.clone(); }
	public List<MonteCarloRunRecord> getRecords() { return records; }
	public boolean isStale() { return stale; }
	public String getStaleReason() { return staleReason; }

	public MonteCarloAnalysis withStaleReason(String reason) {
		String normalized = value(reason);
		if (stale && staleReason.equals(normalized)) return this;
		return new MonteCarloAnalysis(createdAtEpochMillis, sourceSimulationName,
				sourceConfigurationId, launchLatitudeDeg, launchLongitudeDeg, launchAltitudeM,
				usedPhysicsTable, physicsAeroMode, tableGeometryHash, tableSettingsHash,
				tableContentHash, settings, records, true, normalized);
	}

	/** Build a persistent result snapshot after a completed batch. */
	public static MonteCarloAnalysis completed(info.openrocket.core.document.Simulation simulation,
			MonteCarloExtension extension, List<MonteCarloRunRecord> records,
			boolean usePhysicsTable) {
		var options = simulation.getOptions();
		var table = options.getPhysicsAeroSettings();
		return new MonteCarloAnalysis(System.currentTimeMillis(), simulation.getName(),
				simulation.getFlightConfigurationId().toString(), options.getLaunchLatitude(),
				options.getLaunchLongitude(), options.getLaunchAltitude(), usePhysicsTable,
				usePhysicsTable ? table.getMode() : PhysicsAeroMode.OFF,
				usePhysicsTable ? table.getGeometryHash() : "",
				usePhysicsTable ? table.getSettingsHash() : "",
				usePhysicsTable ? table.getTableContentHash() : "",
				extension == null ? new Config() : extension.getConfig(), records, false, "");
	}

	private static String value(String value) { return value == null ? "" : value; }
}
