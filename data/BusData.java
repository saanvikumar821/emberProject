package data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Everything the UI needs that isn't drawing: stops, live journeys from the
 * Ember Public API (with an offline sample), and the in-memory streak store.
 * The network methods block, so call them from a background thread.
 */
public final class BusData {
    private BusData() { }

    public static final String API = "https://api.ember.to";
    public static final ZoneId LONDON = ZoneId.of("Europe/London");
    public static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    // ---- Reward rules ----
    public static final int REWARD_AT = 5;       // streak needed for the discount
    public static final int FREE_TRIP_AT = 10;   // streak needed for a free trip
    public static final double DISCOUNT = 0.20;

    static final int MATCH_MINUTES = 15;         // departures this close to a streak's time are the same bus
    static final int SAMPLE_FROM = 13, SAMPLE_TO = 42;   // offline sample only covers Dundee -> Edinburgh

    // ---- Types ----

    public static class Stop {
        public final int id; public final String name;
        public Stop(int id, String name) { this.id = id; this.name = name; }
        public String shortName() { return name.replaceAll("\\s*\\(.*\\)", ""); }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) { return o instanceof Stop && ((Stop) o).id == id; }
        @Override public int hashCode() { return id; }
    }

    public static class Quote {
        public ZonedDateTime dep, arr;
        public String originStop = "", destStop = "", route = "", board = "", via = "", plate = "";
        public int pence, seats;
        public boolean wifi, toilet, electric;
        public String times() { return HM.format(dep) + " \u2192 " + HM.format(arr); }
        public String duration() {
            long m = Duration.between(dep, arr).toMinutes();
            return (m / 60) + "h " + (m % 60) + "m";
        }
    }

    /** One journey = one bus: a route plus a usual departure time. Tracks its streak and its lifetime bookings. */
    public static class Streak {
        public final Stop from, to; public final LocalTime time;
        public int count;      // current streak (days in a row); drives the discount
        public int bookings;   // lifetime bookings of this journey; drives its rarity level
        public Streak(Stop from, Stop to, LocalTime time, int count, int bookings) {
            this.from = from; this.to = to; this.time = time; this.count = count; this.bookings = bookings;
        }
        public String label() {
            return from.shortName() + " \u2192 " + to.shortName() + " \u00B7 " + HM.format(time);
        }
        /** Current rarity tier, or null if never booked. */
        public Rarity rarity() { return Rarity.forBookings(bookings); }
        /** The tier this journey is working towards, or null at max level. */
        public Rarity nextRarity() { Rarity r = rarity(); return r == null ? Rarity.COMMON : r.next(); }
        /** Progress (0..1) from the current tier to the next one; 1.0 at max level. */
        public double progress() {
            Rarity cur = rarity(), nxt = nextRarity();
            if (nxt == null) return 1.0;
            int base = cur == null ? 0 : cur.bookingsNeeded;
            return Math.min(1.0, (double) (bookings - base) / (nxt.bookingsNeeded - base));
        }
        public int bookingsToNext() {
            Rarity nxt = nextRarity();
            return nxt == null ? 0 : Math.max(0, nxt.bookingsNeeded - bookings);
        }
        /** Which icon id the UI should draw for this journey, picked from the destination. */
        public String iconId() {
            String n = to.name;
            if (n.contains("Airport")) return "PLANE";
            if (n.startsWith("Edinburgh")) return "CASTLE";
            if (n.startsWith("Glasgow")) return "SKYLINE";
            if (n.startsWith("Inverness") || n.startsWith("Fort William")) return "MOUNTAIN";
            if (n.startsWith("Perth") || n.startsWith("Kinross")) return "TREE";
            return "BUS";
        }
    }

    // ---- Stops ----

    /** Built-in stops (real Ember location IDs), used until or unless the live list loads. */
    public static List<Stop> fallbackStops() {
        return new ArrayList<>(Arrays.asList(FALLBACK_STOPS));
    }

    private static final Stop[] FALLBACK_STOPS = {
        new Stop(13, "Dundee (City Centre)"), new Stop(42, "Edinburgh (City Centre)"),
        new Stop(80, "Glasgow Bus Station"), new Stop(160, "Perth (City Centre)"),
        new Stop(174, "Aberdeen (City Centre)"), new Stop(452, "Inverness (City Centre)"),
        new Stop(49, "Edinburgh Airport"), new Stop(17, "Kinross Park and Ride"),
        new Stop(283, "Fort William (Town Centre)"),
    };

    /** Live stop list from GET /v1/locations/search/. Blocking; throws if the API can't be reached. */
    @SuppressWarnings("unchecked")
    public static List<Stop> fetchStops() throws Exception {
        List<Stop> out = new ArrayList<>();
        for (Object o : (List<Object>) Json.parse(httpGet(API + "/v1/locations/search/?limit=50"))) {
            String name = str(o, "name");
            if (!name.isEmpty()) out.add(new Stop((int) num(o, "id"), name));
        }
        return out;
    }

    // ---- Journeys ----

    /** Live departures for one day from GET /v1/quotes/. Blocking; throws if the API can't be reached. */
    @SuppressWarnings("unchecked")
    public static List<Quote> fetchQuotes(Stop from, Stop to, LocalDate day) throws Exception {
        Instant start = day.atStartOfDay(LONDON).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(LONDON).toInstant();
        String url = API + "/v1/quotes/?origin=" + from.id + "&destination=" + to.id
                + "&departure_date_from=" + start + "&departure_date_to=" + end + "&adult=1";

        List<Quote> out = new ArrayList<>();
        Object list = at(Json.parse(httpGet(url)), "quotes");
        if (!(list instanceof List)) return out;
        for (Object q : (List<Object>) list) {
            Object legs = at(q, "legs");
            if (!(legs instanceof List) || ((List<Object>) legs).isEmpty()) continue;
            if (Boolean.FALSE.equals(at(q, "bookable"))) continue;
            Object leg = ((List<Object>) legs).get(0);
            Quote x = new Quote();
            x.dep = OffsetDateTime.parse(str(leg, "departure", "scheduled")).atZoneSameInstant(LONDON);
            x.arr = OffsetDateTime.parse(str(leg, "arrival", "scheduled")).atZoneSameInstant(LONDON);
            x.originStop = str(leg, "origin", "name");
            x.destStop = str(leg, "destination", "name");
            x.route = str(leg, "description", "destination_board_content", "route_number");
            x.board = str(leg, "description", "destination_board_content", "primary_text");
            x.via = str(leg, "description", "destination_board_content", "secondary_text");
            x.plate = str(leg, "description", "number_plate");
            x.electric = Boolean.TRUE.equals(at(leg, "description", "is_electric"));
            x.wifi = Boolean.TRUE.equals(at(leg, "description", "amenities", "has_wifi"));
            x.toilet = Boolean.TRUE.equals(at(leg, "description", "amenities", "has_toilet"));
            x.pence = (int) num(q, "prices", "adult");
            x.seats = (int) num(q, "availability", "seat");
            out.add(x);
        }
        return out;
    }

    /**
     * Offline stand-in: a real Dundee to Edinburgh weekday timetable captured from the API.
     * Returns an empty list for any other route.
     */
    public static List<Quote> sampleQuotes(Stop from, Stop to, LocalDate day) {
        List<Quote> out = new ArrayList<>();
        if (from.id != SAMPLE_FROM || to.id != SAMPLE_TO) return out;
        String[][] rows = {
            {"05:16", "06:58", "47"}, {"06:17", "08:06", "37"}, {"06:28", "08:39", "43"},
            {"07:17", "09:02", "40"}, {"07:26", "09:34", "37"}, {"08:16", "10:01", "41"},
            {"08:23", "10:35", "7"}, {"09:16", "11:01", "42"}, {"10:16", "12:01", "43"},
            {"12:25", "14:08", "52"}, {"14:19", "16:06", "45"}, {"16:19", "18:02", "52"},
            {"17:16", "18:55", "52"}, {"18:17", "19:53", "52"},
        };
        for (String[] r : rows) {
            Quote q = new Quote();
            q.dep = day.atTime(LocalTime.parse(r[0])).atZone(LONDON);
            q.arr = day.atTime(LocalTime.parse(r[1])).atZone(LONDON);
            q.originStop = "Dundee Slessor Gardens";
            q.destStop = "George Street (Stop GL)";
            q.route = "E1"; q.board = "Edinburgh"; q.via = "via Edinburgh Airport";
            q.pence = 975; q.seats = Integer.parseInt(r[2]);
            q.electric = q.wifi = q.toilet = true;
            out.add(q);
        }
        return out;
    }

    // ---- Streaks (in memory) ----

    /** The current account's journeys (see AccountStore). */
    private static List<Streak> journeys() { return AccountStore.current().journeys; }

    /** All streaks, longest first. The returned list is a copy. */
    public static List<Streak> getStreaks() {
        List<Streak> copy = new ArrayList<>(journeys());
        copy.sort((a, b) -> b.count - a.count);
        return copy;
    }

    /** All journeys on the current account, most-booked first. The returned list is a copy. */
    public static List<Streak> getJourneys() {
        List<Streak> copy = new ArrayList<>(journeys());
        copy.sort((a, b) -> b.bookings - a.bookings);
        return copy;
    }

    /** The user's longest streak, or null if they have none. */
    public static Streak bestStreak() {
        List<Streak> all = getStreaks();
        return all.isEmpty() ? null : all.get(0);
    }

    /** The streak for a departure on this route (closest time within 15 minutes), or null. */
    public static Streak findStreak(Stop from, Stop to, LocalTime dep) {
        Streak best = null;
        long bestDiff = MATCH_MINUTES + 1;
        for (Streak s : journeys()) {
            if (!s.from.equals(from) || !s.to.equals(to)) continue;
            long diff = Math.abs(Duration.between(s.time, dep).toMinutes());
            if (diff < bestDiff) { best = s; bestDiff = diff; }
        }
        return best;
    }

    /** Records a booking: adds one day and one booking to the matching journey, or starts a new one. Saves the account. */
    public static Streak recordBooking(Stop from, Stop to, LocalTime dep) {
        Streak s = findStreak(from, to, dep);
        if (s == null) {
            s = new Streak(from, to, dep, 0, 0);
            journeys().add(s);
        }
        s.count++;
        s.bookings++;
        AccountStore.save();
        return s;
    }

    /** Short text for the table, e.g. "2 to 20% off" or "All unlocked". */
    public static String nextReward(int streak) {
        if (streak >= FREE_TRIP_AT) return "All unlocked";
        int next = streak < REWARD_AT ? REWARD_AT : FREE_TRIP_AT;
        return (next - streak) + " to " + (next == REWARD_AT ? "20% off" : "free trip");
    }

    // ---- HTTP ----

    private static String httpGet(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
        return resp.body();
    }

    // ---- Minimal JSON reader (objects become Map, arrays List, numbers Double) ----

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

    private static class Json {
        final String s; int i;
        Json(String s) { this.s = s; }
        static Object parse(String text) { return new Json(text).value(); }

        Object value() {
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
            if (i == start) throw new IllegalArgumentException("Bad JSON at " + i);
            return Double.valueOf(s.substring(start, i));
        }

        String string() {
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

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }
}