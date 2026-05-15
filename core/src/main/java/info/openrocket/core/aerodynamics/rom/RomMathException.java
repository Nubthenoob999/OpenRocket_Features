package info.openrocket.core.aerodynamics.rom;

/**
 * Thrown when a ROM evaluation hits an unrecoverable numeric/singularity
 * condition for a single sample. The preview runner catches this per-row so a
 * problematic Mach/AoA pair does not abort the sweep.
 */
public class RomMathException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public RomMathException(String message) {
		super(message);
	}

	public RomMathException(String message, Throwable cause) {
		super(message, cause);
	}
}
