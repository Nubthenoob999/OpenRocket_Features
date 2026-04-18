package info.openrocket.core.aerodynamics.rom.math;

/**
 * Green-Lag three-equation turbulent boundary-layer closure.
 *
 * <p>Extends the Head two-equation system with a shear-lag memory transport
 * equation for sqrt(C_tau). Activated under strong non-equilibrium conditions
 * such as a large edge-Mach jump or elevated kinematic shape factor H_k.
 *
 * <p>Reference: Green, Weeks, and Brooman (1973); Lag-entrainment method.
 */
public class GreenLagEntrainment {

    private final double C1;
    private final double C2;

    public GreenLagEntrainment(double C1, double C2) {
        this.C1 = C1;
        this.C2 = C2;
    }

    public GreenLagEntrainment() {
        this(2.8, 3.3);
    }

    /**
     * Equilibrium shear-stress coefficient from local skin friction and shape factor.
     *
     * @param Cf  local skin friction coefficient
     * @param H   incompressible shape factor
     * @return equilibrium C_tau
     */
    public double equilibriumCTau(double Cf, double H) {
        double Hs = Math.max(1.0, H);
        return 0.5 * Cf * (0.9 / (Hs - 1.0) - 0.4) * (Hs - 1.0);
    }

    /**
     * Right-hand side of the three-ODE Green-Lag system.
     *
     * <p>State vector layout: [theta, H, C_tau]
     *
     * @param s          surface arc length (m)
     * @param theta      momentum thickness (m)
     * @param H          shape factor
     * @param C_tau      lag shear-stress coefficient
     * @param ue         boundary-layer edge velocity (m/s)
     * @param due_ds     streamwise velocity gradient (1/s)
     * @param Re_per_m   unit Reynolds number (1/m)
     * @param mach_e     edge Mach number
     * @param T_e        edge temperature (K)
     * @param T_w        wall temperature (K)
     * @return d[theta, H, C_tau]/ds
     */
    public double[] rhs(double s, double theta, double H, double C_tau,
                        double ue, double due_ds, double Re_per_m,
                        double mach_e, double T_e, double T_w) {
        if (theta <= 0.0 || H <= 1.0 || ue <= 0.0) {
            return new double[]{0.0, 0.0, 0.0};
        }

        // Turbulent flat-plate Cf via simple White-Christoph approximation
        double ReTh = Math.max(1.0, Re_per_m * theta);
        double Cf = 0.0468 * Math.pow(ReTh, -0.268); // compressible correction applied below
        // Eckert-temperature-based compressibility factor
        double T_ref = T_e * (1.0 + 0.22 * Math.sqrt(0.71) * 0.5 * (1.4 - 1.0) * mach_e * mach_e);
        Cf *= Math.pow(T_e / Math.max(1.0, T_ref), 0.6);
        double C_tau_eq = Math.max(0.0, equilibriumCTau(Cf, H));
        double sqrtCtau    = Math.sqrt(Math.max(0.0, C_tau));
        double sqrtCtauEq  = Math.sqrt(C_tau_eq);

        // Momentum thickness ODE (entrainment form):
        double Ce = H * Cf / 2.0 + 0.0306 * Math.pow(Math.max(0.0, H - 3.0), -0.653);
        double dTheta_ds = Ce - (2.0 + H) * theta * due_ds / Math.max(1e-9, ue);

        // Shape-factor ODE (head form with lag correction):
        double dH_ds;
        double Hk = H - 0.075 * mach_e * mach_e;
        if (Hk > 1.1) {
            double entrainment_dH = (0.021 * (Hk - 1.0) - 0.07) / Math.max(1e-9, theta);
            dH_ds = entrainment_dH * Ce - H / Math.max(1e-9, theta) * dTheta_ds
                    + 0.5 * (H - 1.0) * (sqrtCtau - sqrtCtauEq) / Math.max(1e-9, theta);
        } else {
            dH_ds = 0.0;
        }

        // Lag-entrainment shear ODE:
        double dSqrtCtau_ds = (C1 * (sqrtCtauEq - sqrtCtau)
                + C2 * theta * due_ds / Math.max(1e-9, ue) * sqrtCtau)
                / Math.max(1e-9, theta);
        double dCtau_ds = sqrtCtau > 1e-12
                ? 2.0 * sqrtCtau * dSqrtCtau_ds
                : 0.0;

        return new double[]{dTheta_ds, dH_ds, dCtau_ds};
    }

    /**
     * Single Euler step of the Green-Lag system.
     *
     * @param s      current arc length
     * @param state  [theta, H, C_tau]
     * @param ds     step size
     * @return updated [theta, H, C_tau]
     */
    public double[] step(double s, double[] state, double ds,
                         double ue, double due_ds, double Re_per_m,
                         double mach_e, double T_e, double T_w) {
        double[] k = rhs(s, state[0], state[1], state[2],
                ue, due_ds, Re_per_m, mach_e, T_e, T_w);
        return new double[]{
                Math.max(1e-9, state[0] + ds * k[0]),
                Math.max(1.001, state[1] + ds * k[1]),
                Math.max(0.0,   state[2] + ds * k[2])
        };
    }
}
