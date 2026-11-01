package comp3011;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;

import org.bytedeco.javacv.Frame;


public class FrameSubtitler extends FrameProcessor {
    private volatile SubtitleStore subtitleStore;

    public void setSubtitleStore(SubtitleStore s) {
        this.subtitleStore = s;
    }

    @Override 
    public void process(Frame frame, InfoFrame info) throws Exception {
        if (frame.image == null || frame.image.length == 0 || frame.image[0] == null) return;
        if (subtitleStore == null) return;

        if (!(frame.image[0] instanceof ByteBuffer pixels)) {
            throw new Exception("FrameSubtitler requires byte-backed image frames");
        }

        String text = subtitleStore.activeSubtitleAt(info.timestampUs());
        if (text == null) return;

        BufferedImage textImage = renderText(text);
        compositeOntoFrame(pixels, textImage);
    }

    // AI assisted (Claude)
    private BufferedImage renderText(String text) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 24);
 
        BufferedImage measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measuringGraphics = measuring.createGraphics();
        measuringGraphics.setFont(font);
        FontMetrics metrics = measuringGraphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        measuringGraphics.dispose();
 
        BufferedImage image = new BufferedImage(
                Math.max(1, textWidth), Math.max(1, textHeight), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.drawString(text, 0, metrics.getAscent());
        g.dispose();
 
        return image;
    }

    // AI assisted (Claude)
    private void compositeOntoFrame(ByteBuffer pixels, BufferedImage textImage) {
        int frameWidth = videoInfo.imageWidth();
        int frameHeight = videoInfo.imageHeight();
        int channels = videoInfo.imageChannels();
        int stride = videoInfo.imageStride();
 
        int startX = (frameWidth - textImage.getWidth()) / 2;
        int startY = frameHeight - 40 - textImage.getHeight();
 
        for (int ty = 0; ty < textImage.getHeight(); ty++) {
            int frameY = startY + ty;
            if (frameY < 0 || frameY >= frameHeight) {
                continue;
            }
            for (int tx = 0; tx < textImage.getWidth(); tx++) {
                int frameX = startX + tx;
                if (frameX < 0 || frameX >= frameWidth) {
                    continue;
                }
 
                int argb = textImage.getRGB(tx, ty);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue; // nothing drawn here - leave the frame untouched
                }
 
                int red = (argb >> 16) & 0xFF;
                int green = (argb >> 8) & 0xFF;
                int blue = argb & 0xFF;
 
                int base = frameY * stride + frameX * channels;
                // JavaCV supplies these decoded frames in BGR byte order.
                pixels.put(base, (byte) blue);
                pixels.put(base + 1, (byte) green);
                pixels.put(base + 2, (byte) red);
            }
        }
    }

}