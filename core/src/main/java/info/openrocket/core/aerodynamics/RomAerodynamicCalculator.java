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
 * Aerodynamic calculator that delegates all stability derivatives to Barrowman
 * and replaces drag coefficient (Cd) using a precomputed ROM drag surface.
 */
public class RomAerodynamicCalculator extends AbstractAerodynamicCalculator {

	private static final double PLUME_DECAY_TAU_SEC = 0.3;
	private static final double BLEND_MACH_FULL_BARROWMAN = 0.35;
	private static final double BLEND_MACH_FULL_ROM = 0.55;
	private static final double BLEND_RE_FULL_BARROWMAN = 8.0e5;
	private static final double BLEND_RE_FULL_ROM = 1.4e6;

	private final BarrowmanCalculator barrowman;
	private DragSurfaceInterpolator interpolator;
	private AeroSurface4DInterpolator interpolator4D;
	private DragSurface installedSurface;
	private AeroSurface4D installedSurface4D;
	private double plumeDecayState = 0.0;
	private double currentSimulationTimeSeconds = Double.NaN;
	private final List<RomComputationSnapshot> computationSnapshots = new ArrayList<>();

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

		private RomComputationSnapshot(double timeSeconds, double mach, double reynoldsLength,
				double alphaDeg, double thetaQueryDeg, double plumeState, double blendWeight,
				double queryMach, double queryReynoldsLength, double queryAlphaDeg, double queryBetaDeg,
				boolean machClamped, boolean reynoldsClamped, boolean alphaClamped, boolean betaClamped,
				double cdBefore, double cdAfter, double cdPlumeOff, double cdPlumeOn) {
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

	public void updatePlumeState(boolean burning, double dtSeconds) {
		if (burning) {
			plumeDecayState = 1.0;
			return;
		}
		double dt = Math.max(0.0, dtSeconds);
		if (dt == 0.0) {
			plumeDecayState = 0.0;
			return;
		}
		double decay = Math.exp(-dt / PLUME_DECAY_TAU_SEC);
		plumeDecayState *= decay;
		if (plumeDecayState < 1e-6) {
			plumeDecayState = 0.0;
		}
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
	public double getStallMargin() {
		return barrowman.getStallMargin();
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
		applyRomCd(configuration, conditions, total, false);
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
		applyRomCd(configuration, conditions, forces, true);
		return forces;
	}

	private void applyRomCd(FlightConfiguration configuration, FlightConditions conditions,
			AerodynamicForces forces, boolean updateAxial) {
		double rawMach = conditions.getMach();
		double mach = sanitizeNonNegative(rawMach);
		double velocity = sanitizeNonNegative(conditions.getVelocity());
		AtmosphericConditions atm = conditions.getAtmosphericConditions();
		double nu = atm.getKinematicViscosity();
		double length = Math.max(1e-6, configuration.getLengthAerodynamic());
		double reL = (nu > 0.0) ? (velocity * length / nu) : 1e4;
		double alphaDeg = Math.toDegrees(conditions.getAOA());
		RomQueryValues queryValues = buildRomQueryValues(rawMach, reL, alphaDeg, conditions.getTheta());

		double cdPlumeOff;
		double cdPlumeOn;
		if (interpolator4D != null) {
			cdPlumeOff = interpolator4D.queryCdPlumeOff(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg(), queryValues.queryBetaDeg());
			cdPlumeOn = interpolator4D.queryCdPlumeOn(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg(), queryValues.queryBetaDeg());
		} else {
			cdPlumeOff = interpolator.queryCdPlumeOff(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg());
			cdPlumeOn = interpolator.queryCdPlumeOn(queryValues.queryMach(), queryValues.queryReynoldsLength(),
					queryValues.queryAlphaDeg());
		}
		double oldCd = forces.getCD();
		double cdBlended = cdPlumeOff + plumeDecayState * (cdPlumeOn - cdPlumeOff);
		double blendWeight = computeRomBlendWeight(mach, reL);
		double effectiveCd = blendWeight * cdBlended + (1.0 - blendWeight) * oldCd;
		if (!Double.isFinite(effectiveCd) || effectiveCd <= 0.0) {
			effectiveCd = cdBlended;
		}
		forces.setCD(effectiveCd);
		computationSnapshots.add(new RomComputationSnapshot(currentSimulationTimeSeconds, mach, reL,
				alphaDeg, queryValues.thetaQueryDeg(), plumeDecayState, blendWeight,
				queryValues.queryMach(), queryValues.queryReynoldsLength(),
				queryValues.queryAlphaDeg(), queryValues.queryBetaDeg(),
				queryValues.machClamped(), queryValues.reynoldsClamped(),
				queryValues.alphaClamped(), queryValues.betaClamped(),
				oldCd, effectiveCd, cdPlumeOff, cdPlumeOn));

		if (updateAxial && oldCd > 1e-9) {
			double scaledAxial = forces.getCDaxial() * (effectiveCd / oldCd);
			forces.setCDaxial(scaledAxial);
		}
	}

	private RomQueryValues buildRomQueryValues(double mach, double reL, double alphaDeg, double thetaRad) {
		if (interpolator4D != null && installedSurface4D != null) {
			double theta = safeTheta(thetaRad);
			double alphaComponent = alphaDeg * Math.abs(Math.cos(theta));
			double betaComponent = alphaDeg * Math.abs(Math.sin(theta));
			ClampResult machResult = clampToAxis(mach, installedSurface4D.machAxis);
			ClampResult reResult = clampReynoldsLength(reL, installedSurface4D.logReAxis);
			ClampResult alphaResult = clampToAxis(alphaComponent, installedSurface4D.alphaAxis);
			ClampResult betaResult = clampToAxis(betaComponent, installedSurface4D.betaAxis);
			return new RomQueryValues(
					machResult.value,
					reResult.value,
					alphaResult.value,
					betaResult.value,
					Math.toDegrees(theta),
					machResult.clamped,
					reResult.clamped,
					alphaResult.clamped,
					betaResult.clamped);
		}

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
		return Math.max(machWeight, reWeight);
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
