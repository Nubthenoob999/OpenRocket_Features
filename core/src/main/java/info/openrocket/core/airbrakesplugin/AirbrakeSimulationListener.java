package info.openrocket.core.airbrakesplugin;

import info.openrocket.core.airbrakesplugin.util.AirDensity;
import info.openrocket.core.airbrakesplugin.util.ApogeePredictor;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.simulation.listeners.AbstractSimulationListener;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.CoordinateIF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Predictor-driven listener:
 *  - Gates: burnout + Mach
 *  - Control: if predictedApogee(AGL) > targetApogee(AGL) => EXTEND (ext=1), else RETRACT (ext=0)
 *  - Writes airbrakeExt (always 0.0 or 1.0) and predictedApogee (AGL)
 *  - ApogeePredictor internally integrates in MSL, but we convert to AGL for control.
 */
public final class AirbrakeSimulationListener extends AbstractSimulationListener {

    private static final Logger log = LoggerFactory.getLogger(AirbrakeSimulationListener.class);

    // small threshold to distinguish "no thrust" from "burning"
    private static final double THRUST_EPS_N = 1e-3;

    // Collaborators
    private final AirbrakeAerodynamics airbrakes;
    private final AirbrakeController   controller;
    private final ApogeePredictor      predictor;
    private final AirbrakeConfig       config;
    private final double               airbrakesAreaFallback;

    // State
    private FlightConditions flightConditions = null;
    /** Current commanded extension (0 = retracted, 1 = extended). */
    private double ext = 0.0;
    private double lastTime = Double.NaN;
    private Double lastVz = null;

    /** Time when thrust last fell to (or below) THRUST_EPS_N after having been positive. */
    private Double burnoutTimeS = null;
    /** Track whether we have ever seen positive thrust in this run. */
    private boolean seenPositiveThrust = false;

    // Flight data columns
    private static final FlightDataType AIRBRAKE_EXT =
            FlightDataType.getType("airbrakeExt", "airbrakeExt", UnitGroup.UNITS_RELATIVE);
    private static final FlightDataType PRED_APOGEE =
            FlightDataType.getType("predictedApogee", "predictedApogee", UnitGroup.UNITS_DISTANCE);

    public AirbrakeSimulationListener(AirbrakeAerodynamics airbrakes,
                                      AirbrakeController controller,
                                      ApogeePredictor predictor,
                                      double airbrakesAreaFallback,
                                      AirbrakeConfig config) {
        this.airbrakes             = airbrakes;
        this.controller            = controller;
        this.predictor             = predictor;
        this.config                = config;
        this.airbrakesAreaFallback = airbrakesAreaFallback;
    }

    // Controller callbacks: update authoritative ext
    private final AirbrakeController.ControlContext ctx = new AirbrakeController.ControlContext() {
        @Override public void extend_airbrakes()  { setExt(1.0); }
        @Override public void retract_airbrakes() { setExt(0.0); }
        @Override public void switch_altitude_back_to_pressure() {
            log.debug("Switching altitude back to pressure (no-op in OR)");
        }
    };

    private void setExt(double v) {
        final double val = v >= 0.5 ? 1.0 : 0.0;   // enforce 0/1
        if (this.ext != val) {
            log.debug("Airbrake ext setpoint change: {} -> {}", this.ext, val);
        }
        this.ext = val;
    }

    @Override
    public void startSimulation(SimulationStatus status) throws SimulationException {
        // Initial state for each run
        setExt(0.0);
        lastTime           = status.getSimulationTime();
        lastVz             = null;
        burnoutTimeS       = null;
        seenPositiveThrust = false;

        if (controller != null) {
            controller.setContext(ctx);
            controller.reset();
        }
        if (predictor != null) {
            predictor.reset();
        }

        final FlightDataBranch fdb = status.getFlightDataBranch();
        if (fdb != null) {
            fdb.setValue(AIRBRAKE_EXT, 0.0);
            fdb.setValue(PRED_APOGEE, Double.NaN);
        }

        log.info("Simulation started - initial time: {}, airbrakes retracted", lastTime);
    }

    /** Update burnoutTimeS / seenPositiveThrust based on current thrust. */
    private void updateBurnoutDetection(final SimulationStatus status) {
        final FlightDataBranch fdb = status.getFlightDataBranch();
        if (fdb == null) {
            return;
        }

        final double thrustN = fdb.getLast(FlightDataType.TYPE_THRUST_FORCE);
        final double t = status.getSimulationTime();

        if (thrustN > THRUST_EPS_N) {
            seenPositiveThrust = true;
        }

        if (seenPositiveThrust && burnoutTimeS == null && thrustN <= THRUST_EPS_N) {
            burnoutTimeS = t;
            log.debug("Captured burnout time: {} s", burnoutTimeS);
        }
    }

