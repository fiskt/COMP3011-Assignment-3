package comp3011.assignment1;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class TokenCounter {
    private AtomicLong inputTokens = new AtomicLong(0); 
    private AtomicLong outputTokens = new AtomicLong(0);

    public void addTokens(long inTokens, long outTokens) {
        inputTokens.addAndGet(inTokens);
        outputTokens.addAndGet(outTokens);
    }

    public long getInputTokens() {
        return inputTokens.get();
    }

    public long getOutputTokens() {
        return outputTokens.get();
    }
}
