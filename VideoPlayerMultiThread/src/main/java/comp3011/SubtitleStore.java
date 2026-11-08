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

    public SubtitleStore(List<Subtitle> subtitles) {
        this.subtitles = subtitles;
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
    public static SubtitleStore fetchSubtitles(File audioFile) throws Exception{
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

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());

        List<Subtitle> subtitles = parseSubtitleResponse(response);
        return new SubtitleStore(subtitles);
    }

    // AI assisted (Claude)
    private static List<Subtitle> parseSubtitleResponse(HttpResponse<String> response) {
        List<Subtitle> subtitles = new ArrayList<>();

        JSONObject json = new JSONObject(response.body());
        JSONArray cues = json.getJSONArray("cues");

        for (int i = 0; i < cues.length(); i++) {
            JSONObject cue = cues.getJSONObject(i);
            String text = cue.getString("text");
            double startSeconds = cue.getDouble("startTime");
            double endSeconds = cue.getDouble("endTime");

            long startTimeUs = (long) (startSeconds * 1_000_000);
            long endTimeUs = (long) (endSeconds * 1_000_000);

            subtitles.add(new Subtitle(text, startTimeUs, endTimeUs));
        }

        return subtitles;
    }
}