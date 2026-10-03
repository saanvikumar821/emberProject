package data;

import data.EmberApi.Streak;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/** One local account: profile choices plus that person's journeys. Create via AccountStore. */
public class Account {
    public static final String DEFAULT_ICON = "PERSON";
    public static final String DEFAULT_TITLE = "Newcomer";

    public final String username;           // lowercase id, e.g. "rob"
    public String displayName;
    public String iconId = DEFAULT_ICON;     // the equipped profile icon
    public String title = DEFAULT_TITLE;     // the equipped profile title
    public final List<Streak> journeys = new ArrayList<>();
    private static final Random RNG = new Random();
    public final Set<String> unlockedIcons = new LinkedHashSet<>();

    Account(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    /** The best rarity reached on any journey, or null if no journeys yet. */
    public Rarity highestRarity() {
        Rarity best = null;
        for (Streak s : journeys) {
            Rarity r = s.rarity();
            if (r != null && (best == null || r.ordinal() > best.ordinal())) best = r;
        }
        return best;
    }

    /** True once any journey has reached this tier (so its reward is unlocked). */
    public boolean hasReached(Rarity r) {
        Rarity top = highestRarity();
        return top != null && r.ordinal() <= top.ordinal();
    }

    public boolean hasIcon(String id) {
        return DEFAULT_ICON.equals(id) || unlockedIcons.contains(id);
    }

    public List<String> unlockedTitles() {
        List<String> out = new ArrayList<>();
        out.add(DEFAULT_TITLE);
        for (Rarity r : Rarity.values()) if (hasReached(r)) out.add(r.title);
        return out;
    }

    /** Unlocks one random avatar the account doesn't own yet. Returns its id, or null if every avatar is owned. */
    private String grantRandomIcon() {
        List<String> pool = new ArrayList<>(IconCatalog.all());
        pool.removeAll(unlockedIcons);
        if (pool.isEmpty()) return null;
        String pick = pool.get(RNG.nextInt(pool.size()));
        unlockedIcons.add(pick);
        return pick;
    }

    /**
     * Call after a journey's booking count changes. For every level that journey
     * just reached (above Common), unlocks one random avatar and equips the newest.
     * Also equips the new title if the account's best level went up.
     *
     * @param journeyBefore the journey's level before the booking (null if it was new)
     * @param journeyNow    the journey's level after the booking
     * @param accountBefore the account's best level before the booking
     * @return the avatars unlocked by this booking (empty if none)
     */
    public List<String> syncRewards(Rarity journeyBefore, Rarity journeyNow, Rarity accountBefore) {
        List<String> gained = new ArrayList<>();
        if (journeyNow == null) return gained;
        int from = journeyBefore == null ? -1 : journeyBefore.ordinal();
        for (Rarity r : Rarity.values()) {
            if (r.ordinal() > from && r.ordinal() <= journeyNow.ordinal() && r != Rarity.COMMON) {
                String icon = grantRandomIcon();
                if (icon != null) gained.add(icon);
            }
        }
        if (!gained.isEmpty()) iconId = gained.get(gained.size() - 1);

        Rarity top = highestRarity();
        if (top != null && (accountBefore == null || top.ordinal() > accountBefore.ordinal())) title = top.title;
        return gained;
    }
}
