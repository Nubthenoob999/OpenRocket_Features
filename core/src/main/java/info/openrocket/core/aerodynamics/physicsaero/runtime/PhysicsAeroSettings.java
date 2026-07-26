package info.openrocket.core.aerodynamics.physicsaero.runtime;

import java.util.Objects;

/** Serializable simulation selection and cache identity; no machine-local path is persisted. */
public final class PhysicsAeroSettings {
	private PhysicsAeroMode mode = PhysicsAeroMode.OFF;
	private String geometryHash = "";
	private String settingsHash = "";
	private String tableContentHash = "";
	private boolean forceTurbulentBoundaryLayer;

	public PhysicsAeroMode getMode() { return mode; }
	public void setMode(PhysicsAeroMode mode) { this.mode = mode == null ? PhysicsAeroMode.OFF : mode; }
	public String getGeometryHash() { return geometryHash; }
	public void setGeometryHash(String value) { geometryHash = normalized(value); }
	public String getSettingsHash() { return settingsHash; }
	public void setSettingsHash(String value) { settingsHash = normalized(value); }
	public String getTableContentHash() { return tableContentHash; }
	public void setTableContentHash(String value) { tableContentHash = normalized(value); }
	public boolean isForceTurbulentBoundaryLayer() { return forceTurbulentBoundaryLayer; }
	public void setForceTurbulentBoundaryLayer(boolean value) { forceTurbulentBoundaryLayer = value; }
	public boolean isEnabled() { return mode != PhysicsAeroMode.OFF; }

	public PhysicsAeroSettings copy() {
		PhysicsAeroSettings copy = new PhysicsAeroSettings();
		copy.mode = mode;
		copy.geometryHash = geometryHash;
		copy.settingsHash = settingsHash;
		copy.tableContentHash = tableContentHash;
		copy.forceTurbulentBoundaryLayer = forceTurbulentBoundaryLayer;
		return copy;
	}

	@Override public boolean equals(Object other) {
		if (!(other instanceof PhysicsAeroSettings value)) return false;
		return mode == value.mode && geometryHash.equals(value.geometryHash)
				&& settingsHash.equals(value.settingsHash) && tableContentHash.equals(value.tableContentHash)
				&& forceTurbulentBoundaryLayer == value.forceTurbulentBoundaryLayer;
	}
	@Override public int hashCode() {
		return Objects.hash(mode, geometryHash, settingsHash, tableContentHash,
				forceTurbulentBoundaryLayer);
	}
	@Override public String toString() {
		return "PhysicsAeroSettings[mode=" + mode + ", geometryHash=" + geometryHash
				+ ", settingsHash=" + settingsHash + ", tableContentHash=" + tableContentHash
				+ ", forceTurbulentBoundaryLayer=" + forceTurbulentBoundaryLayer + "]";
	}
	private static String normalized(String value) { return value == null ? "" : value.trim(); }
}
