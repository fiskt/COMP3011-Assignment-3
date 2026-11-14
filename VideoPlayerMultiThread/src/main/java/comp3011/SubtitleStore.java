package comp3011;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

import org.json.JSONArray;
import org.json.JSONObject;

public class SubtitleStore {
    // Define a Subtitle record 
    // Stores the subtitle text, time to appear (startTimeUs) and time to disappear (endTimeUs)
    public record Subtitle(
        String text,
        long startTimeUs,
        long endTimeUs
    ) {}

    private final List<Subtitle> subtitles;
    private static final String API_URL = "http://localhost:8080/api/v1/audio/transcriptions";
    private static final int MAX_CHARS = 84;
    private static final long PAUSE_THRESHOLD_US = 600_000;
    private static final int CLIENT_TIMEOUT = 5;
    private static final int REQUEST_TIMEOUT = 60;

    public SubtitleStore(List<Subtitle> subtitles) {
        this.subtitles = subtitles;
    }

    public List<Subtitle> getSubtitles() {
        return subtitles;
    }

    public String activeSubtitleAt(long timestampUs) {
        // Loop though all the subtitles
        // Return the subtitle text if the timestamp is within its display time
        for (Subtitle subtitle : subtitles) {
            if (timestampUs >= subtitle.startTimeUs() && timestampUs < subtitle.endTimeUs()) {
                return subtitle.text();
            }
        }
        return null;
    }

    // AI assisted (Claude)
    public static SubtitleStore fetchSubtitles(File audioFile) throws Exception {
        String boundary = "----SubtitleBoundary" + System.currentTimeMillis();
        byte[] fileBytes = Files.readAllBytes(audioFile.toPath());

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String partHeader =
            "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"audio-file\"; filename=\"" + audioFile.getName() + "\"\r\n" +
            "Content-Type: audio/wav\r\n\r\n";
        body.write(partHeader.getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CLIENT_TIMEOUT))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        List<Subtitle> subtitles = parseSubtitleResponse(response);
        return new SubtitleStore(subtitles);
    }

    // AI assisted (Claude)
    private static List<Subtitle> parseSubtitleResponse(HttpResponse<String> response) {
        List<Subtitle> subtitles = new ArrayList<>();
        List<Subtitle> currentSentence = new ArrayList<>();

        JSONObject json = new JSONObject(response.body());
        JSONArray cues = json.getJSONArray("cues");

        for (int i = 0; i < cues.length(); i++) {
            JSONObject cue = cues.getJSONObject(i);
            String text = cue.getString("text");
            double startSeconds = cue.getDouble("startTime");
            double endSeconds = cue.getDouble("endTime");

            // Convert to microsecond
            long startTimeUs = (long) (startSeconds * 1_000_000);
            long endTimeUs = (long) (endSeconds * 1_000_000);

            // Check if there is a pause in the current sentence
            if (!currentSentence.isEmpty()) {
                long previousEndTimeUs = currentSentence.getLast().endTimeUs();

                // If the pause is longer than the threshold, add the current segment 
                // and reset currentSentence
                if (startTimeUs - previousEndTimeUs > PAUSE_THRESHOLD_US) {
                    subtitles.addAll(buildSentence(currentSentence));
                    currentSentence = new ArrayList<>();
                }
            }

            // Add the next word
            currentSentence.add(new Subtitle(text, startTimeUs, endTimeUs));

            // Build the sentence if there is punctuation
            if (text.endsWith(".") || text.endsWith("?") || text.endsWith("!")) {
                subtitles.addAll(buildSentence(currentSentence));
                currentSentence = new ArrayList<>();
            }
        }

        if (!currentSentence.isEmpty()) {
            subtitles.addAll(buildSentence(currentSentence));
        }

        return subtitles;
    }

    private static List<Subtitle> buildSentence(List<Subtitle> subtitleWords) {
        List<Subtitle> subtitleSentences = new ArrayList<>();
        List<Subtitle> currentSegment = new ArrayList<>();
        int currentNumChar = 0;

        for (Subtitle word : subtitleWords) {
            int wordLen = word.text().length();
            int extraLen = (currentSegment.isEmpty()) 
                ? wordLen 
                : wordLen + 1;  // +1 char for leading space if not new segment

            // If the segment can't fit the current word
            if (currentNumChar + extraLen > MAX_CHARS && !currentSegment.isEmpty()) {
                // Build the segment and set the start and end times
                subtitleSentences.add(buildSegment(currentSegment));
                currentSegment = new ArrayList<>();
                currentNumChar = 0;

                // word will be the start of a new segment, so numChars is just wordLen (no spaces)
                extraLen = wordLen; 
            }

            currentSegment.add(word);
            currentNumChar += extraLen;
        }

        if (!currentSegment.isEmpty()) {
            subtitleSentences.add(buildSegment(currentSegment));
        }

        return subtitleSentences;
    }

    private static Subtitle buildSegment(List<Subtitle> segment) {
        StringBuilder text = new StringBuilder();

        // Build the phrase with the segment words, adding a leading space
        // if its not the start of the sentence
        for (Subtitle word : segment) {
            if (text.length() > 0) text.append(' ');
            text.append(word.text());
        }
        long start = segment.get(0).startTimeUs();
        long end = segment.get(segment.size() - 1).endTimeUs();

        // If duration < 1 second, make duration 1 second
        // If duration > 7 seconds, cap it at 7
        if (end - start < 1_000_000) end = start + 1_000_000;
        if (end - start > 7_000_000) end = start + 7_000_000;
        return new Subtitle(text.toString(), start, end);
    }
}