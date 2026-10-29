package comp3011;

import org.bytedeco.javacv.Frame;

public class FrameSubtitler extends FrameProcessor {
    private SubtitleStore subtitleStore;

    public void setSubtitleStore(SubtitleStore s) {
        this.subtitleStore = s;
    }

    @Override 
    public void process(Frame frame, InfoFrame info) throws Exception {

    }
}