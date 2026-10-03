package data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public class Booking implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String tripId;
    public final int originId;
    public final int destinationId;
    public final String route;
    public final ZonedDateTime departure;
    public final int pricePaidPence;
    public final int listPricePence;
    public final LocalDateTime bookedAt;

    public Booking(String tripId,
                   int originId,
                   int destinationId,
                   String route,
                   ZonedDateTime departure,
                   int pricePaidPence,
                   int listPricePence) {
        this.tripId = tripId;
        this.originId = originId;
        this.destinationId = destinationId;
        this.route = route == null ? "" : route;
        this.departure = departure;
        this.pricePaidPence = pricePaidPence;
        this.listPricePence = listPricePence;
        this.bookedAt = LocalDateTime.now(EmberApi.LONDON);
    }

    /** How much the streak reward knocked off (0 if none). */
    public int discountPence() {
        return listPricePence - pricePaidPence;
    }

    @Override
    public String toString() {
        return tripId + " " + originId + "->" + destinationId
                + " route=" + route + " dep=" + departure
                + " paid=" + pricePaidPence + "p";
    }
}