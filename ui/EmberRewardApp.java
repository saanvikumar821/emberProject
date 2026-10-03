package ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import static ui.Constants.*;

/**
 * Clickable mockup: bus booking with streak rewards.
 * Run with:  java EmberMockup.java   (Java 11 or newer)
 * All data is fake and held in memory; payment is mocked.
 */
public class EmberRewardApp {
    
    // ---- Fake data ----
    static class Trip {
        final String dep, arr; final double price;
        Trip(String dep, String arr, double price) { this.dep = dep; this.arr = arr; this.price = price; }
    }
    static final String[] STOPS = {"Dundee", "Edinburgh", "Glasgow", "Perth"};
    static final Trip[] TRIPS = {
        new Trip("06:15", "07:50", 8.40),
        new Trip("07:15", "08:50", 9.80),
        new Trip("08:15", "09:50", 9.80),
        new Trip("09:15", "10:50", 7.60),
    };
    static final String USUAL_DEP = "07:15";   // the user's "usual bus"
    static final int REWARD_AT = 5;            // streak needed for the discount
    static final int FREE_TRIP_AT = 10;        // streak needed for a free trip
    static final double DISCOUNT = 0.20;

    // ---- App state ----
    int streak = 4;
    String from = "Dundee", to = "Edinburgh";
    Trip selected;
    boolean justUnlocked;

