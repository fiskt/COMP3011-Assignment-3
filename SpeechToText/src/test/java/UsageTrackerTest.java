import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import comp3011.assignment1.UsageTracker;

public class UsageTrackerTest {

    @Test 
    void usageTracker_startsAtZero() {
        UsageTracker usageTracker = new UsageTracker();

        double expected = 0.0;

        assertEquals(expected, usageTracker.getUsageSeconds(), 0.0001);
    }

    @Test 
    void usageTracker_addUsageSeconds_accumulates() {
        UsageTracker usageTracker = new UsageTracker();
        usageTracker.addSeconds(5.4);
        usageTracker.addSeconds(18.2);
        usageTracker.addSeconds(21.3);
        usageTracker.addSeconds(0.0);

        double expected = 44.9;
        assertEquals(expected, usageTracker.getUsageSeconds(), 0.0001);
    }

}
