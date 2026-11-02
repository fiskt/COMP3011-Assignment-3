package comp3011;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import comp3011.CommandLineController.FPOptions;
import static org.junit.jupiter.api.Assertions.*;

public class CommandLineControllerTest {
    File tempVideo;
    private String tempVideoPath;

    private String helpExpectedOutput = String.join(System.lineSeparator(),
        "Usage: VideoPlayer [options] [video-file]",
        "",
        "Options:",
        "  -h, --help         Show this help message",
        "  -a, --audio        Play audio",
        "  -x, --maximise     Open the player maximised",
        "  -1, --monitor-1    Open the player on display 1",
        "  -2, --monitor-2    Open the player on display 2",
        "",
        "Frame processors:",
        "  -n, --number-frames    Render the frame number onto each frame",
        "  -s, --scratch-frames   Render vertical film scratches",
        "  -f, --flicker-frames   Randomly dim frames",
        "  -w, --black-and-white  Convert frames to black and white",
        "  -y, --yellow-frames    Apply a warmer colour temperature",
        "  -v, --vignette-frames  Darken the frame edges",
        "  -d, --dust-frames      Render dust and hair marks",
        "  -j, --jitter-frames    Randomly displace frames by a few pixels",
        "  -m, --mottle-frames    Add cloudy emulsion mottling",
        "  -b, --bleed-frames     Bleed light into frames",
        "  -p, --pepper-frames    Pepper frames with dark spots/blotches",
        "",
        "Frame processors are applied in command-line order and may be repeated.",
        "Example: -nssnfwyvdjmbp numbers, scratches twice, numbers again, flickers,",
        "converts, warms, vignettes, dusts, jitters, mottles, bleeds, then peppers."
    ) + System.lineSeparator();

    @BeforeEach
    void createPlaceholderVideo() throws IOException {
        // Source - https://stackoverflow.com/a/26860215
        // Posted by Stefan, modified by community. See post 'Timeline' for change history
        // Retrieved 2026-10-22, License - CC BY-SA 4.0
        tempVideo = File.createTempFile("tempVideoFile", ".mp4");
        tempVideoPath = tempVideo.getAbsolutePath();
    }

    @AfterEach
    void deletePlaceholderVideo() throws IOException {
        tempVideo.deleteOnExit();
        tempVideoPath = "";
    }

    private String getOutput(Runnable action) {
        // Source - https://www.geeksforgeeks.org/advance-java/unit-testing-of-system-out-println-with-junit/
        // Retrieved 2026-10-22
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return outputStream.toString();
    }

    // Help tests
    @Test
    void helpOption_noVideo_printsHelp() {
        String[] longArgs = {"--help"};
        String[] shortArgs = {"-h"};

        String longFormOutput = getOutput(() -> new CommandLineController(longArgs));
        String shortFormOutput = getOutput(() -> new CommandLineController(shortArgs));

        String expected = helpExpectedOutput;

        assertEquals(expected, longFormOutput);
        assertEquals(expected, shortFormOutput);
    }

