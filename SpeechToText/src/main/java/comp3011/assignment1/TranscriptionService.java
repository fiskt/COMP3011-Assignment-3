package comp3011.assignment1;

import org.springframework.web.multipart.MultipartFile;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;

public interface TranscriptionService {
    SubtitlesResponse transcribe(MultipartFile file) throws Exception;
}
