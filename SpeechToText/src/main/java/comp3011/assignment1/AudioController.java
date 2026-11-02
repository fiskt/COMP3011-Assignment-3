package comp3011.assignment1;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// https://api.openai.com/v1/audio/transcriptions 

@RestController
public class AudioController {
    private final TranscriptionService transcriptionService;
    public AudioController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    // Runs when a request to /api/v1/transcribe happens
    @PostMapping("/api/v1/transcribe")
    public String receivedAudio(
        // Take the "audio-file" object from incoming request
        // and store it in a MultipartFile variable
        @RequestParam("audio-file") MultipartFile file
    ) throws Exception {
        if (file.isEmpty()) return "Empty audio file.";

        // Call the transcribe function
        // Returns the actual transcription output when not testing
        String output = transcriptionService.transcribe(file);
        return output;
    }
}
