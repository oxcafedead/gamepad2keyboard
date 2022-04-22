package oxcafedead.g2k;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

public class BindingProfilePersiter {

	public void save(BindingsProfile bindingsProfile, Path dir) throws IOException {
		Files.writeString(
				Path.of(dir + "/" + bindingsProfile.getName() + ".g2k"),
				bindingsProfile.getAllMappings().entrySet()
						.stream()
						.map(e -> e.getKey() + "---" + e.getValue().toString())
						.collect(Collectors.joining(System.lineSeparator())),
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
	}

	public BindingsProfile load(File file) throws IOException {
		var profileName = file.getName().replace(".g2k", "");
		var profile = new BindingsProfile(profileName);
		Files.lines(file.toPath())
				.forEach(l -> {
					var parts = l.split("---");
					var gpCode = parts[0];
					var winCode = parts[1];
					profile.setMapping(gpCode, Integer.parseInt(winCode));
				});
		return profile;
	}
}
