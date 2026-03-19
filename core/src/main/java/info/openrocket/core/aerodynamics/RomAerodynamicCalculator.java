package info.openrocket.core.aerodynamics;

import java.util.Map;

import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.DragSurfaceInterpolator;
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

	private final BarrowmanCalculator barrowman;
	private DragSurfaceInterpolator interpolator;
	private DragSurface installedSurface;
	private double plumeDecayState = 0.0;  // 0=off, 1=on

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
			return;
		}
		this.installedSurface = surface;
		this.interpolator = new DragSurfaceInterpolator(surface);
	}

	public DragSurface getInstalledSurface() {
		return installedSurface;
	}

	public boolean hasSurface() {
		return interpolator != null;
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
		copy.installSurface(installedSurface);
		copy.plumeDecayState = plumeDecayState;
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

		double cdPlumeOff = interpolator.queryCdPlumeOff(mach, re_L, alphaDeg);
		double cdPlumeOn = interpolator.queryCdPlumeOn(mach, re_L, alphaDeg);
		double cdBlended = cdPlumeOff + plumeDecayState * (cdPlumeOn - cdPlumeOff);

		double oldCd = forces.getCD();
		forces.setCD(cdBlended);

		if (updateAxial && oldCd > 1e-9) {
			double scaledAxial = forces.getCDaxial() * (cdBlended / oldCd);
			forces.setCDaxial(scaledAxial);
		}
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
