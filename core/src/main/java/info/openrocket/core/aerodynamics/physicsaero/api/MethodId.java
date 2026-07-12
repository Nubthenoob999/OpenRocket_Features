package info.openrocket.core.aerodynamics.physicsaero.api;

import java.util.Objects;

/** Stable, versioned identifier for an aerodynamic method. */
public record MethodId(String value) implements Comparable<MethodId> {
	public MethodId {
		value = Objects.requireNonNull(value, "value").trim();
		if (value.isEmpty()) throw new IllegalArgumentException("method ID must not be blank");
	}

	@Override public int compareTo(MethodId other) { return value.compareTo(other.value); }
	@Override public String toString() { return value; }
}
