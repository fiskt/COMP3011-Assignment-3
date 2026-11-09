package comp3011;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;

import org.bytedeco.javacv.Frame;

import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.RenderingHints;

import java.util.ArrayList;
import java.util.List;

public class FrameSubtitler extends FrameProcessor {
    private static final int MAX_CHARS = 42;
    private static final int MAX_LINES = 2;

    private volatile boolean enabled;
    private volatile SubtitleStore subtitleStore;

    public void setSubtitleStore(SubtitleStore s) {
        this.subtitleStore = s;
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
    }

    public boolean getEnabled() {
        return enabled;
    }

    @Override 
    public void process(Frame frame, InfoFrame info) throws Exception {
        if (!enabled) return;
        if (frame.image == null || frame.image.length == 0 || frame.image[0] == null) return;
        if (subtitleStore == null) return;

        if (!(frame.image[0] instanceof ByteBuffer pixels)) {
            throw new Exception("FrameSubtitler requires byte-backed image frames");
        }

        String text = subtitleStore.activeSubtitleAt(info.timestampUs());
        if (text == null) return;

        List<String> lines = wrapText(text);
        BufferedImage textImage = renderText(lines);
        compositeOntoFrame(pixels, textImage);
    }

    private List<String> wrapText(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        String[] words = text.trim().split("\\s+");

        for (String word : words) {
            int leadSpace = currentLine.isEmpty() ? 0 : 1;
            boolean onLastLine = (lines.size() == MAX_LINES - 1);

            // Only wrap to a new line if we haven't used up our line budget yet.
            // On the last line, keep appending rather than dropping overflow.
            if (!onLastLine && currentLine.length() + leadSpace + word.length() > MAX_CHARS) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
            }

            if (!currentLine.isEmpty()) currentLine.append(' ');
            currentLine.append(word);
        }

        if (!currentLine.isEmpty()) lines.add(currentLine.toString());
        return lines;
    }

    // AI assisted (Claude)
    private BufferedImage renderText(List<String> lines) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
 
        BufferedImage measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measuringGraphics = measuring.createGraphics();
        measuringGraphics.setFont(font);
        FontMetrics metrics = measuringGraphics.getFontMetrics();
 
        int maxLineWidth = 0;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, metrics.stringWidth(line));
        }
        int lineHeight = metrics.getHeight();
        int imageWidth = maxLineWidth;
        int imageHeight = lineHeight * lines.size();
        measuringGraphics.dispose();
 
        BufferedImage image = new BufferedImage(
                Math.max(1, imageWidth), Math.max(1, imageHeight), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Source - https://stackoverflow.com/a/35222059
        // Posted by Eddo
        // Retrieved 2026-11-01, License - CC BY-SA 3.0
        Color fillColor = Color.white;
        Color outlineColor = Color.black;
        BasicStroke outlineStroke = new BasicStroke(2.0f);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(font);
        g.setStroke(outlineStroke);

        // getAscent() gets the distance from the font's baseline to the top of the tallest char
        // Starting y here means the first line's letters are fully inside the image
        int y = metrics.getAscent();
        for (String line : lines) {
            int x = (imageWidth - metrics.stringWidth(line)) / 2;

            // Converts the line of text into a shape
            GlyphVector glyphVector = font.createGlyphVector(g.getFontRenderContext(), line);
            Shape textShape = glyphVector.getOutline(x, y); 

            g.setColor(outlineColor);
            g.draw(textShape);

            g.setColor(fillColor);
            g.fill(textShape); 

            // Moves the baseline down by 1 whole line height
            y += lineHeight;
        }
 
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
                    continue;
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