package info.openrocket.core.aerodynamics.rom.fins;

import info.openrocket.core.aerodynamics.rom.bl.BoundaryLayerState;
import info.openrocket.core.aerodynamics.rom.bl.HeadTurbulent;
import info.openrocket.core.aerodynamics.rom.bl.MichelTransition;
import info.openrocket.core.aerodynamics.rom.bl.ThwaitesLaminar;
import info.openrocket.core.aerodynamics.rom.math.EckertReference;
import info.openrocket.core.aerodynamics.rom.math.GasDynamics;
import info.openrocket.core.aerodynamics.rom.geometry.FinGeometry;

/**
 * Boundary-layer march along a fin chord surface.
 *
 * <p>Performs a streamwise BL integration from the fin leading edge to a given
 * chord station using the Thwaites-laminar / Michel-transition / Head-turbulent
 * closure chain adapted for a flat-plate fin surface.
 */
public class FinBLMarcher {

    /**
     * March the boundary layer along a fin surface from the leading edge.
     *
     * @param fin           fin geometry
     * @param chordStation  chord fraction at which to stop (0–1)
     * @param mach          freestream Mach number
     * @param alpha_local   local angle of attack (fin incidence, rad)
     * @param Re_per_m      unit Reynolds number (1/m)
     * @param T_e           edge temperature (K)
     * @param T_w           wall temperature (K); pass T_e for adiabatic wall
     * @param upperSurface  true for upper (leeward) surface
     * @return array of BL states from leading edge to chord station
     */
    public BoundaryLayerState[] marchFinBL(
            FinGeometry fin,
            double chordStation,
            double mach,
            double alpha_local,
            double Re_per_m,
            double T_e, double T_w,
            boolean upperSurface) {

        if (fin == null || Re_per_m <= 0.0 || fin.getRootChord() <= 0.0 || chordStation <= 0.0) {
            return new BoundaryLayerState[]{BoundaryLayerState.invalid()};
        }

        double chord = fin.getRootChord() * Math.max(0.01, Math.min(1.0, chordStation));
        int nSteps = Math.max(4, (int) Math.ceil(chordStation * 20));
        double ds = chord / nSteps;

        // Kinematic viscosity at edge conditions
        double refTemp = EckertReference.referenceTemperature(
                T_e, T_w, EckertReference.adiabaticWallTemp(T_e, mach, 1.4, true));
        double mu_ref = EckertReference.referenceViscosity(refTemp);
        double rho_e = 1.225 * T_e / Math.max(1.0, refTemp); // approximate
        double nu = mu_ref / Math.max(1e-9, rho_e);

        double ue = mach * GasDynamics.speedOfSound(T_e, 1.4, 287.0);
        double due_ds = 0.0; // flat-plate approximation

        BoundaryLayerState[] states = new BoundaryLayerState[nSteps + 1];
        BoundaryLayerState state = new BoundaryLayerState(
                1e-6, 1e-6, 2.59, 1e-4, false, false, false, true, 0.0, "fin_init");
        states[0] = state;

        boolean turbulent = false;

        for (int i = 1; i <= nSteps; i++) {
            double station = (i - 1) * ds;
            if (!turbulent) {
                state = ThwaitesLaminar.advance(state, station, ue, due_ds, nu, ds);
                double Re_x = ue * (station + ds) / nu;
                double Re_theta = ue * state.getMomentumThickness() / nu;
                if (MichelTransition.shouldTransition(Re_x, Re_theta)) {
                    turbulent = true;
                }
            } else {
                state = HeadTurbulent.advance(state, station, ue, due_ds, nu, ds);
            }
            states[i] = state;
        }
        return states;
    }
}
