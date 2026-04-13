package info.openrocket.core.aerodynamics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.DragSurfaceInterpolator;
import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4DInterpolator;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.ModID;

/**
 * Aerodynamic calculator that delegates stability derivatives to Barrowman and overlays
 * drag coefficients from a precomputed ROM surface. 4D surfaces may be queried for richer
 * diagnostics, but production force/moment replacement stays on the validated Barrowman path
 * until the ROM stability models are externally verified.
 */
public class RomAerodynamicCalculator extends AbstractAerodynamicCalculator {

	private static final double PLUME_DECAY_TAU_SEC = 0.3;
	private static final double BLEND_MACH_FULL_BARROWMAN = 0.35;
	private static final double BLEND_MACH_FULL_ROM = 0.55;
	private static final double BLEND_RE_FULL_BARROWMAN = 8.0e5;
	private static final double BLEND_RE_FULL_ROM = 1.4e6;
	private static final double BETA_DRAG_LOW_MACH_FADE_IN_START = 0.08;
	private static final double BETA_DRAG_LOW_MACH_FADE_IN_END = 0.18;
	private static final double BETA_DRAG_TRANSONIC_FADE_OUT_START = 0.45;
	private static final double BETA_DRAG_TRANSONIC_FADE_OUT_END = 0.55;
	private static final double FOUR_D_DRAG_TRANSONIC_FADE_START = 0.45;
	private static final double FOUR_D_DRAG_TRANSONIC_FADE_END = 0.55;
	private static final double FOUR_D_DRAG_MIN_TRUST = 0.25;
	private static final double ASCENT_EVENT_TRUST_WINDOW_SEC = 0.18;
	private static final double BOUNDARY_TRANSITION_MIN_TRUST = 0.35;
	private static final double BOOST_CD_RATIO_GUARDRAIL_LOW = 0.65;
	private static final double BOOST_CD_RATIO_GUARDRAIL_HIGH = 1.38;
	private static final double COAST_CD_RATIO_GUARDRAIL_LOW = 0.68;
	private static final double COAST_CD_RATIO_GUARDRAIL_HIGH = 1.78;
	private static final double BOOST_CD_ENVELOPE_MIN = 0.72;
	private static final double BOOST_CD_ENVELOPE_MAX = 1.15;
	private static final double COAST_CD_ENVELOPE_MIN = 0.80;
	private static final double COAST_CD_ENVELOPE_MAX = 1.38;
	private static final double TRANSITION_CD_ENVELOPE_MIN = 0.80;
	private static final double TRANSITION_CD_ENVELOPE_MAX = 1.25;
	private static final double BOOST_DYNAMIC_PRESSURE_MACH_START = 0.22;
	private static final double BOOST_DYNAMIC_PRESSURE_MACH_END = 0.90;
	private static final double BOOST_DYNAMIC_ENVELOPE_MIN = 0.86;
	private static final double BOOST_DYNAMIC_ENVELOPE_MAX = 1.18;
	private static final double COAST_HANDOFF_WINDOW_SEC = 0.75;
	private static final double COAST_HANDOFF_MIN_BLEND = 0.20;
	private static final double COAST_HANDOFF_ENVELOPE_MIN = 0.88;
	private static final double COAST_HANDOFF_ENVELOPE_MAX = 1.16;
	private static final double RESIDUAL_PILOT_BLEND_GAIN = 0.45;
	private static final double RESIDUAL_PILOT_MAX_RATIO = 0.22;
	private static final double RESIDUAL_PILOT_UNCERTAINTY_LOW = 0.25;
	private static final double RESIDUAL_PILOT_UNCERTAINTY_HIGH = 0.85;
	private static final double RESIDUAL_PILOT_MIN_CONFIDENCE = 0.10;
	private static final double MIN_EFFECTIVE_CD = 1.0e-6;

	private final BarrowmanCalculator barrowman;
	private DragSurfaceInterpolator interpolator;
	private AeroSurface4DInterpolator interpolator4D;
	private DragSurface installedSurface;
	private AeroSurface4D installedSurface4D;
	private double plumeDecayState = 0.0;
	private double currentSimulationTimeSeconds = Double.NaN;
	private FlightRegime currentFlightRegime = FlightRegime.PRE_LAUNCH;
	private BoundaryEvent currentBoundaryEvent = BoundaryEvent.NONE;
	private double currentBoundaryEventTimeSeconds = Double.NaN;
	private boolean poweredAscentSegmentFallbackActive;
	private boolean coastAscentSegmentFallbackActive;
	private boolean ascentGuardrailsEnabled = true;
	private boolean residualPilotEnabled = true;
	private final List<RomComputationSnapshot> computationSnapshots = new ArrayList<>();
	private final List<GuardrailActivation> guardrailActivations = new ArrayList<>();

	public enum RomCoefficientMode {
		DRAG_ONLY_3D,
		HYBRID_4D
	}

	public enum FlightRegime {
		PRE_LAUNCH,
		POWERED_ASCENT,
		COAST_ASCENT,
		POST_APOGEE,
		RECOVERY,
		LANDED
	}

	public enum BoundaryEvent {
		NONE,
		BURNOUT,
		APOGEE,
		RECOVERY_DEVICE_DEPLOYMENT
	}

	public enum GuardrailReason {
		NONE,
		NON_FINITE_ROM_DRAG,
		OUTSIDE_ROM_DOMAIN,
		BOOST_DRAG_IMBALANCE,
		COAST_DRAG_IMBALANCE,
		BOUNDARY_TRANSITION_SPIKE
	}

	public static final class GuardrailActivation {
		private final double timeSeconds;
		private final FlightRegime flightRegime;
		private final GuardrailReason reason;
		private final double baselineCd;
		private final double romCd;
		private final double cdRatio;
		private final double domainConfidence;

