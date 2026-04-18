package info.openrocket.core.aerodynamics.rom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.openrocket.core.aerodynamics.AbstractAerodynamicCalculator;
import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerMarcher;
import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerState;
import info.openrocket.core.aerodynamics.rom.control.AerodynamicConfidence;
import info.openrocket.core.aerodynamics.rom.control.FallbackBlender;
import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowRegime;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.flow.FlowStateExtractor;
import info.openrocket.core.aerodynamics.rom.flow.RegimeSelector;
import info.openrocket.core.aerodynamics.rom.force.AerodynamicCoefficientAssembler;
import info.openrocket.core.aerodynamics.rom.force.ForceIntegrator;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatureExtractor;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeeder;
import info.openrocket.core.aerodynamics.rom.math.BaseDragClosures;
import info.openrocket.core.aerodynamics.rom.outer.RegimeBlender;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.ModID;

public class PathlineROMCalculator extends AbstractAerodynamicCalculator {
	private static final double PLUME_DECAY_TAU_SEC = 0.30;

	public static final class RomComputationSnapshot {
		private final double timeSeconds;
		private final double mach;
		private final double reynoldsLength;
		private final double alphaDeg;
		private final double betaDeg;
		private final String regime;
		private final int seedCount;
		private final double confidence;
		private final double fallbackWeight;
		private final double separationFraction;
		private final double cdLegacy;
		private final double cdRom;
		private final double cdFinal;
		private final double cnLegacy;
		private final double cnRom;
		private final double cnFinal;
		private final double cmLegacy;
		private final double cmRom;
		private final double cmFinal;
		private final String notes;

		public RomComputationSnapshot(double timeSeconds, double mach, double reynoldsLength, double alphaDeg,
				double betaDeg, String regime, int seedCount, double confidence, double fallbackWeight,
				double separationFraction, double cdLegacy, double cdRom, double cdFinal, double cnLegacy,
				double cnRom, double cnFinal, double cmLegacy, double cmRom, double cmFinal, String notes) {
			this.timeSeconds = timeSeconds;
			this.mach = mach;
			this.reynoldsLength = reynoldsLength;
			this.alphaDeg = alphaDeg;
			this.betaDeg = betaDeg;
			this.regime = regime;
			this.seedCount = seedCount;
			this.confidence = confidence;
			this.fallbackWeight = fallbackWeight;
			this.separationFraction = separationFraction;
			this.cdLegacy = cdLegacy;
			this.cdRom = cdRom;
			this.cdFinal = cdFinal;
			this.cnLegacy = cnLegacy;
			this.cnRom = cnRom;
			this.cnFinal = cnFinal;
			this.cmLegacy = cmLegacy;
			this.cmRom = cmRom;
			this.cmFinal = cmFinal;
			this.notes = notes != null ? notes : "";
		}

		public double getTimeSeconds() {
			return timeSeconds;
		}

		public double getMach() {
			return mach;
		}

		public double getReynoldsLength() {
			return reynoldsLength;
		}

		public double getAlphaDeg() {
			return alphaDeg;
		}

		public double getBetaDeg() {
			return betaDeg;
		}

		public String getRegime() {
			return regime;
		}

		public int getSeedCount() {
			return seedCount;
		}

		public double getConfidence() {
			return confidence;
		}

		public double getFallbackWeight() {
			return fallbackWeight;
		}

		public double getSeparationFraction() {
			return separationFraction;
		}

		public double getCdLegacy() {
			return cdLegacy;
		}

		public double getCdRom() {
			return cdRom;
		}

		public double getCdFinal() {
			return cdFinal;
		}

		public double getCnLegacy() {
			return cnLegacy;
		}

		public double getCnRom() {
			return cnRom;
		}

		public double getCnFinal() {
			return cnFinal;
		}

		public double getCmLegacy() {
			return cmLegacy;
		}

		public double getCmRom() {
			return cmRom;
		}

		public double getCmFinal() {
			return cmFinal;
		}

		public String getNotes() {
			return notes;
		}
	}

	private final AerodynamicCalculator legacyCalculator;
	private final GeometryFeatureExtractor geometryFeatureExtractor = new GeometryFeatureExtractor();
	private final FlowStateExtractor flowStateExtractor = new FlowStateExtractor();
	private final PathlineSeeder pathlineSeeder = new PathlineSeeder();
	private final RegimeSelector regimeSelector = new RegimeSelector();
	private final RegimeBlender outerFlow = new RegimeBlender();
	private final BoundaryLayerMarcher boundaryLayerMarcher = new BoundaryLayerMarcher();
	private final ForceIntegrator forceIntegrator = new ForceIntegrator();
	private final FallbackBlender fallbackBlender = new FallbackBlender();

