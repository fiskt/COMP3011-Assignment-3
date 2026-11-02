package comp3011;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LoadComparisonTestIT {
    private static final File SINGLE_THREAD_REFERENCE_DIR = new File("reference-player").getAbsoluteFile();
    private static final String TEST_CLIP_PATH = new File("src/test/resources/test-clip.mp4").getAbsolutePath();
    private static final String EFFECTS = "-sfwyvdjmbppbmjdvywfssfwyvdjmbppbmjdvywfs --number-frames";

    private static final Pattern HEADROOM_PATTERN = Pattern.compile("Frame headroom is \\D*(-?\\d+)us");

    // Checks if there is an output every 1000ms
    private static final long POLL_INTERVAL_MS = 1000;
   
    // Player is considered idle after 5000ms
    private static final long IDLE_TIMEOUT_MS = 5000;

    // Safety net only, in case nothing is ever printed at all.
    private static final long MAX_WAIT_MS = 60000;


    private record Headroom(
        int totalCount,
        int positive,
        int negative,
        long playbackTimeMs
    ) {
        double msPerContentSecond() {
            int intervals = totalCount - 1;
            return intervals <= 0 ? 0 : (double) playbackTimeMs / intervals;
        }
    }

    private Headroom countHeadroom(File projectDir) throws IOException, InterruptedException {
        // Source - https://www.baeldung.com/run-shell-command-in-java
        // Retrieved 2026-10-23
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        String mvn = (isWindows) ? "mvn.cmd" : "mvn";

        ProcessBuilder processBuilder = new ProcessBuilder(
            mvn,
            "javafx:run",
            "-Djavafx.args=" + EFFECTS + " " + TEST_CLIP_PATH
        );
        processBuilder.directory(projectDir);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Source - https://www.baeldung.com/java-atomic-variables
        // Retrieved 2026-10-24
        // Atomic ensures the variables are thread safe
        AtomicInteger totalCount = new AtomicInteger();
        AtomicInteger positive = new AtomicInteger();
        AtomicInteger negative = new AtomicInteger();
        AtomicLong totalHeadroomUs = new AtomicLong();
        // Record the time of first and last line appearing to calculate
        // the playback time
        // -1 = no line seen yet
        AtomicLong firstLineAt = new AtomicLong(-1);
        AtomicLong lastLineAt = new AtomicLong();

        // AI assisted (Claude) - adding the thread
        Thread reader = new Thread(() -> {
            try (
                // Source - https://www.geeksforgeeks.org/java/ways-to-read-input-from-console-in-java/
                // Retrieved 2026-10-24
                // process.getInputStream() is the input stream for the buffer reader
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            ) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Source - https://www.w3schools.com/java/java_regex.asp
                    // Retrieved 2026-10-23
                    Matcher matcher = HEADROOM_PATTERN.matcher(line);
                    if (matcher.find()) {
                        int headroomUs = Integer.parseInt(matcher.group(1));
                        totalHeadroomUs.addAndGet(headroomUs);
                        totalCount.incrementAndGet();
                        if (headroomUs < 0) {
                            negative.incrementAndGet();
                        } else {
                            positive.incrementAndGet();
                        }

                        long now = System.currentTimeMillis();

                        // Setting firstLineAt only once (when its -1)
                        // Set lastLineAt to the current time
                        firstLineAt.compareAndSet(-1, now);
                        lastLineAt.set(now);
                    }
                }
            } catch (IOException e) {}
        });
        
        // Start the reader and have it run for OBSERVE_DURATION_MS
        reader.start();

        // AI assisted (Claude) - signal for closing the player if it reached idle
        // but wait for the player to actually start
        // Wait for at most MAX_WAIT_MS for an output
        long hardDeadline = System.currentTimeMillis() + MAX_WAIT_MS;
        while (System.currentTimeMillis() < hardDeadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            long sinceLastLine = System.currentTimeMillis() - lastLineAt.get();
            if (totalCount.get() > 0 && sinceLastLine > IDLE_TIMEOUT_MS) {
                break;
            }
        }

        // AI assisted (Claude) - destroy all descendants to close player properly
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
        reader.join(5000);

        long playbackTimeMs = (firstLineAt.get() < 0) ? 0 : lastLineAt.get() - firstLineAt.get();

        // Return the results
        return new Headroom(
            totalCount.get(), 
            positive.get(), 
            negative.get(),
            playbackTimeMs
        );
    }

    @Test
    void headroomComparison_singleVsMultiThread() throws Exception {
        File entryProjectDir = new File(".").getAbsoluteFile();

        Headroom entry = countHeadroom(entryProjectDir);
        Headroom ref = countHeadroom(SINGLE_THREAD_REFERENCE_DIR);

        System.out.printf("Reference: %d positive, %d negative of %d total. Playback time %dms.%n",
            ref.positive(), ref.negative(), ref.totalCount(), ref.playbackTimeMs());

        System.out.printf("Entry: %d positive, %d negative of %d total. Playback time %dms.%n",
            entry.positive(), entry.negative(), entry.totalCount(), entry.playbackTimeMs());

        assertTrue(entry.negative <= ref.negative, 
            "Multithreaded entry falls behind no more than singlethreaded referece.");
        assertTrue(entry.msPerContentSecond() <= ref.msPerContentSecond(),
    "Multithreaded entry tracks real-time pacing closer than the singlethreaded reference.");
    }
}