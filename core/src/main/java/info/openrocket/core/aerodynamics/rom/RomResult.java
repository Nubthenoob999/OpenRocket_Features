package info.openrocket.core.aerodynamics.rom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerState;
import info.openrocket.core.aerodynamics.rom.control.AerodynamicConfidence;
import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowRegime;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;

public class RomResult {
	private final AerodynamicForces romForces;
	private final AerodynamicForces blendedForces;
	private final GeometryFeatures geometryFeatures;
	private final FlowState flowState;
	private final FlowRegime regime;
	private final AerodynamicConfidence confidence;
	private final List<PathlineSeed> seeds;
	private final List<EdgeState> edgeStates;
	private final List<BoundaryLayerState> boundaryLayerStates;
	private final int marchingSteps;
	private final double separationFraction;
	private final double fallbackWeight;
	private final boolean fallbackUsed;
	private final String notes;

	public RomResult(AerodynamicForces romForces, AerodynamicForces blendedForces,
			GeometryFeatures geometryFeatures, FlowState flowState, FlowRegime regime,
			AerodynamicConfidence confidence, List<PathlineSeed> seeds, int marchingSteps,
			double separationFraction, double fallbackWeight, boolean fallbackUsed, String notes) {
		this(romForces, blendedForces, geometryFeatures, flowState, regime, confidence, seeds,
				Collections.emptyList(), Collections.emptyList(), marchingSteps, separationFraction,
				fallbackWeight, fallbackUsed, notes);
	}

	public RomResult(AerodynamicForces romForces, AerodynamicForces blendedForces,
			GeometryFeatures geometryFeatures, FlowState flowState, FlowRegime regime,
			AerodynamicConfidence confidence, List<PathlineSeed> seeds,
			List<EdgeState> edgeStates, List<BoundaryLayerState> boundaryLayerStates,
			int marchingSteps, double separationFraction, double fallbackWeight,
			boolean fallbackUsed, String notes) {
		this.romForces = romForces;
		this.blendedForces = blendedForces;
		this.geometryFeatures = geometryFeatures;
		this.flowState = flowState;
		this.regime = regime;
		this.confidence = confidence;
		this.seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
		this.edgeStates = edgeStates != null
				? Collections.unmodifiableList(new ArrayList<>(edgeStates))
				: Collections.emptyList();
		this.boundaryLayerStates = boundaryLayerStates != null
				? Collections.unmodifiableList(new ArrayList<>(boundaryLayerStates))
				: Collections.emptyList();
		this.marchingSteps = marchingSteps;
		this.separationFraction = separationFraction;
		this.fallbackWeight = fallbackWeight;
		this.fallbackUsed = fallbackUsed;
		this.notes = notes != null ? notes : "";
	}

	public List<EdgeState> getEdgeStates() {
		return edgeStates;
	}

	public List<BoundaryLayerState> getBoundaryLayerStates() {
		return boundaryLayerStates;
	}

	public AerodynamicForces getRomForces() {
		return romForces;
	}

	public AerodynamicForces getBlendedForces() {
		return blendedForces;
	}

	public GeometryFeatures getGeometryFeatures() {
		return geometryFeatures;
	}

	public FlowState getFlowState() {
		return flowState;
	}

	public FlowRegime getRegime() {
		return regime;
	}

	public AerodynamicConfidence getConfidence() {
		return confidence;
	}

	public List<PathlineSeed> getSeeds() {
		return seeds;
	}

	public int getMarchingSteps() {
		return marchingSteps;
	}

	public double getSeparationFraction() {
		return separationFraction;
	}

	public double getFallbackWeight() {
		return fallbackWeight;
	}

	public boolean isFallbackUsed() {
		return fallbackUsed;
	}

	public String getNotes() {
		return notes;
	}
}
