package info.openrocket.core.structures.loads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StructuralLoadDiagram {
	private final String componentName;
	private final String loadCaseDescription;
	private final List<StructuralLoadDiagramPoint> points;

	public StructuralLoadDiagram(String componentName, String loadCaseDescription,
			List<StructuralLoadDiagramPoint> points) {
		this.componentName = componentName;
		this.loadCaseDescription = loadCaseDescription;
		this.points = Collections.unmodifiableList(new ArrayList<>(points));
	}

	public String getComponentName() {
		return componentName;
	}

	public String getLoadCaseDescription() {
		return loadCaseDescription;
	}

	public List<StructuralLoadDiagramPoint> getPoints() {
		return points;
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}
}
