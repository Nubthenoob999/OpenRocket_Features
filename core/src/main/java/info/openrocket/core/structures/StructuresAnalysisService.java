package info.openrocket.core.structures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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
	private static final double BULKHEAD_STACK_GAP_TOLERANCE = 1.0e-7;

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
		double maxAxialLoad = AxialLoadModel.conservativeAxialLoad(loadSeries.getMaxPoweredAxialLoadCase());
		Set<ExtractedStructuralComponent> analyzedBulkheadLayers = new HashSet<>();

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
				if (analyzedBulkheadLayers.contains(component)) {
					continue;
				}
				BulkheadStack stack = findBulkheadStack(component, selectedComponents);
				analyzedBulkheadLayers.addAll(stack.layers);
				StructuresResult result = bulkheadCalculator.calculate(stack.geometry, material, maxAxialLoad,
						StructuresToolModel.DEFAULT_BULKHEAD_FOS);
				results.add(stack.layers.size() == 1 ? result : addBulkheadStackContext(result, stack));
			} else if (geometry instanceof CenteringRingGeometry) {
				results.add(centeringRingCalculator.calculate((CenteringRingGeometry) geometry, material,
						maxAxialLoad, StructuresToolModel.DEFAULT_BULKHEAD_FOS));
			} else if (!(geometry instanceof NoseConeGeometryStructural)) {
				// Nose geometry is selected as load context for the tube calculations,
				// but it has no separate first-iteration analysis result.
				results.add(StructuresResult.insufficientData(component.getComponentName(), component.getType(),
						"Component type is extractable but no first-iteration calculator is available."));
			}
		}
		return new StructuresReport(results);
	}

	private static BulkheadStack findBulkheadStack(ExtractedStructuralComponent seed,
			List<ExtractedStructuralComponent> components) {
		List<ExtractedStructuralComponent> candidates = new ArrayList<>();
		for (ExtractedStructuralComponent candidate : components) {
			if (candidate.getGeometry() instanceof BulkheadGeometry && sameBulkheadStackIdentity(seed, candidate)) {
				candidates.add(candidate);
			}
		}
		candidates.sort(Comparator.comparingDouble(component ->
				((BulkheadGeometry) component.getGeometry()).getAxialPosition()));

		int seedIndex = candidates.indexOf(seed);
		int first = seedIndex;
		int last = seedIndex;
		while (first > 0 && bulkheadLayersTouch(candidates.get(first - 1), candidates.get(first))) {
			first--;
		}
		while (last + 1 < candidates.size() && bulkheadLayersTouch(candidates.get(last), candidates.get(last + 1))) {
			last++;
		}

		List<ExtractedStructuralComponent> layers = new ArrayList<>(candidates.subList(first, last + 1));
		double start = Double.POSITIVE_INFINITY;
		double end = Double.NEGATIVE_INFINITY;
		double outerRadius = Double.POSITIVE_INFINITY;
		for (ExtractedStructuralComponent layer : layers) {
			BulkheadGeometry geometry = (BulkheadGeometry) layer.getGeometry();
			start = Math.min(start, geometry.getAxialPosition());
			end = Math.max(end, geometry.getAxialPosition() + geometry.getThickness());
			outerRadius = Math.min(outerRadius, geometry.getOuterRadius());
		}
		String name = layers.size() == 1 ? seed.getComponentName()
				: seed.getComponentName() + " (" + layers.size() + "-layer stack)";
		return new BulkheadStack(layers, new BulkheadGeometry(outerRadius, end - start, start, name));
	}

	private static boolean sameBulkheadStackIdentity(ExtractedStructuralComponent first,
			ExtractedStructuralComponent second) {
		if (first.getRocketComponent().getParent() != second.getRocketComponent().getParent()
				|| !first.getComponentName().equals(second.getComponentName())) {
			return false;
		}
		StructuralMaterial firstMaterial = first.getStructuralMaterial();
		StructuralMaterial secondMaterial = second.getStructuralMaterial();
		return firstMaterial.getName().equals(secondMaterial.getName())
				&& sameDouble(firstMaterial.getYoungsModulus(), secondMaterial.getYoungsModulus())
				&& sameDouble(firstMaterial.getShearModulus(), secondMaterial.getShearModulus())
				&& sameDouble(firstMaterial.getTensileAllowable(), secondMaterial.getTensileAllowable())
				&& sameDouble(firstMaterial.getCompressiveStrength(), secondMaterial.getCompressiveStrength())
				&& sameDouble(firstMaterial.getDensity(), secondMaterial.getDensity())
				&& java.util.Objects.equals(firstMaterial.getPoissonRatio(), secondMaterial.getPoissonRatio());
	}

	private static boolean bulkheadLayersTouch(ExtractedStructuralComponent first,
			ExtractedStructuralComponent second) {
		BulkheadGeometry firstGeometry = (BulkheadGeometry) first.getGeometry();
		BulkheadGeometry secondGeometry = (BulkheadGeometry) second.getGeometry();
		double firstEnd = firstGeometry.getAxialPosition() + firstGeometry.getThickness();
		return Math.abs(firstEnd - secondGeometry.getAxialPosition()) <= BULKHEAD_STACK_GAP_TOLERANCE;
	}

	private static boolean sameDouble(double first, double second) {
		return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
	}

	private static StructuresResult addBulkheadStackContext(StructuresResult result, BulkheadStack stack) {
		LinkedHashMap<String, Double> values = new LinkedHashMap<>(result.getValues());
		values.put("bondedLayerCount", (double) stack.layers.size());
		values.put("effectiveThickness_m", stack.geometry.getThickness());
		List<String> warnings = new ArrayList<>(result.getWarnings());
		warnings.add("Contiguous same-name, same-material bulkhead layers on one parent are treated as a bonded stack; "
				+ "verify the bonded interface carries interlaminar shear.");
		return new StructuresResult(result.getComponentName(), result.getAnalysisType(), result.getFactorOfSafety(),
				result.getStatus(), result.getGoverningCaseDescription(), values, warnings);
	}

	private static <T> T findFirst(List<ExtractedStructuralComponent> components, Class<T> type) {
		for (ExtractedStructuralComponent component : components) {
			if (type.isInstance(component.getGeometry())) {
				return type.cast(component.getGeometry());
			}
		}
		return null;
	}

	private static final class BulkheadStack {
		private final List<ExtractedStructuralComponent> layers;
		private final BulkheadGeometry geometry;

		private BulkheadStack(List<ExtractedStructuralComponent> layers, BulkheadGeometry geometry) {
			this.layers = layers;
			this.geometry = geometry;
		}
	}
}
