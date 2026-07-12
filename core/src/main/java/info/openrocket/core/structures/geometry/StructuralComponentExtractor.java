package info.openrocket.core.structures.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.Bulkhead;
import info.openrocket.core.rocketcomponent.CenteringRing;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.StructuralComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.TubeCoupler;
import info.openrocket.core.structures.materials.MaterialLibrary;
import info.openrocket.core.structures.materials.StructuralMaterial;
import info.openrocket.core.util.Coordinate;

public final class StructuralComponentExtractor {
	public List<ExtractedStructuralComponent> extract(Rocket rocket) {
		if (rocket == null) {
			return Collections.emptyList();
		}
		List<ExtractedStructuralComponent> components = new ArrayList<>();
		for (RocketComponent component : rocket) {
			if (component instanceof BodyTube) {
				BodyTube tube = (BodyTube) component;
				double thickness = tube.getOuterRadius() - tube.getInnerRadius();
				TubeGeometry geometry = new TubeGeometry(tube.getOuterRadius(), tube.getInnerRadius(), thickness,
						tube.getLength(), tube.getLength(), absoluteX(tube), tube.getName());
				components.add(new ExtractedStructuralComponent(component, "Body tube", geometry, geometry.isComplete(),
						true, structuralMaterial(component)));
			} else if (component instanceof NoseCone) {
				NoseCone nose = (NoseCone) component;
				NoseConeGeometryStructural geometry = new NoseConeGeometryStructural(nose.getLength(), nose.getAftRadius(),
						absoluteX(nose), nose.getShapeType().toString(), nose.getName());
				components.add(new ExtractedStructuralComponent(component, "Nose cone", geometry,
						nose.getLength() > 0 && nose.getAftRadius() > 0, true, structuralMaterial(component)));
			} else if (component instanceof FinSet) {
				FinSet finSet = (FinSet) component;
				FinGeometryStructural geometry = extractFinGeometry(finSet);
				components.add(new ExtractedStructuralComponent(component, "Fin set", geometry, geometry.isComplete(),
						true, structuralMaterial(component)));
			} else if (component instanceof Bulkhead) {
				Bulkhead bulkhead = (Bulkhead) component;
				BulkheadGeometry geometry = new BulkheadGeometry(bulkhead.getOuterRadius(), bulkhead.getLength(),
						absoluteX(bulkhead), bulkhead.getName());
				components.add(new ExtractedStructuralComponent(component, "Bulkhead", geometry,
						bulkhead.getOuterRadius() > 0 && bulkhead.getLength() > 0, true, structuralMaterial(component)));
			} else if (component instanceof CenteringRing) {
				CenteringRing ring = (CenteringRing) component;
				CenteringRingGeometry geometry = new CenteringRingGeometry(ring.getOuterRadius(), ring.getInnerRadius(),
						ring.getLength(), ring.getInstanceCount(), absoluteX(ring), ring.getName());
				components.add(new ExtractedStructuralComponent(component, "Centering ring", geometry,
						ring.getOuterRadius() > 0 && ring.getInnerRadius() >= 0 && ring.getLength() > 0, true,
						structuralMaterial(component)));
			} else if (component instanceof TubeCoupler) {
				TubeCoupler coupler = (TubeCoupler) component;
				CouplerGeometry geometry = new CouplerGeometry(coupler.getOuterRadius(), coupler.getInnerRadius(),
						coupler.getLength(), absoluteX(coupler), coupler.getName());
				components.add(new ExtractedStructuralComponent(component, "Coupler", geometry,
						coupler.getOuterRadius() > 0 && coupler.getInnerRadius() >= 0 && coupler.getLength() > 0,
						true, structuralMaterial(component)));
			}
		}
		return components;
	}

	private static FinGeometryStructural extractFinGeometry(FinSet finSet) {
		double rootChord = finSet.getLength();
		double tipChord = Math.max(0.0, rootChord * 0.5);
		double sweep = 0.0;
		if (finSet instanceof TrapezoidFinSet) {
			TrapezoidFinSet trapezoid = (TrapezoidFinSet) finSet;
			rootChord = trapezoid.getRootChord();
			tipChord = trapezoid.getTipChord();
			sweep = trapezoid.getSweep();
		}
		return new FinGeometryStructural(finSet.getFinCount(), rootChord, tipChord, finSet.getSpan(), sweep,
				finSet.getThickness(), absoluteX(finSet), finSet.getPlanformArea(), finSet.getName());
	}

	private static double absoluteX(RocketComponent component) {
		return component.toAbsolute(Coordinate.NUL)[0].getX();
	}

	private static StructuralMaterial structuralMaterial(RocketComponent component) {
		if (component instanceof StructuralComponent) {
			return MaterialLibrary.fromOpenRocketMaterial(((StructuralComponent) component).getMaterial());
		}
		if (component instanceof ExternalComponent) {
			return MaterialLibrary.fromOpenRocketMaterial(((ExternalComponent) component).getMaterial());
		}
		return MaterialLibrary.USER_DEFINED;
	}

	public static final class ExtractedStructuralComponent {
		private final RocketComponent rocketComponent;
		private final String type;
		private final Object geometry;
		private final boolean geometryComplete;
		private final boolean selectedByDefault;
		private final StructuralMaterial structuralMaterial;

		private ExtractedStructuralComponent(RocketComponent rocketComponent, String type, Object geometry,
				boolean geometryComplete, boolean selectedByDefault, StructuralMaterial structuralMaterial) {
			this.rocketComponent = rocketComponent;
			this.type = type;
			this.geometry = geometry;
			this.geometryComplete = geometryComplete;
			this.selectedByDefault = selectedByDefault;
			this.structuralMaterial = structuralMaterial;
		}

		public RocketComponent getRocketComponent() {
			return rocketComponent;
		}

		public String getType() {
			return type;
		}

		public Object getGeometry() {
			return geometry;
		}

		public boolean isGeometryComplete() {
			return geometryComplete;
		}

		public boolean isSelectedByDefault() {
			return selectedByDefault;
		}

		public String getComponentName() {
			return rocketComponent.getName();
		}

		public StructuralMaterial getStructuralMaterial() {
			return structuralMaterial;
		}
	}
}