	private RomSettings settings;
	private GeometryFeatures cachedGeometryFeatures;
	private RomResult lastResult;
	private double plumeState = 0.0;
	private double currentSimulationTimeSeconds = Double.NaN;
	private final List<RomComputationSnapshot> computationSnapshots = new ArrayList<>();

	public PathlineROMCalculator() {
		this(new BarrowmanCalculator(), RomSettings.defaults());
	}

	public PathlineROMCalculator(AerodynamicCalculator legacyCalculator, RomSettings settings) {
		this.legacyCalculator = legacyCalculator != null ? legacyCalculator : new BarrowmanCalculator();
		this.settings = settings != null ? settings.copy() : RomSettings.defaults();
	}

	public RomSettings getSettings() {
		return settings.copy();
	}

	public void setSettings(RomSettings settings) {
		this.settings = settings != null ? settings.copy() : RomSettings.defaults();
	}

	public boolean isEnabled() {
		return settings.isEnabled();
	}

	public boolean hasSurface() {
		return isEnabled();
	}

	public void setCurrentSimulationTime(double timeSeconds) {
		this.currentSimulationTimeSeconds = timeSeconds;
	}

	public double getCurrentSimulationTimeSeconds() {
		return currentSimulationTimeSeconds;
	}

	public double getPlumeState() {
		return plumeState;
	}

	public void setPlumeState(double plumeState) {
		this.plumeState = clamp01(plumeState);
	}

	public static double evolvePlumeState(double initialState, boolean burning, double dtSeconds) {
		if (burning) {
			return 1.0;
		}
		double plumeState = clamp01(initialState);
		double dt = Math.max(0.0, dtSeconds);
		if (dt == 0.0) {
			return plumeState;
		}
		double decay = Math.exp(-dt / PLUME_DECAY_TAU_SEC);
		double evolved = plumeState * decay;
		return evolved < 1e-6 ? 0.0 : evolved;
	}

	public void clearComputationSnapshots() {
		computationSnapshots.clear();
	}

	public List<RomComputationSnapshot> getComputationSnapshots() {
		return Collections.unmodifiableList(new ArrayList<>(computationSnapshots));
	}

	public RomResult getLastResult() {
		return lastResult;
	}

	@Override
	public double getStallAngle() {
		return legacyCalculator.getStallAngle();
	}

	@Override
	public CoordinateIF getCP(FlightConfiguration configuration, FlightConditions conditions, WarningSet warnings) {
		if (!isEnabled()) {
			return legacyCalculator.getCP(configuration, conditions, warnings);
		}
		return evaluate(configuration, conditions, warnings).getBlendedForces().getCP();
	}

	@Override
	public AerodynamicForces getAerodynamicForces(FlightConfiguration configuration, FlightConditions conditions,
			WarningSet warnings) {
		if (!isEnabled()) {
			return legacyCalculator.getAerodynamicForces(configuration, conditions, warnings);
		}
		return evaluate(configuration, conditions, warnings).getBlendedForces();
	}

	@Override
	public Map<RocketComponent, AerodynamicForces> getForceAnalysis(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		if (!isEnabled()) {
			return legacyCalculator.getForceAnalysis(configuration, conditions, warnings);
		}
		Map<RocketComponent, AerodynamicForces> analysis = legacyCalculator.getForceAnalysis(configuration, conditions, warnings);
		if (analysis == null) {
			analysis = new LinkedHashMap<>();
		}
		analysis.put(configuration.getRocket(), evaluate(configuration, conditions, warnings).getBlendedForces());
		return analysis;
	}

	@Override
	public PathlineROMCalculator newInstance() {
		return new PathlineROMCalculator(legacyCalculator.newInstance(), settings.copy());
	}

	@Override
	public void checkGeometry(FlightConfiguration configuration, RocketComponent component, WarningSet warnings) {
		legacyCalculator.checkGeometry(configuration, component, warnings);
		if (component != null && component.isAerodynamic()
				&& !(component instanceof info.openrocket.core.rocketcomponent.SymmetricComponent)
				&& !(component instanceof info.openrocket.core.rocketcomponent.FinSet)
				&& warnings != null) {
			warnings.add("Phase I ROM is using legacy fallback for unsupported aerodynamic component '"
					+ component.getName() + "'.");
		}
	}

	@Override
	protected void voidAerodynamicCache() {
		super.voidAerodynamicCache();
		cachedGeometryFeatures = null;
		lastResult = null;
		computationSnapshots.clear();
	}

	@Override
	public ModID getModID() {
		return ModID.ZERO;
	}