		private GuardrailActivation(double timeSeconds, FlightRegime flightRegime, GuardrailReason reason,
				double baselineCd, double romCd, double cdRatio, double domainConfidence) {
			this.timeSeconds = timeSeconds;
			this.flightRegime = flightRegime;
			this.reason = reason == null ? GuardrailReason.NONE : reason;
			this.baselineCd = baselineCd;
			this.romCd = romCd;
			this.cdRatio = cdRatio;
			this.domainConfidence = domainConfidence;
		}

		public double getTimeSeconds() {
			return timeSeconds;
		}

		public FlightRegime getFlightRegime() {
			return flightRegime;
		}

		public GuardrailReason getReason() {
			return reason;
		}

		public double getBaselineCd() {
			return baselineCd;
		}

		public double getRomCd() {
			return romCd;
		}

		public double getCdRatio() {
			return cdRatio;
		}

		public double getDomainConfidence() {
			return domainConfidence;
		}
	}

	public static final class RomComputationSnapshot {
		private final double timeSeconds;
		private final double mach;
		private final double reynoldsLength;
		private final double alphaDeg;
		private final double thetaQueryDeg;
		private final double plumeState;
		private final double blendWeight;
		private final double queryMach;
		private final double queryReynoldsLength;
		private final double queryAlphaDeg;
		private final double queryBetaDeg;
		private final boolean machClamped;
		private final boolean reynoldsClamped;
		private final boolean alphaClamped;
		private final boolean betaClamped;
		private final double cdBefore;
		private final double cdAfter;
		private final double cdPlumeOff;
		private final double cdPlumeOn;
		private final double queriedCN;
		private final double effectiveCN;
		private final double queriedCm;
		private final double effectiveCm;
		private final FlightRegime flightRegime;
		private final BoundaryEvent boundaryEvent;
		private final double boundaryEventTimeSeconds;
		private final double domainConfidence;
		private final boolean ascentSegmentFallbackActive;
		private final boolean guardrailTriggered;
		private final GuardrailReason guardrailReason;
		private final double residualPilotCorrection;
		private final double residualPilotConfidence;
		private final RomCoefficientMode coefficientMode;

		private RomComputationSnapshot(double timeSeconds, double mach, double reynoldsLength,
				double alphaDeg, double thetaQueryDeg, double plumeState, double blendWeight,
				double queryMach, double queryReynoldsLength, double queryAlphaDeg, double queryBetaDeg,
				boolean machClamped, boolean reynoldsClamped, boolean alphaClamped, boolean betaClamped,
				double cdBefore, double cdAfter, double cdPlumeOff, double cdPlumeOn,
				double queriedCN, double effectiveCN, double queriedCm, double effectiveCm,
				FlightRegime flightRegime, BoundaryEvent boundaryEvent, double boundaryEventTimeSeconds,
				double domainConfidence, boolean ascentSegmentFallbackActive,
				boolean guardrailTriggered, GuardrailReason guardrailReason,
				double residualPilotCorrection, double residualPilotConfidence,
				RomCoefficientMode coefficientMode) {
			this.timeSeconds = timeSeconds;
			this.mach = mach;
			this.reynoldsLength = reynoldsLength;
			this.alphaDeg = alphaDeg;
			this.thetaQueryDeg = thetaQueryDeg;
			this.plumeState = plumeState;
			this.blendWeight = blendWeight;
			this.queryMach = queryMach;
			this.queryReynoldsLength = queryReynoldsLength;
			this.queryAlphaDeg = queryAlphaDeg;
			this.queryBetaDeg = queryBetaDeg;
			this.machClamped = machClamped;
			this.reynoldsClamped = reynoldsClamped;
			this.alphaClamped = alphaClamped;
			this.betaClamped = betaClamped;
			this.cdBefore = cdBefore;
			this.cdAfter = cdAfter;
			this.cdPlumeOff = cdPlumeOff;
			this.cdPlumeOn = cdPlumeOn;
			this.queriedCN = queriedCN;
			this.effectiveCN = effectiveCN;
			this.queriedCm = queriedCm;
			this.effectiveCm = effectiveCm;
			this.flightRegime = flightRegime;
			this.boundaryEvent = boundaryEvent;
			this.boundaryEventTimeSeconds = boundaryEventTimeSeconds;
			this.domainConfidence = domainConfidence;
			this.ascentSegmentFallbackActive = ascentSegmentFallbackActive;
			this.guardrailTriggered = guardrailTriggered;
			this.guardrailReason = guardrailReason == null ? GuardrailReason.NONE : guardrailReason;
			this.residualPilotCorrection = residualPilotCorrection;
			this.residualPilotConfidence = residualPilotConfidence;
			this.coefficientMode = coefficientMode;
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

		public double getThetaQueryDeg() {
			return thetaQueryDeg;
		}

		public double getPlumeState() {
			return plumeState;
		}

		public double getBlendWeight() {
			return blendWeight;
		}

		public double getQueryMach() {
			return queryMach;
		}

		public double getQueryReynoldsLength() {
			return queryReynoldsLength;
		}

		public double getQueryAlphaDeg() {
			return queryAlphaDeg;
		}

		public double getQueryBetaDeg() {
			return queryBetaDeg;
		}

		public double getBetaDeg() {
			return queryBetaDeg;
		}

		public boolean isMachClamped() {
			return machClamped;
		}

		public boolean isReynoldsClamped() {
			return reynoldsClamped;
		}

		public boolean isReynoldsLengthClamped() {
			return reynoldsClamped;
		}

		public boolean isAlphaClamped() {
			return alphaClamped;
		}

		public boolean isBetaClamped() {
			return betaClamped;
		}

		public boolean isOutsideDomain() {
			return machClamped || reynoldsClamped || alphaClamped || betaClamped;
		}

		public double getCdBefore() {
			return cdBefore;
		}

		public double getCdAfter() {
			return cdAfter;
		}

		public double getCdPlumeOff() {
			return cdPlumeOff;
		}

		public double getCdPlumeOn() {
			return cdPlumeOn;
		}

		public double getQueriedCN() {
			return queriedCN;
		}

		public double getEffectiveCN() {
			return effectiveCN;
		}

		public double getQueriedCm() {
			return queriedCm;
		}

		public double getEffectiveCm() {
			return effectiveCm;
		}

		public FlightRegime getFlightRegime() {
			return flightRegime;
		}

		public BoundaryEvent getBoundaryEvent() {
			return boundaryEvent;
		}

		public double getBoundaryEventTimeSeconds() {
			return boundaryEventTimeSeconds;
		}

		public double getDomainConfidence() {
			return domainConfidence;
		}

		public boolean isAscentSegmentFallbackActive() {
			return ascentSegmentFallbackActive;
		}

		public boolean isGuardrailTriggered() {
			return guardrailTriggered;
		}

		public GuardrailReason getGuardrailReason() {
			return guardrailReason;
		}

		public double getResidualPilotCorrection() {
			return residualPilotCorrection;
		}

		public double getResidualPilotConfidence() {
			return residualPilotConfidence;
		}

		public RomCoefficientMode getCoefficientMode() {
			return coefficientMode;
		}
	}

