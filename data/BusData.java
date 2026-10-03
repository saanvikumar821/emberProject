package data;

public class BusData {

    public static class Trip {
        public final String dep;
        public final String arr;
        public final double price;

        public Trip(String dep, String arr, double price) {
            this.dep = dep;
            this.arr = arr;
            this.price = price;
        }
    }

    public static final String[] STOPS = {
        "Dundee",
        "Edinburgh",
        "Glasgow",
        "Perth"
    };

    public static final Trip[] TRIPS = {
        new Trip("06:15", "07:50", 8.40),
        new Trip("07:15", "08:50", 9.80),
        new Trip("08:15", "09:50", 9.80),
        new Trip("09:15", "10:50", 7.60)
    };
}
