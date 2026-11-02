package comp3011.assignment1;

import java.io.InputStream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.MultipartField;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;

@Service
@Profile("!test")
public class OpenAITranscription implements TranscriptionService {
    // Get the token counter object
    private final TokenCounter tokenCounter;
    public OpenAITranscription(TokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    // Override the transcription function with the actual OpenAI endpoint
    @Override
    public String transcribe(MultipartFile file) throws Exception {
        if (file.isEmpty()) return "Empty audio file.";

        // ref : https://github.com/openai/openai-java
        MultipartField<InputStream> audioFile =
            MultipartField.<InputStream>builder()
                .value(file.getInputStream())
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .build();

        // ref : https://github.com/openai/openai-java
        OpenAIClient client = OpenAIOkHttpClient.fromEnv(); 
        // Create a transcription result
        var result = client
            .audio()
            .transcriptions()
            .create(
                TranscriptionCreateParams.builder()
                    .file(audioFile)
                    .model("gpt-4o-mini-transcribe")
                    .build());

        // AI assisted (ChatGPT)
        var transcription = result.asTranscription();
        if (transcription.usage().isPresent()) {
            var usage = transcription.usage().get();

            if (usage.isTokens()) {
                var tokens = usage.asTokens();

                // Add the tokens to the counter object
                // so it can be used for the stats endpoint
                tokenCounter.addTokens(
                    tokens.inputTokens(),
                    tokens.outputTokens()
                );
            }
        }

        return transcription.text();
    }
}
