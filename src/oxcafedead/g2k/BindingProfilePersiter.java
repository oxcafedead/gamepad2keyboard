package oxcafedead.g2k;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

public class BindingProfilePersiter {

	public void save(BindingsProfile bindingsProfile, Path dir) throws IOException {
		Files.writeString(
				Path.of(dir + "/" + bindingsProfile.getName() + ".g2k"),
				bindingsProfile.getAllMappings().entrySet()
						.stream()
						.map(e -> e.getKey() + "---" + e.getValue().toString())
						.collect(Collectors.joining(System.lineSeparator()))
						+ System.lineSeparator() + "sensitivity---" + bindingsProfile.analogSensitivity(),
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
	}

	public BindingsProfile load(File file) throws IOException {
		var profileName = file.getName().replace(".g2k", "");
		var profile = new BindingsProfile(profileName);
		List<String> readAllLines = Files.readAllLines(file.toPath());
		for (String line : readAllLines) {
			var parts = line.split("---");
			var gpCode = parts[0];
			var winCode = parts[1];
			var code = Integer.parseInt(winCode);
			if (gpCode.equals("sensitivity")) {
				profile.setAnalogSensitivity(code);
			}
			else {
				profile.setMapping(gpCode, code);
			}
		}

		return profile;
	}
}
