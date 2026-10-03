package ui;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Hackathon mockup: Ember bus booking with streak rewards.
 * Run with:  java EmberMockup.java   (Java 11 or newer, no libraries needed)
 *
 * Stops and journeys are read live from the Ember Public API
 * (GET /v1/locations/search/ and GET /v1/quotes/). If the API can't be
 * reached, a small built-in sample timetable is shown instead.
 * Payment is simulated: nothing is ever sent to POST /v1/orders/.
 */
public class EmberMockup {

    static final String API = "https://api.ember.to";
    static final ZoneId LONDON = ZoneId.of("Europe/London");

    // ---- Palette (Ember green on a light, clean background) ----
    static final Color GREEN = new Color(0x4F917A);
    static final Color GREEN_DARK = new Color(0x3B7561);
    static final Color TINT = new Color(0xE8F2EE);
    static final Color BG = new Color(0xF5F7F6);
    static final Color CARD = Color.WHITE;
    static final Color BORDER = new Color(0xDDE5E1);
    static final Color TEXT = new Color(0x1D2B27);
    static final Color MUTED = new Color(0x66756F);
    static final Color AMBER = new Color(0xB86E00);

    // ---- Data ----
    static class Stop {
        final int id; final String name;
        Stop(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) { return o instanceof Stop && ((Stop) o).id == id; }
        @Override public int hashCode() { return id; }
    }

