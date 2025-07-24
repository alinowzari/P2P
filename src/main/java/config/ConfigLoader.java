package config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads GameConfig JSON from file or stream.
 */
public class ConfigLoader {
    private static final ObjectMapper M = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static GameConfig loadConfig(Path jsonFile) throws Exception {
        return M.readValue(jsonFile.toFile(), GameConfig.class);
    }

    public static List<GameConfig> loadConfigs(Path jsonFile) throws Exception {
        return M.readValue(
                jsonFile.toFile(),
                new TypeReference<List<GameConfig>>() {}
        );
    }

    public static List<GameConfig> loadConfigs(InputStream jsonStream) throws Exception {
        return M.readValue(
                jsonStream,
                new TypeReference<List<GameConfig>>() {}
        );
    }
}
