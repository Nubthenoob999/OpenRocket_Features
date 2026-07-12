package info.openrocket.core.aerodynamics.physicsaero.selection;

import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;

public record SelectionDecision(MethodId methodId, boolean selected, SelectionReason reason, String details) {}
