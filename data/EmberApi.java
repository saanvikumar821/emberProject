package data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads stops and journeys from the Ember Public API. No libraries needed (Java 11+).
 *
 *   List<EmberApi.Stop> stops = EmberApi.searchStops("");               // popular stops
 *   List<EmberApi.Quote> buses = EmberApi.getQuotes(13, 42, LocalDate.now());
 *
 * Both calls go over the network and can take a few seconds, so call them from a
 * background thread (for example a SwingWorker), never directly from a button click.
 * They throw IOException if the API can't be reached; catch it and either show a
 * message or fall back to sampleQuotes().
 *
 * Read-only: this class never books or pays for anything.
 *
 * Quick check from the project folder:
 *   javac data/EmberApi.java
 *   java data.EmberApi
 */
public final class EmberApi {

    private EmberApi() { }

    public static final String BASE_URL = "https://api.ember.to";
    public static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).build();

    // ---- What the rest of the app works with ----

    /** A place you can travel from or to, e.g. id 13 = "Dundee (City Centre)". */
    public static final class Stop {
        public final int id;
        public final String name;
        public final String region;

        public Stop(int id, String name, String region) {
            this.id = id;
            this.name = name;
            this.region = region;
        }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) { return o instanceof Stop && ((Stop) o).id == id; }
        @Override public int hashCode() { return id; }
    }

    /** One bookable departure between two stops, with its price and free seats. */
    public static final class Quote {
        /** Ember's ID for this exact bus on this exact day. Save it with a booking. */
        public String tripUid = "";
        /** The stop IDs that were searched for (the same ones passed to getQuotes). */
        public int originId, destinationId;
        /** The exact boarding and arrival points, e.g. "Dundee Slessor Gardens". */
        public String originStop = "", destinationStop = "";
        /** Scheduled times, already in UK time. */
        public ZonedDateTime departure, arrival;
        /** Route number ("E1"), and the destination board ("Edinburgh", "via Edinburgh Airport"). */
        public String route = "", boardText = "", boardVia = "";
        /** Prices in pence: 975 means £9.75. */
        public int adultPence, childPence;
        public int seatsLeft;
        public boolean electric, wifi, toilet;
        public String numberPlate = "";

        public String departureTime() { return HM.format(departure); }
        public String arrivalTime() { return HM.format(arrival); }
        public long minutes() { return Duration.between(departure, arrival).toMinutes(); }
        public String price() { return String.format("£%.2f", adultPence / 100.0); }

        @Override public String toString() {
            return departureTime() + " -> " + arrivalTime() + "  " + route + "  " + price()
                    + "  " + seatsLeft + " seats  (" + originStop + " to " + destinationStop + ")";
        }
    }

    // ---- Calls to the API ----

    /**
     * Stops matching some text, most popular first. Pass "" for the most popular stops.
     * Uses GET /v1/locations/search/
     */
    public static List<Stop> searchStops(String text) throws IOException {
        String query = text == null ? "" : text.trim();
        String url = BASE_URL + "/v1/locations/search/?limit=50";
        if (!query.isEmpty()) url += "&query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        return parseStops(get(url));
    }

    /**
     * Every bookable departure from one stop to another on a given day (UK time),
     * earliest first. Prices are for one adult.
     * Uses GET /v1/quotes/
     */
    public static List<Quote> getQuotes(int originId, int destinationId, LocalDate day) throws IOException {
        Instant from = day.atStartOfDay(LONDON).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(LONDON).toInstant();
        String url = BASE_URL + "/v1/quotes/?origin=" + originId + "&destination=" + destinationId
                + "&departure_date_from=" + from + "&departure_date_to=" + to + "&adult=1";
        return parseQuotes(get(url), originId, destinationId);
    }

    /**
     * Offline stand-in for demos with no internet: a real Dundee (13) to Edinburgh (42)
     * weekday timetable captured from the API, moved onto the day you ask for.
     */
    public static List<Quote> sampleQuotes(LocalDate day) {
        String[][] rows = {
            {"05:16", "06:58", "47"}, {"06:17", "08:06", "37"}, {"06:28", "08:39", "43"},
            {"07:17", "09:02", "40"}, {"07:26", "09:34", "37"}, {"08:16", "10:01", "41"},
            {"08:23", "10:35", "7"}, {"09:16", "11:01", "42"}, {"10:16", "12:01", "43"},
            {"12:25", "14:08", "52"}, {"14:19", "16:06", "45"}, {"16:19", "18:02", "52"},
            {"17:16", "18:55", "52"}, {"18:17", "19:53", "52"},
        };
        List<Quote> out = new ArrayList<>();
        for (String[] r : rows) {
            Quote q = new Quote();
            q.tripUid = "SAMPLE-" + day + "-" + r[0];
            q.originId = 13;
            q.destinationId = 42;
            q.originStop = "Dundee Slessor Gardens";
            q.destinationStop = "George Street (Stop GL)";
            q.departure = day.atTime(LocalTime.parse(r[0])).atZone(LONDON);
            q.arrival = day.atTime(LocalTime.parse(r[1])).atZone(LONDON);
            q.route = "E1";
            q.boardText = "Edinburgh";
            q.boardVia = "via Edinburgh Airport";
            q.adultPence = 975;
            q.childPence = 487;
            q.seatsLeft = Integer.parseInt(r[2]);
            q.electric = q.wifi = q.toilet = true;
            out.add(q);
        }
        return out;
    }

    // ---- Turning the API's JSON into Stops and Quotes ----

    @SuppressWarnings("unchecked")
    static List<Stop> parseStops(String json) throws IOException {
        try {
            List<Stop> out = new ArrayList<>();
            for (Object o : (List<Object>) Json.parse(json)) {
                String name = str(o, "name");
                if (!name.isEmpty()) out.add(new Stop((int) num(o, "id"), name, str(o, "region_name")));
            }
            return out;
        } catch (RuntimeException e) {
            throw new IOException("Unexpected reply from the Ember API", e);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Quote> parseQuotes(String json, int originId, int destinationId) throws IOException {
        try {
            List<Quote> out = new ArrayList<>();
            Object list = at(Json.parse(json), "quotes");
            if (!(list instanceof List)) return out;
            for (Object q : (List<Object>) list) {
                Object legs = at(q, "legs");
                if (!(legs instanceof List) || ((List<Object>) legs).isEmpty()) continue;
                if (Boolean.FALSE.equals(at(q, "bookable"))) continue;
                Object leg = ((List<Object>) legs).get(0);   // Ember has no connections yet: one leg
                Quote x = new Quote();
                x.tripUid = str(leg, "trip_uid");
                x.originId = originId;
                x.destinationId = destinationId;
                x.originStop = str(leg, "origin", "name");
                x.destinationStop = str(leg, "destination", "name");
                x.departure = OffsetDateTime.parse(str(leg, "departure", "scheduled")).atZoneSameInstant(LONDON);
                x.arrival = OffsetDateTime.parse(str(leg, "arrival", "scheduled")).atZoneSameInstant(LONDON);
                x.route = str(leg, "description", "destination_board_content", "route_number");
                x.boardText = str(leg, "description", "destination_board_content", "primary_text");
                x.boardVia = str(leg, "description", "destination_board_content", "secondary_text");
                x.numberPlate = str(leg, "description", "number_plate");
                x.electric = Boolean.TRUE.equals(at(leg, "description", "is_electric"));
                x.wifi = Boolean.TRUE.equals(at(leg, "description", "amenities", "has_wifi"));
                x.toilet = Boolean.TRUE.equals(at(leg, "description", "amenities", "has_toilet"));
                x.adultPence = (int) num(q, "prices", "adult");
                x.childPence = (int) num(q, "prices", "child");
                x.seatsLeft = (int) num(q, "availability", "seat");
                out.add(x);
            }
            return out;
        } catch (RuntimeException e) {
            throw new IOException("Unexpected reply from the Ember API", e);
        }
    }

    private static String get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Ember API replied " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted", e);
        }
    }

    // ---- Small JSON reader (objects become Map, arrays List, numbers Double) ----

    @SuppressWarnings("unchecked")
    private static Object at(Object o, String... path) {
        for (String key : path) {
            if (!(o instanceof Map)) return null;
            o = ((Map<String, Object>) o).get(key);
        }
        return o;
    }

    private static String str(Object o, String... path) {
        Object v = at(o, path);
        return v instanceof String ? (String) v : "";
    }

    private static double num(Object o, String... path) {
        Object v = at(o, path);
        return v instanceof Double ? (Double) v : 0;
    }

    private static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String text) { return new Json(text).value(); }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String key = string();
                    ws();
                    i++;                          // the colon
                    m.put(key, value());
                    ws();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (i == start) throw new IllegalArgumentException("Bad JSON at position " + i);
            return Double.valueOf(s.substring(start, i));
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++;                                  // opening quote
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            }
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }

    // ---- Try it: java data.EmberApi ----

    public static void main(String[] args) {
        LocalDate tomorrow = LocalDate.now(LONDON).plusDays(1);
        try {
            List<Stop> stops = searchStops("");
            System.out.println(stops.size() + " stops, e.g.:");
            for (Stop s : stops.subList(0, Math.min(5, stops.size()))) System.out.println("  " + s.id + "  " + s.name);
            List<Quote> quotes = getQuotes(13, 42, tomorrow);
            System.out.println(quotes.size() + " buses Dundee -> Edinburgh on " + tomorrow + ":");
            for (Quote q : quotes) System.out.println("  " + q);
        } catch (IOException e) {
            System.out.println("Could not reach the Ember API: " + e.getMessage());
            System.out.println("Sample timetable instead:");
            for (Quote q : sampleQuotes(tomorrow)) System.out.println("  " + q);
        }
    }
}