	public RomAerodynamicCalculator() {
		this(new BarrowmanCalculator());
	}

	private RomAerodynamicCalculator(BarrowmanCalculator barrowman) {
		this.barrowman = barrowman;
	}

	public void installSurface(DragSurface surface) {
		if (surface == null) {
			this.interpolator = null;
			this.installedSurface = null;
			this.interpolator4D = null;
			this.installedSurface4D = null;
			resetAscentSegmentFallback();
			clearGuardrailActivations();
			this.computationSnapshots.clear();
			return;
		}
		this.installedSurface = surface;
		this.interpolator = new DragSurfaceInterpolator(surface);
		this.interpolator4D = null;
		this.installedSurface4D = null;
		resetAscentSegmentFallback();
		clearGuardrailActivations();
		this.computationSnapshots.clear();
	}

	public void installSurface4D(AeroSurface4D surface4D) {
		if (surface4D == null) {
			this.interpolator4D = null;
			this.installedSurface4D = null;
			this.interpolator = null;
			this.installedSurface = null;
			resetAscentSegmentFallback();
			clearGuardrailActivations();
			this.computationSnapshots.clear();
			return;
		}
		this.installedSurface4D = surface4D;
		this.interpolator4D = new AeroSurface4DInterpolator(surface4D);
		this.installedSurface = SurfaceAdapter.toBetaZeroDragSurface(surface4D);
		this.interpolator = new DragSurfaceInterpolator(installedSurface);
		resetAscentSegmentFallback();
		clearGuardrailActivations();
		this.computationSnapshots.clear();
	}

	public DragSurface getInstalledSurface() {
		return installedSurface;
	}

	public boolean hasSurface() {
		return interpolator != null || interpolator4D != null;
	}

	public void setCurrentSimulationTime(double timeSeconds) {
		this.currentSimulationTimeSeconds = timeSeconds;
	}

	public FlightRegime getCurrentFlightRegime() {
		return currentFlightRegime;
	}

	public void setFlightRegime(FlightRegime flightRegime) {
		FlightRegime nextRegime = flightRegime == null ? FlightRegime.PRE_LAUNCH : flightRegime;
		if (nextRegime == FlightRegime.PRE_LAUNCH && currentFlightRegime != FlightRegime.PRE_LAUNCH) {
			resetAscentSegmentFallback();
			clearGuardrailActivations();
		}
		this.currentFlightRegime = nextRegime;
	}

	public BoundaryEvent getCurrentBoundaryEvent() {
		return currentBoundaryEvent;
	}

	public double getCurrentBoundaryEventTimeSeconds() {
		return currentBoundaryEventTimeSeconds;
	}

	public void setBoundaryEvent(BoundaryEvent boundaryEvent, double boundaryEventTimeSeconds) {
		this.currentBoundaryEvent = boundaryEvent == null ? BoundaryEvent.NONE : boundaryEvent;
		this.currentBoundaryEventTimeSeconds = Double.isFinite(boundaryEventTimeSeconds)
				? boundaryEventTimeSeconds
				: Double.NaN;
	}

	public void clearComputationSnapshots() {
		this.computationSnapshots.clear();
	}

	public void clearGuardrailActivations() {
		this.guardrailActivations.clear();
	}

	public List<RomComputationSnapshot> getComputationSnapshots() {
		return Collections.unmodifiableList(new ArrayList<>(computationSnapshots));
	}

	public List<GuardrailActivation> getGuardrailActivations() {
		return Collections.unmodifiableList(new ArrayList<>(guardrailActivations));
	}

	public void resetAscentSegmentFallback() {
		this.poweredAscentSegmentFallbackActive = false;
		this.coastAscentSegmentFallbackActive = false;
	}

	public boolean isPoweredAscentSegmentFallbackActive() {
		return poweredAscentSegmentFallbackActive;
	}

	public boolean isCoastAscentSegmentFallbackActive() {
		return coastAscentSegmentFallbackActive;
	}

	public boolean isAscentGuardrailsEnabled() {
		return ascentGuardrailsEnabled;
	}

	public void setAscentGuardrailsEnabled(boolean ascentGuardrailsEnabled) {
		this.ascentGuardrailsEnabled = ascentGuardrailsEnabled;
	}

	public boolean isResidualPilotEnabled() {
		return residualPilotEnabled;
	}

	public void setResidualPilotEnabled(boolean residualPilotEnabled) {
		this.residualPilotEnabled = residualPilotEnabled;
	}

	public double getPlumeState() {
		return plumeDecayState;
	}

	public void setPlumeState(double plumeState) {
		this.plumeDecayState = sanitizePlumeState(plumeState);
	}

	public static double evolvePlumeState(double initialState, boolean burning, double dtSeconds) {
		if (burning) {
			return 1.0;
		}
		double plumeState = sanitizePlumeState(initialState);
		double dt = Math.max(0.0, dtSeconds);
		if (dt == 0.0) {
			return plumeState;
		}
		double decay = Math.exp(-dt / PLUME_DECAY_TAU_SEC);
		double evolved = plumeState * decay;
		return (evolved < 1e-6) ? 0.0 : evolved;
	}

