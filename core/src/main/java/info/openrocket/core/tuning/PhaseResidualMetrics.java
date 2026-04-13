package info.openrocket.core.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PhaseResidualMetrics {
	public static final PhaseResidualMetrics EMPTY =
			new PhaseResidualMetrics(Collections.emptyMap(), DragResidualMetrics.EMPTY);

	private final Map<String, ResidualMetrics> channelResiduals;
	private final DragResidualMetrics dragResidual;

	public PhaseResidualMetrics(Map<String, ResidualMetrics> channelResiduals,
								DragResidualMetrics dragResidual) {
		this.channelResiduals = Collections.unmodifiableMap(new LinkedHashMap<>(channelResiduals));
		this.dragResidual = dragResidual == null ? DragResidualMetrics.EMPTY : dragResidual;
	}

	public Map<String, ResidualMetrics> getChannelResiduals() {
		return channelResiduals;
	}

	public DragResidualMetrics getDragResidual() {
		return dragResidual;
	}
}
