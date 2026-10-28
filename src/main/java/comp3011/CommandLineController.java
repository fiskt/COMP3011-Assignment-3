/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Jerry Wu, a1911280
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created in the VideoPlayerApp's main function to receive and parse the video player's command-line arguments.
 *
 * <p>
 * Extracts the video file and supported launch options, reports invalid input
 * or help text, and exposes the resulting launch configuration via methods to the
 * {@link VideoPlayerApp}.
 * </p>
 */
public class CommandLineController {
    private final String[] args;

    private boolean helpRequested;
    private boolean audioRequested;
    private boolean maximiseRequested;
    private Integer displayId;
    private File videoFile;
    private String errorMessage;

    // enum of the options so we can map the long and short
    // inputs to the same value
    enum FPOptions {
        NUMBER_FRAMES,
        SCRATCH_FRAMES,
        FLICKER_FRAMES,
        BLACK_AND_WHITE,
        YELLOW_FRAMES,
        VIGNETTE_FRAMES,
        DUST_FRAMES,
        JITTER_FRAMES,
        MOTTLE_FRAMES,
        BLEED_FRAMES,
        PEPPER_FRAMES
    }

    private List<FPOptions> frameProcessors = new ArrayList<>();

    // Maps for short form input (E.g. -n -s) and long form input (E.g. -number-frames)
    // Ref: https://www.geeksforgeeks.org/java/map-interface-in-java/
    private static final Map<Character, FPOptions> shortFrameProcessors = new HashMap<>();
    private static final Map<String, FPOptions> longFrameProcessors = new HashMap<>();

    // Add options when this is first run
    static {
        // Add short form frame processors to the map
        shortFrameProcessors.put('n', FPOptions.NUMBER_FRAMES);
        shortFrameProcessors.put('s', FPOptions.SCRATCH_FRAMES);
        shortFrameProcessors.put('f', FPOptions.FLICKER_FRAMES);
        shortFrameProcessors.put('w', FPOptions.BLACK_AND_WHITE);
        shortFrameProcessors.put('y', FPOptions.YELLOW_FRAMES);
        shortFrameProcessors.put('v', FPOptions.VIGNETTE_FRAMES);
        shortFrameProcessors.put('d', FPOptions.DUST_FRAMES);
        shortFrameProcessors.put('j', FPOptions.JITTER_FRAMES);
        shortFrameProcessors.put('m', FPOptions.MOTTLE_FRAMES);
        shortFrameProcessors.put('b', FPOptions.BLEED_FRAMES);
        shortFrameProcessors.put('p', FPOptions.PEPPER_FRAMES);
        
        // Add long form frame processor to the map
        longFrameProcessors.put("--number-frames", FPOptions.NUMBER_FRAMES);
        longFrameProcessors.put("--scratch-frames", FPOptions.SCRATCH_FRAMES);
        longFrameProcessors.put("--flicker-frames", FPOptions.FLICKER_FRAMES);
        longFrameProcessors.put("--black-and-white", FPOptions.BLACK_AND_WHITE);
        longFrameProcessors.put("--yellow-frames", FPOptions.YELLOW_FRAMES);
        longFrameProcessors.put("--vignette-frames", FPOptions.VIGNETTE_FRAMES);
        longFrameProcessors.put("--dust-frames", FPOptions.DUST_FRAMES);
        longFrameProcessors.put("--jitter-frames", FPOptions.JITTER_FRAMES);
        longFrameProcessors.put("--mottle-frames", FPOptions.MOTTLE_FRAMES);
        longFrameProcessors.put("--bleed-frames", FPOptions.BLEED_FRAMES);
        longFrameProcessors.put("--pepper-frames", FPOptions.PEPPER_FRAMES);
    }

    public CommandLineController(String[] args) {
        this.args = args.clone();
        parse();
        if (errorMessage != null) {
            System.out.println(errorMessage);
        }
        if (helpRequested) {
            printHelp();
        }
    }

