package comp3011.assignment1;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;

public class AdminControllerTests {
    private TokenCounter tokenCounter = new TokenCounter();
    private ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

    @Test 
    // Test the 200 status for getUptime()
    void uptimeTest200() {
        AdminController adminController =
            new AdminController(tokenCounter, applicationContext);

        ResponseEntity<?> responseEntity = adminController.getUptime();

        System.out.println("Uptime Test - 200");
        System.out.println("Status code: " + responseEntity.getStatusCode());
        System.out.println("Body: " + responseEntity.getBody());

        if (responseEntity.getStatusCode().value() != 200) {
            throw new RuntimeException(
                "Expected status 200"
            );
        }
    }

    @Test 
    // Test the 500 status for getUptime()
    void uptimeTest500() {
        AdminController adminController =
            new AdminController(tokenCounter, applicationContext) {
                // Force the currentTime function to have an error
                // so the try block fails
                @Override 
                protected Instant currentTime() {
                    throw new RuntimeException("Error");
                }
            };

        ResponseEntity<?> responseEntity = adminController.getUptime();

        System.out.println("Uptime Test - 500");
        System.out.println("Status code: " + responseEntity.getStatusCode());
        System.out.println("Body: " + responseEntity.getBody());

        if (responseEntity.getStatusCode().value() != 500) {
            throw new RuntimeException(
                "Expected status 500"
            );
        }
    }

    @Test
    // Test the 202 and 409 status for shutdownServer()
    void shutdownTest202_409() {
        AdminController adminController =
            new AdminController(tokenCounter, applicationContext);

        // Request shutdown, changes shutdown state to true
        ResponseEntity<?> firstResponse = adminController.shutdownServer();

        System.out.println("Shutdown Test - 202");
        System.out.println("Status: " + firstResponse.getStatusCode());
        System.out.println("Body: " + firstResponse.getBody());

        if (firstResponse.getStatusCode().value() != 202) {
            throw new RuntimeException("Expected status 202");
        }

        // Shutdown state is true, so it returns the shutdown in progress error
        ResponseEntity<?> secondResponse = adminController.shutdownServer();

        System.out.println("Shutdown Test - 409");
        System.out.println("Status: " + secondResponse.getStatusCode());
        System.out.println("Body: " + secondResponse.getBody());

        if (secondResponse.getStatusCode().value() != 409) {
            throw new RuntimeException("Expected status 409");
        }
    }

    @Test 
    // Test the 200 status for getGlobalStats()
    void statsTest200() {
        AdminController adminController =
            new AdminController(tokenCounter, applicationContext);

        // Add input and output tokens to the counter
        tokenCounter.addTokens(100, 100);

        ResponseEntity<?> responseEntity = adminController.getGlobalStats();

        System.out.println("Global Stats Test - 200");
        System.out.println("Status code: " + responseEntity.getStatusCode());
        System.out.println("Body: " + responseEntity.getBody());

        if (responseEntity.getStatusCode().value() != 200) {
            throw new RuntimeException(
                "Expected status 200"
            );
        }
    }

    // AI assisted
    @Test 
    // Test the 500 status for getGlobalStats()
    void statsTest500() {
        TokenCounter brokenCounter =
            mock(TokenCounter.class);

        // Create a mock broken counter to force an error
        when(brokenCounter.getInputTokens())
            .thenThrow(new RuntimeException("Error"));
        
        // Pass the broken counter into the controller instead of the normal one
        AdminController adminController =
            new AdminController(brokenCounter, applicationContext);

        ResponseEntity<?> responseEntity = adminController.getGlobalStats();

        System.out.println("Global Stats Test - 500");
        System.out.println("Status code: " + responseEntity.getStatusCode());
        System.out.println("Body: " + responseEntity.getBody());

        if (responseEntity.getStatusCode().value() != 500) {
            throw new RuntimeException(
                "Expected status 500"
            );
        }
    }
}
