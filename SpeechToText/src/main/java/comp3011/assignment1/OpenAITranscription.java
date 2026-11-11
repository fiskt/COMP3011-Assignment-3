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
import com.openai.models.audio.transcriptions.TranscriptionWord;
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
                    .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                    .timestampGranularities(List.of(
                        TranscriptionCreateParams.TimestampGranularity.WORD,
                        TranscriptionCreateParams.TimestampGranularity.SEGMENT))
                    .build()
            );

        TranscriptionVerbose verbose = result.asVerbose();
        List<TranscriptionSegment> segments = verbose.segments().orElse(List.of());
        List<TranscriptionWord> words = verbose.words().orElse(List.of());
        List<SubtitlesResponse.Cue> subtitleWords = new ArrayList<>();
        int wordIndex = 0;

        for (TranscriptionSegment segment : segments) {
            boolean skipSegment = segment.noSpeechProb() > 0.6 && segment.avgLogprob() < -1.0;

            String[] segmentWords = segment.text().trim().split("\\s+");
            int segmentWordCount = segmentWords.length;
            List<SubtitlesResponse.Cue> parsedSegmentWords = new ArrayList<>();

            for (int curr = 0; curr < segmentWordCount; curr++) {
                double wordStart = words.get(wordIndex).start();
                double wordEnd = words.get(wordIndex).end();
                String word = segmentWords[curr];

                parsedSegmentWords.add(new SubtitlesResponse.Cue(word, wordStart, wordEnd));
                wordIndex++;
            }

            if (skipSegment) continue;
            subtitleWords.addAll(parsedSegmentWords);
        }
        usageTracker.addSeconds(verbose.duration());
        return new SubtitlesResponse(subtitleWords);
    }
}
