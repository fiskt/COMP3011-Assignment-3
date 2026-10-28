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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.JavaFXFrameConverter;

import comp3011.CommandLineController.FPOptions;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.image.Image;

/**
 * The model part of the video player model-view-controller architecture.
 *
 * <p>
 * Owns the FFmpeg decoder, playback clock, seeking logic, audio scheduling,
 * and frame processing - which it all manages through cooperative multi-tasking
 * on a single thread. It publishes decoded frames and playback state through
 * callbacks supplied by its owning {@link VideoPlayerController}, keeping it
 * decoupled from the JavaFX view.
 * </p>
 */
public class VideoPlayerModel {
    private static final long NO_SEEK_REQUEST = -1; // Sentinel value used when no seek position is active.
    private static final long FIVE_SECONDS_US = 5_000_000L;
    private static final long AUDIO_LEAD_NS = 30_000_000L;

    private final List<FrameProcessor> frameProcessors;
    private final Queue<PendingAudio> pendingAudio = new ArrayDeque<>();
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private final Consumer<Image> frameReadyHandler;
    private final Consumer<String> statusChangedHandler;
    private final BiConsumer<Boolean, Boolean> playbackStateChangedHandler;
    private final Consumer<Boolean> audioOutputStateChangedHandler;

    // This is the critical wiring that allows the framework (JavaFX) to call
    // into our model logic every time it goes around its event loop. Every GUI
    // system has an event loop so that button clicks, key presses and window
    // resizes can be responded to. Most offer ways of running additional logic
    // in either idle time (when there are no pending UI events to process) or
    // on a regular heart beat, such as with this JavaFX AnimationTimer. Here
    // we just create a little anonymous local subclass and override the handle
    // method to call the method we want run every heart beat.
    private final AnimationTimer playbackTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            pumpPlayback(now);
        }
    };

    private File videoFile; // Path to the media that comes from the command line
    private FFmpegFrameGrabber grabber; // This is the 3rd party video media decoder. It deals in JavaCV Frame objects.
    private JavaFXFrameConverter converter; // Takes Frame objects to JavaFX Image objects, which can be put on screen.
    private AudioPlayer audioPlayer; // This is ours. It has some real time buffering smarts.
    private PreparedFrame preparedFrame; // Ours, but is just an Image with some meta-data added.
    
    private volatile boolean playbackOpen; // True when playback is happening.
    private boolean pauseRequested;
    private boolean audioOutputEnabled;
    private boolean audioAvailable;
    private boolean frameProcessorsInitialised;
    private long currentTimestampUs;
    private long videoDurationUs = NO_SEEK_REQUEST;
    private int videoFrameDurationUs;
    private double frameRate;
    private int intFrameRate;
    private int totalVideoFrames;
    private long firstTimestampUs = NO_SEEK_REQUEST;
    private long logicalPlaybackBaseUs;
    private long playbackStartNs;
    private long pauseStartedNs;
    private long relativeSeekBaseUs = NO_SEEK_REQUEST;

    // Get the number of available cores 
    // Save 1 core for basic functionality of the app
    // and 1 for rendering
    int numCores = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);

    // Source - https://stackoverflow.com/a/51551555
    // Posted by Vinay
    // Retrieved 2026-10-07, License - CC BY-SA 4.0
    // Creates numCores permits
    // Any thread calling frameSlots.acquire() is blocked until a permit is free
    private final Semaphore frameSlots = new Semaphore(numCores);

    // New thread for getting the frames
    private Thread getFramesThread;

    // Volatile guarantees that the threads will see the updated status
    private volatile boolean isGettingFrames;

    // Used for initialising the local FrameBleeder, FrameMottler objects for each thread
    // because they are created after readNextVideoFrame, which initialises the frame processors, is run
    // volatile guarantees that each thread sees the copy when making the new objects
    private volatile InfoVideo infoCopy = null;

    private final List<Thread> processFramesThreads = new ArrayList<>();

    private record RawFrame(
        Frame frame,
        InfoFrame info, 
        long logicalTimestampUs,
        long targetTimeNs
    ) {}

    // Blocking queue for storing numCores number of raw frames
    private final BlockingQueue<RawFrame> rawFrames = new ArrayBlockingQueue<>(numCores);

    // Map for storing processed frames
    private final ConcurrentHashMap<Integer, PreparedFrame> processedFrames = new ConcurrentHashMap<>();

    private static int NO_FRAME_SET = -1;
    private volatile int nextFrameNumber = NO_FRAME_SET;

    // FrameBleed and FrameMottler uses shared variables that break the player when
    // multiple threads try to access the shared arrays
    // This function loops through the frame processors array built from processing the input
    // For any FrameBleeder, FrameMottler inputs: creates new instances for the thread and adds them to the array
    // For any other frame processor: just adds them to the array
    public List<FrameProcessor> createWorkProcessors(InfoVideo infoVideo) throws Exception {
        List<FrameProcessor> workProcessors = new ArrayList<>();
        int mottleIndex = 0;

        for (FrameProcessor fp : frameProcessors) {
            if (fp instanceof FrameBleeder) {
                // Create its own instance of FrameBleeder
                FrameBleeder ownBleeder = new FrameBleeder();
                ownBleeder.initialise(infoVideo);
                workProcessors.add(ownBleeder);
            } else if (fp instanceof FrameMottler) {
                // Create its own instance of FrameMottler
                FrameMottler ownMottler = new FrameMottler(mottleIndex);
                ownMottler.initialise(infoVideo);
                workProcessors.add(ownMottler);
                mottleIndex++;
            } else {
                // Other frame processors dont need a local instance, so just add them
                workProcessors.add(fp);
            }
        }

        return workProcessors;
    }

    private void getFramesLoop() {
        while (isGettingFrames) {
            RawFrame rf;
            try {
                // Try to read the next frame in the video file
                rf = readNextVideoFrame();
            } catch (Exception e) {
                // Source - https://stackoverflow.com/a/69494970
                // Posted by Neeraj Gahlawat
                // Retrieved 2026-10-06, License - CC BY-SA 4.0
                e.printStackTrace();
                break;
            }

            // If the frame is null (end of video or playback end), break the loop
            if (rf == null) break;

            try {
                // Try to add the frame to a free slot in rawFrames
                // If there are no free slots (rawFrames blocks put), then
                // the thread will wait for the free slot
                rawFrames.put(rf);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processFramesLoop() {
        JavaFXFrameConverter localConverter = new JavaFXFrameConverter();
        List<FrameProcessor> workProcessors = null;
        try {
            while (isGettingFrames) {
                RawFrame rf;
                try {
                    // Get a raw frame from the raw frames array
                    // If it can't get one within 100 ms, give up and try again
                    // This prevents the loop from getting stuck waiting for a new raw frame
                    // and missing isGettingFrames turning false
                    // Source - https://www.geeksforgeeks.org/java/arrayblockingqueue-poll-method-in-java/
                    rf = rawFrames.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (rf == null) continue;

                try {
                    if (workProcessors == null) {
                        workProcessors = createWorkProcessors(infoCopy);
                    }

                    // From the original readNextVideoFrame function
                    processFrame(rf.frame(), rf.info(), workProcessors);
                    Image image = localConverter.convert(rf.frame());

                    PreparedFrame pf = new PreparedFrame(
                        image, 
                        rf.info().frameNumber(), 
                        rf.info().timestampUs(), 
                        rf.logicalTimestampUs(), 
                        rf.targetTimeNs(), 
                        System.nanoTime()
                    );

                    // Blocks the put() if there are already numCores elements in processedFrames
                    // Waits for a processed frame to be displayed before adding another one
                    // This stops processedFrames from growing too large and causes memory issues
                    frameSlots.acquire();
                    processedFrames.put(pf.frameNumber(), pf);
                } catch (InterruptedException e) {
                    // Exits the try/catch block if processedFrames.put fails
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        } finally {
            localConverter.close();
        }
    }

    private void startFrameThreads() {
        isGettingFrames = true;

        // Create and start a thread for getFramesLoop 
        getFramesThread = new Thread(this::getFramesLoop);
        getFramesThread.start();

        // Create and start numCores threads for processFramesLoop
        for (int i = 0; i < numCores; i++) {
            Thread workerThread = new Thread(this::processFramesLoop);
            workerThread.start();

            // Add the threads to an array
            processFramesThreads.add(workerThread);
        }
    }

    private void stopFrameThreads() {
        isGettingFrames = false;
        rawFrames.clear();
        processedFrames.clear();

        if (getFramesThread != null) {
            getFramesThread.interrupt();
            try {
                getFramesThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        for (Thread workerThread : processFramesThreads) {
            workerThread.interrupt();
            try {
                workerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        processFramesThreads.clear();
    }

    public VideoPlayerModel(
            boolean audioEnabled,
            // Take in the frame processor list as a parameter
            List<CommandLineController.FPOptions> frameProcessorOptions,
            BiConsumer<Integer, Integer> videoSizeChangedHandler,
            Consumer<Image> frameReadyHandler,
            Consumer<String> statusChangedHandler,
            BiConsumer<Boolean, Boolean> playbackStateChangedHandler,
            Consumer<Boolean> audioOutputStateChangedHandler) {
        audioOutputEnabled = audioEnabled;
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        this.frameReadyHandler = frameReadyHandler;
        this.statusChangedHandler = statusChangedHandler;
        this.playbackStateChangedHandler = playbackStateChangedHandler;
        this.audioOutputStateChangedHandler = audioOutputStateChangedHandler;
        this.frameProcessors = new ArrayList<>();

        // Loop through the frame processor list from 
        // CommandLineController and add the frame processors
        for (FPOptions option : frameProcessorOptions) {
            frameProcessors.add(newFP(option));
        }
    }

    private FrameProcessor newFP(CommandLineController.FPOptions fpOption) {
        return switch (fpOption) {
            case NUMBER_FRAMES -> new FrameNumberer();
            case SCRATCH_FRAMES -> new FrameScratcher();
            case FLICKER_FRAMES -> new FrameFlickerer();
            case BLACK_AND_WHITE -> new FrameBlackAndWhiter();
            case YELLOW_FRAMES -> new FrameYellower();
            case VIGNETTE_FRAMES -> new FrameVignetter();
            case DUST_FRAMES -> new FrameDuster();
            case JITTER_FRAMES -> new FrameJitterer();
            case MOTTLE_FRAMES -> new FrameMottler();
            case BLEED_FRAMES -> new FrameBleeder();
            case PEPPER_FRAMES -> new FramePepperer();
        };
    }

    public void play(File file) {
        videoFile = file;
        videoDurationUs = NO_SEEK_REQUEST;
        videoFrameDurationUs = 0;
        startPlayback(0, false, NO_SEEK_REQUEST);
    }

    public void startOver() {
        if (videoFile == null) {
            return;
        }

        seekTo(0);
    }

    public void backFiveSeconds() {
        seekRelative(-FIVE_SECONDS_US);
    }

    public void forwardFiveSeconds() {
        seekRelative(FIVE_SECONDS_US);
    }

    public void togglePause() {
        if (!playbackOpen) {
            if (videoFile != null) {
                startPlayback(displayableSeekTimestamp(currentTimestampUs), false, currentTimestampUs);
            }
            return;
        }

        pauseRequested = !pauseRequested;
        relativeSeekBaseUs = NO_SEEK_REQUEST;

        if (pauseRequested) {
            pauseStartedNs = System.nanoTime();
            flushAudioOutput();
        } else {
            resumePlaybackClock(System.nanoTime());
        }

        notifyPlaybackStateChanged();
    }

    public void toggleAudioOutput() {
        audioOutputEnabled = !audioOutputEnabled;
        pendingAudio.clear();
        flushAudioOutput();
        notifyAudioOutputStateChanged();
    }

    public boolean isAudioOutputEnabled() {
        return audioOutputEnabled;
    }

    public void stopPlayback() {
        closePlaybackResources();
        currentTimestampUs = 0;
        pauseRequested = false;
        notifyStatusChanged("Stopped");
        notifyFrameReady(null);
        notifyPlaybackStateChanged();
    }

    public void shutdown() {
        closePlaybackResources();
    }

    private void seekRelative(long offsetUs) {
        if (videoFile == null) {
            return;
        }

        long baseTimestampUs = relativeSeekBaseUs != NO_SEEK_REQUEST
                ? relativeSeekBaseUs
                : currentTimestampUs;
        seekTo(baseTimestampUs + offsetUs);
    }

    private void seekTo(long timestampUs) {
        long logicalTimestampUs = clampSeekTimestamp(timestampUs);
        long grabTimestampUs = displayableSeekTimestamp(logicalTimestampUs);

        if (!playbackOpen) {
            startPlayback(grabTimestampUs, pauseRequested, logicalTimestampUs);
            return;
        }

        try {
            // Stop getting and processing frames first
            stopFrameThreads();

            grabber.setTimestamp(grabTimestampUs);
            resetPlaybackClock(logicalTimestampUs);
            flushAudioOutput();

            // Start getting and processing frames once the resets are done
            startFrameThreads();

            prepareNextFrame();
            notifyPlaybackStateChanged();
        } catch (Exception e) {
            handlePlaybackError(e);
        }
    }

    private void startPlayback(long startTimestampUs, boolean initiallyPaused, long initialRelativeSeekBaseUs) {
        closePlaybackResources();

        pauseRequested = initiallyPaused;
        currentTimestampUs = initialRelativeSeekBaseUs != NO_SEEK_REQUEST
                ? initialRelativeSeekBaseUs
                : startTimestampUs;
        relativeSeekBaseUs = initialRelativeSeekBaseUs;

        notifyStatusChanged(videoFile.getName());

        try {
            openPlaybackResources(startTimestampUs);
            resetPlaybackClock(currentTimestampUs);
            playbackOpen = true;

            // Start getting and processing frames once playback opens
            startFrameThreads();

            playbackTimer.start();
            prepareNextFrame();
            notifyPlaybackStateChanged();
        } catch (Exception e) {
            handlePlaybackError(e);
        }
    }

    private void openPlaybackResources(long startTimestampUs) throws Exception {
        converter = new JavaFXFrameConverter();
        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.setImageMode(FrameGrabber.ImageMode.COLOR);
        grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
        grabber.start();

        notifyVideoSizeChanged(grabber.getImageWidth(), grabber.getImageHeight());

        frameRate = grabber.getFrameRate();
        intFrameRate = (int) Math.round(frameRate);
        videoFrameDurationUs = frameRate > 0
                ? (int) Math.round(1_000_000.0 / frameRate)
                : 0;
        totalVideoFrames = grabber.getLengthInVideoFrames();

        long durationUs = grabber.getLengthInTime();
        videoDurationUs = durationUs > 0
                ? durationUs
                : NO_SEEK_REQUEST;

        audioAvailable = grabber.hasAudio();
        audioPlayer = new AudioPlayer();
        if (audioAvailable) {
            audioPlayer.open(grabber.getSampleRate(), grabber.getAudioChannels());
        }

        if (startTimestampUs > 0) {
            grabber.setTimestamp(startTimestampUs);
        }
    }

    private void resetPlaybackClock(long logicalTimestampUs) {
        pendingAudio.clear();
        preparedFrame = null;
        currentTimestampUs = logicalTimestampUs;
        relativeSeekBaseUs = logicalTimestampUs;
        firstTimestampUs = NO_SEEK_REQUEST;

        // Reset the frame index, arrays and semaphore
        nextFrameNumber = NO_FRAME_SET;
        processedFrames.clear();
        frameSlots.drainPermits();
        frameSlots.release(numCores);

        logicalPlaybackBaseUs = logicalTimestampUs;
        playbackStartNs = 0;
        pauseStartedNs = pauseRequested ? System.nanoTime() : 0;
    }

    private void resumePlaybackClock(long now) {
        if (pauseStartedNs > 0 && playbackStartNs > 0) {
            playbackStartNs += now - pauseStartedNs;
        }
        pauseStartedNs = 0;
    }

    private void prepareNextFrame() {
        if (!playbackOpen || preparedFrame != null) {
            return;
        }

        // Take the correct prepared frame from the processedFrames array
        PreparedFrame nextFrame = processedFrames.remove(nextFrameNumber);
        if (nextFrame != null) {
            preparedFrame = nextFrame;
            nextFrameNumber++;
            frameSlots.release();
        }
    }

    private RawFrame readNextVideoFrame() throws Exception {
        while (playbackOpen) {
            Frame frame = grabFrame();
            if (frame == null) {
                // Source - https://stackoverflow.com/a/32489845
                // Posted by Martin Pfeffer, modified by community. See post 'Timeline' for change history
                // Retrieved 2026-10-18, License - CC BY-SA 4.0
                //
                // Exception in thread "Thread-4" java.lang.IllegalStateException: Not on FX application thread;
                // This error occurs when running finishPlayback on a non FX thread
                // Platform.runLater fixes this error by running finishPlaybak on the FX thread
                // since only FX threads can modify UI
                Platform.runLater(this::finishPlayback);
                return null;
            }

            long timestampUs = grabber.getTimestamp();
            if (audioAvailable && frame.samples != null) {
                queueAudio(timestampUs, frame);
            }

            if (frame.image == null) {
                continue;
            }

            if (!frameProcessorsInitialised) {
                InfoVideo info = new InfoVideo(
                    mediaName(videoFile),
                    totalVideoFrames,
                    frame.imageWidth,
                    frame.imageHeight,
                    frame.imageDepth,
                    frame.imageChannels,
                    frame.imageStride,
                    frameRate,
                    intFrameRate,
                    videoFrameDurationUs,
                    grabber.getPixelFormat()
                );
                initialiseFrameProcessors(info);

                // Save a copy of the frame info so it can be used to initialise the 
                // local FrameBleeder, FrameMottler objects
                infoCopy = info;
                frameProcessorsInitialised = true;
            }

            if (firstTimestampUs == NO_SEEK_REQUEST) {
                firstTimestampUs = timestampUs;
                playbackStartNs = System.nanoTime();
                if (pauseRequested) {
                    pauseStartedNs = playbackStartNs;
                }
            }

            int frameNumber = grabber.getFrameNumber();
            if (nextFrameNumber == NO_FRAME_SET) nextFrameNumber = frameNumber;

            InfoFrame info = new InfoFrame(frameNumber, timestampUs);

            long relativeTimestampUs = Math.max(0, timestampUs - firstTimestampUs);
            long logicalTimestampUs = logicalPlaybackBaseUs + relativeTimestampUs;
            long targetTimeNs = playbackStartNs + relativeTimestampUs * 1_000L;

            return new RawFrame(frame.clone(), info, logicalTimestampUs, targetTimeNs);
        }

        return null;
    }

    // This is called on a heart beat by the GUI thread. We want to be co-operative here by (a) not blocking, and (b)
    // getting our required work out of the way quickly so that we can return control flow to the JavaFX event loop for
    // handling user interaction with the GUI and rendering. We have three jobs: (1) keep audio flowing if sound is on,
    // (2) display a frame if it is due and (3) prepare the next frame if we're in the window after the previous frame
    // has gone to the display. We don't buffer frames here, just handling them one at a time. Not a great architecture,
    // living on the edge a bit, but can't do much better on a single thread.
    private void pumpPlayback(long now) {
        if (!playbackOpen) {
            return;
        }

        // Task (1)
        writeDueAudio(now);

        // Task (2)
        if (preparedFrame != null && preparedFrame.targetTimeNs() <= now) {
            // We have a prepared frame ready to go and it is due (or just past due!) so get it up on screen ASAP!
            displayPreparedFrame(now);
        }

        // Task (3)
        // Take the next frame once preparedFrame has been displayed and set to null
        if (!pauseRequested && preparedFrame == null) {
            prepareNextFrame();
        }
    }

    private void displayPreparedFrame(long now) {
        PreparedFrame frame = preparedFrame;
        preparedFrame = null;
        currentTimestampUs = frame.logicalTimestampUs();
        relativeSeekBaseUs = NO_SEEK_REQUEST;

        // Dump some logging to the console once per second so that real time performance can be monitored.
        if (intFrameRate > 0 && frame.frameNumber() % intFrameRate == 0) {
            long remainingUs = (frame.targetTimeNs() - frame.preparedAtNs()) / 1_000L;
            if (remainingUs < 0) {
                System.out.printf("Frame headroom is \u001B[31m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            } else {
                System.out.printf("Frame headroom is \u001B[32m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            }
        }

        notifyFrameReady(frame.image());
    }

    private void queueAudio(long timestampUs, Frame frame) {
        if (!audioOutputEnabled || audioPlayer == null) {
            return;
        }

        byte[] samples = audioPlayer.copySamples(frame);
        if (samples.length > 0) {
            pendingAudio.add(new PendingAudio(timestampUs, samples));
        }
    }

    private void writeDueAudio(long now) {
        // Handle some obvious early exits
        if (!audioOutputEnabled || audioPlayer == null) {
            pendingAudio.clear();
            return;
        }
        if (pauseRequested || firstTimestampUs == NO_SEEK_REQUEST || playbackStartNs <= 0) {
            return;
        }

        // Here is the real time dependent logic
        long dueTimestampUs = firstTimestampUs + (now + AUDIO_LEAD_NS - playbackStartNs) / 1_000L;
        while (!pendingAudio.isEmpty()) {
            PendingAudio audio = pendingAudio.peek();
            if (audio.timestampUs() > dueTimestampUs) {
                return;
            }

            int written = audioPlayer.write(audio.samples(), audio.offset(), audio.remaining());
            if (written <= 0) {
                return;
            }

            audio.advance(written);
            if (!audio.finished()) {
                return;
            }
            pendingAudio.remove();
        }
    }

    private void finishPlayback() {
        long endTimestampUs = videoDurationUs != NO_SEEK_REQUEST
                ? videoDurationUs
                : currentTimestampUs;

        closePlaybackResources();
        pauseRequested = true;
        currentTimestampUs = endTimestampUs;
        relativeSeekBaseUs = endTimestampUs;
        notifyStatusChanged("Playback finished");
        notifyPlaybackStateChanged();
    }

    private void handlePlaybackError(Exception e) {
        e.printStackTrace();
        closePlaybackResources();
        pauseRequested = false;
        notifyStatusChanged("Error: " + e.getMessage());
        notifyPlaybackStateChanged();
    }

    private void closePlaybackResources() {    
        // Stop the getting and processing threads
        stopFrameThreads();

        playbackTimer.stop();
        playbackOpen = false;
        preparedFrame = null;
        pendingAudio.clear();
        firstTimestampUs = NO_SEEK_REQUEST;
        playbackStartNs = 0;
        pauseStartedNs = 0;
        audioAvailable = false;
        frameProcessorsInitialised = false;

        if (audioPlayer != null) {
            audioPlayer.close();
            audioPlayer = null;
        }

        if (grabber != null) {
            try {
                grabber.stop();
            } catch (Exception e) {
                // The resource is being closed; there is no useful recovery action.
            }
            try {
                grabber.close();
            } catch (Exception e) {
                // The resource is being closed; there is no useful recovery action.
            }
            grabber = null;
        }

        if (converter != null) {
            converter.close();
            converter = null;
        }
    }

    private String mediaName(File file) {
        String fileName = file.getName();
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionStart);
    }

    private long clampSeekTimestamp(long timestampUs) {
        long clampedTimestampUs = Math.max(0, timestampUs);
        long durationUs = videoDurationUs;
        if (durationUs != NO_SEEK_REQUEST) {
            clampedTimestampUs = Math.min(clampedTimestampUs, durationUs);
        }
        return clampedTimestampUs;
    }

    private long displayableSeekTimestamp(long logicalTimestampUs) {
        long durationUs = videoDurationUs;
        if (durationUs == NO_SEEK_REQUEST || logicalTimestampUs < durationUs) {
            return logicalTimestampUs;
        }

        int frameDurationUs = videoFrameDurationUs;
        if (frameDurationUs <= 0) {
            return Math.max(0, durationUs - 1);
        }

        return Math.max(0, durationUs - frameDurationUs);
    }

    private void processFrame(Frame frame, InfoFrame info, List<FrameProcessor> workProcessors) throws Exception {
        for (FrameProcessor processor : workProcessors) {
            processor.process(frame, info);
        }
    }

    private void initialiseFrameProcessors(InfoVideo videoInfo) throws Exception {
        for (FrameProcessor processor : frameProcessors) {
            processor.initialise(videoInfo);
        }
    }

    private Frame grabFrame() throws Exception {
        if (audioAvailable) {
            return grabber.grab();
        }
        return grabber.grabImage();
    }

    private void flushAudioOutput() {
        if (audioPlayer != null) {
            audioPlayer.flush();
        }
    }

    private void notifyVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    private void notifyFrameReady(Image image) {
        frameReadyHandler.accept(image);
    }

    private void notifyStatusChanged(String status) {
        statusChangedHandler.accept(status);
    }

    private void notifyPlaybackStateChanged() {
        playbackStateChangedHandler.accept(playbackOpen, pauseRequested);
    }

    private void notifyAudioOutputStateChanged() {
        audioOutputStateChangedHandler.accept(audioOutputEnabled);
    }

    private record PreparedFrame(
            Image image,
            int frameNumber,
            long mediaTimestampUs,
            long logicalTimestampUs,
            long targetTimeNs,
            long preparedAtNs) {
    }

    private static class PendingAudio {
        private final long timestampUs;
        private final byte[] samples;
        private int offset;

        PendingAudio(long timestampUs, byte[] samples) {
            this.timestampUs = timestampUs;
            this.samples = samples;
        }

        long timestampUs() {
            return timestampUs;
        }

        byte[] samples() {
            return samples;
        }

        int offset() {
            return offset;
        }

        int remaining() {
            return samples.length - offset;
        }

        void advance(int byteCount) {
            offset += byteCount;
        }

        boolean finished() {
            return offset >= samples.length;
        }
    }
}