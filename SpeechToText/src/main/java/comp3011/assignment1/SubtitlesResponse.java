package comp3011.assignment1;

import java.util.List;

// AI assisted (Claude)
public record SubtitlesResponse(
    List<Cue> cues
) {
    public record Cue(
        String text, 
        double startTime, 
        double endTime
    ) {}
}