    public File getVideoFile() {
        return videoFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getExitCode() {
        return errorMessage == null ? 0 : 1;
    }

    public Integer getDisplayId() {
        return displayId;
    }

    public boolean isAudioRequested() {
        return audioRequested;
    }

    public boolean isMaximiseRequested() {
        return maximiseRequested;
    }

    public boolean shouldLaunchApplication() {
        return errorMessage == null && videoFile != null && !helpRequested;
    }

    private void parseShortFP(String s, List<FPOptions> parsedFP) {
        List<FPOptions> temp = new ArrayList<>();

        // Exclude the "-" and turn the input string into a char array
        char[] fp = s.substring(1).toCharArray();
        for (char curr : fp) {
            // Get the enum result from the short form map
            FPOptions fpOption = shortFrameProcessors.get(curr);
            if (fpOption == null) {
                // If the inputed option doesnt exist in the map, set temp to null
                // and set the error
                errorMessage = "Unknown option: " + s;
                temp = null;
                break;
            }
            temp.add(fpOption);
        }
        // The input options are all valid, add them all to the 
        // frame processor list
        if (temp != null) parsedFP.addAll(temp);
    }

    private void parseLongFP(String s, List<FPOptions> parsedFP) {
        if (longFrameProcessors.containsKey(s)) {
            parsedFP.add(longFrameProcessors.get(s));
        } else {
            errorMessage = "Unknown option: " + s;
        }
    }

    private void parse() {
        List<String> videoFiles = new ArrayList<>();
        List<FPOptions> parsedFP = new ArrayList<>();

        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                helpRequested = true;
            } else if ("-a".equals(arg) || "--audio".equals(arg)) {
                audioRequested = true;
            } else if ("-x".equals(arg) || "--maximise".equals(arg)) {
                maximiseRequested = true;
            } else if ("-1".equals(arg) || "--monitor-1".equals(arg)) {
                setDisplayId(1);
            } else if ("-2".equals(arg) || "--monitor-2".equals(arg)) {
                setDisplayId(2);
            } else if (arg.startsWith("--")) {
                parseLongFP(arg, parsedFP);
            } else if (!arg.startsWith("--") && arg.startsWith("-") && arg.length() > 1) {
                parseShortFP(arg, parsedFP);
            } else if (arg.startsWith("-")) {
                errorMessage = "Unknown option: " + arg;
            } else {
                videoFiles.add(arg);
            }
        }

        if (videoFiles.size() > 1) {
            errorMessage = "Usage: VideoPlayer [options] [video-file]";
        } else if (videoFiles.size() == 1) {
            videoFile = new File(videoFiles.get(0));
            if (!videoFile.isFile()) {
                errorMessage = "File not found: " + videoFile.getPath();
                videoFile = null;
            }
        } else {
            if (!helpRequested) {
                errorMessage = "No video file specified.";
            }
        }

        if (errorMessage == null) frameProcessors.addAll(parsedFP);
    }

    public List<FPOptions> getFP() {
        return frameProcessors;
    }

    private void setDisplayId(int displayId) {
        if (this.displayId != null && this.displayId != displayId) {
            errorMessage = "Only one display option can be used";
            return;
        }
        this.displayId = displayId;
    }

    private void printHelp() {
        System.out.println("Usage: VideoPlayer [options] [video-file]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help         Show this help message");
        System.out.println("  -a, --audio        Play audio");
        System.out.println("  -x, --maximise     Open the player maximised");
        System.out.println("  -1, --monitor-1    Open the player on display 1");
        System.out.println("  -2, --monitor-2    Open the player on display 2");
        System.out.println();
        System.out.println("Frame processors:");
        System.out.println("  -n, --number-frames    Render the frame number onto each frame");
        System.out.println("  -s, --scratch-frames   Render vertical film scratches");
        System.out.println("  -f, --flicker-frames   Randomly dim frames");
        System.out.println("  -w, --black-and-white  Convert frames to black and white");
        System.out.println("  -y, --yellow-frames    Apply a warmer colour temperature");
        System.out.println("  -v, --vignette-frames  Darken the frame edges");
        System.out.println("  -d, --dust-frames      Render dust and hair marks");
        System.out.println("  -j, --jitter-frames    Randomly displace frames by a few pixels");
        System.out.println("  -m, --mottle-frames    Add cloudy emulsion mottling");
        System.out.println("  -b, --bleed-frames     Bleed light into frames");
        System.out.println("  -p, --pepper-frames    Pepper frames with dark spots/blotches");
        System.out.println();
        System.out.println("Frame processors are applied in command-line order and may be repeated.");
        System.out.println("Example: -nssnfwyvdjmbp numbers, scratches twice, numbers again, flickers,");
        System.out.println("converts, warms, vignettes, dusts, jitters, mottles, bleeds, then peppers.");
    }
}