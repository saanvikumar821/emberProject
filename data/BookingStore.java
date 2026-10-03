package data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import data.EmberApi.Quote;
import data.EmberApi.Stop;

/**
 * In-memory store for completed bookings.
 *
 * Newest bookings come first in {@link #all()} so screens can render
 * "Recent" without sorting. Ticket IDs are generated sequentially.
 */
public final class BookingStore {

    private BookingStore() { }

    private static final List<Booking> ALL = new ArrayList<>();
    private static int counter = 1000;

    /**
     * Records a new booking and returns it. Uses the given origin/destination
     * stops and quote, plus the price actually paid (after any discount).
     */
    public static synchronized Booking record(Stop from,
                                              Stop to,
                                              Quote q,
                                              int pricePaidPence) {
        String id = "DEMO-" + (++counter);
        Booking b = new Booking(
                id,
                from.id,
                to.id,
                q.route,
                q.dep,
                pricePaidPence,
                q.pence);
        ALL.add(0, b);           // newest first
        return b;
    }

    /** All bookings, newest first. */
    public static synchronized List<Booking> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL));
    }

    /** How many bookings have been made. */
    public static synchronized int count() {
        return ALL.size();
    }

    /**
     * True if the user has ever booked this origin/destination/route combo.
     */
    public static synchronized boolean hasBooked(Stop from, Stop to, String route) {
        for (Booking b : ALL) {
            if (b.originId == from.id
                    && b.destinationId == to.id
                    && b.route.equals(route == null ? "" : route)) {
                return true;
            }
        }
        return false;
    }

    /** Total spent across all bookings, in pence. */
    public static synchronized int totalSpentPence() {
        int sum = 0;
        for (Booking b : ALL) sum += b.pricePaidPence;
        return sum;
    }

    /** Total saved (streak discounts) across all bookings, in pence. */
    public static synchronized int totalSavedPence() {
        int sum = 0;
        for (Booking b : ALL) sum += b.discountPence();
        return sum;
    }
}