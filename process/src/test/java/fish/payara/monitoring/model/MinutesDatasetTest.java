package fish.payara.monitoring.model;

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.junit.Test;

public class MinutesDatasetTest {

    private static final Duration OFFSET_FROM_ABSOLUTE_ZERO = Duration.ofMinutes(30);

    @Test
    public void oneMinuteAggregate() {
        SeriesDataset set = createDatasetWithSeconds(60, 10);
        assertEquals(60, set.size());
        MinutesDataset min1 = set.getRecentMinutes();
        assertEquals(1, min1.size());
        assertEquals(min1.firstIndex(), min1.lastIndex());
        assertEquals(0, min1.firstIndex());
        assertEquals(0, min1.lastIndex());
        assertEquals(0, min1.getMinimum(min1.firstIndex()));
        assertEquals(590, min1.getMaximum(min1.firstIndex()));
        assertEquals(295d, min1.getAverage(min1.firstIndex()), 0.1d);
        assertEquals(60, min1.getNumberOfPoints(min1.firstIndex()));
        assertEquals(OFFSET_FROM_ABSOLUTE_ZERO.toMillis(), min1.getTime(min1.firstIndex()));
    }

    @Test
    public void twoMinutesAggregate() {
        SeriesDataset set = createDatasetWithSeconds(120, 10);
        assertEquals(60, set.size());
        MinutesDataset min1 = set.getRecentMinutes();
        assertEquals(2, min1.size());
        assertEquals(0, min1.firstIndex());
        assertEquals(1, min1.lastIndex());
        assertEquals(0, min1.getMinimum(min1.firstIndex()));
        assertEquals(590, min1.getMaximum(min1.firstIndex()));
        assertEquals(295d, min1.getAverage(min1.firstIndex()), 0.1d);
        assertEquals(60, min1.getNumberOfPoints(min1.firstIndex()));
        assertEquals(OFFSET_FROM_ABSOLUTE_ZERO.toMillis(), min1.getTime(min1.firstIndex()));
        assertEquals(600, min1.getMinimum(min1.lastIndex()));
        assertEquals(1190, min1.getMaximum(min1.lastIndex()));
        assertEquals(895d, min1.getAverage(min1.lastIndex()), 0.1d);
        assertEquals(60, min1.getNumberOfPoints(min1.lastIndex()));
        assertEquals(OFFSET_FROM_ABSOLUTE_ZERO.plusMinutes(1).toMillis(), min1.getTime(min1.lastIndex()));
    }

    @Test
    public void threeDaysAggregate() {
        int secondsIn3Days = (int) (Duration.ofDays(3).toMinutes() * 60);
        SeriesDataset set = createDatasetWithSeconds(secondsIn3Days, 10);
        assertEquals(3, set.getRecentMinutes().getRecentHours().getRecentDays().size());
    }

    private static SeriesDataset createDatasetWithSeconds(int secondsToAdd, int delta) {
        SeriesDataset set = emptySeconds(60);
        long time = OFFSET_FROM_ABSOLUTE_ZERO.toMillis();
        long value = 0L;
        for (int i = 0; i < secondsToAdd; i++) {
            set = set.add(time, value, true);
            time += 1000L;
            value += delta;
        }
        return set;
    }

    private static EmptyDataset emptySeconds(int capacity) {
        return new EmptyDataset("instance", new Series("series"), capacity);
    }
}
