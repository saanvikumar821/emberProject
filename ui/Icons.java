package ui;

import java.awt.*;
import java.awt.geom.*;

/** Small vector icons drawn with Java2D, so no image files are needed. Ids match data.Rarity.iconId. */
final class Icons {
    private Icons() { }

    /** Icons a player can equip on their profile (the first is the free default). */
    static final String[] ACCOUNT_ICONS = {"PERSON", "BUS", "TREE", "MOUNTAIN", "CASTLE", "STAR"};

    static String name(String id) {
        switch (id) {
            case "PERSON": return "Default";
            case "BUS": return "Bus";
            case "TREE": return "Tree";
            case "MOUNTAIN": return "Mountain";
            case "CASTLE": return "Castle";
            case "STAR": return "Star";
            case "PLANE": return "Plane";
            case "SKYLINE": return "Skyline";
            default: return id;
        }
    }

    /** Draws icon `id` inside the square (x, y, size, size). Unknown ids draw nothing. */
    static void draw(Graphics2D g0, String id, int x, int y, int size, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale(size / 100.0, size / 100.0);   // draw everything in a 100 x 100 box
        g.setColor(color);
        switch (id) {
            case "PERSON":
                g.fill(new Ellipse2D.Double(34, 12, 32, 32));
                g.fill(new Arc2D.Double(16, 52, 68, 68, 0, 180, Arc2D.PIE));
                break;
            case "BUS": {
                Area bus = new Area(new RoundRectangle2D.Double(10, 20, 80, 52, 16, 16));
                for (int i = 0; i < 3; i++) {
                    bus.subtract(new Area(new RoundRectangle2D.Double(18 + i * 22, 30, 18, 16, 4, 4)));
                }
                g.fill(bus);
                g.fill(new Ellipse2D.Double(22, 62, 18, 18));
                g.fill(new Ellipse2D.Double(60, 62, 18, 18));
                break;
            }
            case "TREE":
                g.fill(poly(50, 6, 76, 46, 24, 46));
                g.fill(poly(50, 24, 84, 72, 16, 72));
                g.fill(new Rectangle2D.Double(43, 72, 14, 20));
                break;
            case "MOUNTAIN":
                g.fill(poly(4, 88, 36, 28, 54, 58, 68, 40, 96, 88));
                g.fill(new Ellipse2D.Double(68, 10, 18, 18));
                break;
            case "CASTLE": {
                Area c = new Area(new Rectangle2D.Double(14, 40, 72, 48));
                c.add(new Area(new Rectangle2D.Double(14, 26, 16, 16)));
                c.add(new Area(new Rectangle2D.Double(42, 26, 16, 16)));
                c.add(new Area(new Rectangle2D.Double(70, 26, 16, 16)));
                Area gate = new Area(new Rectangle2D.Double(40, 66, 20, 22));
                gate.add(new Area(new Ellipse2D.Double(40, 56, 20, 20)));
                c.subtract(gate);
                g.fill(c);
                break;
            }
            case "STAR": {
                Path2D star = new Path2D.Double();
                for (int i = 0; i < 10; i++) {
                    double r = i % 2 == 0 ? 46 : 19;
                    double a = Math.toRadians(-90 + 36 * i);
                    double px = 50 + r * Math.cos(a), py = 54 + r * Math.sin(a);
                    if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
                }
                star.closePath();
                g.fill(star);
                break;
            }
            case "PLANE":
                g.fill(poly(50, 6, 58, 36, 94, 60, 94, 70, 58, 60, 56, 82, 70, 92, 70, 96,
                        50, 90, 30, 96, 30, 92, 44, 82, 42, 60, 6, 70, 6, 60, 42, 36));
                break;
            case "SKYLINE": {
                Area sk = new Area(new Rectangle2D.Double(6, 52, 22, 38));
                sk.add(new Area(new Rectangle2D.Double(30, 26, 24, 64)));
                sk.add(new Area(new Rectangle2D.Double(56, 40, 18, 50)));
                sk.add(new Area(new Rectangle2D.Double(76, 60, 18, 30)));
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 2; col++) {
                        sk.subtract(new Area(new Rectangle2D.Double(36 + col * 10, 34 + row * 16, 6, 8)));
                    }
                }
                g.fill(sk);
                break;
            }
            case "LOCK":
                g.fill(new RoundRectangle2D.Double(24, 44, 52, 42, 10, 10));
                g.setStroke(new BasicStroke(8f));
                g.draw(new Arc2D.Double(34, 14, 32, 60, 0, 180, Arc2D.OPEN));
                break;
            default:
                break;
        }
        g.dispose();
    }

    private static Path2D poly(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        return p;
    }
}