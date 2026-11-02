package comp3011.assignment1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// ref : https://stackoverflow.com/questions/53524045/how-to-get-the-running-server-port-in-a-springboot-test
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConcurrencyTests {
    // Source - https://stackoverflow.com/q/53524045
	// Posted by Juan Villalobos, modified by community. See post 'Timeline' for change history
	// Retrieved 2026-09-12, License - CC BY-SA 4.0
    @LocalServerPort
	private int port; 

    // AI assisted
	@Test
	void concurrencyTest() throws Exception {

		int count = 300;

		ExecutorService executor =
            Executors.newFixedThreadPool(count);

        RestClient client = RestClient.create(
            "http://localhost:" + port
        );

		List<Future<Boolean>> results = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			results.add(
                executor.submit(() -> {
                    ByteArrayResource file =
                        new ByteArrayResource(
                            "fake audio".getBytes()
                        ) {
                            @Override
                            public String getFilename() {
                                return "recording.webm";
                            }
                        };

                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("audio-file", file);

                    String response = client
                        .post()
                        .uri("/api/v1/transcribe")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                    return response.equals("Test transcription");
                })
			);
		}
		int successful = 0;
		for (Future<Boolean> result : results) {
            if (result.get()) {
                successful++;
            }
        }

		executor.shutdown();

        System.out.println("Successful requests: " + successful + " of " + count);
	}
}
