package comp3011;

import java.util.List;

public class SubtitleStore {
    // Define a Subtitle record 
    // Stores the subtitle text, time to appear (startTimeUs) and time to disappear (endTimeUs)
    public record Subtitle(
        String text,
        long startTimeUs,
        long endTimeUs
    ) {}

    private final List<Subtitle> videoSubtitles;

    public SubtitleStore(List<Subtitle> videoSubs) {
        this.videoSubtitles = videoSubs;
    }

    public String activeSubtitleAt(long timestampUs) {
        // Loop though all the subtitles
        // Return the subtitle text if the timestamp is within its display time
        for (Subtitle s : videoSubtitles) {
            if (timestampUs >= s.startTimeUs() && timestampUs < s.endTimeUs()) {
                return s.text();
            }
        }
        return null;
    }
}