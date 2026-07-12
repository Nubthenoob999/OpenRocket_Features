package info.openrocket.core.aerodynamics.physicsaero.integration;
import java.nio.file.Files;
import java.nio.file.Path;
public final class PhysicsAeroFeatureController {
	public static final String DISPLAY_NAME="Physics-Based Aerodynamics (Experimental)";
	private boolean enabled;private Path table;private String geometryHash,settingsHash;
	public void setEnabled(boolean enabled){if(enabled&&new PhysicsAeroValidationGate().evaluate().status()!=PhysicsAeroValidationGate.Status.PASS)throw new IllegalStateException("HEADLESS_VALIDATION_GATE_FAILED");this.enabled=enabled;}
	public boolean enabled(){return enabled;}
	public void attachTable(Path table,String geometryHash,String settingsHash){if(table==null||!Files.isRegularFile(table)||geometryHash==null||settingsHash==null)throw new IllegalArgumentException("INVALID_TABLE_ATTACHMENT");this.table=table;this.geometryHash=geometryHash;this.settingsHash=settingsHash;}
	public boolean tableReady(String currentGeometryHash,String currentSettingsHash){return enabled&&table!=null&&Files.isRegularFile(table)&&geometryHash.equals(currentGeometryHash)&&settingsHash.equals(currentSettingsHash);}
	public String staleReason(String currentGeometryHash,String currentSettingsHash){if(table==null)return "MISSING_TABLE_NO_SILENT_FALLBACK";if(!Files.isRegularFile(table))return "TABLE_FILE_MISSING";if(!geometryHash.equals(currentGeometryHash))return "STALE_GEOMETRY_HASH";if(!settingsHash.equals(currentSettingsHash))return "STALE_SETTINGS_HASH";return "CURRENT";}
	public Path table(){return table;}
}
