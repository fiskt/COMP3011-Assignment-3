package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

import comp3011.CommandLineController.FPOptions;

class FrameProcessorThreadTest {

    // AI assisted (Claude) - placeholder video info
    private static final InfoVideo TEST_VIDEO_INFO = new InfoVideo(
            "test-video", 100,
            640, 480,
            Frame.DEPTH_UBYTE, 3, 640 * 3,
            24.0, 24, 41_667,
            0);

    @Test
    void mottleBleedProcessors_separateInstancePerWorker() throws Exception {
        List<FPOptions> options = List.of(
                FPOptions.NUMBER_FRAMES,   // no thread sharing problem so should stay shared
                FPOptions.MOTTLE_FRAMES,   // mottle 1: each thread needs its own instance of FrameMottler
                FPOptions.BLEED_FRAMES,    // bleed 1: each thread needs its own instance of FrameBleeder
                FPOptions.MOTTLE_FRAMES    // mottle 2: each thread needs another local instance
        );

        VideoPlayerModel model = new VideoPlayerModel(
                false, options,
                (width, height) -> {},
                image -> {},
                status -> {},
                (playing, paused) -> {},
                audioEnabled -> {});

        List<FrameProcessor> workerAProcessors = model.createWorkProcessors(TEST_VIDEO_INFO);
        List<FrameProcessor> workerBProcessors = model.createWorkProcessors(TEST_VIDEO_INFO);

        // Expecting each worker to have 4 processors:
        // number frames, local mottle frames 1, local bleed frames, local mottle frames 2
        int expected = 4;

        assertEquals(expected, workerAProcessors.size());
        assertEquals(expected, workerBProcessors.size());

        // Number frames don't need local instances because it doesn't have a shared mutable state
        assertSame(workerAProcessors.get(0), workerBProcessors.get(0));

        // Mottle frames need local instances because a ready flag is set before the arrays
        // are fully complete, and another thread can access that unfinished array in a shared instance
        FrameMottler workerAMottle1 = (FrameMottler) workerAProcessors.get(1);
        FrameMottler workerBMottle1 = (FrameMottler) workerBProcessors.get(1);
        assertNotSame(workerAMottle1, workerBMottle1);

        // The separate thread instances at the same index must have the same instanceIndex
        // so the pattern is identical 
        assertEquals(workerAMottle1.instanceIndex(), workerBMottle1.instanceIndex());

        // Bleed frames need local instances because it reuses brightMask, horizontalBlurMask, blurredMask
        // across calls, multiple threads calling process() on the same instance would overwrite 
        // progress
        FrameBleeder workerABleed = (FrameBleeder) workerAProcessors.get(2);
        FrameBleeder workerBBleed = (FrameBleeder) workerBProcessors.get(2);
        assertNotSame(workerABleed, workerBBleed);

        // Different mottle frame instances for a single thread can't have identical instanceIndex 
        // because we want each instance of mottle frame to apply a distinct pattern
        FrameMottler workerAMottle2 = (FrameMottler) workerAProcessors.get(3);
        assertNotEquals(workerAMottle1.instanceIndex(), workerAMottle2.instanceIndex());
    }
}