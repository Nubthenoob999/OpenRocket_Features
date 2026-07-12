package info.openrocket.core.aerodynamics.physicsaero.integration;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
public final class LegacyRomRemovalGuard {public void verify(TableCell cell){for(String id:cell.methodIds())if(id.toLowerCase(java.util.Locale.ROOT).contains("pathline")||id.toLowerCase(java.util.Locale.ROOT).contains("rom"))throw new IllegalStateException("LEGACY_ROM_METHOD_IN_PHYSICS_TABLE:"+id);}}
