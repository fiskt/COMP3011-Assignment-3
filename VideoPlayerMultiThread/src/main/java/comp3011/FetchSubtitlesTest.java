package comp3011;

import java.io.File;

import comp3011.SubtitleStore.Subtitle;

public class FetchSubtitlesTest {
    private static final String VIDEO_PATH = "/Users/jerrywu/COMP3011-Assignment-3/SimpsonsTrailer.mp4";

    public static void main(String[] args) throws Exception {
        File videoFile = new File(VIDEO_PATH);

        File audioFile = AudioExtractor.extractAudio(videoFile);
        System.out.println("Extracted audio to: " + audioFile.getAbsolutePath());

        SubtitleStore store = SubtitleStore.fetchSubtitles(audioFile);
        for (Subtitle subtitle : store.getSubtitles()) {
            System.out.println(subtitle);
        }
    }
}