    @Test
    void helpOption_invalidOptions_error() {
        String[] args = {"-z", "--help", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        assertTrue(output.getErrorMessage().startsWith("Unknown option: "));
        assertEquals(1, output.getExitCode());
    }

    @Test
    void helpOption_doesNotLaunchPlayer() {
        String[] args = {"--help", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        assertFalse(output.shouldLaunchApplication());
    }

    // Audio tests
    @Test
    void audioOption_playsAudio() {
        String[] longArgs = {"--audio", tempVideoPath};
        String[] shortArgs = {"-a", tempVideoPath};

        CommandLineController longForm = new CommandLineController(longArgs);
        CommandLineController shortForm = new CommandLineController(shortArgs);

        assertTrue(longForm.isAudioRequested());
        assertTrue(shortForm.isAudioRequested());
    }

    // Maximise tests
    @Test
    void maximiseOption_maximiseWindow() {
        String[] longArgs = {"--maximise", tempVideoPath};
        String[] shortArgs = {"-x", tempVideoPath};

        CommandLineController longForm = new CommandLineController(longArgs);
        CommandLineController shortForm = new CommandLineController(shortArgs);

        assertTrue(longForm.isMaximiseRequested());
        assertTrue(shortForm.isMaximiseRequested());
    }

    // Display tests
    @Test
    void displayOption_openPlayerOnDisplay() {
        String[] longArgs1 = {"--monitor-1", tempVideoPath};
        String[] shortArgs1 = {"-1", tempVideoPath};

        String[] longArgs2 = {"--monitor-2", tempVideoPath};
        String[] shortArgs2 = {"-2", tempVideoPath};

        CommandLineController longForm1 = new CommandLineController(longArgs1);
        CommandLineController shortForm1 = new CommandLineController(shortArgs1);

        CommandLineController longForm2 = new CommandLineController(longArgs2);
        CommandLineController shortForm2 = new CommandLineController(shortArgs2);

        assertEquals(1, longForm1.getDisplayId());
        assertEquals(1, shortForm1.getDisplayId());

        assertEquals(2, longForm2.getDisplayId());
        assertEquals(2, shortForm2.getDisplayId());
    }

    @Test
    void displayOptions_conflictingId_error() {
        String[] args = {"-1", "-2", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        String expected = "Only one display option can be used";

        assertEquals(expected, output.getErrorMessage());
        assertEquals(1, output.getExitCode());
    }

    // Video tests
    @Test
    void videoPlayer_noOptions_launchPlayer() {
        String[] args = {tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        assertTrue(output.shouldLaunchApplication());
    }

    @Test
    void videoPlayer_missingVideoFile_error() {
        String[] args = {"-a"};

        CommandLineController output = new CommandLineController(args);

        String expected = "No video file specified.";

        assertEquals(expected, output.getErrorMessage());
        assertEquals(1, output.getExitCode());
    }

    @Test
    void videoPlayer_nonexistantVideoFile_error() {
        String[] args = {"video.mp4"};

        CommandLineController output = new CommandLineController(args);

        String expected = "File not found: ";

        assertTrue(output.getErrorMessage().startsWith(expected));
        assertNull(output.getVideoFile());
        assertEquals(1, output.getExitCode());
    }

    @Test
    void videoPlayer_multipleVideoFiles_error() {
        String[] args = {tempVideoPath, tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        String expected = "Usage: VideoPlayer [options] [video-file]";

        assertEquals(expected, output.getErrorMessage());
        assertEquals(1, output.getExitCode());
    }

    @Test
    void videoPlayer_validVideoFile_launchPlayer() {
        String[] args = {tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        assertNull(output.getErrorMessage());
        assertEquals(0, output.getExitCode());
        assertTrue(output.shouldLaunchApplication());
    }
    
    @Test
    void videoPlayer_validBasicArgs_launchPlayer() {
        String[] args = {"-a", "-x", "-1", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        assertNull(output.getErrorMessage());
        assertEquals(0, output.getExitCode());
        assertTrue(output.shouldLaunchApplication());
    }

    @Test
    void frameProcessor_shortForm_parsed() {
        String[] args = {"-n", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        List<FPOptions> expected = List.of(FPOptions.NUMBER_FRAMES);

        assertEquals(expected, output.getFP());
    }

    @Test
    void frameProcessor_longForm_parsed() {
        String[] args = {"--number-frames", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        List<FPOptions> expected = List.of(FPOptions.NUMBER_FRAMES);

        assertEquals(expected, output.getFP());
    }

    @Test
    void frameProcessor_multipleShortForm_parsed() {
        String[] args = {"-ns", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        List<FPOptions> expected = List.of(
            FPOptions.NUMBER_FRAMES,
            FPOptions.SCRATCH_FRAMES
        );

        assertEquals(expected, output.getFP());
    }

    @Test
    void frameProcessor_multipleLongForm_parsed() {
        String[] args = {"--number-frames", "--scratch-frames", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        List<FPOptions> expected = List.of(
            FPOptions.NUMBER_FRAMES,
            FPOptions.SCRATCH_FRAMES
        );

        assertEquals(expected, output.getFP());
    }

    @Test
    void frameProcessor_separatedShortForm_parsed() {
        String[] args = {"-bv", "-p", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        List<FPOptions> expected = List.of(
            FPOptions.BLEED_FRAMES,
            FPOptions.VIGNETTE_FRAMES,
            FPOptions.PEPPER_FRAMES
        );

        assertEquals(expected, output.getFP());
    }

    @Test 
    void frameProcessor_combinedForm_parsed() {
        String[] args = {"-dj", "--mottle-frames", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        List<FPOptions> expected = List.of(
            FPOptions.DUST_FRAMES,
            FPOptions.JITTER_FRAMES,
            FPOptions.MOTTLE_FRAMES
        );

        assertEquals(expected, output.getFP());
    }

    @Test 
    void frameProcessor_repeatedOptions_parsed() {
        String[] args = {"-jfddn", tempVideoPath};

        CommandLineController output = new CommandLineController(args);

        List<FPOptions> expected = List.of(
            FPOptions.JITTER_FRAMES,
            FPOptions.FLICKER_FRAMES,
            FPOptions.DUST_FRAMES,
            FPOptions.DUST_FRAMES,
            FPOptions.NUMBER_FRAMES
        );

        assertEquals(expected, output.getFP());
    }

    @Test
    void frameProcessor_unknownOptions_error() {
        String[] shortArgs = {"-nzj", tempVideoPath};
        String[] longArgs = {"--number-frames", "--z-frames", "--jitter-frames", tempVideoPath};

        CommandLineController shortOutput = new CommandLineController(shortArgs);
        CommandLineController longOutput = new CommandLineController(longArgs);

        String expected = "Unknown option: ";

        assertTrue(shortOutput.getErrorMessage().startsWith(expected));
        assertTrue(longOutput.getErrorMessage().startsWith(expected));
        assertTrue(shortOutput.getFP().isEmpty());
        assertTrue(longOutput.getFP().isEmpty());
    }

    @Test
    void frameProcessor_wrongCaseOptions_error() {
        String[] shortArgs = {"-S", tempVideoPath};
        String[] longArgs = {"--SRATCH-FRAMES", tempVideoPath};

        CommandLineController shortOutput = new CommandLineController(shortArgs);
        CommandLineController longOutput = new CommandLineController(longArgs);

        String expected = "Unknown option: ";

        assertTrue(shortOutput.getErrorMessage().startsWith(expected));
        assertTrue(longOutput.getErrorMessage().startsWith(expected));
    }
}