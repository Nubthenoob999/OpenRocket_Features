package info.openrocket.core.aerodynamics.rom.math;

import info.openrocket.core.aerodynamics.rom.geometry.FinGeometry;

/**
 * Pitts-Nielsen-Kaattari fin-body interference carryover factors.
 *
 * <p>Computes the wing-body (K_WB) and body-wing (K_BW) interference lift
 * carryover factors as well as fin normal-force, center-of-pressure,
 * wave-drag, and pitching-moment contributions.
 *
 * <p>Reference: Pitts, Nielsen &amp; Kaattari (1959), NACA Report 1307.
 */
public final class PittsNielsenKaattari {

    private PittsNielsenKaattari() {}

    /**
     * Wing-body carryover factor K_WB (wing lift augmented by body presence).
     *
     * @param sOverA  span-to-body-radius ratio s/a
     * @param mach    freestream Mach number
     * @return K_WB interference factor
     */
    public static double KWB(double sOverA, double mach) {
        double kw = kWB(sOverA);
        // Compressibility correction via Prandtl-Glauert-like factor in subsonic
        if (mach < 1.0) {
            double beta = Math.sqrt(Math.max(0.01, 1.0 - mach * mach));
            return kw / beta;
        }
        return kw;
    }

    /**
     * Body-wing carryover factor K_BW (body lift induced by fin presence).
     *
     * @param sOverA  span-to-body-radius ratio s/a
     * @param mach    freestream Mach number
     * @return K_BW interference factor
     */
    public static double KBW(double sOverA, double mach) {
        double kb = kBW(sOverA);
        if (mach < 1.0) {
            double beta = Math.sqrt(Math.max(0.01, 1.0 - mach * mach));
            return kb / beta;
        }
        return kb;
    }

    /**
     * Incompressible wing-body factor k_WB from PNK geometry tables.
     *
     * @param sOverA  span-to-body-radius ratio
     * @return k_WB
     */
    public static double kWB(double sOverA) {
        // PNK Fig 4 correlation (curve fit):
        // k_WB = (1 + 1/(sOverA))^2 / (1 + (1/sOverA)^2)
        if (sOverA <= 0.0) {
            return 1.0;
        }
        double inv = 1.0 / sOverA;
        return (1.0 + inv) * (1.0 + inv) / (1.0 + inv * inv);
    }

    /**
     * Incompressible body-wing factor k_BW from PNK geometry tables.
     *
     * @param sOverA  span-to-body-radius ratio
     * @return k_BW
     */
    public static double kBW(double sOverA) {
        // PNK: k_BW = (sOverA - 1)^2 / sOverA^2 (first-order approximation)
        if (sOverA <= 1.0) {
            return 0.0;
        }
        double t = (sOverA - 1.0) / sOverA;
        return t * t;
    }

    /**
     * Fin normal-force coefficient contribution (all fins in set).
     *
     * @param fin    fin geometry
     * @param alpha  angle of attack (rad)
     * @param mach   freestream Mach number
     * @return CN contribution from this fin set
     */
    public static double finNormalForce(FinGeometry fin, double alpha, double mach) {
        if (fin == null || Math.abs(alpha) < 1e-9) {
            return 0.0;
        }
        double sOverA = (fin.getBodyRadiusAtRoot() + fin.getSpan())
                / Math.max(1e-6, fin.getBodyRadiusAtRoot());
        double kw = KWB(sOverA, mach);
        double kb = KBW(sOverA, mach);
        // Thin-wing lift slope CN_alpha * alpha, scaled by interference
        double cnAlpha = finLiftSlope(fin, mach);
        return fin.getFinCount() * (kw + kb) * cnAlpha * alpha;
    }

    /**
     * Fin aerodynamic center (center of pressure, x measured from nose).
     *
     * @param fin   fin geometry
     * @param mach  freestream Mach number
     * @return x-position of fin aerodynamic center (m from nose)
     */
    public static double finCP(FinGeometry fin, double mach) {
        if (fin == null) {
            return 0.0;
        }
        // Subsonic: CP at ~half-root-chord behind leading edge; supersonic: shifts aft
        double cpFraction = mach < 1.0 ? 0.25 : 0.5;
        return fin.getXStart() + cpFraction * fin.getRootChord();
    }

    /**
     * Fin wave-drag coefficient at supersonic speed.
     *
     * @param fin   fin geometry
     * @param mach  freestream Mach number
     * @param alpha angle of attack (rad)
     * @param aRef  reference area (m^2)
     * @return fin set wave-drag coefficient (referenced to aRef)
     */
    public static double finWaveDrag(FinGeometry fin, double mach, double alpha, double aRef) {
        if (fin == null || mach <= 1.0 || aRef <= 0.0) {
            return 0.0;
        }
        // Ackeret thin-airfoil wave drag: Cdw = 4 * tau^2 / beta
        // where tau = thickness / chord
        double beta = Math.sqrt(Math.max(0.01, mach * mach - 1.0));
        double tau = fin.getThickness() / Math.max(1e-6, fin.getRootChord());
        double cdw_per_fin = 4.0 * tau * tau / beta;
        // Total over fin set, scaled to reference area
        double finArea = fin.getPlanformArea() * fin.getFinCount();
        return cdw_per_fin * finArea / aRef;
    }

    /**
     * Fin pitching-moment coefficient contribution.
     *
     * @param fin   fin geometry
     * @param alpha angle of attack (rad)
     * @param mach  freestream Mach number
     * @param xRef  moment reference station (m from nose)
     * @param lRef  reference length (m)
     * @param aRef  reference area (m^2)
     * @return Cm contribution from fin set
     */
    public static double finPitchingMoment(FinGeometry fin, double alpha, double mach,
                                           double xRef, double lRef, double aRef) {
        if (fin == null || lRef <= 0.0 || aRef <= 0.0) {
            return 0.0;
        }
        double cn = finNormalForce(fin, alpha, mach);
        double xcp = finCP(fin, mach);
        return -cn * (xcp - xRef) / lRef;
    }

    // --- helpers ---

    private static double finLiftSlope(FinGeometry fin, double mach) {
        double ar = 2.0 * fin.getSpan() * fin.getSpan()
                / Math.max(1e-9, fin.getPlanformArea());
        if (mach >= 1.0) {
            double beta = Math.sqrt(Math.max(0.01, mach * mach - 1.0));
            return 4.0 / beta;
        }
        // Polhamus leading-edge suction analogy approximation
        double beta = Math.sqrt(Math.max(0.01, 1.0 - mach * mach));
        return (Math.PI / 2.0) * ar / (1.0 + Math.sqrt(1.0 + (ar * beta / 2.0) * (ar * beta / 2.0)));
    }
}
