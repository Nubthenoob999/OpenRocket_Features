package info.openrocket.core.aerodynamics.rom.plume;

import info.openrocket.core.aerodynamics.rom.math.GasDynamics;

/**
 * Powered-flight base-pressure and boattail modification via plume modelling.
 *
 * <p>Implements the Addy plume-expansion angle, Brazzel boattail modifier,
 * and jet-on base-pressure coefficient based on the Addy-Brazzel-Lamb family
 * of semi-empirical correlations.
 *
 * <p>References: Addy (1967); Brazzel &amp; Henderson (1966); Lamb &amp; Hawkins (1963).
 */
public final class PlumeModel {

    private PlumeModel() {}

    /**
     * Plume expansion half-angle (rad) at the nozzle lip.
     *
     * @param pe       nozzle exit pressure (Pa)
     * @param p_inf    freestream static pressure (Pa)
     * @param Me       nozzle exit Mach number
     * @param gamma_j  jet specific-heat ratio
     * @return plume half-angle (rad); 0 when under-expanded
     */
    public static double plumeExpansionAngle(double pe, double p_inf, double Me,
                                              double gamma_j) {
        if (pe <= 0.0 || p_inf <= 0.0 || Me <= 0.0) {
            return 0.0;
        }
        double pressureRatio = pe / p_inf;
        if (pressureRatio <= 1.0) {
            return 0.0; // over-expanded or matched, no free expansion
        }
        // Prandtl-Meyer expansion to reach ambient pressure
        double nu_e = GasDynamics.prandtlMeyerAngle(Me, gamma_j);
        // Ambient Mach after full expansion
        double M_ambient = GasDynamics.prandtlMeyerMach(nu_e + Math.log(pressureRatio), gamma_j);
        double nu_ambient = GasDynamics.prandtlMeyerAngle(M_ambient, gamma_j);
        return Math.max(0.0, nu_ambient - nu_e);
    }

    /**
     * Effective aftbody angle as seen by the outer flow, including plume displacement.
     *
     * @param boattailAngle  geometric boattail half-angle (rad)
     * @param delta_p        plume expansion half-angle (rad)
     * @return effective angle (rad)
     */
    public static double effectiveAfterbodyAngle(double boattailAngle, double delta_p) {
        return boattailAngle + delta_p;
    }

    /**
     * Addy jet-base interaction factor.
     *
     * @param R_body_base  body base radius (m)
     * @param R_jet_exit   nozzle exit radius (m)
     * @return Addy factor f_a (dimensionless)
     */
    public static double addyFactor(double R_body_base, double R_jet_exit) {
        if (R_body_base <= 0.0 || R_jet_exit <= 0.0) {
            return 1.0;
        }
        double ratio = R_jet_exit / R_body_base;
        return 1.0 - ratio * ratio; // annular base area fraction
    }

    /**
     * Jet-on base pressure coefficient (Lamb-Hawkins correlation).
     *
     * @param mach_inf     freestream Mach number
     * @param Cp_base_jet_off  coast base pressure coefficient
     * @param pe_ratio     pe / p_inf
     * @param Me           nozzle exit Mach
     * @param Ae_Ab        exit-area-to-base-area ratio
     * @param Ra_Rj        aftbody-radius-to-jet-radius ratio
     * @param gamma_j      jet specific-heat ratio
     * @return jet-on base pressure coefficient
     */
    public static double jetOnBaseCp(double mach_inf, double Cp_base_jet_off,
                                      double pe_ratio, double Me, double Ae_Ab,
                                      double Ra_Rj, double gamma_j) {
        if (!Double.isFinite(Cp_base_jet_off) || mach_inf <= 0.0) {
            return Cp_base_jet_off;
        }
        // Shielding factor: jet fills fraction of base area
        double shielding = Math.min(1.0, Ae_Ab);
        // Jet plume elevates base pressure toward pe
        double cpJet = 2.0 * (pe_ratio - 1.0) / (gamma_j * mach_inf * mach_inf);
        return Cp_base_jet_off * (1.0 - shielding) + cpJet * shielding;
    }

    /**
     * Brazzel-Henderson boattail drag modifier for powered flight.
     *
     * @param mach_inf  freestream Mach number
     * @param D_base    base diameter (m)
     * @param D_max     maximum body diameter (m)
     * @param delta_p   plume expansion half-angle (rad)
     * @return multiplicative boattail drag modifier (1.0 = no change)
     */
    public static double brazzelBoattailModifier(double mach_inf, double D_base,
                                                  double D_max, double delta_p) {
        if (D_max <= 0.0 || D_base <= 0.0 || delta_p <= 0.0) {
            return 1.0;
        }
        // Plume displaces flow; boattail drag decreases with expansion
        double tau = D_base / D_max;
        double reduction = 0.4 * tau * Math.sin(delta_p) / Math.max(1.0, mach_inf);
        return Math.max(0.0, 1.0 - reduction);
    }

    /**
     * Compute the powered base pressure coefficient combining all closures.
     *
     * @param plume          current plume state
     * @param mach_inf       freestream Mach number
     * @param Cp_base_coast  coast base Cp
     * @param D_base         base diameter (m)
     * @param D_max          max body diameter (m)
     * @param R_body_base    body base radius (m)
     * @return powered base pressure coefficient
     */
    public static double poweredBaseCp(PlumeState plume, double mach_inf,
                                        double Cp_base_coast, double D_base,
                                        double D_max, double R_body_base) {
        if (plume == null || !plume.powered || mach_inf <= 0.0) {
            return Cp_base_coast;
        }
        double p_inf = 101325.0; // assume standard if unknown
        double pe_ratio = Math.max(1.0, plume.pe / p_inf);
        double Ae_Ab = plume.Ae / Math.max(1e-9, Math.PI * R_body_base * R_body_base);
        double Ra_Rj = R_body_base / Math.max(1e-6, Math.sqrt(plume.Ae / Math.PI));

        double delta_p = plumeExpansionAngle(plume.pe, p_inf, plume.Me, plume.gamma_j);

        double cpJetOn = jetOnBaseCp(mach_inf, Cp_base_coast, pe_ratio, plume.Me,
                Ae_Ab, Ra_Rj, plume.gamma_j);
        double modifier = brazzelBoattailModifier(mach_inf, D_base, D_max, delta_p);
        return cpJetOn * modifier;
    }
}
