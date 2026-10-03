package data;

import java.util.List;

public final class IconCatalog {
    private IconCatalog() { }

    /** File names in images/ (without .png). PERSON is the free default and is never awarded. */
    private static final List<String> IDS = List.of(
        "avatar-01-bus", "avatar-02-ghost", "avatar-03-cat",
        "avatar-04-robot", "avatar-05-sprout", "avatar-06-cloud",
        "avatar-07-star", "avatar-08-cactus", "avatar-09-owl"
    );

    public static List<String> all() { return IDS; }
}