	public void updatePlumeState(boolean burning, double dtSeconds) {
		plumeDecayState = evolvePlumeState(plumeDecayState, burning, dtSeconds);
	}

	@Override
	public RomAerodynamicCalculator newInstance() {
		RomAerodynamicCalculator copy = new RomAerodynamicCalculator(barrowman.newInstance());
		if (installedSurface4D != null) {
			copy.installSurface4D(installedSurface4D);
		} else {
			copy.installSurface(installedSurface);
		}
		copy.plumeDecayState = plumeDecayState;
		copy.currentSimulationTimeSeconds = currentSimulationTimeSeconds;
		copy.currentFlightRegime = currentFlightRegime;
		copy.currentBoundaryEvent = currentBoundaryEvent;
		copy.currentBoundaryEventTimeSeconds = currentBoundaryEventTimeSeconds;
		copy.poweredAscentSegmentFallbackActive = poweredAscentSegmentFallbackActive;
		copy.coastAscentSegmentFallbackActive = coastAscentSegmentFallbackActive;
		copy.ascentGuardrailsEnabled = ascentGuardrailsEnabled;
		copy.residualPilotEnabled = residualPilotEnabled;
		copy.guardrailActivations.addAll(guardrailActivations);
		return copy;
	}

	@Override
	public double getStallAngle() {
		return barrowman.getStallAngle();
	}

	@Override
	public CoordinateIF getCP(FlightConfiguration configuration, FlightConditions conditions, WarningSet warnings) {
		return barrowman.getCP(configuration, conditions, warnings);
	}

	@Override
	public Map<RocketComponent, AerodynamicForces> getForceAnalysis(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		Map<RocketComponent, AerodynamicForces> analysis = barrowman.getForceAnalysis(configuration, conditions, warnings);
		if (!hasSurface() || analysis == null) {
			return analysis;
		}

		AerodynamicForces total = analysis.get(configuration.getRocket());
		if (total == null) {
			return analysis;
		}
		applyRomCoefficients(configuration, conditions, total, false);
		return analysis;
	}

	@Override
	public AerodynamicForces getAerodynamicForces(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		checkCache(configuration);

		AerodynamicForces forces = barrowman.getAerodynamicForces(configuration, conditions, warnings);
		if (!hasSurface()) {
			return forces;
		}
		applyRomCoefficients(configuration, conditions, forces, true);
		return forces;
	}

