package fish.payara.monitoring.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MinutesDatasetTest {

    @Test
    public void emptyMinutesAddOne() {
        MinutesDataset min1 = MinutesDataset.EMPTY.add(minuteAfter(emptySeconds(60), 10));
        assertEquals(1, min1.size());
        assertEquals(min1.firstIndex(), min1.lastIndex());
        assertEquals(1, min1.firstIndex());
        assertEquals(1, min1.lastIndex());
        assertEquals(10, min1.getMinimum(min1.firstIndex()));
        assertEquals(600, min1.getMaximum(min1.firstIndex()));
        assertEquals(305d, min1.getAverage(min1.firstIndex()), 0.1d);
        assertEquals(60, min1.getNumberOfPoints(min1.firstIndex()));
        assertEquals(60000L, min1.getTime(min1.firstIndex()));
    }

    private static EmptyDataset emptySeconds(int capacity) {
        return new EmptyDataset("instance", new Series("series"), capacity);
    }

    private static SeriesDataset minuteAfter(SeriesDataset minuteBefore, long delta) {
        SeriesDataset set = minuteBefore;
        boolean empty = minuteBefore.size() == 0;
        long time = empty ? 59500L : minuteBefore.lastTime();
        long value = empty ? 0L : minuteBefore.lastValue();
        for (int i = 0; i < 60; i++) {
            time += 1000L;
            value += delta;
            set = set.add(time, value);
        }
        return set;
    }
}
