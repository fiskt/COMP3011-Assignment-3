package comp3011.assignment1;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.openai.models.audio.AudioResponseFormat;

@Service
@Profile("!test")
public class OpenAITranscription implements TranscriptionService {
    @Autowired
    private String openAiApiKey;

    private final UsageTracker usageTracker;

    // AI assisted (Claude)
    // Keyed by a hash of the audio bytes, not filename so the same
    // audio played twice, or played again after being renamed, is served
    // from cache instead of calling transcribe again.
    private final Map<String, SubtitlesResponse> cacheByContentHash = new ConcurrentHashMap<>();


    public OpenAITranscription(UsageTracker usageTracker) {
        this.usageTracker = usageTracker;
    }

    private OpenAIClient buildClient() throws IOException {
        return OpenAIOkHttpClient.builder()
                .fromEnv()
                .apiKey(openAiApiKey)
                .build();
    }

    // AI assisted (Claude)
    private static String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available on the standard Java platform.
            throw new IllegalStateException(e);
        }
    }

    // Override the transcription function with the actual OpenAI endpoint
    @Override
    public SubtitlesResponse transcribe(MultipartFile file) throws Exception {
        if (file.isEmpty()) return new SubtitlesResponse(List.of());

        // AI assisted (Claude)
        byte[] audioBytes = file.getBytes();
        String contentHash = hash(audioBytes);
        SubtitlesResponse cached = cacheByContentHash.get(contentHash);
        if (cached != null) {
            return cached;
        }

        MultipartField<InputStream> audioFile =
            MultipartField.<InputStream>builder()
                .value(new ByteArrayInputStream(audioBytes))
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
        SubtitlesResponse response = new SubtitlesResponse(subtitleWords);

        // Cache the whole file's subtitles keyed by the contentHash
        cacheByContentHash.put(contentHash, response);
        usageTracker.addSeconds(verbose.duration());
        return response;
    }
}
