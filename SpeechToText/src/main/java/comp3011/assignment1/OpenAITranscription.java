package comp3011.assignment1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.DoubleAdder;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.MultipartField;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionSegment;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;

import com.openai.models.audio.AudioResponseFormat;

@Service
@Profile("!test")
public class OpenAITranscription implements TranscriptionService {
    private final UsageTracker usageTracker;

    private static final Path API_KEY_FILE = Path.of("key.txt");

    public OpenAITranscription(UsageTracker usageTracker) {
        this.usageTracker = usageTracker;
    }

    // AI assisted (Claude)
    private static String getKey() throws IOException {
        if (Files.exists(API_KEY_FILE)) {
            String key = Files.readString(API_KEY_FILE).trim();
            if (!key.isEmpty()) return key;
        }

        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) return envKey;

        throw new IllegalStateException("No OpenAI key found.");
    }

    // AI assisted (Claude)
    private static OpenAIClient buildClient() throws IOException {
        return OpenAIOkHttpClient.builder()
                .fromEnv()
                .apiKey(getKey())
                .build();
    }

    // Override the transcription function with the actual OpenAI endpoint
    @Override
    public SubtitlesResponse transcribe(MultipartFile file) throws Exception {
        if (file.isEmpty()) return new SubtitlesResponse(List.of());

        // Source - https://github.com/openai/openai-java
        MultipartField<InputStream> audioFile =
            MultipartField.<InputStream>builder()
                .value(file.getInputStream())
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .build();

        OpenAIClient client = buildClient();
        var result = client
            .audio()
            .transcriptions()
            .create(
                TranscriptionCreateParams.builder()
                    .file(audioFile)
                    .model("whisper-1")
                    // AI assisted (Claude) and
                    // Source - https://github.com/openai/openai-java/blob/main/openai-java-core/src/main/kotlin/com/openai/models/audio/AudioResponseFormat.kt
                    .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                    .timestampGranularities(List.of(
                        TranscriptionCreateParams.TimestampGranularity.WORD,
                        TranscriptionCreateParams.TimestampGranularity.SEGMENT))
                    .build()
                );

        // AI assisted (Claude) and
        // Source - https://github.com/openai/openai-java/blob/main/openai-java-core/src/main/kotlin/com/openai/models/audio/transcriptions/TranscriptionSegment.kt
        TranscriptionVerbose verbose = result.asVerbose();
        List<SubtitlesResponse.Cue> rawCues = new ArrayList<>();
        for (TranscriptionSegment segment : verbose.segments().orElse(List.of())) {
            boolean noDisplay = segment.noSpeechProb() > 0.6 && segment.avgLogprob() < -1.0;
            if (noDisplay) continue;

            SubtitlesResponse.Cue subtitleCue = new SubtitlesResponse.Cue(
                segment.text().trim(),
                segment.start(),
                segment.end()
            );
            rawCues.add(subtitleCue);
        }

        usageTracker.addSeconds(verbose.duration());

        
        return new SubtitlesResponse(rawCues);
    }
}
