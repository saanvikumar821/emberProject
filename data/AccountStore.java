package data;

import data.EmberApi.Stop;
import data.EmberApi.Streak;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;

/**
 * Local-only account storage for the hackathon. Everything is kept in memory
 * and written to  ~/.ember_rewards/accounts.properties  after every change.
 * Delete that file to reset to the demo account.
 *
 * File layout (java.util.Properties):
 *   accounts=demo,rob
 *   current=rob
 *   account.rob.name=Rob
 *   account.rob.icon=avatar-03-cat
 *   account.rob.icons=avatar-03-cat,avatar-09-owl
 *   account.rob.title=Passenger
 *   account.rob.journeys=1
 *   account.rob.journey.0=13|Dundee (City Centre)|42|Edinburgh (City Centre)|07:17|4|12
 *                         fromId|fromName|toId|toName|time|streak|bookings
 *
 * To move to a real backend later, only this class needs to change.
 */
public final class AccountStore {
    private AccountStore() { }

    private static final Path FILE =
            Paths.get(System.getProperty("user.home"), ".ember_rewards", "accounts.properties");
    private static final Map<String, Account> ACCOUNTS = new LinkedHashMap<>();
    private static Account current;

    static {
        load();
        if (ACCOUNTS.isEmpty()) seedDemo();
        if (current == null) current = ACCOUNTS.values().iterator().next();
    }

    // ---- Public API ----

    /** The signed-in account. Never null. */
    public static Account current() { return current; }

    /** All accounts, in creation order. */
    public static List<Account> all() { return new ArrayList<>(ACCOUNTS.values()); }

    public static Account find(String username) { return ACCOUNTS.get(username); }

    /** Turns a display name into a username: lowercase letters and digits only. May return "". */
    public static String usernameFor(String displayName) {
        return displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Creates an empty account and signs in to it. Returns null if the name is invalid or taken. */
    public static Account create(String displayName) {
        String name = displayName.trim();
        String user = usernameFor(name);
        if (name.isEmpty() || user.isEmpty() || ACCOUNTS.containsKey(user)) return null;
        Account a = new Account(user, name);
        ACCOUNTS.put(user, a);
        current = a;
        save();
        return a;
    }

    /** Signs in to an existing account. */
    public static void setCurrent(Account a) {
        if (a != null && ACCOUNTS.containsKey(a.username)) {
            current = a;
            save();
        }
    }

    /** Writes every account to disk. Call after changing an account's fields. */
    public static void save() {
        Properties p = new Properties();
        p.setProperty("accounts", String.join(",", ACCOUNTS.keySet()));
        if (current != null) p.setProperty("current", current.username);
        for (Account a : ACCOUNTS.values()) {
            String k = "account." + a.username + ".";
            p.setProperty(k + "name", a.displayName);
            p.setProperty(k + "icon", a.iconId);
            p.setProperty(k + "icons", String.join(",", a.unlockedIcons));
            p.setProperty(k + "title", a.title);
            p.setProperty(k + "journeys", String.valueOf(a.journeys.size()));
            for (int i = 0; i < a.journeys.size(); i++) {
                Streak s = a.journeys.get(i);
                p.setProperty(k + "journey." + i, s.from.id + "|" + s.from.name + "|" + s.to.id + "|"
                        + s.to.name + "|" + s.time + "|" + s.count + "|" + s.bookings);
            }
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                p.store(w, "Ember reward app accounts (hackathon, local only)");
            }
        } catch (IOException e) {
            System.err.println("Couldn't save accounts (continuing in memory): " + e.getMessage());
        }
    }

    // ---- Loading ----

    private static void load() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return;   // first run: no file yet
        }
        for (String user : p.getProperty("accounts", "").split(",")) {
            user = user.trim();
            if (user.isEmpty()) continue;
            String k = "account." + user + ".";
            Account a = new Account(user, p.getProperty(k + "name", user));
            a.iconId = p.getProperty(k + "icon", Account.DEFAULT_ICON);
            a.title = p.getProperty(k + "title", Account.DEFAULT_TITLE);
            for (String id : p.getProperty(k + "icons", "").split(",")) {
                if (!id.trim().isEmpty()) a.unlockedIcons.add(id.trim());
            }
            int n = 0;
            try { n = Integer.parseInt(p.getProperty(k + "journeys", "0")); } catch (NumberFormatException ignored) { }
            for (int i = 0; i < n; i++) {
                try {
                    String[] f = p.getProperty(k + "journey." + i, "").split("\\|");
                    a.journeys.add(new Streak(
                            new Stop(Integer.parseInt(f[0]), f[1]), new Stop(Integer.parseInt(f[2]), f[3]),
                            LocalTime.parse(f[4]), Integer.parseInt(f[5]), Math.max(1, Integer.parseInt(f[6]))));
                } catch (RuntimeException ignored) { /* skip a damaged entry */ }
            }
            ACCOUNTS.put(user, a);
        }
        current = ACCOUNTS.get(p.getProperty("current", ""));
    }

    /** First-run demo account with one journey at every rarity, so every colour is visible. */
    private static void seedDemo() {
        List<Stop> st = EmberApi.fallbackStops();   // 0 Dundee, 1 Edinburgh, 2 Glasgow, 3 Perth, 4 Aberdeen
        Account a = new Account("demo", "Demo Rider");
        a.journeys.add(new Streak(st.get(0), st.get(1), LocalTime.of(7, 17), 4, 12));    // Rare
        a.journeys.add(new Streak(st.get(0), st.get(1), LocalTime.of(8, 16), 2, 3));     // Uncommon
        a.journeys.add(new Streak(st.get(3), st.get(2), LocalTime.of(9, 15), 7, 16));    // Epic
        a.journeys.add(new Streak(st.get(0), st.get(4), LocalTime.of(10, 0), 1, 1));     // Common
        a.journeys.add(new Streak(st.get(1), st.get(0), LocalTime.of(17, 16), 3, 32));   // Legendary
        a.unlockedIcons.addAll(List.of("avatar-07-star", "avatar-03-cat", "avatar-09-owl", "avatar-01-bus"));
        a.iconId = "avatar-07-star";
        a.title = "Commuter";
        ACCOUNTS.put(a.username, a);
        current = a;
        save();
    }
}