package data;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The avatars that can be unlocked: every  images/avatar-*.png  file.
 * Drop a new PNG named like  avatar-10-fox.png  into images/ and it joins the
 * pool automatically. PERSON is the free default and is never awarded.
 */
public final class IconCatalog {
    private IconCatalog() { }

    /** Used only if the images/ folder can't be read (e.g. app started from another directory). */
    private static final List<String> FALLBACK = List.of(
        "avatar-01-bus", "avatar-02-ghost", "avatar-03-cat",
        "avatar-04-robot", "avatar-05-sprout", "avatar-06-cloud",
        "avatar-07-star", "avatar-08-cactus", "avatar-09-owl"
    );

    private static final List<String> IDS = scan();

    private static List<String> scan() {
        String[] files = new File("images").list();
        List<String> ids = new ArrayList<>();
        if (files != null) {
            for (String f : files) {
                if (f.startsWith("avatar-") && f.toLowerCase().endsWith(".png")) {
                    ids.add(f.substring(0, f.length() - 4));
                }
            }
        }
        if (ids.isEmpty()) return FALLBACK;
        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    /** Avatar ids (file names in images/ without .png), in file-name order. */
    public static List<String> all() { return IDS; }
}
