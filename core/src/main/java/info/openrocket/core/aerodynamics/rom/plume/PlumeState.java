package info.openrocket.core.aerodynamics.rom.plume;

/**
 * Propulsive plume thermodynamic state at the nozzle exit plane.
 *
 * <p>Encapsulates the exit-plane conditions required for base-pressure and
 * boattail aerodynamic corrections during powered flight.
 */
public class PlumeState {

    /** Nozzle exit static pressure (Pa). */
    public final double pe;

    /** Nozzle exit static temperature (K). */
    public final double Te;

    /** Nozzle exit Mach number. */
    public final double Me;

    /** Nozzle exit area (m^2). */
    public final double Ae;

    /** Ratio of specific heats for the jet gas. */
    public final double gamma_j;

    /** True when motor is burning (thrust > 0). */
    public final boolean powered;

    /** Thrust force (N). */
    public final double thrust;

    public PlumeState(double pe, double Te, double Me, double Ae,
                      double gamma_j, boolean powered, double thrust) {
        this.pe      = pe;
        this.Te      = Te;
        this.Me      = Me;
        this.Ae      = Ae;
        this.gamma_j = gamma_j;
        this.powered = powered;
        this.thrust  = thrust;
    }

    /** Unpowered (coast) state. */
    public static PlumeState coast() {
        return new PlumeState(0.0, 0.0, 0.0, 0.0, 1.25, false, 0.0);
    }

    /**
     * Approximate plume state from available motor parameters.
     *
     * <p>When full thermodynamic data is unavailable, the nozzle exit Mach is
     * estimated as 2.5 (typical solid motor) and the exit temperature from a
     * representative chamber temperature and isentropic expansion.
     *
     * @param thrust       motor thrust (N)
     * @param exitArea     nozzle exit area (m^2)
     * @param ambientPa    ambient pressure (Pa)
     * @return approximate PlumeState
     */
    public static PlumeState fromThrust(double thrust, double exitArea, double ambientPa) {
        if (thrust <= 0.0 || exitArea <= 0.0) {
            return coast();
        }
        double Me = 2.5;            // typical exit Mach for solid motor
        double gamma_j = 1.25;      // typical solid propellant exhaust
        double T0 = 2800.0;         // typical chamber temperature (K)
        double Te = T0 / (1.0 + 0.5 * (gamma_j - 1.0) * Me * Me);
        // Exit pressure from thrust equation: F ≈ pe * Ae * (1 + Me^2)  (rough)
        double pe = thrust / (exitArea * (1.0 + Me * Me)) + ambientPa;
        return new PlumeState(pe, Te, Me, exitArea, gamma_j, true, thrust);
    }
}
