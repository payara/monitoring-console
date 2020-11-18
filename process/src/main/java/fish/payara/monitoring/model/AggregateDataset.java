package fish.payara.monitoring.model;

import static java.lang.System.arraycopy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Base class for specific {@link AggregateDataset}s.
 *
 * An {@link AggregateDataset} is statistical data. The original data points are aggregated within intervals to minimum,
 * maximum and average values as well as remembering the number of points used to compute these aggregates.
 *
 * When adding to the dataset new instances are created each time but many will share the same underlying arrays to
 * store data. Once data is written to an array index that particular element is never changed again. Therefore it can
 * be shared. When index exceeds the capacity or would override a used index after a wrap the data from the arrays are
 * copied to a new array of twice the capacity. In this new underlying storage a window of single capacity will slide
 * until it reached again the end of the double capacity at which point a single capacity is copied again. This then
 * continues endlessly offering effectively immutable sliding windows of single capacity size.
 *
 * @author Jan Bernitt
 *
 * @param <T> Type of the extending subclass of this type (self referencing type)
 *
 * @see MinutesDataset
 * @see HoursDataset
 */
public abstract class AggregateDataset<T extends AggregateDataset<T>> {

    private final long[] mins;
    private final long[] maxs;
    private final double[] avgs;
    private final int[] points;
    private final long firstTime;
    /**
     * Data on or after the offset index is in the {@link #firstTime} hour when recording started,
     * data before the offset index is in the hour after the one when recording started
     */
    protected final int offset;
    private int size;

    /**
     * Used to create a new empty dataset.
     */
    protected AggregateDataset() {
        this.mins = new long[0];
        this.maxs = new long[0];
        this.avgs = new double[0];
        this.points = new int[0];
        this.firstTime = -1L;
        this.offset = -1;
        this.size = 0;
    }

    protected AggregateDataset(int windowSize, AggregateDataset<T> predecessor, long time) {
        boolean empty = predecessor.isEmpty();
        int requiredSize = empty ? 1 : predecessor.indexOf(time) + 1;
        if (!empty && time < predecessor.lastTime()) {
            throw new IllegalArgumentException("Cannot set data in the past");
        }
        int availableSize = empty ? 0 : predecessor.capacity() - predecessor.offset;
        int requiredSizeInc = requiredSize - predecessor.size;
        this.size = Math.min(windowSize, requiredSize);
        if (requiredSize <= availableSize) {
            int lastIndex = predecessor.lastIndex() +  requiredSizeInc;
            this.mins = predecessor.mins;
            this.maxs = predecessor.maxs;
            this.avgs = predecessor.avgs;
            this.points = predecessor.points;
            this.offset = Math.max(predecessor.offset, lastIndex + 1 - windowSize);
            this.firstTime = predecessor.getTime(this.offset);
        } else {
            int newCapacity = empty ? windowSize : windowSize * 2;
            this.mins = new long[newCapacity];
            this.maxs = new long[newCapacity];
            this.avgs = new double[newCapacity];
            this.points = new int[newCapacity];
            int copyLength = Math.min(predecessor.size, windowSize - 1);
            int from = predecessor.lastIndex() - copyLength + 1;
            AggregateDataset<T> src = predecessor;
            arraycopy(src.mins, from, mins, 0, copyLength);
            arraycopy(src.maxs, from, maxs, 0, copyLength);
            arraycopy(src.avgs, from, avgs, 0, copyLength);
            arraycopy(src.points, from, points, 0, copyLength);
            this.offset = 0;
            this.firstTime = predecessor.isEmpty() ? time : predecessor.getTime(from);
        }
    }

    private int indexOf(long time) {
        return (int)((time - getTime(firstIndex())) / getIntervalLength());
    }

    /**
     * Used when adding points to the sliding window.
     *
     * @param predecessor Must be an instance created with the copy constructor for new sliding windows
     */
    protected AggregateDataset(AggregateDataset<T> predecessor) {
        this.mins = predecessor.mins;
        this.maxs = predecessor.maxs;
        this.avgs = predecessor.avgs;
        this.points = predecessor.points;
        this.size = predecessor.size; // length stays the same but we slide a window
        this.offset = predecessor.offset + 1;
        this.firstTime = predecessor.getTime(predecessor.firstIndex() + 1);
    }

