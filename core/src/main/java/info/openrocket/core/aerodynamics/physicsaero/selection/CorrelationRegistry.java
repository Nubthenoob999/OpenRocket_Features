package info.openrocket.core.aerodynamics.physicsaero.selection;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.api.*;

public final class CorrelationRegistry {
	private final Map<MethodId, AerodynamicCorrelation<?>> methods = new TreeMap<>();
	public void register(AerodynamicCorrelation<?> correlation) {
		if (methods.putIfAbsent(correlation.metadata().methodId(), correlation) != null) throw new IllegalArgumentException("duplicate method ID");
	}
	public Optional<AerodynamicCorrelation<?>> find(MethodId id) { return Optional.ofNullable(methods.get(id)); }
	public List<CorrelationMetadata> metadata() { return methods.values().stream().map(AerodynamicCorrelation::metadata).toList(); }
}
