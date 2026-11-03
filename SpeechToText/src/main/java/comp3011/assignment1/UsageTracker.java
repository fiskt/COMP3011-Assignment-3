package comp3011.assignment1;
import java.util.concurrent.atomic.DoubleAdder;

import org.springframework.stereotype.Service;

@Service
public class UsageTracker {
    // Source - https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/DoubleAdder.html
    private final DoubleAdder usageSeconds = new DoubleAdder();

    public void addSeconds(double seconds) {
        usageSeconds.add(seconds);
    }

    public double getUsageSeconds() {
        return usageSeconds.sum();
    }
}
