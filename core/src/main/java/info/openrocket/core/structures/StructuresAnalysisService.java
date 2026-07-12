package info.openrocket.core.structures;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.structures.calculators.BulkheadCalculator;
import info.openrocket.core.structures.calculators.CenteringRingCalculator;
import info.openrocket.core.structures.calculators.FinFlutterCalculator;
import info.openrocket.core.structures.calculators.FinRootStressCalculator;
import info.openrocket.core.structures.calculators.TubeBucklingCalculator;
import info.openrocket.core.structures.calculators.TubeStressCalculator;
import info.openrocket.core.structures.geometry.BulkheadGeometry;
import info.openrocket.core.structures.geometry.CenteringRingGeometry;
import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.geometry.NoseConeGeometryStructural;
import info.openrocket.core.structures.geometry.StructuralComponentExtractor.ExtractedStructuralComponent;
import info.openrocket.core.structures.geometry.TubeGeometry;
import info.openrocket.core.structures.loads.AxialLoadModel;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.materials.StructuralMaterial;

public final class StructuresAnalysisService {
	private final TubeBucklingCalculator tubeBucklingCalculator = new TubeBucklingCalculator();
	private final TubeStressCalculator tubeStressCalculator = new TubeStressCalculator();
	private final FinFlutterCalculator finFlutterCalculator = new FinFlutterCalculator();
	private final FinRootStressCalculator finRootStressCalculator = new FinRootStressCalculator();
	private final BulkheadCalculator bulkheadCalculator = new BulkheadCalculator();
	private final CenteringRingCalculator centeringRingCalculator = new CenteringRingCalculator();

	public StructuresReport analyze(List<ExtractedStructuralComponent> selectedComponents, FlightLoadSeries loadSeries) {
		List<StructuresResult> results = new ArrayList<>();
		if (loadSeries == null || loadSeries.isEmpty()) {
			results.add(StructuresResult.insufficientData("Simulation", "Flight loads", "No simulation loads."));
			return new StructuresReport(results);
		}

		TubeGeometry firstTube = findFirst(selectedComponents, TubeGeometry.class);
		NoseConeGeometryStructural nose = findFirst(selectedComponents, NoseConeGeometryStructural.class);
		FinGeometryStructural fin = findFirst(selectedComponents, FinGeometryStructural.class);
		double maxAxialLoad = AxialLoadModel.conservativeAxialLoad(loadSeries.getMaxAxialLoadCase());

		for (ExtractedStructuralComponent component : selectedComponents) {
			Object geometry = component.getGeometry();
			StructuralMaterial material = component.getStructuralMaterial();
			if (geometry instanceof TubeGeometry) {
				TubeGeometry tube = (TubeGeometry) geometry;
				results.add(tubeBucklingCalculator.calculate(tube, material, 1.0, maxAxialLoad,
						StructuresToolModel.DEFAULT_TUBE_BUCKLING_FOS));
				results.add(tubeStressCalculator.calculate(tube, material, loadSeries, nose, fin,
						StructuresToolModel.DEFAULT_TUBE_STRESS_FOS));
			} else if (geometry instanceof FinGeometryStructural) {
				FinGeometryStructural finGeometry = (FinGeometryStructural) geometry;
				double bodyRadius = firstTube == null ? Double.NaN : firstTube.getOuterRadius();
				results.add(finFlutterCalculator.calculate(finGeometry, material, loadSeries,
						StructuresToolModel.DEFAULT_FIN_FLUTTER_FOS));
				results.add(finRootStressCalculator.calculate(finGeometry, material, loadSeries, bodyRadius,
						StructuresToolModel.DEFAULT_FIN_ROOT_STRESS_FOS));
			} else if (geometry instanceof BulkheadGeometry) {
				results.add(bulkheadCalculator.calculate((BulkheadGeometry) geometry, material, maxAxialLoad,
						StructuresToolModel.DEFAULT_BULKHEAD_FOS));
			} else if (geometry instanceof CenteringRingGeometry) {
				results.add(centeringRingCalculator.calculate((CenteringRingGeometry) geometry, material,
						maxAxialLoad, StructuresToolModel.DEFAULT_BULKHEAD_FOS));
			} else if (geometry instanceof NoseConeGeometryStructural) {
				results.add(StructuresResult.insufficientData(component.getComponentName(), "Nose cone structural check",
						"Nose cone geometry is used for tube normal-force loads; direct nose-cone structural checks are not implemented yet."));
			} else {
				results.add(StructuresResult.insufficientData(component.getComponentName(), component.getType(),
						"Component type is extractable but no first-iteration calculator is available."));
			}
		}
		return new StructuresReport(results);
	}

	private static <T> T findFirst(List<ExtractedStructuralComponent> components, Class<T> type) {
		for (ExtractedStructuralComponent component : components) {
			if (type.isInstance(component.getGeometry())) {
				return type.cast(component.getGeometry());
			}
		}
		return null;
	}
}
