package info.openrocket.core.aerodynamics.physicsaero.runtime;

import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroQueryException.QueryCoordinates;

/** First simulation state at which a typed runtime failure occurred. */
public record PhysicsAeroFailureOccurrence(double simulationTimeSeconds,
		QueryCoordinates coordinates, String detail) { }
