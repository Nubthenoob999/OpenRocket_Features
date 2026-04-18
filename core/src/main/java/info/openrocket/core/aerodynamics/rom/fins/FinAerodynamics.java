package info.openrocket.core.aerodynamics.rom.fins;

import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerState;
import info.openrocket.core.aerodynamics.rom.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.rom.math.PittsNielsenKaattari;

/**
 * Fin aerodynamic force and moment assembly across all fin sets.
 *
 * <p>Computes per-fin-set contributions to CN, CA, Cm, and xCP using the
 * Pitts-Nielsen-Kaattari interference factors for the normal force, Ackeret
 * wave drag for supersonic conditions, and the fin BL march for friction drag.
 */
public class FinAerodynamics {

    private final FinBLMarcher blMarcher = new FinBLMarcher();

    /**
     * Compute total aerodynamic contributions from all fin sets.
     *
     * <p>Returns a coefficient array: [CN, CA_friction, CA_wave, Cm, xCP].
     *
     * @param fins       array of fin geometries (one entry per fin set)
     * @param alpha      angle of attack (rad)
     * @param mach       freestream Mach number
     * @param Re_per_m   unit Reynolds number (1/m)
     * @param T_e        edge temperature (K)
     * @param T_w        wall temperature (K)
     * @param qInf       dynamic pressure (Pa)
     * @param lRef       reference length (m)
     * @param aRef       reference area (m^2)
     * @param xRef       moment reference station (m from nose)
     * @return [CN, CA_friction, CA_wave, Cm, xCP_weighted]
     */
    public double[] computeFinContributions(
            FinGeometry[] fins,
            double alpha,
            double mach,
            double Re_per_m,
            double T_e, double T_w,
            double qInf,
            double lRef, double aRef, double xRef) {

        if (fins == null || fins.length == 0 || aRef <= 0.0) {
            return new double[]{0.0, 0.0, 0.0, 0.0, xRef};
        }

        double totalCN = 0.0;
        double totalCaFriction = 0.0;
        double totalCaWave = 0.0;
        double totalCm = 0.0;
        double weightedXcp = 0.0;
        double cnSum = 0.0;

        for (FinGeometry fin : fins) {
            if (fin == null) continue;

            // Normal force from Pitts-Nielsen-Kaattari
            double cn = PittsNielsenKaattari.finNormalForce(fin, alpha, mach);
            double xcp = PittsNielsenKaattari.finCP(fin, mach);
            double cm = PittsNielsenKaattari.finPitchingMoment(fin, alpha, mach, xRef, lRef, aRef);
            double caWave = PittsNielsenKaattari.finWaveDrag(fin, mach, alpha, aRef);

            // Friction drag from BL march (trailing-edge state)
            double caFriction = computeFinFrictionCa(fin, mach, Re_per_m, T_e, T_w, aRef);

            totalCN       += cn;
            totalCaFriction += caFriction;
            totalCaWave   += caWave;
            totalCm       += cm;

            double absCn = Math.abs(cn);
            weightedXcp += xcp * absCn;
            cnSum += absCn;
        }

        double xcpFinal = cnSum > 1e-12 ? weightedXcp / cnSum : xRef;
        return new double[]{totalCN, totalCaFriction, totalCaWave, totalCm, xcpFinal};
    }

    private double computeFinFrictionCa(FinGeometry fin, double mach, double Re_per_m,
                                         double T_e, double T_w, double aRef) {
        if (Re_per_m <= 0.0 || fin.getPlanformArea() <= 0.0) {
            return 0.0;
        }
        BoundaryLayerState[] states = blMarcher.marchFinBL(fin, 1.0, mach, 0.0,
                Re_per_m, T_e, T_w, true);
        if (states.length == 0) {
            return 0.0;
        }
        BoundaryLayerState te = states[states.length - 1];
        double cf = te.getSkinFrictionCoefficient();
        // Two-sided (upper + lower), all fin counts
        double wetArea = 2.0 * fin.getPlanformArea() * fin.getFinCount();
        return cf * wetArea / Math.max(1e-9, aRef);
    }
}
