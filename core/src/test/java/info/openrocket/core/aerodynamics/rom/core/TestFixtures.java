package info.openrocket.core.aerodynamics.rom.core;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public final class TestFixtures {

	private TestFixtures() {
	}

	public static RomGeometryInput typical4Fin() {
		double diameter = 0.15;
		double refArea = Math.PI * diameter * diameter / 4.0;
		return new RomGeometryInput(
				2.0,
				diameter,
				refArea,
				0.95,
				0.35,
				RomGeometryInput.NoseShape.OGIVE,
				2.0 / diameter,
				refArea,
				0.12,
				0.12,
				4,
				0.18,
				0.08,
				0.12,
				0.004,
				Math.toRadians(20.0),
				0.008,
				Math.PI * 0.02 * 0.02 / 4.0,
				6.4e-6);
	}

	public static RomGeometryInput typical3Fin() {
		RomGeometryInput g = typical4Fin();
		return new RomGeometryInput(
				g.bodyLength,
				g.maxDiameter,
				g.baseArea,
				g.wetArea,
				g.noseLength,
				g.noseShape,
				g.finenessRatio,
				g.referenceArea,
				g.boattailLength,
				g.boattailBaseDiameter,
				3,
				g.finRootChord,
				g.finTipChord,
				g.finSpan,
				g.finThickness,
				g.finSweepAngle,
				g.finWettedArea,
				g.motorExitArea,
				g.surfaceRoughness);
	}

	public static RomGeometryInput noFins() {
		double diameter = 0.15;
		double refArea = Math.PI * diameter * diameter / 4.0;
		return new RomGeometryInput(
				2.0,
				diameter,
				refArea,
				0.90,
				0.35,
				RomGeometryInput.NoseShape.OGIVE,
				2.0 / diameter,
				refArea,
				0.12,
				0.12,
				0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				Math.PI * 0.02 * 0.02 / 4.0,
				6.4e-6);
	}

	public static RomGeometryInput longerBodyForMomentArm() {
		RomGeometryInput g = typical4Fin();
		return new RomGeometryInput(
				3.0,
				g.maxDiameter,
				g.baseArea,
				g.wetArea,
				2.0,
				g.noseShape,
				3.0 / g.maxDiameter,
				g.referenceArea,
				g.boattailLength,
				g.boattailBaseDiameter,
				g.finCount,
				g.finRootChord,
				g.finTipChord,
				g.finSpan,
				g.finThickness,
				g.finSweepAngle,
				g.finWettedArea,
				g.motorExitArea,
				g.surfaceRoughness);
	}

	public static RomGeometryInput shorterBodyForMomentArm() {
		RomGeometryInput g = typical4Fin();
		return new RomGeometryInput(
				1.5,
				g.maxDiameter,
				g.baseArea,
				g.wetArea,
				0.35,
				g.noseShape,
				1.5 / g.maxDiameter,
				g.referenceArea,
				g.boattailLength,
				g.boattailBaseDiameter,
				g.finCount,
				g.finRootChord,
				g.finTipChord,
				g.finSpan,
				g.finThickness,
				g.finSweepAngle,
				g.finWettedArea,
				g.motorExitArea,
				g.surfaceRoughness);
	}
}