    // Gating policy: burnout + Mach  (no minimum deployment altitude)
    private boolean isExtensionAllowed(final SimulationStatus status) {
        final double t = status.getSimulationTime();

        updateBurnoutDetection(status);

        if (burnoutTimeS == null || t < burnoutTimeS) {
            log.debug("Extension NOT allowed: waiting for burnout (t={}, burnoutTimeS={})", t, burnoutTimeS);
            return false;
        }

        final CoordinateIF v = status.getRocketVelocity();
        final double speed = Math.sqrt(v.length2());
        final double altitudeMSL = status.getRocketPosition().getZ() +
                status.getSimulationConditions().getLaunchSite().getAltitude();
        final double mach = AirDensity.machFromV(speed, altitudeMSL);

        final double machMax = (config != null) ? config.getMaxMachForDeployment() : Double.POSITIVE_INFINITY;
        if (!Double.isFinite(mach) || mach > machMax) {
            log.debug("Extension NOT allowed: mach {} > max {}", mach, machMax);
            return false;
        }

        return true;
    }

    @Override
    public boolean preStep(SimulationStatus status) {
        final double t  = status.getSimulationTime();
        final double dt = Double.isFinite(lastTime) ? Math.max(1e-6, t - lastTime) : 1e-3;
        lastTime = t;

        final CoordinateIF vel = status.getRocketVelocity();
        final CoordinateIF pos = status.getRocketPosition();
        final double vz = vel.getZ();

        final double siteAlt     = status.getSimulationConditions().getLaunchSite().getAltitude();
        final double altitudeAGL = pos.getZ();
        final double altitudeMSL = altitudeAGL + siteAlt;

        log.debug("preStep t={} dt={}  pos={}  vel={}  vz={}  altAGL={}  altMSL={}",
                  t, dt, pos, vel, vz, altitudeAGL, altitudeMSL);

        final FlightDataBranch fdb = status.getFlightDataBranch();

        // Keep burnout detection up to date for override / gating
        updateBurnoutDetection(status);

        // Predictor update (world-Z acceleration including g) using finite-difference
        if (lastVz != null && predictor != null) {
            final double a_worldZ_incl_g = (vz - lastVz) / dt;
            predictor.update(a_worldZ_incl_g, dt, altitudeAGL, altitudeMSL, vz);
            log.debug("Predictor updated: az={} lastVz={} zAGL={} zMSL={}",
                      a_worldZ_incl_g, lastVz, altitudeAGL, altitudeMSL);
        }
        lastVz = vz;

        // Read predictor outputs (MSL) and convert to AGL
        Double apUsedMSL = null;
        if (predictor != null) {
            try {
                final Double apStrict = predictor.getPredictionIfReady();
                if (apStrict != null && Double.isFinite(apStrict)) {
                    apUsedMSL = apStrict;
                } else {
                    final Double apBest = predictor.getApogeeBestEffort();
                    if (apBest != null && Double.isFinite(apBest)) apUsedMSL = apBest;
                }
            } catch (Throwable ignored) {
                final Double apBest = predictor.getApogeeBestEffort();
                if (apBest != null && Double.isFinite(apBest)) apUsedMSL = apBest;
            }
        }

        Double apUsedAGL = null;
        if (apUsedMSL != null && Double.isFinite(apUsedMSL)) {
            apUsedAGL = apUsedMSL - siteAlt;
        }

        if (fdb != null) {
            fdb.setValue(PRED_APOGEE, apUsedAGL != null ? apUsedAGL : Double.NaN);
        }

        if (apUsedAGL != null) {
            log.debug("Apogee(pred_AGL)={} m vs altAGL={} m  (margin_AGL={} m)",
                      apUsedAGL, altitudeAGL, apUsedAGL - altitudeAGL);
        } else {
            log.debug("Apogee(pred)=N/A (predictor not ready)");
        }

        // Burnout-only override
        if (config != null && config.isDeployAfterBurnoutOnly()) {
            final double delay = Math.max(0.0, config.getDeployAfterBurnoutDelayS());
            double airbrake_ext_override;
            if (burnoutTimeS == null) {
                airbrake_ext_override = 0.0;
                log.debug("Override active: waiting for burnout");
            } else {
                final double dtSinceBurnout = t - burnoutTimeS;
                if (dtSinceBurnout >= delay) {
                    airbrake_ext_override = 1.0;
                    log.debug("Override active: burnout+delay reached (dtSinceBurnout={} >= delay={}) → EXTEND",
                              dtSinceBurnout, delay);
                } else {
                    airbrake_ext_override = 0.0;
                    log.debug("Override active: within delay (dtSinceBurnout={} < delay={}) → RETRACT",
                              dtSinceBurnout, delay);
                }
            }
            airbrake_ext_override = (airbrake_ext_override >= 0.5) ? 1.0 : 0.0;
            setExt(airbrake_ext_override);
            if (fdb != null) {
                fdb.setValue(AIRBRAKE_EXT, this.ext);
            }
            return true;
        }

        // Control: if apogee(AGL) > target(AGL) → EXTEND; else RETRACT (subject to gates)
        double airbrake_ext;
        if (isExtensionAllowed(status)) {
            final double targetAGL = (config != null) ? config.getTargetApogee() : Double.POSITIVE_INFINITY;

            if (apUsedAGL == null || !Double.isFinite(apUsedAGL)) {
                airbrake_ext = 0.0;
                log.debug("Predictor not ready; retracting by default");
            } else {
                airbrake_ext = (apUsedAGL > targetAGL) ? 1.0 : 0.0;
                log.debug("Control decision: apUsedAGL={} targetAGL={} => airbrake_ext={}",
                          apUsedAGL, targetAGL, airbrake_ext);
            }
        } else {
            airbrake_ext = 0.0;
            log.debug("Outside gates; forcing retract");
        }

        airbrake_ext = (airbrake_ext >= 0.5) ? 1.0 : 0.0;
        setExt(airbrake_ext);
        if (fdb != null) {
            fdb.setValue(AIRBRAKE_EXT, this.ext);
        }

        return true;
    }

