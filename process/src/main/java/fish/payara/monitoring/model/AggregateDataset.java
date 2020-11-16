package fish.payara.monitoring.model;

import static java.lang.System.arraycopy;

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

    /**
     * Used when appending a new point to a dataset of single capacity.
     *
     * @param predecessor The dataset which has 1 datapoint less than this will have
     * @param capacity    the single capacity (no sliding window) only used in case the predecessor was the empty set
     * @param offset      the offset of this dataset; this is the same as the one of the predecessor (if set) or the one
     *                    of the first datapoint
     * @param firstTime   the first time of this dataset; this is the same as the one of the predecessor (if set) or the
     *                    one of the first datapoint
     */
    protected AggregateDataset(AggregateDataset<T> predecessor, int capacity, int offset, long firstTime) {
        boolean first = predecessor.size == 0;
        this.mins = first ? new long[capacity] : predecessor.mins;
        this.maxs = first ? new long[capacity] : predecessor.maxs;
        this.avgs = first ? new double[capacity] : predecessor.avgs;
        this.points = first ? new int[capacity] : predecessor.points;
        this.offset = offset;
        this.size = predecessor.size + 1;
        this.firstTime = firstTime;
    }

    /**
     * Used when copying a section of the base dataset as a basis for a new sliding window dataset
     *
     * @param predecessor The dataset which has the number of points that should be used as sliding window, all but the
     *                    oldest point of this will be copied as a basis for the created dataset
     * @param newCapacity must be reasonable larger then the length of the base dataset (usually 1.5 to 2 times
     *                    the length)
     */
    protected AggregateDataset(AggregateDataset<T> predecessor, int newCapacity) {
        if (newCapacity <= predecessor.size) {
            throw new IllegalArgumentException("Capacity must be larger than the sliding window size (length of base dataset)");
        }
        this.size = predecessor.size; // window length stays the same and is the single capacity
        this.offset = 0; // a copy should always occur when a full interval has been recorded, this is either when single capacity is filled up or when double capacity has slided to the end
        this.mins = new long[newCapacity];
        this.maxs = new long[newCapacity];
        this.avgs = new double[newCapacity];
        this.points = new int[newCapacity];
        AggregateDataset<T> src = predecessor;
        int from = src.firstIndex();
        int to = Math.max(from + src.size, src.capacity());
        int copyLength = to - from;
        this.firstTime = predecessor.getTime(from);
        arraycopy(src.mins, from, mins, 0, copyLength);
        arraycopy(src.maxs, from, maxs, 0, copyLength);
        arraycopy(src.avgs, from, avgs, 0, copyLength);
        arraycopy(src.points, from, points, 0, copyLength);
        if (copyLength < src.size) { // there was a wrap that needs copying as well
            copyLength = src.size - copyLength;
            arraycopy(src.mins, 0, mins, to, copyLength);
            arraycopy(src.maxs, 0, maxs, to, copyLength);
            arraycopy(src.avgs, 0, avgs, to, copyLength);
            arraycopy(src.points,0, points, to, copyLength);
        }
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
        return size == 0 ? -1 : (offset + size - 1) % capacity();
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
    public abstract long getTime(int index);

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
        int from = firstIndex();
        if (!isWrapped()) {
            arraycopy(src, from, dest, 0, size);
            return dest;
        }
        int len = capacity() - from;
        arraycopy(src, from, dest, 0, len);
        arraycopy(src, 0, dest, len, size - len);
        return dest;
    }

}
