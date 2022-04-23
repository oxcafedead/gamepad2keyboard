package oxcafedead.g2k;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import net.java.games.input.Component;
import net.java.games.input.Event;

public class BindingsProfile {

	private String name;
	private final Map<String, Integer> mappings;

	public BindingsProfile(String name) {
		this.name = name;
		mappings = new HashMap<>();
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setMapping(Event gamepadCode, int winCode) {
		mappings.put(normalize(gamepadCode), winCode);
	}

	public void setMapping(String gamepadCode, int winCode) {
		mappings.put(gamepadCode, winCode);
	}

	public void removeMapping(String gamepadCode) {
		mappings.remove(gamepadCode);
	}

	public Collection<Integer> getCodes(Event gamepadCode) {
		var identifier = gamepadCode.getComponent().getIdentifier();
		if (identifier.equals(Component.Identifier.Axis.POV)) {
			if (gamepadCode.getValue() == Component.POV.DOWN_LEFT) {
				return List.of(mappings.getOrDefault(identifier + "/" + Component.POV.DOWN, -1),
						mappings.getOrDefault(identifier + "/" + Component.POV.LEFT, -1));
			}
			else if (gamepadCode.getValue() == Component.POV.DOWN_RIGHT) {
				return List.of(mappings.getOrDefault(identifier + "/" + Component.POV.DOWN, -1),
						mappings.getOrDefault(identifier + "/" + Component.POV.RIGHT, -1));
			}
			else if (gamepadCode.getValue() == Component.POV.UP_LEFT) {
				return List.of(mappings.getOrDefault(identifier + "/" + Component.POV.UP, -1),
						mappings.getOrDefault(identifier + "/" + Component.POV.LEFT, -1));
			}
			else if (gamepadCode.getValue() == Component.POV.UP_RIGHT) {
				return List.of(mappings.getOrDefault(identifier + "/" + Component.POV.UP, -1),
						mappings.getOrDefault(identifier + "/" + Component.POV.RIGHT, -1));
			}
		}
		return List.of(mappings.getOrDefault(normalize(gamepadCode), -1));
	}

	private Collection<Integer> getCodes(String substring) {
		return mappings.entrySet().stream()
				.filter(e -> e.getKey().contains(substring))
				.filter(e -> e.getValue() != -1)
				.map(Map.Entry::getValue)
				.collect(Collectors.toSet());
	}

	public Collection<Integer> getCodesToRelease(Event e) {
		var identifier = e.getComponent().getIdentifier();

		if (!e.getComponent().isAnalog()) {
			if (identifier.equals(Component.Identifier.Axis.POV) && e.getValue() == 0) {
				return getCodes("pov");
			}
			return List.of();
		}

		if (identifier.equals(Component.Identifier.Axis.X)) {
			if (e.getValue() > 0) {
				return getCodes(identifier.getName() + "/l");
			}
			else {
				return getCodes(identifier.getName() + "/r");
			}
		}
		else if (identifier.equals(Component.Identifier.Axis.Y)) {
			if (e.getValue() > 0) {
				return getCodes(identifier.getName() + "/u");
			}
			else {
				return getCodes(identifier.getName() + "/d");
			}
		}
		if (identifier.equals(Component.Identifier.Axis.Z)) {
			if (e.getValue() > 0) {
				return getCodes(identifier.getName() + "/l");
			}
			else {
				return getCodes(identifier.getName() + "/r");
			}
		}
		else if (identifier.equals(Component.Identifier.Axis.RZ)) {
			if (e.getValue() > 0) {
				return getCodes(identifier.getName() + "/u");
			}
			else {
				return getCodes(identifier.getName() + "/d");
			}
		}


		return List.of();
	}

	public Map<String, Integer> getAllMappings() {
		return Collections.unmodifiableMap(new TreeMap<>(mappings));
	}

	public String getName() {
		return name;
	}

	private String normalize(Event e) {
		var identifier = e.getComponent().getIdentifier();
		if (e.getComponent().isAnalog()) {
			if (identifier.equals(Component.Identifier.Axis.X)) {
				if (e.getValue() > 0) {
					return identifier.getName() + "/r";
				}
				else {
					return identifier.getName() + "/l";
				}
			}
			else if (identifier.equals(Component.Identifier.Axis.Y)) {
				if (e.getValue() > 0) {
					return identifier.getName() + "/d";
				}
				else {
					return identifier.getName() + "/u";
				}
			}
			if (identifier.equals(Component.Identifier.Axis.Z)) {
				if (e.getValue() > 0) {
					return identifier.getName() + "/r";
				}
				else {
					return identifier.getName() + "/l";
				}
			}
			else if (identifier.equals(Component.Identifier.Axis.RZ)) {
				if (e.getValue() > 0) {
					return identifier.getName() + "/d";
				}
				else {
					return identifier.getName() + "/u";
				}
			}
		}
		if (identifier.equals(Component.Identifier.Axis.POV)) {
			return identifier.getName() + "/" + e.getValue();
		}
		return identifier.getName();
	}
}
