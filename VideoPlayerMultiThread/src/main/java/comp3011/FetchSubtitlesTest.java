package comp3011;

import java.io.File;

public class FetchSubtitlesTest {
    private static final String VIDEO_PATH = "/Users/jerrywu/COMP3011-Assignment-3/SimpsonsTrailer.mp4";

    public static void main(String[] args) throws Exception {
        File videoFile = new File(VIDEO_PATH);

        File audioFile = AudioExtractor.extractAudio(videoFile);
        System.out.println("Extracted audio to: " + audioFile.getAbsolutePath());

        SubtitleStore store = SubtitleStore.fetchSubtitles(audioFile);
        System.out.println("Fetched subtitles.");

        for (long t = 0; t < 15_000_000; t += 1_000_000) {
            System.out.println(t + "us -> " + store.activeSubtitleAt(t));
        }
    }
}
