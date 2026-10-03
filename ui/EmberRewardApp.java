package ui;

import data.Account;
import data.AccountStore;
import data.Booking;
import data.BookingStore;
import data.EmberApi;
import data.EmberApi.Quote;
import data.EmberApi.Stop;
import data.EmberApi.Streak;
import data.Rarity;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import static ui.Constants.*;

/**
 * Ember bus booking where every journey you book levels up.
 * Compile from the project root:  javac data/*.java ui/*.java
 * Run with:                       java ui.EmberRewardApp
 *
 * Tabs: Journeys (the level-up collection), Book, Streaks, Account.
 * Colours come from ui.Constants; data and accounts come from the data package.
 * Payment is simulated: nothing is ever sent to the API.
 */
public class EmberRewardApp {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);
    static final int REWARD_AT = EmberApi.REWARD_AT;
    static final int FREE_TRIP_AT = EmberApi.FREE_TRIP_AT;

    // The one colour the palette has no equivalent for: "only N seats left" warnings.
    static final Color WARN = new Color(0xE0A030);
    static final Color SHADE = new Color(0, 0, 0, 60);   // dark overlay on rarity-coloured cards

    // ---- App state ----
    List<Stop> stops = EmberApi.fallbackStops();
    Stop from = stops.get(0), to = stops.get(1);
    LocalDate date = LocalDate.now(EmberApi.LONDON).plusDays(1);
    List<Quote> quotes = new ArrayList<>();
    boolean loading, live;
    String error;
    int requestNo;
    Quote selected;
    Streak selectedJourney;
    Streak lastBooked;
    Booking lastBooking;
    Rarity tierBefore, rewardTier;     // for the "levelled up" panel after paying
    boolean levelledUp, justUnlocked;
    String accountMsg;
    String screen = "journeys";

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
        show("journeys", journeysScreen());
        frame.setVisible(true);
        loadStops();
        loadQuotes();   // loads in the background; the Book tab shows it when opened
    }

    void show(String name, JComponent content) {
        screen = name;
        page.removeAll();
        page.add(content, BorderLayout.NORTH);
        page.revalidate();
        page.repaint();
        page.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
    }

    /** Redraws the Book tab, but only if the user is looking at it. */
    void refreshSearch() {
        if (screen.equals("search")) show("search", searchScreen());
    }

    /** Opens the Book tab and (re)loads departures for the current from/to/date. */
    void openSearch() {
        screen = "search";
        loadQuotes();
    }

    // ---- Loading data (network calls run off the UI thread) ----

    void loadStops() {
        new SwingWorker<List<Stop>, Void>() {
            @Override protected List<Stop> doInBackground() throws Exception {
                return EmberApi.fetchStops();
            }
            @Override protected void done() {
                try {
                    List<Stop> loaded = get();
                    if (loaded.size() < 2) return;
                    if (!loaded.contains(from)) loaded.add(0, from);
                    if (!loaded.contains(to)) loaded.add(to);
                    stops = loaded;
                    refreshSearch();
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
            refreshSearch();
            return;
        }
        loading = true;
        refreshSearch();
        new SwingWorker<List<Quote>, Void>() {
            boolean gotLive = true;
            String problem;
            @Override protected List<Quote> doInBackground() {
                try {
                    return EmberApi.fetchQuotes(f, t, d);
                } catch (Exception e) {
                    gotLive = false;
                    List<Quote> sample = EmberApi.sampleQuotes(f, t, d);
                    if (!sample.isEmpty()) return sample;
                    problem = "Couldn't reach the Ember API. Check your connection and try again.";
                    return new ArrayList<>();
                }
            }
            @Override protected void done() {
                if (req != requestNo) return;   // a newer search replaced this one
                List<Quote> result;
                try { result = get(); } catch (Exception e) { result = new ArrayList<>(); }
                ZonedDateTime now = ZonedDateTime.now(EmberApi.LONDON);
                result.removeIf(q -> q.dep.isBefore(now));
                result.removeIf(q -> q.seats <= 0);
                quotes = result;
                live = gotLive;
                error = problem;
                loading = false;
                refreshSearch();
            }
        }.execute();
    }

    Streak journeyOf(Quote q) { return EmberApi.findStreak(from, to, q.dep.toLocalTime()); }

    int streakOf(Quote q) {
        Streak s = journeyOf(q);
        return s == null ? 0 : s.count;
    }

    static Color rarityColor(Rarity r) {
        if (r == null) return OVERVIEW;
        switch (r) {
            case COMMON: return RARITY_COMMON;
            case UNCOMMON: return RARITY_UNCOMMON;
            case RARE: return RARITY_RARE;
            case EPIC: return RARITY_EPIC;
            default: return RARITY_LEGENDARY;
        }
    }

    static Rarity rarityForIcon(String iconId) {
        for (Rarity r : Rarity.values()) if (r.iconId.equals(iconId)) return r;
        return null;
    }

    // ---- Journeys tab (the main feature) ----

    JComponent journeysScreen() {
        Account acc = AccountStore.current();
        List<Streak> js = EmberApi.getJourneys();
        Col p = new Col(BG, null, 0, 0);
        p.add(header("Journeys", "Book a trip again to level it up"));
        Col body = new Col(BG, null, 0, 16);

        JPanel who = new JPanel(new BorderLayout(10, 0));
        who.setOpaque(false);
        Rarity top = acc.highestRarity();
        who.add(new IconView(acc.iconId, 36, top == null ? CARD : rarityColor(top), TEXT, true), BorderLayout.WEST);
        JPanel who2 = vbox();
        who2.add(label(acc.displayName + " \u00B7 " + acc.title, 14, true, TEXT));
        who2.add(label(js.size() + (js.size() == 1 ? " journey" : " journeys") + " collected", 12, false, MUTED));
        who.add(who2, BorderLayout.CENTER);
        body.add(fill(who, 38));
        body.add(gap(12));

        if (js.isEmpty()) {
            body.add(wrapped("No journeys yet. Book a bus and it appears here as a Common journey; book it "
                    + "again and again to level it up.", 14, MUTED));
            body.add(gap(10));
            Btn go = new Btn("Find a bus", 0);
            go.addActionListener(e -> openSearch());
            body.add(fill(go, 42));
        } else {
            Grid grid = new Grid(2);
            for (Streak s : js) grid.add(journeyCard(s));
            body.add(grid);
        }
        p.add(body);
        return p;
    }

    JComponent journeyCard(Streak s) {
        Rarity r = s.rarity();
        Color c = rarityColor(r);
        Col card = new Col(c, c.brighter(), 16, 12);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(new IconView(s.iconId(), 44, SHADE, TEXT, false), BorderLayout.WEST);
        JPanel tag = vbox();
        tag.add(label(r.label.toUpperCase(), 11, true, TEXT));
        tag.add(label("Level " + r.level(), 14, true, TEXT));
        top.add(tag, BorderLayout.CENTER);
        card.add(fill(top, 44));
        card.add(gap(8));

        card.add(fill(label("<html><body style='width:125px'>" + s.from.shortName() + " \u2192 "
                + s.to.shortName() + "</body></html>", 13, true, TEXT), 36));
        card.add(label("Around " + EmberApi.HM.format(s.time), 12, false, TEXT));
        card.add(gap(8));
        card.add(fill(new Bar((int) Math.round(s.progress() * 100), 100, new Color(0, 0, 0, 70), TEXT), 8));
        card.add(gap(4));
        card.add(label(s.nextRarity() == null ? "Max level \u00B7 " + s.bookings + " trips"
                : s.bookings + " / " + s.nextRarity().bookingsNeeded + " trips", 11, false, TEXT));

        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                selectedJourney = s;
                show("journey", journeyScreen());
            }
        });
        return card;
    }

    JComponent journeyScreen() {
        Streak s = selectedJourney;
        Rarity r = s.rarity();
        Color c = rarityColor(r);
        Col p = new Col(BG, null, 0, 0);
        p.add(header("Journey", "Level up by booking it again"));
        Col body = new Col(BG, null, 0, 16);

        Col hero = new Col(c, c.brighter(), 16, 16);
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(new IconView(s.iconId(), 64, SHADE, TEXT, false), BorderLayout.WEST);
        JPanel info = vbox();
        info.add(label(r.label.toUpperCase() + " \u00B7 LEVEL " + r.level(), 12, true, TEXT));
        info.add(label("<html><body style='width:200px'>" + s.from.shortName() + " \u2192 "
                + s.to.shortName() + "</body></html>", 16, true, TEXT));
        info.add(label("Around " + EmberApi.HM.format(s.time), 12, false, TEXT));
        top.add(info, BorderLayout.CENTER);
        hero.add(fill(top, 70));
        hero.add(gap(12));
        hero.add(fill(new Bar((int) Math.round(s.progress() * 100), 100, new Color(0, 0, 0, 70), TEXT), 10));
        hero.add(gap(6));
        hero.add(label(s.nextRarity() == null
                ? "Max level reached \u00B7 " + s.bookings + " trips"
                : s.bookings + " trips \u00B7 " + s.bookingsToNext() + " more to reach " + s.nextRarity().label,
                13, true, TEXT));
        body.add(hero);
        body.add(gap(12));

        Btn book = new Btn("Book this journey", 0);
        book.addActionListener(e -> {
            if (!stops.contains(s.from)) stops.add(s.from);
            if (!stops.contains(s.to)) stops.add(s.to);
            from = s.from;
            to = s.to;
            openSearch();
        });
        body.add(fill(book, 44));
        body.add(gap(6));
        Btn back = new Btn("Back to journeys", 2);
        back.addActionListener(e -> show("journeys", journeysScreen()));
        body.add(fill(back, 36));
        body.add(gap(14));

        body.add(label("Levels and rewards", 15, true, TEXT));
        body.add(label("Reaching a level on any journey unlocks its reward for your account.", 12, false, MUTED));
        body.add(gap(8));
        for (Rarity t : Rarity.values()) {
            boolean reached = s.bookings >= t.bookingsNeeded;
            Color tc = rarityColor(t);
            Col row = reached ? new Col(tc, tc.brighter(), 14, 10) : new Col(CARD, tc, 14, 10);
            JPanel line = new JPanel(new BorderLayout(10, 0));
            line.setOpaque(false);
            line.add(new IconView(t.iconId, 40, reached ? SHADE : OVERVIEW, reached ? TEXT : MUTED, false),
                    BorderLayout.WEST);
            JPanel txt = vbox();
            txt.add(label(t.label + " \u00B7 Level " + t.level(), 14, true, TEXT));
            txt.add(label(t.bookingsNeeded + (t.bookingsNeeded == 1 ? " trip" : " trips"), 12, false,
                    reached ? TEXT : MUTED));
            txt.add(label(Icons.name(t.iconId) + " icon + \u201C" + t.title + "\u201D title", 12, false,
                    reached ? TEXT : MUTED));
            line.add(txt, BorderLayout.CENTER);
            line.add(label(reached ? "\u2713" : "", 18, true, TEXT), BorderLayout.EAST);
            row.add(fill(line, 48));
            body.add(row);
            body.add(gap(8));
        }
        p.add(body);
        return p;
    }

    // ---- Book tab ----

    JComponent searchScreen() {
        Col p = new Col(BG, null, 0, 0);
        p.add(header("Where to?", "Book the same bus to level it up"));
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
        prev.setEnabled(date.isAfter(LocalDate.now(EmberApi.LONDON)));
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
            // Buses you've booked before go in their own section, most-levelled first.
            List<Quote> mine = new ArrayList<>();
            for (Quote q : quotes) if (journeyOf(q) != null) mine.add(q);
            mine.sort((a, b) -> journeyOf(b).bookings - journeyOf(a).bookings);
            if (!mine.isEmpty()) {
                body.add(label(mine.size() == 1 ? "Your journey" : "Your journeys", 15, true, TEXT));
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

    JComponent quoteCard(Quote q, boolean booked) {
        Streak j = journeyOf(q);
        Rarity r = j == null ? null : j.rarity();
        Col c = booked ? new Col(OVERVIEW, rarityColor(r), 14, 12) : card(12);
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
        if (booked) {
            c.add(gap(4));
            c.add(label("\u2605 " + r.label + " journey \u00B7 " + j.count + "-day streak", 12, true, GOOD));
        }
        c.add(gap(8));
        Btn pick = new Btn("Select", booked ? 0 : 1);
        boolean soldOut = q.seats <= 0;
        pick.setEnabled(!soldOut);
        if (soldOut) pick.setText("Sold out");
        pick.addActionListener(e -> {
            selected = q;
            show("verification", verifyScreen());
        });
        c.add(fill(pick, 36));
        return c;
    }

    JComponent verifyScreen() {
        Quote q = selected;
        Streak j = journeyOf(q);
        int s = j == null ? 0 : j.count;
        boolean discounted = s >= REWARD_AT;

        Col p = new Col(BG, null, 0, 0);
        p.add(header("Verification", dayName()));
        Col body = new Col(BG, null, 0, 16);

        body.add(journeyCard(q));
        body.add(gap(12));

        String note;
        if (discounted) note = "\u2605 Booking this keeps your streak going: day " + (s + 1) + ".";
        else if (s + 1 >= REWARD_AT) note = "\u2605 Booking this unlocks 20% off this bus!";
        else if (s == 0) note = "\u2605 Book this bus to start a streak.";
        else note = "\u2605 Booking this takes your streak to day " + (s + 1) + ".";
        String levelNote;
        if (j == null) levelNote = "New journey: you'll discover it at Common.";
        else if (j.nextRarity() == null) levelNote = "This journey is already Legendary.";
        else levelNote = j.rarity().label + " journey \u00B7 " + (j.bookingsToNext() == 1
                ? "this trip levels it up to " + j.nextRarity().label + "!"
                : j.bookingsToNext() + " trips to " + j.nextRarity().label + ".");
        Col banner = j != null ? new Col(OVERVIEW, rarityColor(j.rarity()), 12, 12) : card(12);
        banner.add(wrapped(note, 13, s > 0 ? GOOD : MUTED));
        banner.add(gap(4));
        banner.add(wrapped(levelNote, 13, TEXT));
        body.add(banner);
        body.add(gap(16));

        Btn pay = new Btn("Verify by uploading your receipt", 0);
        pay.addActionListener(e -> {
            uploadPdf();
            Streak prev = journeyOf(q);
            tierBefore = prev == null ? null : prev.rarity();
            Rarity accountBefore = AccountStore.current().highestRarity();
            lastBooked = EmberApi.recordBooking(from, to, q.dep.toLocalTime());
            Rarity now = lastBooked.rarity();
            levelledUp = now != tierBefore;   // also true for a brand-new journey
            rewardTier = levelledUp && (accountBefore == null || now.ordinal() > accountBefore.ordinal())
                    ? now : null;
            justUnlocked = (lastBooked.count == REWARD_AT || lastBooked.count == FREE_TRIP_AT);
            show("confirm", confirmScreen());
        });
        body.add(fill(pay, 40));
        body.add(gap(16));
        Btn back = new Btn("Back to departures", 2);
        back.addActionListener(e -> show("search", searchScreen()));
        body.add(fill(back, 36));
        p.add(body);
        return p;
    }

    /** The bus details card used on the checkout and confirm screens. */
    JComponent journeyCard(Quote q) {
        Col c = card(14);
        if (!q.route.isEmpty()) {
            c.add(label(q.route + "  " + q.board + (q.via.isEmpty() ? "" : " " + q.via), 13, true, ACCENT));
            c.add(gap(8));
        }
        c.add(label(EmberApi.HM.format(q.dep) + "   " + q.originStop, 15, true, TEXT));
        c.add(label("              " + q.duration(), 12, false, MUTED));
        c.add(label(EmberApi.HM.format(q.arr) + "   " + q.destStop, 15, true, TEXT));
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
        Rarity now = st.rarity();
        Color rc = rarityColor(now);
        Col p = new Col(BG, null, 0, 0);
        p.add(header("\u2713 You're booked", dayName()));
        Col body = new Col(BG, null, 0, 16);
        body.add(journeyCard(q));
        body.add(gap(6));
        body.add(gap(12));

        // Level panel: celebrates a level-up, otherwise shows progress to the next level.
        Col lv = new Col(rc, rc.brighter(), 16, 14);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(new IconView(st.iconId(), 52, SHADE, TEXT, false), BorderLayout.WEST);
        JPanel txt = vbox();
        if (levelledUp) {
            txt.add(label(tierBefore == null ? "New journey discovered!" : "Journey levelled up!", 16, true, TEXT));
            txt.add(label(tierBefore == null ? now.label + " \u00B7 Level 1"
                    : tierBefore.label + " \u2192 " + now.label, 13, true, TEXT));
        } else {
            txt.add(label(now.label + " journey \u00B7 Level " + now.level(), 15, true, TEXT));
            txt.add(label(st.nextRarity() == null ? "Max level" : st.bookingsToNext() + " more trips to "
                    + st.nextRarity().label, 13, false, TEXT));
        }
        row.add(txt, BorderLayout.CENTER);
        lv.add(fill(row, 52));
        lv.add(gap(10));
        lv.add(fill(new Bar((int) Math.round(st.progress() * 100), 100, new Color(0, 0, 0, 70), TEXT), 8));
        if (rewardTier != null) {
            lv.add(gap(10));
            lv.add(wrapped("New reward: " + Icons.name(rewardTier.iconId) + " icon and the \u201C"
                    + rewardTier.title + "\u201D title. Equip them in Account.", 13, TEXT));
        }
        body.add(lv);
        body.add(gap(10));

        Col result = new Col(OVERVIEW, null, 12, 12);
        result.add(label("\u2605 Streak: " + st.count + " days", 16, true, GOOD));
        result.add(label("on the " + st.label(), 12, false, MUTED));
        if (justUnlocked) {
            String what = st.count >= FREE_TRIP_AT ? "a free trip" : "20% off this bus";
            result.add(gap(4));
            result.add(label("Streak reward unlocked: " + what + "!", 14, true, TEXT));
        }
        body.add(result);
        body.add(gap(16));

        Btn viewB = new Btn("View my bookings", 0);
        viewB.addActionListener(e -> show("bookings", bookingsScreen()));
        body.add(fill(viewB, 46));
        body.add(gap(8));
        Btn view = new Btn("View my journeys", 1);
        view.addActionListener(e -> show("journeys", journeysScreen()));
        body.add(fill(view, 40));
        body.add(gap(8));
        Btn again = new Btn("Book another trip", 2);
        again.addActionListener(e -> { date = date.plusDays(1); openSearch(); });
        body.add(fill(again, 40));
        p.add(body);
        return p;
    }

    // ---- My bookings ----

    JComponent bookingsScreen() {
        Col p = new Col(BG, null, 0, 0);
        p.add(header("My bookings", "Every trip you've booked"));
        Col body = new Col(BG, null, 0, 16);

        List<Booking> all = BookingStore.all();
        if (all.isEmpty()) {
            Col empty = card(14);
            empty.add(label("No bookings yet", 16, true, TEXT));
            empty.add(gap(4));
            empty.add(wrapped("Your tickets will show up here once you've booked a trip.", 13, MUTED));
            empty.add(gap(12));
            Btn go = new Btn("Book a trip", 0);
            go.addActionListener(e -> openSearch());
            empty.add(fill(go, 40));
            body.add(empty);
            p.add(body);
            return p;
        }

        int spent = 0, saved = 0;
        for (Booking b : all) { spent += b.pricePaidPence; saved += b.discountPence(); }
        Col sum = new Col(OVERVIEW, ACCENT, 16, 14);
        sum.add(label(all.size() + (all.size() == 1 ? " booking" : " bookings"), 18, true, TEXT));
        sum.add(gap(4));
        sum.add(label("Total paid: " + money(spent), 13, false, TEXT));
        if (saved > 0) sum.add(label("Saved with rewards: " + money(saved), 13, true, GOOD));
        body.add(sum);
        body.add(gap(14));

        body.add(label("Recent", 15, true, TEXT));
        body.add(gap(6));
        for (Booking b : all) {
            body.add(bookingCard(b));
            body.add(gap(8));
        }
        p.add(body);
        return p;
    }

    JComponent bookingCard(Booking b) {
        Col c = card(12);
        String fromName = stopName(b.originId);
        String toName = stopName(b.destinationId);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(label(fromName + " \u2192 " + toName, 14, true, TEXT), BorderLayout.WEST);
        row.add(label(money(b.pricePaidPence), 15, true, TEXT), BorderLayout.EAST);
        c.add(fill(row, 22));

        String day = DAY.format(b.departure);
        String time = EmberApi.HM.format(b.departure);
        c.add(label(day + " \u00B7 " + time
                + (b.route.isEmpty() ? "" : " \u00B7 Route " + b.route), 12, false, MUTED));
        c.add(gap(4));
        if (b.discountPence() > 0) {
            c.add(label("Saved " + money(b.discountPence()) + " with a streak reward", 12, true, GOOD));
            c.add(gap(4));
        }
        c.add(label("Ticket " + b.tripId, 11, false, MUTED));
        return c;
    }

    String stopName(int id) {
        for (Stop s : stops) if (s.id == id) return s.name;
        for (Stop s : EmberApi.fallbackStops()) if (s.id == id) return s.name;
        return "Stop " + id;
    }

    // ---- Streaks tab ----

    JComponent streakScreen() {
        Col p = new Col(BG, null, 0, 0);
        p.add(header("My streaks", "Same bus, day after day"));
        Col body = new Col(BG, null, 0, 16);

        Streak best = EmberApi.bestStreak();
        int bestCount = best == null ? 0 : best.count;
        int next = bestCount < REWARD_AT ? REWARD_AT : FREE_TRIP_AT;
        Col hero = new Col(ACCENT, null, 16, 18);
        hero.add(label(String.valueOf(bestCount), 56, true, TEXT));
        hero.add(label(best == null ? "No streaks yet: book a bus to start one"
                : "best streak \u00B7 days in a row on the " + best.label(), 13, false, TEXT));
        hero.add(gap(12));
        hero.add(fill(new Bar(Math.min(bestCount, next), next, ACCENT.darker(), TEXT), 10));
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

        body.add(label("Streak rewards (earned per bus)", 15, true, TEXT));
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
        List<Streak> all = EmberApi.getStreaks();
        if (all.isEmpty()) return card(12);
        DefaultTableModel model = new DefaultTableModel(new String[]{"Bus", "Streak", "Next reward"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Streak s : all) {
            model.addRow(new Object[]{s.label(), "\u2605 " + s.count, EmberApi.nextReward(s.count)});
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

    // ---- Account tab ----

    JComponent accountScreen() {
        Account acc = AccountStore.current();
        Rarity top = acc.highestRarity();
        Col p = new Col(BG, null, 0, 0);
        p.add(header("Account", "Your profile and rewards"));
        Col body = new Col(BG, null, 0, 16);

        // Profile card
        Color pc = rarityColor(top);
        Col profile = top == null ? card(14) : new Col(pc, pc.brighter(), 16, 14);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(new IconView(acc.iconId, 64, top == null ? OVERVIEW : SHADE, TEXT, true), BorderLayout.WEST);
        JPanel info = vbox();
        info.add(label(acc.displayName, 18, true, TEXT));
        info.add(label(acc.title, 13, false, TEXT));
        info.add(label("@" + acc.username + " \u00B7 " + acc.journeys.size() + " journeys \u00B7 "
                + (top == null ? "no tier yet" : top.label + " tier"), 12, false, top == null ? MUTED : TEXT));
        row.add(info, BorderLayout.CENTER);
        profile.add(fill(row, 64));
        body.add(profile);
        body.add(gap(16));

        // My bookings button
        Btn myBookings = new Btn("My bookings (" + BookingStore.all().size() + ")", 1);
        myBookings.addActionListener(e -> show("bookings", bookingsScreen()));
        body.add(fill(myBookings, 40));
        body.add(gap(16));

        // Icon chooser
        body.add(label("Profile icon", 15, true, TEXT));
        body.add(label("Unlock new icons by levelling up journeys.", 12, false, MUTED));
        body.add(gap(6));
        Grid icons = new Grid(6);
        for (String id : Icons.ACCOUNT_ICONS) {
            boolean ok = acc.hasIcon(id);
            Rarity need = rarityForIcon(id);
            IconView v = new IconView(ok ? id : "LOCK", 52, ok ? rarityColor(need) : CARD,
                    ok ? TEXT : MUTED, false);
            v.selected = id.equals(acc.iconId);
            v.setToolTipText(ok ? Icons.name(id) : "Reach " + need.label + " on any journey to unlock");
            if (ok) {
                v.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                v.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        acc.iconId = id;
                        AccountStore.save();
                        show("account", accountScreen());
                    }
                });
            }
            icons.add(v);
        }
        body.add(icons);
        body.add(gap(16));

        // Title chooser
        body.add(label("Profile title", 15, true, TEXT));
        body.add(gap(6));
        JComboBox<String> titles = new JComboBox<>(acc.unlockedTitles().toArray(new String[0]));
        styleCombo(titles);
        titles.setSelectedItem(acc.title);
        titles.addActionListener(e -> {
            String t = (String) titles.getSelectedItem();
            if (t != null && !t.equals(acc.title)) {
                acc.title = t;
                AccountStore.save();
                show("account", accountScreen());
            }
        });
        body.add(fill(titles, 34));
        body.add(gap(16));

        // Switch account
        body.add(label("Accounts on this computer", 15, true, TEXT));
        body.add(gap(6));
        for (Account other : AccountStore.all()) {
            boolean me = other == acc;
            Btn b = new Btn(other.displayName + (me ? "  (signed in)" : ""), me ? 0 : 1);
            b.addActionListener(e -> {
                if (!me) AccountStore.setCurrent(other);
                accountMsg = null;
                show("account", accountScreen());
            });
            body.add(fill(b, 38));
            body.add(gap(6));
        }
        body.add(gap(10));

        // Create account
        body.add(label("Create a new account", 15, true, TEXT));
        body.add(gap(6));
        JTextField name = new JTextField();
        styleField(name);
        body.add(fill(name, 36));
        body.add(gap(6));
        Btn create = new Btn("Create and sign in", 0);
        Runnable doCreate = () -> {
            Account made = AccountStore.create(name.getText());
            accountMsg = made == null
                    ? "Pick a name that isn't empty or already taken." : "Welcome, " + made.displayName + "!";
            show("account", accountScreen());
        };
        create.addActionListener(e -> doCreate.run());
        name.addActionListener(e -> doCreate.run());
        body.add(fill(create, 40));
        if (accountMsg != null) {
            body.add(gap(6));
            body.add(label(accountMsg, 12, false, accountMsg.startsWith("Welcome") ? GOOD : WARN));
        }
        body.add(gap(8));
        body.add(label("Accounts are saved on this computer only.", 11, false, MUTED));
        p.add(body);
        return p;
    }

    // ---- Navigation ----

    JComponent navBar() {
        JPanel nav = new JPanel(new GridLayout(1, 4, 6, 0));
        nav.setBackground(CARD);
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, OVERVIEW), new EmptyBorder(8, 10, 8, 10)));
        nav.setPreferredSize(new Dimension(WINDOW_WIDTH, NAGIVATION_HEIGHT));
        Btn journeys = new Btn("Journeys", 2);
        Btn book = new Btn("Book", 2);
        Btn str = new Btn("Streaks", 2);
        Btn acct = new Btn("Account", 2);
        journeys.addActionListener(e -> show("journeys", journeysScreen()));
        book.addActionListener(e -> show("search", searchScreen()));
        str.addActionListener(e -> show("streak", streakScreen()));
        acct.addActionListener(e -> { accountMsg = null; show("account", accountScreen()); });
        nav.add(journeys);
        nav.add(book);
        nav.add(str);
        nav.add(acct);
        return nav;
    }

    // ---- UI helpers ----

    void uploadPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a PDF to upload");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF documents (*.pdf)", "pdf"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;   // cancelled: stay put

        File pdf = chooser.getSelectedFile();
        if (!pdf.isFile() || !pdf.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            JOptionPane.showMessageDialog(frame, "Please choose a PDF file.",
                    "Not a PDF", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".ember_rewards", "uploads",
                    AccountStore.current().username);
            Files.createDirectories(dir);
            Files.copy(pdf.toPath(), dir.resolve(pdf.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Couldn't upload that file: " + ex.getMessage(),
                    "Upload failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(frame, "\u201C" + pdf.getName() + "\u201D was uploaded successfully.",
                "Upload complete", JOptionPane.INFORMATION_MESSAGE);
    }

    String dayName() {
        LocalDate today = LocalDate.now(EmberApi.LONDON);
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

    static JPanel vbox() {
        JPanel v = new JPanel();
        v.setLayout(new BoxLayout(v, BoxLayout.Y_AXIS));
        v.setOpaque(false);
        return v;
    }

    static void styleCombo(JComboBox<?> b) {
        b.setBackground(CARD);
        b.setForeground(TEXT);
    }

    static void styleField(JTextField f) {
        f.setBackground(CARD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OVERVIEW), new EmptyBorder(4, 8, 4, 8)));
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

    /** A grid with a fixed number of columns that keeps its natural height. */
    static class Grid extends JPanel {
        Grid(int cols) {
            super(new GridLayout(0, cols, cols > 2 ? 8 : 10, cols > 2 ? 8 : 10));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    static class IconView extends JComponent {
        final String id; final Color bg, fg; final boolean circle;
        boolean selected;
        IconView(String id, int size, Color bg, Color fg, boolean circle) {
            this.id = id; this.bg = bg; this.fg = fg; this.circle = circle;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int side = Math.min(getWidth(), getHeight());
            if (bg != null) {
                g2.setColor(bg);
                if (circle) g2.fillOval(0, 0, side, side);
                else g2.fillRoundRect(0, 0, side, side, side / 4, side / 4);
            }
            int pad = side / 6;
            Icons.draw(g2, id, pad, pad, side - 2 * pad, fg);
            if (selected) {
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(3f));
                if (circle) g2.drawOval(1, 1, side - 3, side - 3);
                else g2.drawRoundRect(1, 1, side - 3, side - 3, side / 4, side / 4);
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

    /** Thin rounded progress bar with a track colour and a fill colour. */
    static class Bar extends JComponent {
        final int value, max; final Color track, fillColor;
        Bar(int value, int max, Color track, Color fillColor) {
            this.value = value; this.max = max; this.track = track; this.fillColor = fillColor;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(track);
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(fillColor);
            g2.fillRoundRect(0, 0, Math.max(h, w * value / Math.max(1, max)), h, h, h);
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