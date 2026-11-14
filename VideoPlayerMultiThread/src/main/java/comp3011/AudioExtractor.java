package comp3011;

import java.io.File;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class AudioExtractor {
    // AI assisted (Claude)
    public static final Object NATIVE_OPEN_LOCK = new Object();

    public static File extractAudio(File videoFile) throws Exception {
        File audioFile = File.createTempFile("audio", ".wav");
        audioFile.deleteOnExit();

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
        synchronized (NATIVE_OPEN_LOCK) {
            grabber.start();
        }

        if (!grabber.hasAudio()) {
            grabber.stop();
            grabber.release();
            grabber.close();
            return null;
        }

        // AI assisted (Claude) and
        // Source - https://bytedeco.org/javacv/apidocs/org/bytedeco/javacv/FFmpegFrameRecorder.html
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(audioFile, grabber.getAudioChannels());
        recorder.setFormat("wav");
        recorder.setSampleRate(grabber.getSampleRate());
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);
        synchronized (NATIVE_OPEN_LOCK) {
            recorder.start();
        }

        Frame audioFrame;
        while ((audioFrame = grabber.grabSamples()) != null) {
            recorder.record(audioFrame);
        }

        recorder.stop();
        recorder.release();
        recorder.close();

        grabber.stop();
        grabber.release();
        grabber.close();

        return audioFile;
    }
}