    static class Quote {
        ZonedDateTime dep, arr;
        String originStop = "", destStop = "", route = "", board = "", via = "", plate = "";
        int pence, seats;
        boolean wifi, toilet, electric;
        String times() { return HM.format(dep) + " → " + HM.format(arr); }
        String duration() {
            long m = Duration.between(dep, arr).toMinutes();
            return (m / 60) + "h " + (m % 60) + "m";
        }
    }

    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);

    // Used until (or unless) the live stop list loads. IDs are real Ember location IDs.
    static final Stop[] FALLBACK_STOPS = {
        new Stop(13, "Dundee (City Centre)"), new Stop(42, "Edinburgh (City Centre)"),
        new Stop(80, "Glasgow Bus Station"), new Stop(160, "Perth (City Centre)"),
        new Stop(174, "Aberdeen (City Centre)"), new Stop(452, "Inverness (City Centre)"),
        new Stop(49, "Edinburgh Airport"), new Stop(17, "Kinross Park and Ride"),
        new Stop(283, "Fort William (Town Centre)"),
    };

    // The demo user's "usual bus" and the reward rules.
    static final int USUAL_FROM = 13, USUAL_TO = 42;
    static final LocalTime USUAL_TIME = LocalTime.of(7, 17);
    static final int REWARD_AT = 5;      // streak needed for the discount
    static final int FREE_TRIP_AT = 10;  // streak needed for a free trip
    static final double DISCOUNT = 0.20;

    // ---- App state ----
    int streak = 4;
    List<Stop> stops = new ArrayList<>(Arrays.asList(FALLBACK_STOPS));
    Stop from = FALLBACK_STOPS[0], to = FALLBACK_STOPS[1];
    LocalDate date = LocalDate.now(LONDON).plusDays(1);
    List<Quote> quotes = new ArrayList<>();
    boolean loading, live;
    String error;
    int requestNo;
    Quote selected;
    boolean selectedIsUsual, justUnlocked;
    String screen = "search";

    final JFrame frame = new JFrame("Ember streaks – hackathon mockup");
    final Page page = new Page();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EmberMockup().start());
    }

    void start() {
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", TINT);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 780);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(page, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().setBackground(BG);
        frame.add(scroll, BorderLayout.CENTER);
        frame.add(navBar(), BorderLayout.SOUTH);
        frame.setVisible(true);
        loadStops();
        loadQuotes();
    }

    void show(String name, JComponent content) {
        screen = name;
        page.removeAll();
        page.add(content, BorderLayout.NORTH);
        page.revalidate();
        page.repaint();
        page.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
    }

    // ---- Talking to the Ember API ----

    static String httpGet(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
        return resp.body();
    }

    static String quotesUrl(Stop from, Stop to, LocalDate day) {
        Instant start = day.atStartOfDay(LONDON).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(LONDON).toInstant();
        return API + "/v1/quotes/?origin=" + from.id + "&destination=" + to.id
                + "&departure_date_from=" + start + "&departure_date_to=" + end + "&adult=1";
    }

    void loadStops() {
        new SwingWorker<List<Stop>, Void>() {
            @Override protected List<Stop> doInBackground() throws Exception {
                return parseStops(httpGet(API + "/v1/locations/search/?limit=50"));
            }
            @Override protected void done() {
                try {
                    List<Stop> loaded = get();
                    if (loaded.size() < 2) return;
                    if (!loaded.contains(from)) loaded.add(0, from);
                    if (!loaded.contains(to)) loaded.add(to);
                    stops = loaded;
                    if (screen.equals("search")) show("search", searchScreen());
                } catch (Exception ignored) { /* keep the fallback list */ }
            }
        }.execute();
    }

    void loadQuotes() {
        final int req = ++requestNo;
        final Stop f = from, t = to;
        final LocalDate d = date;
        quotes = new ArrayList<>();
        error = null;
        if (f.equals(t)) {
            loading = false;
            error = "Pick two different stops.";
            show("search", searchScreen());
            return;
        }
        loading = true;
        show("search", searchScreen());
        new SwingWorker<List<Quote>, Void>() {
            boolean gotLive = true;
            String problem;
            @Override protected List<Quote> doInBackground() {
                try {
                    return parseQuotes(httpGet(quotesUrl(f, t, d)));
                } catch (Exception e) {
                    gotLive = false;
                    if (f.id == USUAL_FROM && t.id == USUAL_TO) return sampleQuotes(d);
                    problem = "Couldn't reach the Ember API. Check your connection and try again.";
                    return new ArrayList<>();
                }
            }
            @Override protected void done() {
                if (req != requestNo) return;   // a newer search replaced this one
                List<Quote> result;
                try { result = get(); } catch (Exception e) { result = new ArrayList<>(); }
                ZonedDateTime now = ZonedDateTime.now(LONDON);
                result.removeIf(q -> q.dep.isBefore(now));
                quotes = result;
                live = gotLive;
                error = problem;
                loading = false;
                if (screen.equals("search")) show("search", searchScreen());
            }
        }.execute();
    }

    @SuppressWarnings("unchecked")
    static List<Stop> parseStops(String json) {
        List<Stop> out = new ArrayList<>();
        for (Object o : (List<Object>) Json.parse(json)) {
            String name = str(o, "name");
            if (!name.isEmpty()) out.add(new Stop((int) num(o, "id"), name));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Quote> parseQuotes(String json) {
        List<Quote> out = new ArrayList<>();
        Object list = at(Json.parse(json), "quotes");
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

    /** Offline stand-in: a real Dundee to Edinburgh weekday timetable, captured from the API. */
    static List<Quote> sampleQuotes(LocalDate day) {
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

    /** The departure closest to the user's usual time on their usual route, if any. */
    Quote usualQuote() {
        if (from.id != USUAL_FROM || to.id != USUAL_TO) return null;
        Quote best = null;
        long bestDiff = 16;
        for (Quote q : quotes) {
            long diff = Math.abs(Duration.between(USUAL_TIME, q.dep.toLocalTime()).toMinutes());
            if (diff < bestDiff) { best = q; bestDiff = diff; }
        }
        return best;
    }

    // ---- Screens ----

    JComponent searchScreen() {
        Col p = new Col(BG, null, 0, 0);
        p.add(header("Where to?", "Book your usual bus to build a streak"));
        Col body = new Col(BG, null, 0, 16);

        Col form = card(14);
        JComboBox<Stop> fromBox = new JComboBox<>(stops.toArray(new Stop[0]));
        JComboBox<Stop> toBox = new JComboBox<>(stops.toArray(new Stop[0]));
        fromBox.setSelectedItem(from);
        toBox.setSelectedItem(to);
        fromBox.addActionListener(e -> { from = (Stop) fromBox.getSelectedItem(); loadQuotes(); });
        toBox.addActionListener(e -> { to = (Stop) toBox.getSelectedItem(); loadQuotes(); });
        form.add(label("From", 12, true, MUTED));
        form.add(gap(4));
        form.add(fill(fromBox, 34));
        form.add(gap(10));
        form.add(label("To", 12, true, MUTED));
        form.add(gap(4));
        form.add(fill(toBox, 34));
        form.add(gap(12));

        JPanel dateRow = new JPanel(new BorderLayout(8, 0));
        dateRow.setOpaque(false);
        Btn prev = new Btn("‹", 1), next = new Btn("›", 1);
        prev.setPreferredSize(new Dimension(44, 34));
        next.setPreferredSize(new Dimension(44, 34));
        prev.setEnabled(date.isAfter(LocalDate.now(LONDON)));
        prev.addActionListener(e -> { date = date.minusDays(1); loadQuotes(); });
        next.addActionListener(e -> { date = date.plusDays(1); loadQuotes(); });
        JLabel dayLabel = label(dayName(), 15, true, TEXT);
        dayLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dateRow.add(prev, BorderLayout.WEST);
        dateRow.add(dayLabel, BorderLayout.CENTER);
        dateRow.add(next, BorderLayout.EAST);
        form.add(fill(dateRow, 34));
        body.add(form);
        body.add(gap(14));

        if (loading) {
            body.add(label("Finding buses…", 14, false, MUTED));
        } else if (error != null) {
            body.add(wrapped(error, 14, MUTED));
            body.add(gap(10));
            Btn retry = new Btn("Try again", 1);
            retry.addActionListener(e -> loadQuotes());
            body.add(fill(retry, 38));
        } else if (quotes.isEmpty()) {
            body.add(wrapped("No buses left on this day for this route. Try another date.", 14, MUTED));
        } else {
            Quote usual = usualQuote();
            if (usual != null) {
                body.add(label("Your usual bus", 15, true, TEXT));
                body.add(gap(6));
                body.add(quoteCard(usual, true));
                body.add(gap(14));
            }
            body.add(label(quotes.size() + " departures", 15, true, TEXT));
            body.add(label(live ? "Live times and prices from the Ember API"
                    : "Offline: showing a sample timetable", 12, false, live ? MUTED : AMBER));
            body.add(gap(6));
            for (Quote q : quotes) {
                if (q == usual) continue;
                body.add(quoteCard(q, false));
                body.add(gap(8));
            }
        }
        p.add(body);
        return p;
    }

    JComponent quoteCard(Quote q, boolean usual) {
        Col c = usual ? new Col(TINT, GREEN, 14, 12) : card(12);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(label(q.times(), 19, true, TEXT), BorderLayout.WEST);
        row.add(label(money(q.pence), 17, true, TEXT), BorderLayout.EAST);
        c.add(fill(row, 26));
        String meta = q.duration() + (q.route.isEmpty() ? "" : " · " + q.route);
        boolean few = q.seats > 0 && q.seats < 10;
        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);
        row2.add(label(meta, 12, false, MUTED), BorderLayout.WEST);
        row2.add(label(few ? "Only " + q.seats + " seats left" : q.seats + " seats", 12, few, few ? AMBER : MUTED),
                BorderLayout.EAST);
        c.add(fill(row2, 18));
        if (usual) {
            c.add(gap(4));
            c.add(label("★ " + streak + "-day streak · book to make it " + (streak + 1), 12, true, GREEN_DARK));
        }
        c.add(gap(8));
        Btn pick = new Btn("Select", usual ? 0 : 1);
        pick.addActionListener(e -> {
            selected = q;
            selectedIsUsual = usual;
            show("checkout", checkoutScreen());
        });
        c.add(fill(pick, 36));
        return c;
    }

    JComponent checkoutScreen() {
        Quote q = selected;
        boolean usual = selectedIsUsual;
        boolean discounted = usual && streak >= REWARD_AT;
        int off = discounted ? (int) Math.round(q.pence * DISCOUNT) : 0;

        Col p = new Col(BG, null, 0, 0);
        p.add(header("Checkout", dayName()));
        Col body = new Col(BG, null, 0, 16);

        body.add(journeyCard(q));
        body.add(gap(12));

        Col fare = card(14);
        fare.add(line("1 adult", money(q.pence), TEXT));
        if (discounted) fare.add(line("Streak reward (20% off)", "-" + money(off), GREEN_DARK));
        fare.add(gap(4));
        fare.add(line("Total", money(q.pence - off), TEXT));
        body.add(fare);
        body.add(gap(12));

        String note;
        if (!usual) note = "This isn't your usual bus, so it won't add to your streak.";
        else if (discounted) note = "★ Booking this keeps your streak going: day " + (streak + 1) + ".";
        else if (streak + 1 >= REWARD_AT) note = "★ Booking this unlocks 20% off your usual bus!";
        else note = "★ Booking this takes your streak to day " + (streak + 1) + ".";
        Col banner = new Col(usual ? TINT : CARD, usual ? null : BORDER, 12, 12);
        banner.add(wrapped(note, 13, usual ? GREEN_DARK : MUTED));
        body.add(banner);
        body.add(gap(16));

        Btn pay = new Btn("Pay " + money(q.pence - off), 0);
        pay.addActionListener(e -> {
            justUnlocked = false;
            if (usual) {
                streak++;
                justUnlocked = (streak == REWARD_AT || streak == FREE_TRIP_AT);
            }
            show("confirm", confirmScreen());
        });
        body.add(fill(pay, 46));
        body.add(gap(6));
        body.add(label("Demo only: no payment is taken and no ticket is issued.", 11, false, MUTED));
        body.add(gap(10));
        Btn back = new Btn("Back to departures", 2);
        back.addActionListener(e -> show("search", searchScreen()));
        body.add(fill(back, 36));
        p.add(body);
        return p;
    }

    JComponent journeyCard(Quote q) {
        Col c = card(14);
        if (!q.route.isEmpty()) {
            c.add(label(q.route + "  " + q.board + (q.via.isEmpty() ? "" : " " + q.via), 13, true, GREEN_DARK));
            c.add(gap(8));
        }
        c.add(label(HM.format(q.dep) + "   " + q.originStop, 15, true, TEXT));
        c.add(label("              " + q.duration(), 12, false, MUTED));
        c.add(label(HM.format(q.arr) + "   " + q.destStop, 15, true, TEXT));
        List<String> extras = new ArrayList<>();
        if (q.electric) extras.add("Electric coach");
        if (q.wifi) extras.add("Wi-Fi");
        if (q.toilet) extras.add("Toilet");
        if (!extras.isEmpty()) {
            c.add(gap(8));
            c.add(label(String.join(" · ", extras), 12, false, MUTED));
        }
        return c;
    }

    JComponent confirmScreen() {
        Quote q = selected;
        Col p = new Col(BG, null, 0, 0);
        p.add(header("✓ You're booked", dayName()));
        Col body = new Col(BG, null, 0, 16);
        body.add(journeyCard(q));
        body.add(gap(6));
        body.add(label("Demo ticket ref: DEMO-" + (1000 + streak * 37), 12, false, MUTED));
        body.add(gap(12));

        Col result = new Col(selectedIsUsual ? TINT : CARD, selectedIsUsual ? null : BORDER, 12, 14);
        if (selectedIsUsual) {
            result.add(label("★ Streak: " + streak + " days", 19, true, GREEN_DARK));
            if (justUnlocked) {
                String what = streak >= FREE_TRIP_AT ? "a free trip" : "20% off your usual bus";
                result.add(gap(4));
                result.add(label("Reward unlocked: " + what + "!", 14, true, TEXT));
            }
        } else {
            result.add(label("Streak unchanged (" + streak + " days)", 14, false, MUTED));
        }
        body.add(result);
        body.add(gap(16));

        Btn view = new Btn("View my streak", 0);
        view.addActionListener(e -> show("streak", streakScreen()));
        body.add(fill(view, 46));
        body.add(gap(8));
        Btn again = new Btn("Book another trip", 1);
        again.addActionListener(e -> { date = date.plusDays(1); loadQuotes(); });
        body.add(fill(again, 40));
        p.add(body);
        return p;
    }

    JComponent streakScreen() {
        Col p = new Col(BG, null, 0, 0);
        p.add(header("My streak", "Same bus, day after day"));
        Col body = new Col(BG, null, 0, 16);

        int next = streak < REWARD_AT ? REWARD_AT : FREE_TRIP_AT;
        Col hero = new Col(GREEN, null, 16, 18);
        hero.add(label(String.valueOf(streak), 56, true, Color.WHITE));
        hero.add(label("days in a row on the " + HM.format(USUAL_TIME) + " Dundee → Edinburgh",
                13, false, Color.WHITE));
        hero.add(gap(12));
        hero.add(fill(new Bar(Math.min(streak, next), next), 10));
        hero.add(gap(8));
        String nextText = streak >= FREE_TRIP_AT ? "All rewards unlocked"
                : (next - streak) + " more to unlock " + (next == REWARD_AT ? "20% off" : "a free trip");
        hero.add(label(nextText, 13, true, Color.WHITE));
        body.add(hero);
        body.add(gap(16));

        body.add(label("Rewards", 15, true, TEXT));
        body.add(gap(6));
        body.add(rewardCard("20% off your usual bus", REWARD_AT));
        body.add(gap(8));
        body.add(rewardCard("One free trip", FREE_TRIP_AT));
        body.add(gap(16));

        body.add(label("Streak freeze", 15, true, TEXT));
        body.add(gap(6));
        Col freeze = card(12);
        freeze.add(label("1 available", 14, true, TEXT));
        freeze.add(label("Miss a day without losing your streak.", 12, false, MUTED));
        freeze.add(label("Cancelled or disrupted buses never break it.", 12, false, MUTED));
        body.add(freeze);
        p.add(body);
        return p;
    }

    Col rewardCard(String name, int needed) {
        boolean unlocked = streak >= needed;
        Col c = unlocked ? new Col(TINT, GREEN, 14, 12) : card(12);
        c.add(label(name, 14, true, TEXT));
        c.add(label(unlocked ? "✓ Unlocked" : "Locked · reach a " + needed + "-day streak",
                12, unlocked, unlocked ? GREEN_DARK : MUTED));
        return c;
    }

    JComponent navBar() {
        JPanel nav = new JPanel(new GridLayout(1, 2, 8, 0));
        nav.setBackground(CARD);
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER), new EmptyBorder(8, 12, 8, 12)));
        Btn book = new Btn("Book", 2);
        Btn str = new Btn("★ My streak", 2);
        book.setPreferredSize(new Dimension(100, 38));
        book.addActionListener(e -> show("search", searchScreen()));
        str.addActionListener(e -> show("streak", streakScreen()));
        nav.add(book);
        nav.add(str);
        return nav;
    }

    // ---- UI helpers ----

    String dayName() {
        LocalDate today = LocalDate.now(LONDON);
        String prefix = date.equals(today) ? "Today, " : date.equals(today.plusDays(1)) ? "Tomorrow, " : "";
        return prefix + DAY.format(date);
    }

    static String money(int pence) { return String.format("£%.2f", pence / 100.0); }

    static Col header(String title, String subtitle) {
        Col h = new Col(GREEN, null, 0, 18);
        h.add(label(title, 25, true, Color.WHITE));
        h.add(gap(2));
        h.add(label(subtitle, 13, false, Color.WHITE));
        return h;
    }

    static Col card(int pad) { return new Col(CARD, BORDER, 14, pad); }

    static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JLabel wrapped(String text, int size, Color color) {
        return label("<html><body style='width:240px'>" + text + "</body></html>", size, false, color);
    }

    static JPanel line(String left, String right, Color color) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(label(left, 14, false, color), BorderLayout.WEST);
        row.add(label(right, 14, true, color), BorderLayout.EAST);
        return (JPanel) fill(row, 24);
    }

    static JComponent fill(JComponent c, int height) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        c.setPreferredSize(new Dimension(100, height));
        return c;
    }

    static Component gap(int h) { return Box.createVerticalStrut(h); }

    /** Vertical stack with an optional rounded background and outline. */
    static class Col extends JPanel {
        final Color fillColor, lineColor; final int radius;
        Col(Color fillColor, Color lineColor, int radius, int pad) {
            this.fillColor = fillColor; this.lineColor = lineColor; this.radius = radius;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(new EmptyBorder(pad, pad, pad, pad));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fillColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if (lineColor != null) {
                g2.setColor(lineColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
        }
    }

    /** Rounded button. kind: 0 = filled green, 1 = outlined, 2 = text only. */
    static class Btn extends JButton {
        final int kind;
        Btn(String text, int kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            setForeground(kind == 0 ? Color.WHITE : GREEN_DARK);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hot = isEnabled() && (getModel().isRollover() || getModel().isPressed());
            int w = getWidth(), h = getHeight();
            if (kind == 0) {
                g2.setColor(hot ? GREEN_DARK : GREEN);
                g2.fillRoundRect(0, 0, w, h, 12, 12);
            } else {
                if (kind == 1 || hot) {
                    g2.setColor(hot ? TINT : Color.WHITE);
                    g2.fillRoundRect(0, 0, w, h, 12, 12);
                }
                if (kind == 1) {
                    g2.setColor(isEnabled() ? GREEN : BORDER);
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Thin rounded progress bar, drawn white on the green streak card. */
    static class Bar extends JComponent {
        final int value, max;
        Bar(int value, int max) { this.value = value; this.max = max; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(GREEN_DARK);
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, Math.max(h, w * value / max), h, h, h);
            g2.dispose();
        }
    }

    /** Scrollable page that always matches the window width. */
    static class Page extends JPanel implements Scrollable {
        Page() { super(new BorderLayout()); setBackground(BG); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ---- Minimal JSON reader (objects become Map, arrays List, numbers Double) ----

    @SuppressWarnings("unchecked")
    static Object at(Object o, String... path) {
        for (String key : path) {
            if (!(o instanceof Map)) return null;
            o = ((Map<String, Object>) o).get(key);
        }
        return o;
    }
    static String str(Object o, String... path) {
        Object v = at(o, path);
        return v instanceof String ? (String) v : "";
    }
    static double num(Object o, String... path) {
        Object v = at(o, path);
        return v instanceof Double ? (Double) v : 0;
    }

    static class Json {
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
