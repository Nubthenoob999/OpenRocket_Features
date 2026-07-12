package info.openrocket.core.structures.calculators;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.geometry.NoseConeGeometryStructural;
import info.openrocket.core.structures.geometry.TubeGeometry;
import info.openrocket.core.structures.loads.FlightLoadCase;
import info.openrocket.core.structures.loads.NormalForceLoadModel;
import info.openrocket.core.structures.loads.StructuralLoadDiagram;
import info.openrocket.core.structures.loads.StructuralLoadDiagramPoint;

public final class ShearBendingMomentDiagramCalculator {
	private static final int DEFAULT_STATION_COUNT = 100;

	public StructuralLoadDiagram calculate(TubeGeometry tube, NoseConeGeometryStructural nose, FinGeometryStructural fin,
			FlightLoadCase loadCase) {
		return calculate(tube, nose, fin, loadCase, DEFAULT_STATION_COUNT);
	}

	public StructuralLoadDiagram calculate(TubeGeometry tube, NoseConeGeometryStructural nose, FinGeometryStructural fin,
			FlightLoadCase loadCase, int stationCount) {
		if (tube == null || !tube.isComplete() || loadCase == null || stationCount < 2) {
			return new StructuralLoadDiagram(tube == null ? "Tube" : tube.getComponentName(), "Insufficient data",
					new ArrayList<>());
		}

		List<PointLoad> loads = pointLoads(tube, nose, fin, loadCase);
		List<StructuralLoadDiagramPoint> points = new ArrayList<>();
		for (int i = 0; i < stationCount; i++) {
			double station = tube.getLength() * i / (stationCount - 1.0);
			double absoluteX = tube.getAxialPosition() + station;
			double shear = 0.0;
			double moment = 0.0;
			for (PointLoad load : loads) {
				if (load.position <= absoluteX) {
					shear += load.force;
					moment += load.force * (absoluteX - load.position);
				}
			}
			points.add(new StructuralLoadDiagramPoint(station, shear, moment));
		}

		String description = String.format("Worst tube stress case at t=%.3f s", loadCase.getTime());
		return new StructuralLoadDiagram(tube.getComponentName(), description, points);
	}

	private static List<PointLoad> pointLoads(TubeGeometry tube, NoseConeGeometryStructural nose,
			FinGeometryStructural fin, FlightLoadCase loadCase) {
		List<PointLoad> loads = new ArrayList<>();
		double alpha = loadCase.getAngleOfAttack();
		if (!Double.isFinite(alpha)) {
			alpha = 0.0;
		}
		double referenceArea = tube.getReferenceArea();
		double dynamicPressure = loadCase.getDynamicPressure();
		if (!Double.isFinite(dynamicPressure)) {
			return loads;
		}

		if (nose != null) {
			double force = dynamicPressure * referenceArea * alpha * NormalForceLoadModel.noseCnAlpha(nose);
			double position = nose.getAxialPosition() + 2.0 * nose.getLength() / 3.0;
			loads.add(new PointLoad(position, force));
		}
		if (fin != null && fin.isComplete()) {
			double cnAlpha = NormalForceLoadModel.finCnAlpha(fin, tube.getOuterRadius());
			double force = dynamicPressure * referenceArea * alpha * cnAlpha;
			double position = fin.getAxialLeadingEdgePosition() + fin.getRootChord() * 0.55;
			loads.add(new PointLoad(position, force));
		}
		return loads;
	}

	private static final class PointLoad {
		private final double position;
		private final double force;

		private PointLoad(double position, double force) {
			this.position = position;
			this.force = force;
		}
	}
}