	private void applyRomCoefficients(FlightConfiguration configuration, FlightConditions conditions,
			AerodynamicForces forces, boolean updateAxial) {
		double rawMach = conditions.getMach();
		double mach = sanitizeNonNegative(rawMach);
		double velocity = sanitizeNonNegative(conditions.getVelocity());
		AtmosphericConditions atm = conditions.getAtmosphericConditions();
		double nu = (atm == null) ? Double.NaN : atm.getKinematicViscosity();
		double length = Math.max(1e-6, configuration.getLengthAerodynamic());
		double reL = (nu > 0.0) ? (velocity * length / nu) : 1e4;
		double alphaDeg = Math.toDegrees(conditions.getAOA());
		double cdPlumeOff;
		double cdPlumeOn;
		double queriedCN = Double.NaN;
		double queriedCm = Double.NaN;
		double effectiveCN = forces.getCN();
		double effectiveCm = forces.getCm();
		RomCoefficientMode coefficientMode = RomCoefficientMode.DRAG_ONLY_3D;
		RomQueryValues queryValues;
		if (interpolator4D != null) {
			double theta = safeTheta(conditions.getTheta());
			double alphaComponent = alphaDeg * Math.abs(Math.cos(theta));
			double betaComponent = alphaDeg * Math.abs(Math.sin(theta));
			AeroSurface4DInterpolator.QueryResult queryResult = interpolator4D.query(rawMach, reL, alphaComponent, betaComponent);
			AeroSurface4DInterpolator.QueryResult betaZeroQuery = queryResult;
			if (betaComponent > 1e-9) {
				betaZeroQuery = interpolator4D.query(rawMach, reL, alphaDeg, 0.0);
			}
			double betaDragWeight = computeFourDBetaDragWeight(mach);
			cdPlumeOff = blendFourDBetaIncrement(betaZeroQuery.cdPlumeOff, queryResult.cdPlumeOff, betaDragWeight);
			cdPlumeOn = blendFourDBetaIncrement(betaZeroQuery.cdPlumeOn, queryResult.cdPlumeOn, betaDragWeight);
			queriedCN = queryResult.CN;
			queriedCm = queryResult.Cm;
			queryValues = new RomQueryValues(
					queryResult.usedMach,
					Math.pow(10.0, queryResult.usedLogRe),
					queryResult.usedAlphaDeg,
					queryResult.usedBetaDeg,
					Math.toDegrees(theta),
					queryResult.machClamped,
					queryResult.reynoldsClamped,
					queryResult.alphaClamped,
					queryResult.betaClamped,
					computeDomainConfidence(
							queryResult.usedMach,
							installedSurface4D.machAxis,
							queryResult.machClamped,
							queryResult.usedLogRe,
							installedSurface4D.logReAxis,
							queryResult.reynoldsClamped,
							queryResult.usedAlphaDeg,
							installedSurface4D.alphaAxis,
							queryResult.alphaClamped,
							queryResult.usedBetaDeg,
							installedSurface4D.betaAxis,
							queryResult.betaClamped));
			coefficientMode = RomCoefficientMode.HYBRID_4D;
		} else {
			queryValues = buildRomQueryValues(rawMach, reL, alphaDeg);
			cdPlumeOff = interpolator.queryCdPlumeOff(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg());
			cdPlumeOn = interpolator.queryCdPlumeOn(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg());
		}
		double oldCd = forces.getCD();
		double cdBlended = cdPlumeOff + plumeDecayState * (cdPlumeOn - cdPlumeOff);
		double romCandidateCd = stabilizeDragCoefficient(cdBlended, oldCd, oldCd);
		double boundaryTrust = computeBoundaryTransitionTrust(
				currentFlightRegime,
				currentBoundaryEvent,
				currentBoundaryEventTimeSeconds,
				currentSimulationTimeSeconds);
		double blendWeight = computeRomBlendWeight(mach, reL);
		if (coefficientMode == RomCoefficientMode.HYBRID_4D) {
			blendWeight *= computeFourDDragTrust(mach);
		}
		blendWeight = tuneAscentBlendWeight(blendWeight, currentFlightRegime, mach, queryValues.domainConfidence(), boundaryTrust);
		blendWeight = applyCoastHandoffBlendWeight(blendWeight, currentFlightRegime);

		boolean segmentFallbackActive = isSegmentFallbackActive(currentFlightRegime);
		GuardrailReason guardrailReason = GuardrailReason.NONE;
		boolean guardrailTriggered = false;
		if (!segmentFallbackActive && ascentGuardrailsEnabled) {
			guardrailReason = assessAscentGuardrail(currentFlightRegime, oldCd, romCandidateCd, queryValues, boundaryTrust);
			if (guardrailReason != GuardrailReason.NONE) {
				activateAscentSegmentFallback(currentFlightRegime);
				segmentFallbackActive = isSegmentFallbackActive(currentFlightRegime);
				guardrailTriggered = segmentFallbackActive;
				recordGuardrailActivation(guardrailReason, oldCd, romCandidateCd, queryValues.domainConfidence());
			}
		}

		double effectiveBlendWeight = segmentFallbackActive ? 0.0 : blendWeight;
		double effectiveCd = segmentFallbackActive
				? stabilizeDragCoefficient(oldCd, romCandidateCd, oldCd)
				: effectiveBlendWeight * romCandidateCd + (1.0 - effectiveBlendWeight) * oldCd;
		if (!segmentFallbackActive) {
			effectiveCd = tuneAscentCdEnvelope(
					effectiveCd,
					oldCd,
					currentFlightRegime,
					boundaryTrust,
					mach,
					queryValues.domainConfidence());
		}
		ResidualPilotResult residualPilotResult = applyResidualPilot(
				effectiveCd,
				oldCd,
				mach,
				queryValues,
				boundaryTrust,
				segmentFallbackActive);
		effectiveCd = stabilizeDragCoefficient(residualPilotResult.cd(), romCandidateCd, oldCd);
		forces.setCD(effectiveCd);
		// Preserve Barrowman stability coefficients in production simulation. The current 4D
		// ROM CN/Cm fields are recorded for diagnostics, but they are not yet trusted enough
		// to replace the flight-dynamics path without causing large trajectory regressions.
		computationSnapshots.add(new RomComputationSnapshot(currentSimulationTimeSeconds, mach, reL,
				alphaDeg, queryValues.thetaQueryDeg(), plumeDecayState, effectiveBlendWeight,
				queryValues.queryMach(), queryValues.queryReynoldsLength(),
				queryValues.queryAlphaDeg(), queryValues.queryBetaDeg(),
				queryValues.machClamped(), queryValues.reynoldsClamped(),
				queryValues.alphaClamped(), queryValues.betaClamped(),
				oldCd, effectiveCd, cdPlumeOff, cdPlumeOn,
				queriedCN, effectiveCN, queriedCm, effectiveCm,
				currentFlightRegime, currentBoundaryEvent, currentBoundaryEventTimeSeconds,
				queryValues.domainConfidence(), segmentFallbackActive, guardrailTriggered,
				guardrailReason, residualPilotResult.appliedCorrection(), residualPilotResult.confidence(),
				coefficientMode));

		if (updateAxial) {
			forces.setCDaxial(stabilizeAxialDrag(forces.getCDaxial(), oldCd, effectiveCd));
		}
	}

	private RomQueryValues buildRomQueryValues(double mach, double reL, double alphaDeg) {
		ClampResult machResult = clampToAxis(mach, installedSurface.machAxis);
		ClampResult reResult = clampReynoldsLength(reL, installedSurface.logReAxis);
		ClampResult alphaResult = clampToAxis(alphaDeg, installedSurface.alphaAxis);
		double logRe = Math.log10(Math.max(reResult.value, 1e4));
		return new RomQueryValues(
				machResult.value,
				reResult.value,
				alphaResult.value,
				0.0,
				0.0,
				machResult.clamped,
				reResult.clamped,
				alphaResult.clamped,
				false,
				computeDomainConfidence(
						machResult.value,
						installedSurface.machAxis,
						machResult.clamped,
						logRe,
						installedSurface.logReAxis,
						reResult.clamped,
						alphaResult.value,
						installedSurface.alphaAxis,
						alphaResult.clamped,
						0.0,
						null,
						false));
	}

	private static double computeBoundaryTransitionTrust(FlightRegime flightRegime,
			BoundaryEvent boundaryEvent,
			double boundaryEventTimeSeconds,
			double simulationTimeSeconds) {
		if (!isAscentRegime(flightRegime)) {
			return 1.0;
		}
		if (boundaryEvent != BoundaryEvent.BURNOUT && boundaryEvent != BoundaryEvent.APOGEE) {
			return 1.0;
		}
		if (!Double.isFinite(boundaryEventTimeSeconds) || !Double.isFinite(simulationTimeSeconds)) {
			return 1.0;
		}
		double timeToBoundary = boundaryEventTimeSeconds - simulationTimeSeconds;
		if (timeToBoundary < 0.0 || timeToBoundary >= ASCENT_EVENT_TRUST_WINDOW_SEC) {
			return 1.0;
		}
		double proximity = 1.0 - smoothStep(0.0, ASCENT_EVENT_TRUST_WINDOW_SEC, timeToBoundary);
		return 1.0 - (1.0 - BOUNDARY_TRANSITION_MIN_TRUST) * proximity;
	}