    protected final void setEntry(int points, long min, long max, double avg) {
        int index = lastIndex();
        this.points[index] = points;
        this.mins[index] = min;
        this.maxs[index] = max;
        this.avgs[index] = avg;
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The minimum value of all values recorded in the provided minute
     */
    public final long getMinimum(int index) {
        return mins[index];
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The maximum value of all values recorded in the provided minute
     */
    public final long getMaximum(int index) {
        return maxs[index];
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The average of all values recorded in the provided minute
     */
    public final double getAverage(int index) {
        return avgs[index];
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The number of points recorded in the provided minute that were used to compute min, max and average values.
     */
    public final int getNumberOfPoints(int index) {
        return points[index];
    }

    /**
     * @return The start of the minute when this {@link MinutesDataset} started to be filled with data
     */
    public final long firstTime() {
        return firstTime;
    }

    public final long lastTime() {
        return getTime(lastIndex());
    }

    public final boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return the number of data points in this set.
     *
     * For example the number of minutes in an hour (0-60)
     * or the numbers of hours in a day (0-24)
     */
    public final int size() {
        return size;
    }

    /**
     * @return the total number of points that can be stored. This refers to the underlying shared array. Different
     *         instances of {@link AggregateDataset} instances reference to sections within the same array using
     *         {@link #offset} and {@link #size()}. While adding points writes to the array only so far unused cells
     *         are written and referenced. Once written the cell does not change.
     */
    public final int capacity() {
        return points.length;
    }

    /**
     * @return Is the chronologically first index in the underlying array that is used by this dataset.
     *
     *         No index smaller than this must be passed to any of the methods accepting an index value.
     *
     *         An empty set returns -1
     */
    public final int firstIndex() {
        return offset;
    }

    /**
     * @return Is the chronologically last index in the underlying array that is used by this dataset.
     *
     *         No index larger than this must be passed to any of the methods accepting an index value.
     *
     *         Note that this can "wrap" and return a lower index than {@link #firstIndex()} which means the array
     *         elements from 0 to the last index are chronologically after the element at the array which start
     *         chronologically at the {@link #firstIndex()}.
     *
     *         An empty set returns -1
     */
    public final int lastIndex() {
        return capacity() == 0 ? -1 : (offset + size - 1) % capacity();
    }

    /**
     * @return true if the set has wrapped, that means at least one element chronologically following the item at the
     *         end of the array is stored between index zero and {@link #offset}.
     */
    public final boolean isWrapped() {
        return offset + size >= capacity();
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return the timestamp value pointing to the beginning of the aggregated interval at the given index, for example
     *         for a minute aggregate this points to to the second at the start of the minute.
     *
     *         This value is computed based on the {@link #firstTime()}, the {@link #firstIndex()} and the interval
     *         between data points which depends on the subclass.
     */
    public final long getTime(int index) {
        if (index < offset)
            throw new IllegalArgumentException("No data availabel for absolute index");
        return firstTime() + ((index - offset) * getIntervalLength());
    }

    /**
     * @return The duration of the time-span that got aggregated into one data point (index). This is the same for any
     *         of the indexes as the interval aggregated is constant/regular.
     */
    public abstract long getIntervalLength();

    /**
     * @return minimum values in chronological order
     */
    public long[] mins() {
        return copy(mins, new long[size]);
    }

    /**
     * @return maximum values in chronological order
     */
    public long[] maxs() {
        return copy(maxs, new long[size]);
    }

    /**
     * @return average values in chronological order
     */
    public double[] avgs() {
        return copy(avgs, new double[size]);
    }

    /**
     * @return number of points aggregated into min/max/avg in chronological order
     */
    public int[] numberOfPoints() {
        return copy(points, new int[size]);
    }

    private <A> A copy(A src, A dest) {
        arraycopy(src, firstIndex(), dest, 0, size);
        return dest;
    }

    /**
     * @return the estimated memory in bytes used by this dataset. Since the object layout in memory is a JVM internal
     *         this is only a rough estimation based on the fields. References are assumed to use 8 bytes. Padding is
     *         not included.
     */
    public int estimatedBytesMemory() {
        return size * 8 * 3 + size * 4 + 16;
    }

    @Override
    public String toString() {
        String str = getClass().getSimpleName() + "["+ size() + "]";
        if (!isEmpty()) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            str += "[" + formatter.format(Instant.ofEpochMilli(firstTime()).atOffset(ZoneOffset.UTC)) + "-"
                    + formatter.format(Instant.ofEpochMilli(lastTime()).atOffset(ZoneOffset.UTC)) + "]";
        }
        return str;
    }
}