    @Override
    public FlightConditions postFlightConditions(SimulationStatus status,
                                                 FlightConditions fc) throws SimulationException {
        this.flightConditions = fc;
        log.debug("Flight conditions updated - AOA={}, refArea(rocket)={}, refArea(cfg)={}, Mach={}",
                  fc.getAOA(), fc.getRefArea(),
                  (config != null ? config.getReferenceArea() : Double.NaN),
                  fc.getMach());
        return fc;
    }

    @Override
    public AerodynamicForces postAerodynamicCalculation(final SimulationStatus status,
                                                        final AerodynamicForces forces) throws SimulationException {
        updateBurnoutDetection(status);

        boolean overrideActiveNow = false;
        if (config != null && config.isDeployAfterBurnoutOnly() && burnoutTimeS != null) {
            final double delay = Math.max(0.0, config.getDeployAfterBurnoutDelayS());
            final double dtSinceBurnout = status.getSimulationTime() - burnoutTimeS;
            overrideActiveNow = (dtSinceBurnout >= delay);
        }
        if (!(overrideActiveNow || isExtensionAllowed(status))) {
            log.debug("Extension not allowed (and override not active), no aerodynamic modifications");
            return forces;
        }

        final CoordinateIF vVec = status.getRocketVelocity();
        final double v2 = vVec.length2();
        final double speed = Math.sqrt(v2);
        final double vz = vVec.getZ();

        if (flightConditions == null) {
            log.debug("No FlightConditions yet; skipping aero override this step");
            return forces;
        }

        final double mach = flightConditions.getMach();

        final double altitudeMSL = status.getRocketPosition().getZ() +
                status.getSimulationConditions().getLaunchSite().getAltitude();

        final double rhoDyn = AirDensity.rhoForDynamicPressure(altitudeMSL, mach);
        final double dynP = 0.5 * rhoDyn * v2;

        final double rocket_area = flightConditions.getRefArea();
        final FlightDataBranch fdb = status.getFlightDataBranch();

        // Gate: if predicted apogee is not above target, leave forces unchanged
        if (!overrideActiveNow && config != null && fdb != null) {
            final double targetAGL = config.getTargetApogee();
            final double apAGL = fdb.getLast(PRED_APOGEE);
            final double time_af_bo = status.getSimulationTime() - burnoutTimeS;
            if (apAGL <= targetAGL && time_af_bo >= 0.4) {
                log.debug("Predicted apogee ({}) not above target ({}); skipping airbrake aero", apAGL, targetAGL);
                return forces;
            }
        }

        if (dynP <= 0 || rocket_area <= 0) {
            log.debug("dynP={} or rocket_area={} non-positive; skipping aero override", dynP, rocket_area);
            return forces;
        }

        final double cd_roc_axial = forces.getCDaxial();
        final double dragForceN_roc_axial = cd_roc_axial * dynP * rocket_area;

        final double airbrakeExt = (this.ext >= 0.5) ? 1.0 : 0.0;
        final double dragForceN_airbrakes = airbrakes.calculateDragForce(airbrakeExt, speed, altitudeMSL);
        if (dragForceN_airbrakes <= 0.0) {
            log.debug("Airbrake drag is non-positive at ext={}; leaving aerodynamic forces unchanged", airbrakeExt);
            return forces;
        }

        final double airbrake_area = (config != null && config.getReferenceArea() > 0)
                ? config.getReferenceArea() : airbrakesAreaFallback;
        final double combinedArea = rocket_area + airbrake_area;

        log.debug("Airbrakes Drag = {} N at ext={}, Rocket Drag = {} N (speed={} m/s, vz={} m/s, altMSL={}, combinedArea={})",
                  dragForceN_airbrakes, airbrakeExt, dragForceN_roc_axial, speed, vz, altitudeMSL, combinedArea);

        final double Cd_total_axial = (dragForceN_roc_axial + dragForceN_airbrakes) / (dynP * combinedArea);

        forces.setCDaxial(Cd_total_axial);

        if (fdb != null) {
            final double apAGL = fdb.getLast(PRED_APOGEE);
            if (Double.isFinite(apAGL)) {
                log.debug("PostAero: set CDaxial={} (was {}), Apogee(pred_AGL)={} m",
                          Cd_total_axial, cd_roc_axial, apAGL);
            } else {
                log.debug("PostAero: set CDaxial={} (was {}), Apogee(pred)=N/A",
                          Cd_total_axial, cd_roc_axial);
            }
        }

        return forces;
    }
}