	private static double tuneAscentBlendWeight(double blendWeight,
			FlightRegime flightRegime,
			double mach,
			double domainConfidence,
			double boundaryTrust) {
		if (!isAscentRegime(flightRegime)) {
			return clampUnit(blendWeight);
		}
		double regimeScale = flightRegime == FlightRegime.POWERED_ASCENT ? 0.92 : 0.98;
		double confidenceScale = 0.55 + 0.45 * clampUnit(domainConfidence);
		double tuned = blendWeight * regimeScale * confidenceScale * clampUnit(boundaryTrust);
		if (flightRegime == FlightRegime.POWERED_ASCENT) {
			double highDynamicPressure = smoothStep(
					BOOST_DYNAMIC_PRESSURE_MACH_START,
					BOOST_DYNAMIC_PRESSURE_MACH_END,
					mach);
			double confidenceRisk = 1.0 - clampUnit(domainConfidence);
			double dynamicPressureScale = 1.0 - 0.22 * highDynamicPressure * (0.35 + 0.65 * confidenceRisk);
			tuned *= clamp(dynamicPressureScale, 0.60, 1.0);
		}
		return clampUnit(tuned);
	}

	private double tuneAscentCdEnvelope(double candidateCd,
			double baselineCd,
			FlightRegime flightRegime,
			double boundaryTrust,
			double mach,
			double domainConfidence) {
		if (!isAscentRegime(flightRegime) || !Double.isFinite(baselineCd) || baselineCd <= MIN_EFFECTIVE_CD) {
			return candidateCd;
		}
		double minRatio = (flightRegime == FlightRegime.POWERED_ASCENT)
				? BOOST_CD_ENVELOPE_MIN
				: COAST_CD_ENVELOPE_MIN;
		double maxRatio = (flightRegime == FlightRegime.POWERED_ASCENT)
				? BOOST_CD_ENVELOPE_MAX
				: COAST_CD_ENVELOPE_MAX;
		double transitionBlend = 1.0 - clampUnit(boundaryTrust);
		minRatio = lerp(minRatio, TRANSITION_CD_ENVELOPE_MIN, transitionBlend);
		maxRatio = lerp(maxRatio, TRANSITION_CD_ENVELOPE_MAX, transitionBlend);
		if (flightRegime == FlightRegime.POWERED_ASCENT) {
			double highDynamicPressure = smoothStep(
					BOOST_DYNAMIC_PRESSURE_MACH_START,
					BOOST_DYNAMIC_PRESSURE_MACH_END,
					mach);
			double confidenceRisk = 1.0 - clampUnit(domainConfidence);
			double compression = clampUnit(0.55 * highDynamicPressure + 0.35 * confidenceRisk);
			minRatio = lerp(minRatio, BOOST_DYNAMIC_ENVELOPE_MIN, compression);
			maxRatio = lerp(maxRatio, BOOST_DYNAMIC_ENVELOPE_MAX, compression);
		}
		if (flightRegime == FlightRegime.COAST_ASCENT && shouldApplyCoastHandoffDamping()) {
			double elapsed = Math.max(0.0, currentSimulationTimeSeconds - currentBoundaryEventTimeSeconds);
			double recovery = smoothStep(0.0, COAST_HANDOFF_WINDOW_SEC, elapsed);
			minRatio = lerp(COAST_HANDOFF_ENVELOPE_MIN, minRatio, recovery);
			maxRatio = lerp(COAST_HANDOFF_ENVELOPE_MAX, maxRatio, recovery);
		}
		double minCd = baselineCd * minRatio;
		double maxCd = baselineCd * maxRatio;
		return clamp(candidateCd, minCd, maxCd);
	}

	private double applyCoastHandoffBlendWeight(double blendWeight,
			FlightRegime flightRegime) {
		if (flightRegime != FlightRegime.COAST_ASCENT || !shouldApplyCoastHandoffDamping()) {
			return clampUnit(blendWeight);
		}
		double elapsed = Math.max(0.0, currentSimulationTimeSeconds - currentBoundaryEventTimeSeconds);
		double recovery = smoothStep(0.0, COAST_HANDOFF_WINDOW_SEC, elapsed);
		double handoffScale = lerp(COAST_HANDOFF_MIN_BLEND, 1.0, recovery);
		return clampUnit(blendWeight * handoffScale);
	}

	private boolean shouldApplyCoastHandoffDamping() {
		if (!poweredAscentSegmentFallbackActive || !Double.isFinite(currentBoundaryEventTimeSeconds)
				|| !Double.isFinite(currentSimulationTimeSeconds)) {
			return false;
		}
		if (currentFlightRegime != FlightRegime.COAST_ASCENT || currentBoundaryEvent != BoundaryEvent.BURNOUT) {
			return false;
		}
		double elapsed = currentSimulationTimeSeconds - currentBoundaryEventTimeSeconds;
		if (!Double.isFinite(elapsed) || elapsed < 0.0 || elapsed > COAST_HANDOFF_WINDOW_SEC) {
			return false;
		}
		return true;
	}

	private GuardrailReason assessAscentGuardrail(FlightRegime flightRegime,
			double baselineCd,
			double romCandidateCd,
			RomQueryValues queryValues,
			double boundaryTrust) {
		if (!isAscentRegime(flightRegime)) {
			return GuardrailReason.NONE;
		}
		if (!Double.isFinite(romCandidateCd)) {
			return GuardrailReason.NON_FINITE_ROM_DRAG;
		}
		if (queryValues.isOutsideDomain() && queryValues.domainConfidence() < 0.20) {
			return GuardrailReason.OUTSIDE_ROM_DOMAIN;
		}
		if (!Double.isFinite(baselineCd) || baselineCd <= MIN_EFFECTIVE_CD) {
			return GuardrailReason.NONE;
		}
		double ratio = romCandidateCd / baselineCd;
		if (!Double.isFinite(ratio) || ratio <= 0.0) {
			return GuardrailReason.NON_FINITE_ROM_DRAG;
		}
		if (boundaryTrust < 0.60
				&& (ratio < TRANSITION_CD_ENVELOPE_MIN || ratio > TRANSITION_CD_ENVELOPE_MAX)) {
			return GuardrailReason.BOUNDARY_TRANSITION_SPIKE;
		}
		if (flightRegime == FlightRegime.POWERED_ASCENT
				&& (ratio < BOOST_CD_RATIO_GUARDRAIL_LOW || ratio > BOOST_CD_RATIO_GUARDRAIL_HIGH)) {
			return GuardrailReason.BOOST_DRAG_IMBALANCE;
		}
		if (flightRegime == FlightRegime.COAST_ASCENT
				&& (ratio < COAST_CD_RATIO_GUARDRAIL_LOW || ratio > COAST_CD_RATIO_GUARDRAIL_HIGH)) {
			return GuardrailReason.COAST_DRAG_IMBALANCE;
		}
		return GuardrailReason.NONE;
	}

