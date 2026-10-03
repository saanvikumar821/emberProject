package data;

/**
 * Journey levels. Each level is a rarity tier, reached by booking the same
 * journey enough times. Reaching a tier on ANY journey unlocks that tier's
 * reward (a profile icon and a profile title) for the whole account.
 *
 * To change the grind, edit bookingsNeeded. To change rewards, edit iconId
 * and title (icon ids are drawn by ui.Icons).
 */
public enum Rarity {
    //        label        bookings  reward icon  reward title
    COMMON   ("Common",     1,       "BUS",       "Passenger"),
    UNCOMMON ("Uncommon",   3,       "TREE",      "Regular"),
    RARE     ("Rare",       7,       "MOUNTAIN",  "Commuter"),
    EPIC     ("Epic",       15,      "CASTLE",    "Road Warrior"),
    LEGENDARY("Legendary",  30,      "STAR",      "Ember Legend");

    public final String label;
    public final int bookingsNeeded;
    public final String iconId, title;

    Rarity(String label, int bookingsNeeded, String iconId, String title) {
        this.label = label; this.bookingsNeeded = bookingsNeeded;
        this.iconId = iconId; this.title = title;
    }

    /** 1 for Common up to 5 for Legendary. */
    public int level() { return ordinal() + 1; }

    /** The next tier up, or null at Legendary. */
    public Rarity next() { return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null; }

    /** The highest tier reached with this many bookings, or null for zero bookings. */
    public static Rarity forBookings(int bookings) {
        Rarity best = null;
        for (Rarity r : values()) if (bookings >= r.bookingsNeeded) best = r;
        return best;
    }
}