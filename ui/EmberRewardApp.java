package ui;

import java.awt.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import static ui.Constants.*;
import data.BusData;
import data.BusData.Quote;
import data.BusData.Stop;
import data.BusData.Streak;

/**
 * Ember bus booking with per-bus streak rewards.
 * Compile from the project root:  javac data/*.java ui/*.java
 * Run with:                       java ui.EmberRewardApp
 *
 * All colours come from ui.Constants; all stops, journeys and streaks come
 * from data.BusData. Payment is simulated: nothing is ever sent to the API.
 */
public class EmberRewardApp {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);
    static final int REWARD_AT = BusData.REWARD_AT;
    static final int FREE_TRIP_AT = BusData.FREE_TRIP_AT;

    // The one colour the palette has no equivalent for: "only N seats left" warnings.
    static final Color WARN = new Color(0xE0A030);

    // ---- App state ----
    List<Stop> stops = BusData.fallbackStops();
    Stop from = stops.get(0), to = stops.get(1);
    LocalDate date = LocalDate.now(BusData.LONDON).plusDays(1);
    List<Quote> quotes = new ArrayList<>();
    boolean loading, live;
    String error;
    int requestNo;
    Quote selected;
    Streak lastBooked;
    boolean justUnlocked;
    String screen = "search";

    final JFrame frame = new JFrame(APP_TITLE);
    final Page page = new Page();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EmberRewardApp().start());
    }

    void start() {
        UIManager.put("ComboBox.background", CARD);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", OVERVIEW);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
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

    // ---- Loading data (network calls run off the UI thread) ----

    void loadStops() {
        new SwingWorker<List<Stop>, Void>() {
            @Override protected List<Stop> doInBackground() throws Exception {
                return BusData.fetchStops();
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
                    return BusData.fetchQuotes(f, t, d);
                } catch (Exception e) {
                    gotLive = false;
                    List<Quote> sample = BusData.sampleQuotes(f, t, d);
                    if (!sample.isEmpty()) return sample;
                    problem = "Couldn't reach the Ember API. Check your connection and try again.";
                    return new ArrayList<>();
                }
            }
            @Override protected void done() {
                if (req != requestNo) return;   // a newer search replaced this one
                List<Quote> result;
                try { result = get(); } catch (Exception e) { result = new ArrayList<>(); }
                ZonedDateTime now = ZonedDateTime.now(BusData.LONDON);
                result.removeIf(q -> q.dep.isBefore(now));
                quotes = result;
                live = gotLive;
                error = problem;
                loading = false;
                if (screen.equals("search")) show("search", searchScreen());
            }
        }.execute();
    }

    int streakOf(Quote q) {
        Streak s = BusData.findStreak(from, to, q.dep.toLocalTime());
        return s == null ? 0 : s.count;
    }

    // ---- Screens ----

    JComponent searchScreen() {
        Col p = new Col(BG, null, 0, 0);
        p.add(header("Where to?", "Book the same bus to build a streak"));
        Col body = new Col(BG, null, 0, 16);

        Col form = card(14);
        JComboBox<Stop> fromBox = new JComboBox<>(stops.toArray(new Stop[0]));
        JComboBox<Stop> toBox = new JComboBox<>(stops.toArray(new Stop[0]));
        styleCombo(fromBox);
        styleCombo(toBox);
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
        Btn prev = new Btn("\u2039", 1), next = new Btn("\u203A", 1);
        prev.setPreferredSize(new Dimension(44, 34));
        next.setPreferredSize(new Dimension(44, 34));
        prev.setEnabled(date.isAfter(LocalDate.now(BusData.LONDON)));
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
            body.add(label("Finding buses\u2026", 14, false, MUTED));
        } else if (error != null) {
            body.add(wrapped(error, 14, MUTED));
            body.add(gap(10));
            Btn retry = new Btn("Try again", 1);
            retry.addActionListener(e -> loadQuotes());
            body.add(fill(retry, 38));
        } else if (quotes.isEmpty()) {
            body.add(wrapped("No buses left on this day for this route. Try another date.", 14, MUTED));
        } else {
            // Buses that already have a streak go in their own section, longest streak first.
            List<Quote> mine = new ArrayList<>();
            for (Quote q : quotes) if (streakOf(q) > 0) mine.add(q);
            mine.sort((a, b) -> streakOf(b) - streakOf(a));
            if (!mine.isEmpty()) {
                body.add(label(mine.size() == 1 ? "Your bus" : "Your buses", 15, true, TEXT));
                body.add(gap(6));
                for (Quote q : mine) {
                    body.add(quoteCard(q, true));
                    body.add(gap(8));
                }
                body.add(gap(6));
            }
            body.add(label(quotes.size() + " departures", 15, true, TEXT));
            body.add(label(live ? "Live times and prices from the Ember API"
                    : "Offline: showing a sample timetable", 12, false, live ? MUTED : WARN));
            body.add(gap(6));
            for (Quote q : quotes) {
                if (mine.contains(q)) continue;
                body.add(quoteCard(q, false));
                body.add(gap(8));
            }
        }
        p.add(body);
        return p;
    }

    JComponent quoteCard(Quote q, boolean hasStreak) {
        int s = streakOf(q);
        Col c = hasStreak ? new Col(OVERVIEW, ACCENT, 14, 12) : card(12);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(label(q.times(), 19, true, TEXT), BorderLayout.WEST);
        row.add(label(money(q.pence), 17, true, TEXT), BorderLayout.EAST);
        c.add(fill(row, 26));
        String meta = q.duration() + (q.route.isEmpty() ? "" : " \u00B7 " + q.route);
        boolean few = q.seats > 0 && q.seats < 10;
        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);
        row2.add(label(meta, 12, false, MUTED), BorderLayout.WEST);
        row2.add(label(few ? "Only " + q.seats + " seats left" : q.seats + " seats", 12, few, few ? WARN : MUTED),
                BorderLayout.EAST);
        c.add(fill(row2, 18));
        if (hasStreak) {
            c.add(gap(4));
            c.add(label("\u2605 " + s + "-day streak \u00B7 book to make it " + (s + 1), 12, true, GOOD));
        }
        c.add(gap(8));
        Btn pick = new Btn("Select", hasStreak ? 0 : 1);
        pick.addActionListener(e -> {
            selected = q;
            show("checkout", checkoutScreen());
        });
        c.add(fill(pick, 36));
        return c;
    }

    JComponent checkoutScreen() {
        Quote q = selected;
        int s = streakOf(q);
        boolean discounted = s >= REWARD_AT;
        int off = discounted ? (int) Math.round(q.pence * BusData.DISCOUNT) : 0;

        Col p = new Col(BG, null, 0, 0);
        p.add(header("Checkout", dayName()));
        Col body = new Col(BG, null, 0, 16);

        body.add(journeyCard(q));
        body.add(gap(12));

        Col fare = card(14);
        fare.add(line("1 adult", money(q.pence), TEXT));
        if (discounted) fare.add(line("Streak reward (20% off)", "-" + money(off), GOOD));
        fare.add(gap(4));
        fare.add(line("Total", money(q.pence - off), TEXT));
        body.add(fare);
        body.add(gap(12));

        String note;
        if (discounted) note = "\u2605 Booking this keeps your streak going: day " + (s + 1) + ".";
        else if (s + 1 >= REWARD_AT) note = "\u2605 Booking this unlocks 20% off this bus!";
        else if (s == 0) note = "\u2605 Book this bus to start a streak.";
        else note = "\u2605 Booking this takes your streak to day " + (s + 1) + ".";
        Col banner = s > 0 ? new Col(OVERVIEW, ACCENT, 12, 12) : card(12);
        banner.add(wrapped(note, 13, s > 0 ? GOOD : MUTED));
        body.add(banner);
        body.add(gap(16));

        Btn pay = new Btn("Pay " + money(q.pence - off), 0);
        pay.addActionListener(e -> {
            lastBooked = BusData.recordBooking(from, to, q.dep.toLocalTime());
            justUnlocked = (lastBooked.count == REWARD_AT || lastBooked.count == FREE_TRIP_AT);
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
            c.add(label(q.route + "  " + q.board + (q.via.isEmpty() ? "" : " " + q.via), 13, true, ACCENT));
            c.add(gap(8));
        }
        c.add(label(BusData.HM.format(q.dep) + "   " + q.originStop, 15, true, TEXT));
        c.add(label("              " + q.duration(), 12, false, MUTED));
        c.add(label(BusData.HM.format(q.arr) + "   " + q.destStop, 15, true, TEXT));
        List<String> extras = new ArrayList<>();
        if (q.electric) extras.add("Electric coach");
        if (q.wifi) extras.add("Wi-Fi");
        if (q.toilet) extras.add("Toilet");
        if (!extras.isEmpty()) {
            c.add(gap(8));
            c.add(label(String.join(" \u00B7 ", extras), 12, false, MUTED));
        }
        return c;
    }

    JComponent confirmScreen() {
        Quote q = selected;
        Streak st = lastBooked;
        Col p = new Col(BG, null, 0, 0);
        p.add(header("\u2713 You're booked", dayName()));
        Col body = new Col(BG, null, 0, 16);
        body.add(journeyCard(q));
        body.add(gap(6));
        body.add(label("Demo ticket ref: DEMO-" + (1000 + st.count * 37), 12, false, MUTED));
        body.add(gap(12));

        Col result = new Col(OVERVIEW, ACCENT, 12, 14);
        result.add(label("\u2605 Streak: " + st.count + " days", 19, true, GOOD));
        result.add(label("on the " + st.label(), 12, false, MUTED));
        if (justUnlocked) {
            String what = st.count >= FREE_TRIP_AT ? "a free trip" : "20% off this bus";
            result.add(gap(4));
            result.add(label("Reward unlocked: " + what + "!", 14, true, TEXT));
        }
        body.add(result);
        body.add(gap(16));

        Btn view = new Btn("View my streaks", 0);
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
        p.add(header("My streaks", "Same bus, day after day"));
        Col body = new Col(BG, null, 0, 16);

        Streak best = BusData.bestStreak();
        int bestCount = best == null ? 0 : best.count;
        int next = bestCount < REWARD_AT ? REWARD_AT : FREE_TRIP_AT;
        Col hero = new Col(ACCENT, null, 16, 18);
        hero.add(label(String.valueOf(bestCount), 56, true, TEXT));
        hero.add(label(best == null ? "No streaks yet: book a bus to start one"
                : "best streak \u00B7 days in a row on the " + best.label(), 13, false, TEXT));
        hero.add(gap(12));
        hero.add(fill(new Bar(Math.min(bestCount, next), next), 10));
        hero.add(gap(8));
        String nextText = bestCount >= FREE_TRIP_AT ? "All rewards unlocked"
                : (next - bestCount) + " more to unlock " + (next == REWARD_AT ? "20% off" : "a free trip");
        hero.add(label(nextText, 13, true, TEXT));
        body.add(hero);
        body.add(gap(16));

        body.add(label("All buses", 15, true, TEXT));
        body.add(gap(6));
        body.add(streakTable());
        body.add(gap(16));

        body.add(label("Rewards (earned per bus)", 15, true, TEXT));
        body.add(gap(6));
        body.add(rewardCard("20% off that bus", REWARD_AT, bestCount));
        body.add(gap(8));
        body.add(rewardCard("One free trip", FREE_TRIP_AT, bestCount));
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

    JComponent streakTable() {
        List<Streak> all = BusData.getStreaks();
        if (all.isEmpty()) return card(12);
        DefaultTableModel model = new DefaultTableModel(new String[]{"Bus", "Streak", "Next reward"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Streak s : all) {
            model.addRow(new Object[]{s.label(), "\u2605 " + s.count, BusData.nextReward(s.count)});
        }

        JTable table = new JTable(model);
        table.setRowHeight(34);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        table.setBackground(CARD);
        table.setForeground(TEXT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowSelectionAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);

        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        table.getTableHeader().setBackground(OVERVIEW);
        table.getTableHeader().setForeground(ACCENT);
        table.getTableHeader().setReorderingAllowed(false);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createLineBorder(OVERVIEW));
        wrap.add(table.getTableHeader(), BorderLayout.NORTH);
        wrap.add(table, BorderLayout.CENTER);
        return fill(wrap, 34 * model.getRowCount() + 28);
    }

    Col rewardCard(String name, int needed, int bestCount) {
        boolean unlocked = bestCount >= needed;
        Col c = unlocked ? new Col(OVERVIEW, ACCENT, 14, 12) : card(12);
        c.add(label(name, 14, true, TEXT));
        c.add(label(unlocked ? "\u2713 Unlocked on at least one bus"
                        : "Locked \u00B7 reach a " + needed + "-day streak on one bus",
                12, unlocked, unlocked ? GOOD : MUTED));
        return c;
    }

    JComponent navBar() {
        JPanel nav = new JPanel(new GridLayout(1, 2, 8, 0));
        nav.setBackground(CARD);
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, OVERVIEW), new EmptyBorder(8, 12, 8, 12)));
        nav.setPreferredSize(new Dimension(WINDOW_WIDTH, NAGIVATION_HEIGHT));
        Btn book = new Btn("Book", 2);
        Btn str = new Btn("\u2605 My streaks", 2);
        book.addActionListener(e -> show("search", searchScreen()));
        str.addActionListener(e -> show("streak", streakScreen()));
        nav.add(book);
        nav.add(str);
        return nav;
    }

    // ---- UI helpers ----

    String dayName() {
        LocalDate today = LocalDate.now(BusData.LONDON);
        String prefix = date.equals(today) ? "Today, " : date.equals(today.plusDays(1)) ? "Tomorrow, " : "";
        return prefix + DAY.format(date);
    }

    static String money(int pence) { return String.format("\u00A3%.2f", pence / 100.0); }

    static Col header(String title, String subtitle) {
        Col h = new Col(OVERVIEW, null, 0, 12);
        h.add(label(title, 22, true, TEXT));
        h.add(gap(2));
        h.add(label(subtitle, 13, false, MUTED));
        h.setPreferredSize(new Dimension(100, HEADER_HEIGHT));
        return h;
    }

    static Col card(int pad) { return new Col(CARD, OVERVIEW, 14, pad); }

    static void styleCombo(JComboBox<?> b) {
        b.setBackground(CARD);
        b.setForeground(TEXT);
    }

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

    /** Rounded button. kind: 0 = filled accent, 1 = outlined, 2 = text only. */
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
            setForeground(kind == 1 ? ACCENT : TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hot = isEnabled() && (getModel().isRollover() || getModel().isPressed());
            int w = getWidth(), h = getHeight();
            if (kind == 0) {
                g2.setColor(hot ? ACCENT.darker() : ACCENT);
                g2.fillRoundRect(0, 0, w, h, 12, 12);
            } else {
                if (kind == 1 || hot) {
                    g2.setColor(hot ? OVERVIEW : CARD);
                    g2.fillRoundRect(0, 0, w, h, 12, 12);
                }
                if (kind == 1) {
                    g2.setColor(isEnabled() ? ACCENT : OVERVIEW);
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Thin rounded progress bar, drawn on the accent-coloured streak card. */
    static class Bar extends JComponent {
        final int value, max;
        Bar(int value, int max) { this.value = value; this.max = max; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(ACCENT.darker());
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(TEXT);
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
}