	private void activateAscentSegmentFallback(FlightRegime flightRegime) {
		if (flightRegime == FlightRegime.POWERED_ASCENT) {
			poweredAscentSegmentFallbackActive = true;
		} else if (flightRegime == FlightRegime.COAST_ASCENT) {
			coastAscentSegmentFallbackActive = true;
		}
	}

	private boolean isSegmentFallbackActive(FlightRegime flightRegime) {
		if (flightRegime == FlightRegime.POWERED_ASCENT) {
			return poweredAscentSegmentFallbackActive;
		}
		if (flightRegime == FlightRegime.COAST_ASCENT) {
			return coastAscentSegmentFallbackActive;
		}
		return false;
	}

	private void recordGuardrailActivation(GuardrailReason reason,
			double baselineCd,
			double romCd,
			double domainConfidence) {
		double ratio = (Double.isFinite(baselineCd) && baselineCd > MIN_EFFECTIVE_CD)
				? romCd / baselineCd
				: Double.NaN;
		guardrailActivations.add(new GuardrailActivation(
				currentSimulationTimeSeconds,
				currentFlightRegime,
				reason,
				baselineCd,
				romCd,
				ratio,
				domainConfidence));
	}

	private ResidualPilotResult applyResidualPilot(double candidateCd,
			double baselineCd,
			double mach,
			RomQueryValues queryValues,
			double boundaryTrust,
			boolean segmentFallbackActive) {
		if (!residualPilotEnabled || segmentFallbackActive || !isAscentRegime(currentFlightRegime)) {
			return ResidualPilotResult.notApplied(candidateCd);
		}
		if (!Double.isFinite(candidateCd) || !Double.isFinite(baselineCd)) {
			return ResidualPilotResult.notApplied(stabilizeDragCoefficient(candidateCd, baselineCd, candidateCd));
		}
		double rawCorrection = RESIDUAL_PILOT_BLEND_GAIN * (baselineCd - candidateCd);
		double correctionLimit = RESIDUAL_PILOT_MAX_RATIO * Math.max(Math.max(candidateCd, baselineCd), MIN_EFFECTIVE_CD);
		double boundedCorrection = clamp(rawCorrection, -correctionLimit, correctionLimit);
		double uncertainty = computeResidualPilotUncertainty(mach, queryValues, boundaryTrust);
		double confidence = 1.0 - smoothStep(RESIDUAL_PILOT_UNCERTAINTY_LOW, RESIDUAL_PILOT_UNCERTAINTY_HIGH, uncertainty);
		if (confidence < RESIDUAL_PILOT_MIN_CONFIDENCE) {
			confidence = 0.0;
		}
		double appliedCorrection = confidence * boundedCorrection;
		double correctedCd = stabilizeDragCoefficient(candidateCd + appliedCorrection, candidateCd, baselineCd);
		return new ResidualPilotResult(correctedCd, appliedCorrection, confidence);
	}

	private static double computeResidualPilotUncertainty(double mach,
			RomQueryValues queryValues,
			double boundaryTrust) {
		double domainRisk = 1.0 - clampUnit(queryValues.domainConfidence());
		double transitionRisk = 1.0 - clampUnit(boundaryTrust);
		double transonicRisk = smoothStep(0.38, 0.70, mach);
		double uncertainty = 0.55 * domainRisk + 0.25 * transitionRisk + 0.20 * transonicRisk;
		if (queryValues.isOutsideDomain()) {
			uncertainty = Math.max(uncertainty, 0.95);
		}
		return clampUnit(uncertainty);
	}

	private static boolean isAscentRegime(FlightRegime flightRegime) {
		return flightRegime == FlightRegime.POWERED_ASCENT || flightRegime == FlightRegime.COAST_ASCENT;
	}

	private static double computeDomainConfidence(double mach,
			double[] machAxis,
			boolean machClamped,
			double logRe,
			double[] logReAxis,
			boolean reynoldsClamped,
			double alphaDeg,
			double[] alphaAxis,
			boolean alphaClamped,
			double betaDeg,
			double[] betaAxis,
			boolean betaClamped) {
		double machConfidence = computeAxisConfidence(mach, machAxis, machClamped);
		double reConfidence = computeAxisConfidence(logRe, logReAxis, reynoldsClamped);
		double alphaConfidence = computeAxisConfidence(alphaDeg, alphaAxis, alphaClamped);
		double betaConfidence = (betaAxis == null || betaAxis.length == 0)
				? 1.0
				: computeAxisConfidence(betaDeg, betaAxis, betaClamped);
		return clampUnit(Math.min(Math.min(machConfidence, reConfidence), Math.min(alphaConfidence, betaConfidence)));
	}

	private static double computeAxisConfidence(double value, double[] axis, boolean clamped) {
		if (clamped || !Double.isFinite(value) || axis == null || axis.length == 0) {
			return 0.0;
		}
		if (axis.length == 1) {
			return 1.0;
		}
		double min = axis[0];
		double max = axis[axis.length - 1];
		double range = max - min;
		if (!(range > 0.0)) {
			return 1.0;
		}
		double edgeDistance = Math.min(value - min, max - value);
		if (edgeDistance <= 0.0) {
			return 0.0;
		}
		return clampUnit((2.0 * edgeDistance) / range);
	}