	private RomResult evaluate(FlightConfiguration configuration, FlightConditions conditions, WarningSet warnings) {
		checkCache(configuration);
		WarningSet actualWarnings = warnings != null ? warnings : ignoreWarningSet;

		AerodynamicForces legacyForces = legacyCalculator.getAerodynamicForces(configuration, conditions, actualWarnings).clone();
		GeometryFeatures geometry = cachedGeometryFeatures;
		if (geometry == null) {
			geometry = geometryFeatureExtractor.extract(configuration);
			cachedGeometryFeatures = geometry;
		}
		FlowState flowState = flowStateExtractor.extract(conditions, geometry.getReferenceLength(), plumeState);
		FlowRegime regime = regimeSelector.select(flowState);
		List<PathlineSeed> seeds = pathlineSeeder.createSeeds(geometry, settings);

		List<EdgeState> edgeStates = new ArrayList<>(seeds.size());
		List<BoundaryLayerState> boundaryLayerStates = new ArrayList<>(seeds.size());
		double separationCount = 0.0;
		double stiffnessAccumulator = 0.0;
		int marchingSteps = 0;

		for (PathlineSeed seed : seeds) {
			EdgeState edgeState = outerFlow.reconstruct(seed, geometry, flowState);
			edgeStates.add(edgeState);
			double pathLength = estimatePathLength(seed, geometry);
			BoundaryLayerState boundaryLayerState = boundaryLayerMarcher.march(flowState, edgeState, pathLength,
					Math.max(1e-6, geometry.getReferenceLength()));
			boundaryLayerStates.add(boundaryLayerState);
			if (boundaryLayerState.isSeparated()) {
				separationCount += 1.0;
			}
			stiffnessAccumulator += Math.max(0.0, boundaryLayerState.getStiffnessIndicator());
			marchingSteps += Math.max(1, (int) Math.ceil(pathLength / Math.max(geometry.getReferenceLength() / 10.0, 1e-3)));
		}

		double separationFraction = seeds.isEmpty() ? 0.0 : separationCount / seeds.size();
		AerodynamicCoefficientAssembler assembler = forceIntegrator.integrate(geometry, flowState, seeds, edgeStates,
				boundaryLayerStates);
		AerodynamicForces romForces = deriveRomForces(legacyForces, geometry, flowState, regime, assembler,
				separationFraction);
		AerodynamicConfidence confidence = computeConfidence(flowState, separationFraction,
				seeds.isEmpty() ? 1.0 : stiffnessAccumulator / seeds.size());
		FallbackBlender.BlendResult blendResult = fallbackBlender.blend(legacyForces, romForces, confidence,
				settings.getFallbackMode());

		String notes = buildNotes(regime, confidence, geometry, seeds);
		RomResult result = new RomResult(romForces, blendResult.forces(), geometry, flowState, regime,
				confidence, seeds, marchingSteps, separationFraction, blendResult.fallbackWeight(),
				blendResult.fallbackUsed(), notes);
		lastResult = result;

		if (settings.isDiagnosticsEnabled()) {
			computationSnapshots.add(new RomComputationSnapshot(currentSimulationTimeSeconds, flowState.getMach(),
					flowState.getReynoldsNumber(), flowState.getAngleOfAttackDeg(), flowState.getSideslipDeg(),
					regime.name(), seeds.size(), confidence.getOverallScore(), blendResult.fallbackWeight(),
					separationFraction, legacyForces.getCD(), romForces.getCD(), blendResult.forces().getCD(),
					legacyForces.getCN(), romForces.getCN(), blendResult.forces().getCN(),
					legacyForces.getCm(), romForces.getCm(), blendResult.forces().getCm(), notes));
		}
		return result;
	}

