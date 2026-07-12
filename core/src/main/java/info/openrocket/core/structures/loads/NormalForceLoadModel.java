package info.openrocket.core.structures.loads;

import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.geometry.NoseConeGeometryStructural;
import info.openrocket.core.structures.geometry.TubeGeometry;

public final class NormalForceLoadModel {
	private NormalForceLoadModel() {
	}

	public static double noseCnAlpha(NoseConeGeometryStructural nose) {
		return nose == null ? 0.0 : 2.0;
	}

	public static double finCnAlpha(FinGeometryStructural fin, double bodyRadius) {
		if (fin == null || !fin.isComplete() || bodyRadius <= 0) {
			return 0.0;
		}
		double diameter = 2.0 * bodyRadius;
		double chordSum = fin.getRootChord() + fin.getTipChord();
		if (chordSum <= 0) {
			return 0.0;
		}
		double aspectTerm = 2.0 * fin.getSemiSpan() / chordSum;
		double denominator = 1.0 + Math.sqrt(1.0 + aspectTerm * aspectTerm);
		double interference = 1.0 + bodyRadius / (bodyRadius + fin.getSemiSpan());
		return interference * 4.0 * fin.getFinCount() * Math.pow(fin.getSemiSpan() / diameter, 2) / denominator;
	}

	public static double normalForce(TubeGeometry tube, NoseConeGeometryStructural nose, FinGeometryStructural fin,
			FlightLoadCase loadCase) {
		double alpha = loadCase.getAngleOfAttack();
		if (!Double.isFinite(alpha)) {
			return 0.0;
		}
		double cnAlpha = noseCnAlpha(nose) + finCnAlpha(fin, tube.getOuterRadius());
		return loadCase.getDynamicPressure() * tube.getReferenceArea() * alpha * cnAlpha;
	}

	public static double bendingMomentAtTube(TubeGeometry tube, NoseConeGeometryStructural nose, FinGeometryStructural fin,
			FlightLoadCase loadCase) {
		double normalForce = normalForce(tube, nose, fin, loadCase);
		double cp = approximateCp(tube, nose, fin);
		double cg = loadCase.getCg();
		if (!Double.isFinite(cp) || !Double.isFinite(cg)) {
			return 0.0;
		}
		return Math.abs(normalForce * (cp - cg));
	}

	public static double approximateCp(TubeGeometry tube, NoseConeGeometryStructural nose, FinGeometryStructural fin) {
		double totalCn = 0.0;
		double weighted = 0.0;
		if (nose != null) {
			double cn = noseCnAlpha(nose);
			totalCn += cn;
			weighted += cn * (nose.getAxialPosition() + 2.0 * nose.getLength() / 3.0);
		}
		if (fin != null && fin.isComplete()) {
			double cn = finCnAlpha(fin, tube.getOuterRadius());
			totalCn += cn;
			weighted += cn * (fin.getAxialLeadingEdgePosition() + fin.getRootChord() * 0.55);
		}
		if (totalCn <= 0) {
			return Double.NaN;
		}
		return weighted / totalCn;
	}
}
