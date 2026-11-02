package comp3011.assignment1;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("test")
public class TestTranscription implements TranscriptionService {
    // Override the transcribe function with this testing one
    @Override
    public String transcribe(MultipartFile file) 
    throws InterruptedException {
        // Simulate the transcription
        Thread.sleep(2000);
        return "Test transcription";
    }
}
