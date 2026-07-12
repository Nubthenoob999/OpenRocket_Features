package info.openrocket.core.aerodynamics.physicsaero.force;

import info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode;
import info.openrocket.core.aerodynamics.physicsaero.math.CompensatedSum;
import info.openrocket.core.aerodynamics.physicsaero.math.VectorTransforms;
import info.openrocket.core.util.Coordinate;

public final class CoefficientAssembler {
	private CoefficientAssembler() {}
	public static AerodynamicCoefficients assemble(ContributionLedger ledger, ReferenceState reference) {
		CompensatedSum[] f = sums(), m = sums();
		for (ForceContribution c : ledger.entries()) {
			if (c.owner().mode() == OwnershipMode.DIAGNOSTIC_ONLY) continue;
			add(f, c.forceBodyN());
			Coordinate arm = new Coordinate(c.applicationPointM().x - reference.momentOriginM().x,
					c.applicationPointM().y - reference.momentOriginM().y,
					c.applicationPointM().z - reference.momentOriginM().z);
			Coordinate translated = VectorTransforms.cross(arm, c.forceBodyN());
			add(m, new Coordinate(c.intrinsicMomentBodyNm().x + translated.x,
					c.intrinsicMomentBodyNm().y + translated.y, c.intrinsicMomentBodyNm().z + translated.z));
		}
		return OpenRocketAxisAdapter.coefficients(value(f), value(m), reference);
	}
	private static CompensatedSum[] sums() { return new CompensatedSum[] { new CompensatedSum(), new CompensatedSum(), new CompensatedSum() }; }
	private static void add(CompensatedSum[] sums, Coordinate v) { sums[0].add(v.x); sums[1].add(v.y); sums[2].add(v.z); }
	private static Coordinate value(CompensatedSum[] sums) { return new Coordinate(sums[0].value(), sums[1].value(), sums[2].value()); }
}
