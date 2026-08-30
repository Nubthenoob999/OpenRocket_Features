package info.openrocket.core.montecarlo;

import java.util.Objects;
import java.util.Random;

/**
 * Derives stable, independent pseudo-random streams from one per-run seed.
 * <p>
 * A subsystem is identified by name rather than by call order.  Adding a random
 * draw to the gust generator therefore cannot shift the shear realization (and
 * vice versa).
 */
public final class RandomStreamManager {
	private final long masterSeed;

	public RandomStreamManager(long masterSeed) {
		this.masterSeed = masterSeed;
	}

	/**
	 * Return a new stream at the beginning of the named deterministic sequence.
	 * Repeating this call with the same master seed and name reproduces the stream.
	 */
	public Random stream(String name) {
		return new Random(seedFor(name));
	}

	public long seedFor(String name) {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) {
			throw new IllegalArgumentException("Random stream name must not be blank");
		}

		// FNV-1a gives the stream name a stable JVM-independent 64-bit identity.
		long nameHash = 0xcbf29ce484222325L;
		for (int index = 0; index < name.length(); index++) {
			nameHash ^= name.charAt(index);
			nameHash *= 0x100000001b3L;
		}
		return mix64(masterSeed ^ nameHash);
	}

	/** SplitMix64 finalizer; useful here as a high-quality deterministic seed mixer. */
	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
		value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
		return value ^ (value >>> 31);
	}
}
