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
	private static final double COAST_ROM_BLEND_MACH_START = 0.12;
	private static final double COAST_ROM_BLEND_MACH_END = 0.32;
	private static final double COAST_ROM_BLEND_RE_START = 2.5e5;
	private static final double COAST_ROM_BLEND_RE_END = 9.0e5;
	private static final double COAST_ROM_BLEND_FLOOR = 0.22;
	private static final double COAST_APOGEE_DRAG_MACH_START = 0.12;
	private static final double COAST_APOGEE_DRAG_MACH_END = 0.48;
	private static final double COAST_APOGEE_DRAG_RE_START = 2.5e5;
	private static final double COAST_APOGEE_DRAG_RE_END = 1.1e6;
	private static final double COAST_APOGEE_DRAG_GAIN_MAX = 0.06;

	private final BarrowmanCalculator barrowman;
	private DragSurfaceInterpolator interpolator;
	private AeroSurface4DInterpolator interpolator4D;
	private DragSurface installedSurface;
	private AeroSurface4D installedSurface4D;
	private double plumeDecayState = 0.0;
	private double currentSimulationTimeSeconds = Double.NaN;
	private final List<RomComputationSnapshot> computationSnapshots = new ArrayList<>();

	public enum RomCoefficientMode {
		DRAG_ONLY_3D,
		HYBRID_4D
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
		private final RomCoefficientMode coefficientMode;

		private RomComputationSnapshot(double timeSeconds, double mach, double reynoldsLength,
				double alphaDeg, double thetaQueryDeg, double plumeState, double blendWeight,
				double queryMach, double queryReynoldsLength, double queryAlphaDeg, double queryBetaDeg,
				boolean machClamped, boolean reynoldsClamped, boolean alphaClamped, boolean betaClamped,
				double cdBefore, double cdAfter, double cdPlumeOff, double cdPlumeOn,
				double queriedCN, double effectiveCN, double queriedCm, double effectiveCm,
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
			this.computationSnapshots.clear();
			return;
		}
		this.installedSurface = surface;
		this.interpolator = new DragSurfaceInterpolator(surface);
		this.interpolator4D = null;
		this.installedSurface4D = null;
		this.computationSnapshots.clear();
	}

	public void installSurface4D(AeroSurface4D surface4D) {
		if (surface4D == null) {
			this.interpolator4D = null;
			this.installedSurface4D = null;
			this.interpolator = null;
			this.installedSurface = null;
			this.computationSnapshots.clear();
			return;
		}
		this.installedSurface4D = surface4D;
		this.interpolator4D = new AeroSurface4DInterpolator(surface4D);
		this.installedSurface = SurfaceAdapter.toBetaZeroDragSurface(surface4D);
		this.interpolator = new DragSurfaceInterpolator(installedSurface);
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

	public void clearComputationSnapshots() {
		this.computationSnapshots.clear();
	}

	public List<RomComputationSnapshot> getComputationSnapshots() {
		return Collections.unmodifiableList(new ArrayList<>(computationSnapshots));
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
		double nu = atm.getKinematicViscosity();
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
					queryResult.betaClamped);
			coefficientMode = RomCoefficientMode.HYBRID_4D;
		} else {
			queryValues = buildRomQueryValues(rawMach, reL, alphaDeg);
			cdPlumeOff = interpolator.queryCdPlumeOff(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg());
			cdPlumeOn = interpolator.queryCdPlumeOn(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg());
		}
		double oldCd = forces.getCD();
		double coastPhaseWeight = 1.0 - plumeDecayState;
		double cdBlended = cdPlumeOff + plumeDecayState * (cdPlumeOn - cdPlumeOff);
		double boostBlendWeight = computeRomBlendWeight(mach, reL);
		double coastBlendWeight = computeCoastRomBlendWeight(mach, reL);
		double blendWeight = blendTowardCoastTrust(boostBlendWeight, coastBlendWeight, coastPhaseWeight);
		if (coefficientMode == RomCoefficientMode.HYBRID_4D) {
			blendWeight *= computeFourDDragTrust(mach);
		}
		double calibratedCd = cdBlended * computeCoastApogeeDragGain(mach, reL, coastPhaseWeight);
		double effectiveCd = blendWeight * calibratedCd + (1.0 - blendWeight) * oldCd;
		if (!Double.isFinite(effectiveCd) || effectiveCd <= 0.0) {
			effectiveCd = calibratedCd;
		}
		forces.setCD(effectiveCd);
		// Preserve Barrowman stability coefficients in production simulation. The current 4D
		// ROM CN/Cm fields are recorded for diagnostics, but they are not yet trusted enough
		// to replace the flight-dynamics path without causing large trajectory regressions.
		computationSnapshots.add(new RomComputationSnapshot(currentSimulationTimeSeconds, mach, reL,
				alphaDeg, queryValues.thetaQueryDeg(), plumeDecayState, blendWeight,
				queryValues.queryMach(), queryValues.queryReynoldsLength(),
				queryValues.queryAlphaDeg(), queryValues.queryBetaDeg(),
				queryValues.machClamped(), queryValues.reynoldsClamped(),
				queryValues.alphaClamped(), queryValues.betaClamped(),
				oldCd, effectiveCd, cdPlumeOff, cdPlumeOn,
				queriedCN, effectiveCN, queriedCm, effectiveCm, coefficientMode));

		if (updateAxial && oldCd > 1e-9) {
			double scaledAxial = forces.getCDaxial() * (effectiveCd / oldCd);
			forces.setCDaxial(scaledAxial);
		}
	}

	private RomQueryValues buildRomQueryValues(double mach, double reL, double alphaDeg) {
		ClampResult machResult = clampToAxis(mach, installedSurface.machAxis);
		ClampResult reResult = clampReynoldsLength(reL, installedSurface.logReAxis);
		ClampResult alphaResult = clampToAxis(alphaDeg, installedSurface.alphaAxis);
		return new RomQueryValues(
				machResult.value,
				reResult.value,
				alphaResult.value,
				0.0,
				0.0,
				machResult.clamped,
				reResult.clamped,
				alphaResult.clamped,
				false);
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
			boolean betaClamped) {
	}

	private record ClampResult(double value, boolean clamped) {
	}

	private static double computeRomBlendWeight(double mach, double reL) {
		double machWeight = smoothStep(BLEND_MACH_FULL_BARROWMAN, BLEND_MACH_FULL_ROM, mach);
		double reWeight = smoothStep(BLEND_RE_FULL_BARROWMAN, BLEND_RE_FULL_ROM, reL);
		// Require both Mach and Reynolds to be inside the trusted ROM band. The previous
		// max() blend let high-Reynolds subsonic boost switch almost completely to the ROM,
		// which over-amplified drag in the low-Mach regime where Barrowman is more stable.
		return machWeight * reWeight;
	}

	private static double computeCoastRomBlendWeight(double mach, double reL) {
		double machWeight = smoothStep(COAST_ROM_BLEND_MACH_START, COAST_ROM_BLEND_MACH_END, mach);
		double reWeight = smoothStep(COAST_ROM_BLEND_RE_START, COAST_ROM_BLEND_RE_END, reL);
		double coastTrust = machWeight * (COAST_ROM_BLEND_FLOOR + (1.0 - COAST_ROM_BLEND_FLOOR) * reWeight);
		return Math.max(computeRomBlendWeight(mach, reL), coastTrust);
	}

	private static double blendTowardCoastTrust(double boostTrust, double coastTrust, double coastPhaseWeight) {
		double coastWeight = sanitizePlumeState(coastPhaseWeight);
		return boostTrust + coastWeight * (coastTrust - boostTrust);
	}

	private static double computeCoastApogeeDragGain(double mach, double reL, double coastPhaseWeight) {
		double machWeight = smoothStep(COAST_APOGEE_DRAG_MACH_START, COAST_APOGEE_DRAG_MACH_END, mach);
		double reWeight = smoothStep(COAST_APOGEE_DRAG_RE_START, COAST_APOGEE_DRAG_RE_END, reL);
		double gainWeight = sanitizePlumeState(coastPhaseWeight) * machWeight * reWeight;
		return 1.0 + COAST_APOGEE_DRAG_GAIN_MAX * gainWeight;
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
