package comp3011.assignment1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIKeyConfig {
    private static final Path API_KEY_FILE = Path.of("key.txt");

    @Value("${openai.key-file:}")
    private String keyFilePath;

    @Bean
    public String openAiApiKey() throws IOException {
        // 1. Explicit --openai.key-file=... launch argument (e.g. pointing at the USB drive).
        if (keyFilePath != null && !keyFilePath.isBlank()) {
            String key = Files.readString(Path.of(keyFilePath)).trim();
            if (!key.isEmpty()) return key;
        }

        // 2. A key.txt sitting in the working directory (your existing fallback).
        if (Files.exists(API_KEY_FILE)) {
            String key = Files.readString(API_KEY_FILE).trim();
            if (!key.isEmpty()) return key;
        }

        // 3. Environment variable fallback.
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) return envKey;

        throw new IllegalStateException(
            "No OpenAI key found. Launch with --openai.key-file=/path/to/key.txt, "
            + "place a key.txt in the working directory, or set OPENAI_API_KEY.");
    }
}