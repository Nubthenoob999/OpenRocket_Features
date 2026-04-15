package info.openrocket.core.file.openrocket.savers;

import java.util.ArrayList;

import info.openrocket.core.rocketcomponent.RingTailFinSet;

public class RingTailFinSetSaver extends ExternalComponentSaver {

	private static final RingTailFinSetSaver instance = new RingTailFinSetSaver();

	public static ArrayList<String> getElements(info.openrocket.core.rocketcomponent.RocketComponent c) {
		ArrayList<String> list = new ArrayList<>();

		list.add("<ringtailfinset>");
		instance.addParams(c, list);

		RingTailFinSet ring = (RingTailFinSet) c;
		list.add("<ringradius>" + ring.getRingRadius() + "</ringradius>");
		list.add("<ringchord>" + ring.getRingChord() + "</ringchord>");
		list.add("<ringthickness>" + ring.getRingThickness() + "</ringthickness>");

		list.add("</ringtailfinset>");

		return list;
	}
}