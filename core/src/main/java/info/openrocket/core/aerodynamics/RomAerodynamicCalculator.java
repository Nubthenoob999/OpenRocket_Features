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
	private double plumeDecayState = 0.0;  // 0=off, 1=on
	private double currentSimulationTimeSeconds = Double.NaN;
	private final List<RomComputationSnapshot> computationSnapshots = new ArrayList<>();

	public static final class RomComputationSnapshot {
		private final double timeSeconds;
		private final double mach;
		private final double reynoldsLength;
		private final double alphaDeg;
		private final double plumeState;
		private final double cdBefore;
		private final double cdAfter;
		private final double cdPlumeOff;
		private final double cdPlumeOn;

		private RomComputationSnapshot(double timeSeconds, double mach, double reynoldsLength,
					double alphaDeg, double plumeState, double cdBefore, double cdAfter,
					double cdPlumeOff, double cdPlumeOn) {
			this.timeSeconds = timeSeconds;
			this.mach = mach;
			this.reynoldsLength = reynoldsLength;
			this.alphaDeg = alphaDeg;
			this.plumeState = plumeState;
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

		public double getPlumeState() {
			return plumeState;
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

	/**
	 * Updates plume state with first-order decay dynamics.
	 * Callers can feed current burn state and step size during simulation.
	 */
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
		double mach = Math.max(0.0, conditions.getMach());
		double velocity = Math.max(0.0, conditions.getVelocity());
		AtmosphericConditions atm = conditions.getAtmosphericConditions();
		double nu = atm.getKinematicViscosity();
		double length = Math.max(1e-6, configuration.getLengthAerodynamic());
		double re_L = (nu > 0.0) ? (velocity * length / nu) : 1e4;
		double alphaDeg = Math.toDegrees(conditions.getAOA());
		double betaDeg = 0.0;

		double cdPlumeOff;
		double cdPlumeOn;
		if (interpolator4D != null) {
			cdPlumeOff = interpolator4D.queryCdPlumeOff(mach, re_L, alphaDeg, betaDeg);
			cdPlumeOn = interpolator4D.queryCdPlumeOn(mach, re_L, alphaDeg, betaDeg);
		} else {
			cdPlumeOff = interpolator.queryCdPlumeOff(mach, re_L, alphaDeg);
			cdPlumeOn = interpolator.queryCdPlumeOn(mach, re_L, alphaDeg);
		}
		double oldCd = forces.getCD();
		double cdBlended = cdPlumeOff + plumeDecayState * (cdPlumeOn - cdPlumeOff);
		double blendWeight = computeRomBlendWeight(mach, re_L);
		double effectiveCd = blendWeight * cdBlended + (1.0 - blendWeight) * oldCd;
		if (!Double.isFinite(effectiveCd) || effectiveCd <= 0.0) {
			effectiveCd = cdBlended;
		}
		forces.setCD(effectiveCd);
		computationSnapshots.add(new RomComputationSnapshot(currentSimulationTimeSeconds, mach, re_L,
				alphaDeg, plumeDecayState, oldCd, effectiveCd, cdPlumeOff, cdPlumeOn));

		if (updateAxial && oldCd > 1e-9) {
			double scaledAxial = forces.getCDaxial() * (effectiveCd / oldCd);
			forces.setCDaxial(scaledAxial);
		}
	}

	private static double computeRomBlendWeight(double mach, double reL) {
		double machWeight = smoothStep(BLEND_MACH_FULL_BARROWMAN, BLEND_MACH_FULL_ROM, mach);
		double reWeight = smoothStep(BLEND_RE_FULL_BARROWMAN, BLEND_RE_FULL_ROM, reL);
		return machWeight * reWeight;
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