	private static double clamp(double value, double min, double max) {
		if (!Double.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	private static double clampUnit(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static double lerp(double a, double b, double t) {
		double blend = clampUnit(t);
		return a + blend * (b - a);
	}

	private static double sanitizeNonNegative(double value) {
		return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
	}

	private static double sanitizePlumeState(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static double safeTheta(double thetaRad) {
		return Double.isFinite(thetaRad) ? thetaRad : 0.0;
	}

	private static ClampResult clampReynoldsLength(double reL, double[] logReAxis) {
		if (!Double.isFinite(reL)) {
			double minRe = Math.pow(10.0, logReAxis[0]);
			return new ClampResult(minRe, true);
		}
		double safeRe = Math.max(reL, 1e4);
		double logRe = Math.log10(safeRe);
		ClampResult logResult = clampToAxis(logRe, logReAxis);
		double clampedRe = Math.pow(10.0, logResult.value);
		boolean clamped = logResult.clamped || Double.compare(safeRe, reL) != 0;
		return new ClampResult(clampedRe, clamped);
	}

	private static ClampResult clampToAxis(double value, double[] axis) {
		if (axis == null || axis.length == 0) {
			return new ClampResult(value, false);
		}
		if (!Double.isFinite(value)) {
			return new ClampResult(axis[0], true);
		}
		double min = axis[0];
		double max = axis[axis.length - 1];
		double clamped = Math.max(min, Math.min(max, value));
		return new ClampResult(clamped, Double.compare(clamped, value) != 0);
	}

	private record RomQueryValues(
			double queryMach,
			double queryReynoldsLength,
			double queryAlphaDeg,
			double queryBetaDeg,
			double thetaQueryDeg,
			boolean machClamped,
			boolean reynoldsClamped,
			boolean alphaClamped,
			boolean betaClamped,
			double domainConfidence) {

		boolean isOutsideDomain() {
			return machClamped || reynoldsClamped || alphaClamped || betaClamped;
		}
	}

	private record ResidualPilotResult(double cd, double appliedCorrection, double confidence) {
		private static ResidualPilotResult notApplied(double cd) {
			return new ResidualPilotResult(cd, 0.0, 0.0);
		}
	}

	private record ClampResult(double value, boolean clamped) {
	}

	static double stabilizeDragCoefficient(double candidate, double fallbackFromSurface, double fallbackFromCurrent) {
		if (Double.isFinite(candidate) && candidate > MIN_EFFECTIVE_CD) {
			return candidate;
		}
		if (Double.isFinite(fallbackFromSurface) && fallbackFromSurface > MIN_EFFECTIVE_CD) {
			return fallbackFromSurface;
		}
		if (Double.isFinite(fallbackFromCurrent) && fallbackFromCurrent > MIN_EFFECTIVE_CD) {
			return fallbackFromCurrent;
		}
		return MIN_EFFECTIVE_CD;
	}

	static double stabilizeAxialDrag(double currentAxialCd, double oldCd, double effectiveCd) {
		double stableEffectiveCd = stabilizeDragCoefficient(effectiveCd, oldCd, currentAxialCd);
		if (!Double.isFinite(currentAxialCd)) {
			return stableEffectiveCd;
		}
		if (Double.isFinite(oldCd) && oldCd > MIN_EFFECTIVE_CD) {
			return currentAxialCd * (stableEffectiveCd / oldCd);
		}
		if (Math.abs(currentAxialCd) > MIN_EFFECTIVE_CD) {
			return Math.copySign(stableEffectiveCd, currentAxialCd);
		}
		return stableEffectiveCd;
	}

	private static double computeRomBlendWeight(double mach, double reL) {
		double machWeight = smoothStep(BLEND_MACH_FULL_BARROWMAN, BLEND_MACH_FULL_ROM, mach);
		double reWeight = smoothStep(BLEND_RE_FULL_BARROWMAN, BLEND_RE_FULL_ROM, reL);
		return Math.max(machWeight, reWeight);
	}

	private static double computeFourDBetaDragWeight(double mach) {
		double lowMachTrust = smoothStep(BETA_DRAG_LOW_MACH_FADE_IN_START, BETA_DRAG_LOW_MACH_FADE_IN_END, mach);
		double transonicTrust = 1.0 - smoothStep(BETA_DRAG_TRANSONIC_FADE_OUT_START,
				BETA_DRAG_TRANSONIC_FADE_OUT_END, mach);
		return lowMachTrust * transonicTrust;
	}

	private static double computeFourDDragTrust(double mach) {
		double fade = smoothStep(FOUR_D_DRAG_TRANSONIC_FADE_START, FOUR_D_DRAG_TRANSONIC_FADE_END, mach);
		return 1.0 - (1.0 - FOUR_D_DRAG_MIN_TRUST) * fade;
	}

	private static double blendFourDBetaIncrement(double betaZeroCd, double fullFourDCd, double betaDragWeight) {
		if (!Double.isFinite(betaZeroCd)) {
			return fullFourDCd;
		}
		if (!Double.isFinite(fullFourDCd)) {
			return betaZeroCd;
		}
		return betaZeroCd + betaDragWeight * (fullFourDCd - betaZeroCd);
	}

	private static double smoothStep(double min, double max, double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		if (value <= min) {
			return 0.0;
		}
		if (value >= max) {
			return 1.0;
		}
		double t = (value - min) / (max - min);
		return t * t * (3.0 - 2.0 * t);
	}

	@Override
	public void checkGeometry(FlightConfiguration configuration, RocketComponent component, WarningSet warnings) {
		barrowman.checkGeometry(configuration, component, warnings);
	}

	@Override
	protected void voidAerodynamicCache() {
		super.voidAerodynamicCache();
		barrowman.voidAerodynamicCache();
	}

	@Override
	public ModID getModID() {
		return ModID.ZERO;
	}
}
