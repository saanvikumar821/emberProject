package data;

import data.BusData.Streak;
import java.util.ArrayList;
import java.util.List;

/** One local account: profile choices plus that person's journeys. Create via AccountStore. */
public class Account {
    public static final String DEFAULT_ICON = "PERSON";
    public static final String DEFAULT_TITLE = "Newcomer";

    public final String username;           // lowercase id, e.g. "rob"
    public String displayName;
    public String iconId = DEFAULT_ICON;     // the equipped profile icon
    public String title = DEFAULT_TITLE;     // the equipped profile title
    public final List<Streak> journeys = new ArrayList<>();

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
        if (DEFAULT_ICON.equals(id)) return true;
        for (Rarity r : Rarity.values()) if (r.iconId.equals(id)) return hasReached(r);
        return false;
    }

    public List<String> unlockedTitles() {
        List<String> out = new ArrayList<>();
        out.add(DEFAULT_TITLE);
        for (Rarity r : Rarity.values()) if (hasReached(r)) out.add(r.title);
        return out;
    }
}