	private AerodynamicForces deriveRomForces(AerodynamicForces legacyForces, GeometryFeatures geometry,
			FlowState flowState, FlowRegime regime, AerodynamicCoefficientAssembler assembler,
			double separationFraction) {
		AerodynamicForces romForces = legacyForces.clone();
		double refArea = Math.max(geometry.getReferenceArea(), 1e-6);
		double finAreaRatio = geometry.getTotalFinPlanformArea() / refArea;
		double slopeMetric = maxAbs(geometry.getAreaSlope()) / refArea;
		double regimeDragGain = switch (regime) {
			case SUBSONIC -> 1.00;
			case TRANSONIC -> 1.10;
			case SUPERSONIC -> 1.25;
			case HYPERSONIC_LEANING -> 1.55;
		};
		double regimeNormalGain = switch (regime) {
			case SUBSONIC -> 1.00;
			case TRANSONIC -> 0.93;
			case SUPERSONIC -> 1.10;
			case HYPERSONIC_LEANING -> 1.18;
		};
		double separationDamping = Math.max(0.55, 1.0 - 0.45 * separationFraction);
		double pressureCD = Math.max(0.0, legacyForces.getPressureCD() * 0.70
				+ regimeDragGain * assembler.getPressureCA()
				+ 0.015 * slopeMetric);
		double frictionCD = Math.max(0.0, legacyForces.getFrictionCD() * 0.60
				+ assembler.getFrictionCA()
				+ 0.004 * (1.0 + finAreaRatio));
		double baseCD = Math.max(0.0, legacyForces.getBaseCD()
				+ BaseDragClosures.coastBaseDrag(flowState.getMach(), geometry.getBaseArea(), refArea, 1.4)
						* (1.0 - 0.6 * flowState.getPlumeState()));
		double overrideCD = legacyForces.getOverrideCD();
		double totalCD = pressureCD + frictionCD + baseCD + overrideCD;
		double cdScale = legacyForces.getCD() > 1e-6 ? totalCD / legacyForces.getCD() : 1.0;

		double cpX = clamp(assembler.getCenterOfPressureX(legacyForces.getCP().getX()), 0.0, geometry.getBodyLength());
		double cnDelta = assembler.getCnDelta() * (0.6 + 0.8 * settings.getMode().getNormalForceGain());
		double cmDelta = assembler.getCmDelta();
		double geometryBias = 0.06 * slopeMetric + 0.03 * finAreaRatio;
		double normalScale = settings.getMode().getNormalForceGain() * regimeNormalGain * separationDamping;
		double cn = legacyForces.getCN() * (0.82 + 0.18 * normalScale)
				+ cnDelta + geometryBias * flowState.getAngleOfAttackRad();
		double cm = legacyForces.getCm() * (0.82 + 0.18 * normalScale)
				+ cmDelta + 0.03 * (cpX - legacyForces.getCP().getX()) / Math.max(geometry.getReferenceLength(), 1e-6)
				* flowState.getAngleOfAttackRad();

		romForces.setPressureCD(pressureCD);
		romForces.setFrictionCD(frictionCD);
		romForces.setBaseCD(baseCD);
		romForces.setOverrideCD(overrideCD);
		romForces.setCD(totalCD);
		romForces.setCDaxial(legacyForces.getCDaxial() * cdScale);
		romForces.setCN(cn);
		romForces.setCm(cm);
		romForces.setCP(new Coordinate(cpX, 0.0, 0.0, Math.max(1e-6, Math.abs(cn))));
		return romForces;
	}

	private AerodynamicConfidence computeConfidence(FlowState flowState, double separationFraction,
			double meanStiffnessIndicator) {
		List<String> reasons = new ArrayList<>();
		double transonicPenalty = regimeSelector.transonicProximity(flowState, settings.getTransonicBandHalfWidth());
		double anglePenalty = clamp01(Math.abs(flowState.getAngleOfAttackDeg()) / settings.getHighAngleDeg());
		double separationPenalty = clamp01(separationFraction / settings.getMaxTrustedSeparationFraction());
		double stiffnessPenalty = clamp01(meanStiffnessIndicator);

		if (transonicPenalty > 0.15) {
			reasons.add("near Mach 1 placeholder branch");
		}
		if (anglePenalty > 0.85) {
			reasons.add("angle of attack exceeds trusted Phase I band");
		}
		if (separationPenalty > 0.10) {
			reasons.add("boundary-layer separation detected");
		}
		if (stiffnessPenalty > 0.25) {
			reasons.add("boundary-layer marcher became stiff");
		}

		double overall = clamp01(1.0
				- 0.45 * transonicPenalty
				- 0.20 * anglePenalty
				- 0.25 * separationPenalty
				- 0.10 * stiffnessPenalty);
		return new AerodynamicConfidence(overall, transonicPenalty, anglePenalty, separationPenalty,
				stiffnessPenalty, reasons);
	}

	private static String buildNotes(FlowRegime regime, AerodynamicConfidence confidence, GeometryFeatures geometry,
			List<PathlineSeed> seeds) {
		StringBuilder builder = new StringBuilder();
		builder.append("regime=").append(regime.name().toLowerCase());
		builder.append(", seeds=").append(seeds.size());
		builder.append(", shoulders=").append(geometry.getShoulderCount());
		if (!confidence.getReasons().isEmpty()) {
			builder.append(", flags=").append(String.join("; ", confidence.getReasons()));
		}
		return builder.toString();
	}

	private static double estimatePathLength(PathlineSeed seed, GeometryFeatures geometry) {
		if (seed.getFamily() == PathlineSeed.SeedFamily.FIN_SURFACE) {
			return Math.max(0.05, geometry.getReferenceLength() * 0.4);
		}
		return Math.max(0.05, geometry.getBodyLength() - seed.getX());
	}

	private static double maxAbs(double[] values) {
		double max = 0.0;
		for (double value : values) {
			max = Math.max(max, Math.abs(value));
		}
		return max;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp01(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return clamp(value, 0.0, 1.0);
	}
}