    final JFrame frame = new JFrame(APP_TITLE);
    final JPanel content = new JPanel(new BorderLayout());

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EmberRewardApp().start());
    }

    void start() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(390, 760);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        content.setBackground(BG);
        frame.add(content, BorderLayout.CENTER);
        frame.add(navBar(), BorderLayout.SOUTH);
        show(searchScreen());
        frame.setVisible(true);
    }

    void show(JComponent screen) {
        content.removeAll();
        content.add(screen, BorderLayout.NORTH);
        content.revalidate();
        content.repaint();
    }

    // ---- Screens ----

    JComponent searchScreen() {
        JPanel p = column(BG, 20);
        p.add(label("Where to?", 26, true, TEXT));
        p.add(gap(12));

        JComboBox<String> fromBox = new JComboBox<>(STOPS);
        JComboBox<String> toBox = new JComboBox<>(STOPS);
        fromBox.setSelectedItem(from);
        toBox.setSelectedItem(to);
        fromBox.addActionListener(e -> { from = (String) fromBox.getSelectedItem(); show(searchScreen()); });
        toBox.addActionListener(e -> { to = (String) toBox.getSelectedItem(); show(searchScreen()); });
        p.add(label("From", 12, false, MUTED));
        p.add(fill(fromBox, 32));
        p.add(gap(6));
        p.add(label("To", 12, false, MUTED));
        p.add(fill(toBox, 32));
        p.add(gap(16));
        p.add(label("Tomorrow \u00B7 " + from + " \u2192 " + to, 14, true, TEXT));
        p.add(gap(8));

        for (Trip t : TRIPS) {
            boolean usual = isUsual(t);
            JPanel card = column(CARD, 12);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.add(label(t.dep + " \u2192 " + t.arr, 18, true, TEXT), BorderLayout.WEST);
            row.add(label(money(t.price), 16, false, TEXT), BorderLayout.EAST);
            card.add(fill(row, 26));
            if (usual) {
                card.add(label("\u2605 Your usual bus \u00B7 " + streak + "-day streak", 12, true, ACCENT));
            }
            JButton pick = button("Select", usual);
            pick.addActionListener(e -> { selected = t; show(checkoutScreen()); });
            card.add(gap(6));
            card.add(fill(pick, 34));
            p.add(card);
            p.add(gap(8));
        }
        return p;
    }

    JComponent checkoutScreen() {
        Trip t = selected;
        boolean usual = isUsual(t);
        boolean discounted = usual && streak >= REWARD_AT;
        double off = discounted ? t.price * DISCOUNT : 0;

        JPanel p = column(BG, 20);
        JButton back = button("\u2190 Back", false);
        back.addActionListener(e -> show(searchScreen()));
        p.add(fill(back, 32));
        p.add(gap(12));
        p.add(label("Checkout", 26, true, TEXT));
        p.add(gap(12));

        JPanel card = column(CARD, 14);
        card.add(label(from + " \u2192 " + to, 18, true, TEXT));
        card.add(label("Tomorrow \u00B7 " + t.dep + " \u2013 " + t.arr, 14, false, MUTED));
        card.add(gap(12));
        card.add(line("Fare", money(t.price), TEXT));
        if (discounted) card.add(line("Streak reward (20% off)", "-" + money(off), GOOD));
        card.add(gap(6));
        card.add(line("Total", money(t.price - off), TEXT));
        p.add(card);
        p.add(gap(12));

        String note;
        if (!usual) note = "This isn't your usual bus, so it won't add to your streak.";
        else if (discounted) note = "Booking this keeps your streak going: day " + (streak + 1) + ".";
        else if (streak + 1 >= REWARD_AT) note = "Booking this unlocks 20% off your usual bus!";
        else note = "Booking this takes your streak to day " + (streak + 1) + ".";
        p.add(label("<html><body style='width:250px'>" + note + "</body></html>", 13, false, usual ? ACCENT : MUTED));
        p.add(gap(16));

        JButton pay = button("Pay " + money(t.price - off) + " (mock)", true);
        pay.addActionListener(e -> {
            justUnlocked = false;
            if (usual) {
                streak++;
                justUnlocked = (streak == REWARD_AT || streak == FREE_TRIP_AT);
            }
            show(confirmScreen());
        });
        p.add(fill(pay, 46));
        return p;
    }

    JComponent confirmScreen() {
        Trip t = selected;
        JPanel p = column(BG, 20);
        p.add(gap(30));
        p.add(label("\u2713 You're booked", 26, true, GOOD));
        p.add(gap(12));

        JPanel card = column(CARD, 14);
        card.add(label(from + " \u2192 " + to, 18, true, TEXT));
        card.add(label("Tomorrow \u00B7 " + t.dep + " \u2013 " + t.arr, 14, false, MUTED));
        card.add(gap(8));
        card.add(label("Ticket ref: EMB-" + (1000 + streak * 37), 13, false, MUTED));
        p.add(card);
        p.add(gap(12));

        if (isUsual(t)) {
            p.add(label("\u2605 Streak: " + streak + " days", 18, true, ACCENT));
            if (justUnlocked) {
                String what = streak >= FREE_TRIP_AT ? "a free trip" : "20% off your usual bus";
                p.add(gap(4));
                p.add(label("Reward unlocked: " + what + "!", 15, true, GOOD));
            }
        } else {
            p.add(label("Streak unchanged (" + streak + " days)", 14, false, MUTED));
        }
        p.add(gap(16));

        JButton view = button("View my streak", true);
        view.addActionListener(e -> show(streakScreen()));
        p.add(fill(view, 46));
        p.add(gap(8));
        JButton again = button("Book another trip", false);
        again.addActionListener(e -> show(searchScreen()));
        p.add(fill(again, 40));
        return p;
    }

    JComponent streakScreen() {
        JPanel p = column(BG, 20);
        p.add(label("My streak", 26, true, TEXT));
        p.add(gap(12));

        JPanel hero = column(CARD, 16);
        hero.add(label(String.valueOf(streak), 54, true, ACCENT));
        hero.add(label("days in a row on the " + USUAL_DEP + " Dundee \u2192 Edinburgh", 13, false, MUTED));
        hero.add(gap(10));
        int next = streak < REWARD_AT ? REWARD_AT : FREE_TRIP_AT;
        StringBuilder dots = new StringBuilder();
        for (int i = 1; i <= next; i++) dots.append(i <= streak ? "\u25CF " : "\u25CB ");
        hero.add(label(dots.toString(), 18, false, ACCENT));
        hero.add(gap(8));
        JProgressBar bar = new JProgressBar(0, next);
        bar.setValue(Math.min(streak, next));
        bar.setForeground(ACCENT);
        bar.setBackground(BG);
        bar.setBorderPainted(false);
        hero.add(fill(bar, 10));
        hero.add(gap(6));
        String nextText = streak >= FREE_TRIP_AT ? "All rewards unlocked"
                : (next - streak) + " more to unlock " + (next == REWARD_AT ? "20% off" : "a free trip");
        hero.add(label(nextText, 13, true, TEXT));
        p.add(hero);
        p.add(gap(12));

        p.add(label("Rewards", 16, true, TEXT));
        p.add(gap(6));
        p.add(rewardCard("20% off your usual bus", REWARD_AT));
        p.add(gap(6));
        p.add(rewardCard("One free trip", FREE_TRIP_AT));
        p.add(gap(12));

        JPanel freeze = column(CARD, 12);
        freeze.add(label("Streak freeze: 1 available", 14, true, TEXT));
        freeze.add(label("Miss a day without losing your streak.", 12, false, MUTED));
        p.add(freeze);
        return p;
    }

    JPanel rewardCard(String name, int needed) {
        boolean unlocked = streak >= needed;
        JPanel c = column(CARD, 12);
        c.add(label(name, 14, true, TEXT));
        c.add(label(unlocked ? "\u2713 Unlocked" : "Locked \u00B7 reach a " + needed + "-day streak",
                12, unlocked, unlocked ? GOOD : MUTED));
        return c;
    }

    JComponent navBar() {
        JPanel nav = new JPanel(new GridLayout(1, 2, 8, 0));
        nav.setBackground(CARD);
        nav.setBorder(new EmptyBorder(8, 12, 8, 12));
        JButton book = button("Book", false);
        JButton str = button("\u2605 My streak", false);
        book.addActionListener(e -> show(searchScreen()));
        str.addActionListener(e -> show(streakScreen()));
        nav.add(book);
        nav.add(str);
        return nav;
    }

    // ---- Helpers ----

    boolean isUsual(Trip t) {
        return t.dep.equals(USUAL_DEP) && from.equals("Dundee") && to.equals("Edinburgh");
    }

    static String money(double v) { return String.format("\u00A3%.2f", v); }

    static JPanel column(Color bg, int pad) {
        JPanel p = new JPanel() {
            // stretch to full width, keep natural height
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(bg);
        p.setBorder(new EmptyBorder(pad, pad, pad, pad));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        b.setBackground(primary ? ACCENT : BG);
        b.setForeground(primary ? Color.BLACK : TEXT);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
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
}
