package info.openrocket.core.aerodynamics;

import info.openrocket.core.aerodynamics.rom.PathlineROMCalculator;
import info.openrocket.core.aerodynamics.rom.RomSettings;

/**
 * Compatibility wrapper that preserves the historical runtime integration point while
 * delegating to the Phase I pathline ROM implementation.
 */
public class RomAerodynamicCalculator extends PathlineROMCalculator {
	public RomAerodynamicCalculator() {
		super();
	}

	public RomAerodynamicCalculator(AerodynamicCalculator legacyCalculator, RomSettings settings) {
		super(legacyCalculator, settings);
	}
}
