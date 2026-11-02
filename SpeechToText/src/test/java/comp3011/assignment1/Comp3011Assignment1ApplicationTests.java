package comp3011.assignment1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

// ref : https://stackoverflow.com/questions/53524045/how-to-get-the-running-server-port-in-a-springboot-test
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Comp3011Assignment1ApplicationTests {
	// ref : https://stackoverflow.com/questions/75482134/generating-unit-tests-for-my-service-implementations-on-the-spring-boot-applicat
	@Autowired 
	private TestTranscription testTranscription;

	@Test
	void contextLoads() {
	}

	@Test 
	void testService() throws Exception {
		// ref : https://stackoverflow.com/questions/21800726/using-spring-mvc-test-to-unit-test-multipart-post-request
		MockMultipartFile file = new MockMultipartFile(
			"audio-file",
			"recording.webm",
			"audio/webm",
			"fake audio".getBytes()
		);

		String result = testTranscription.transcribe(file);

		if (!result.equals("Test transcription")) {
			throw new Exception("Test transcription failed");
		}
	}
}
