package info.openrocket.core.aerodynamics.rom.control;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;

public class FallbackBlender {
	public record BlendResult(AerodynamicForces forces, double fallbackWeight, boolean fallbackUsed) {
	}

	public BlendResult blend(AerodynamicForces legacy, AerodynamicForces rom, AerodynamicConfidence confidence,
			RomFallbackMode fallbackMode) {
		if (fallbackMode == RomFallbackMode.FORCE_ROM) {
			return new BlendResult(rom.clone(), 0.0, false);
		}
		double romWeight = confidence != null ? clamp01(confidence.getOverallScore()) : 0.0;
		if (fallbackMode == RomFallbackMode.BARROWMAN_ONLY && romWeight < 0.999) {
			return new BlendResult(legacy.clone(), 1.0, true);
		}
		double fallbackWeight = 1.0 - romWeight;
		AerodynamicForces blended = legacy.clone();
		blended.setCP(blendCp(legacy.getCP(), rom.getCP(), romWeight, legacy.getCN(), rom.getCN()));
		blended.setCN(lerp(legacy.getCN(), rom.getCN(), romWeight));
		blended.setCm(lerp(legacy.getCm(), rom.getCm(), romWeight));
		blended.setCside(lerp(legacy.getCside(), rom.getCside(), romWeight));
		blended.setCyaw(lerp(legacy.getCyaw(), rom.getCyaw(), romWeight));
		blended.setCroll(lerp(legacy.getCroll(), rom.getCroll(), romWeight));
		blended.setCrollDamp(lerp(legacy.getCrollDamp(), rom.getCrollDamp(), romWeight));
		blended.setCrollForce(lerp(legacy.getCrollForce(), rom.getCrollForce(), romWeight));
		blended.setPressureCD(lerp(legacy.getPressureCD(), rom.getPressureCD(), romWeight));
		blended.setFrictionCD(lerp(legacy.getFrictionCD(), rom.getFrictionCD(), romWeight));
		blended.setBaseCD(lerp(legacy.getBaseCD(), rom.getBaseCD(), romWeight));
		blended.setOverrideCD(legacy.getOverrideCD());
		// M19: Derive total CD from blended components to ensure consistency
		// (CD = pressureCD + baseCD + frictionCD) instead of blending CD independently
		blended.setCD(blended.getPressureCD() + blended.getBaseCD() + blended.getFrictionCD());
		blended.setCDaxial(lerp(legacy.getCDaxial(), rom.getCDaxial(), romWeight));
		blended.setPitchDampingMoment(lerp(legacy.getPitchDampingMoment(), rom.getPitchDampingMoment(), romWeight));
		blended.setYawDampingMoment(lerp(legacy.getYawDampingMoment(), rom.getYawDampingMoment(), romWeight));
		blended.setAxisymmetric(legacy.isAxisymmetric() && rom.isAxisymmetric());
		return new BlendResult(blended, fallbackWeight, fallbackWeight > 1e-6);
	}

	private static double lerp(double left, double right, double weight) {
		if (!Double.isFinite(left)) {
			return right;
		}
		if (!Double.isFinite(right)) {
			return left;
		}
		return left + weight * (right - left);
	}

	private static CoordinateIF blendCp(CoordinateIF legacy, CoordinateIF rom, double weight,
			double cnLegacy, double cnRom) {
		if (legacy == null) {
			return rom;
		}
		if (rom == null) {
			return legacy;
		}
		// CN-weighted CP blending: weight each CP by its source's |CN| contribution
		double wLeg = (1.0 - weight) * Math.abs(cnLegacy);
		double wRom = weight * Math.abs(cnRom);
		double totalWeight = wLeg + wRom;
		double x, y, z;
		if (totalWeight > 1e-10) {
			x = (wLeg * legacy.getX() + wRom * rom.getX()) / totalWeight;
			y = (wLeg * legacy.getY() + wRom * rom.getY()) / totalWeight;
			z = (wLeg * legacy.getZ() + wRom * rom.getZ()) / totalWeight;
		} else {
			// Both CN ~ 0: fall back to simple lerp
			x = lerp(legacy.getX(), rom.getX(), weight);
			y = lerp(legacy.getY(), rom.getY(), weight);
			z = lerp(legacy.getZ(), rom.getZ(), weight);
		}
		double w = Math.max(0.0, lerp(legacy.getWeight(), rom.getWeight(), weight));
		return new Coordinate(x, y, z, w);
	}

	private static double clamp01(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
