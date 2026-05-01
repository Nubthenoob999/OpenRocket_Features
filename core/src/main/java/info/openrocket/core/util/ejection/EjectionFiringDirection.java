package info.openrocket.core.util.ejection;

/**
 * Direction the ejection charge fires relative to the rocket axis. The
 * firing direction tells us which end of the bay tube hosts the joint
 * that gets pushed off (coupler / nose-cone shoulder) and therefore which
 * end is "open" — which in turn picks out the nearest internal barrier
 * (centering ring, motor mount, bulkhead) opposite the joint as the
 * closed end of the pressurized bay.
 */
public enum EjectionFiringDirection {

	/** Charge pushes the chute / coupler toward the nose (forward). */
	FORWARD("Forward (toward nose)"),

	/** Charge pushes the chute / coupler toward the aft / motor end. */
	AFT("Aft (toward motor)");

	private final String displayName;

	EjectionFiringDirection(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
