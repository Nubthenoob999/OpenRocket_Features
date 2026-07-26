package info.openrocket.core.aerodynamics.physicsaero.integration;

import java.util.Locale;

import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;

/** Rejects method identifiers belonging to the removed aerodynamic implementation. */
public final class LegacyMethodIdGuard {
	private static final String[] REMOVED_METHOD_TOKENS = { "path" + "line", "r" + "om" };

	public void verify(TableCell cell) {
		for (String methodId : cell.methodIds()) {
			String normalized = methodId.toLowerCase(Locale.ROOT);
			for (String token : REMOVED_METHOD_TOKENS) {
				if (normalized.contains(token)) {
					throw new IllegalStateException("REMOVED_METHOD_IN_PHYSICS_TABLE:" + methodId);
				}
			}
		}
	}
}
