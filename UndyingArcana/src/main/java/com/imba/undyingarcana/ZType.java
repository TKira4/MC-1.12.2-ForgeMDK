package com.imba.undyingarcana;

/**
 * The zombie types of the Undying Arcana zombie system. Order matches ModelUAZombie (generated from zombie_design.py).
 * Stats are week-0 values; EntityUAZombie scales them by the world's week.
 */
public enum ZType {
    //          health dmg  speed  armor tough  mres  kbres  width height scale  xp weight minWeek
    WALKER(      22, 4.0, 0.22,   2, 0, 0.00F, 0.0,  0.6F, 1.95F, 1.00F,  5, 50, 0),
    RUNNER(      14, 3.0, 0.36,   0, 0, 0.00F, 0.0,  0.6F, 2.00F, 1.05F,  6, 18, 0),
    CRAWLER(     16, 4.0, 0.28,   0, 0, 0.00F, 0.0,  0.8F, 0.70F, 0.90F,  6, 10, 1),
    BRUTE(       80, 10.0, 0.20, 10, 4, 0.00F, 1.0,  1.0F, 3.10F, 1.60F, 25,  4, 2),
    SCREAMER(    18, 2.0, 0.27,   0, 0, 0.00F, 0.0,  0.6F, 2.15F, 1.10F, 12,  5, 1),
    SPITTER(     20, 3.0, 0.24,   1, 0, 0.00F, 0.0,  0.6F, 1.95F, 1.00F, 10,  8, 1),
    BLOATER(     30, 2.0, 0.20,   2, 0, 0.00F, 0.3,  0.9F, 2.30F, 1.20F, 10,  6, 2),
    ARMORED(     30, 5.0, 0.23,  16, 6, 0.00F, 0.5,  0.65F, 2.05F, 1.05F, 15,  6, 3),
    NECROMANCER( 45, 4.0, 0.22,   4, 0, 0.50F, 0.2,  0.65F, 2.20F, 1.15F, 30,  2, 4);

    public final double health, damage, speed, armor, toughness, kbResist;
    public final float magicResist, width, height, scale;
    public final int xp, weight, minWeek;

    ZType(double health, double damage, double speed, double armor, double toughness, float magicResist, double kbResist,
          float width, float height, float scale, int xp, int weight, int minWeek) {
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.armor = armor;
        this.toughness = toughness;
        this.magicResist = magicResist;
        this.kbResist = kbResist;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.xp = xp;
        this.weight = weight;
        this.minWeek = minWeek;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String display() {
        String s = id();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static ZType byId(String id) {
        for (ZType t : values()) {
            if (t.id().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    public static ZType of(int ordinal) {
        ZType[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : WALKER;
    }

    /** Weighted pick among the types unlocked by this week. */
    public static ZType pick(java.util.Random r, long week) {
        int total = 0;
        for (ZType t : values()) {
            if (week >= t.minWeek) {
                total += t.weight;
            }
        }
        int n = r.nextInt(total);
        for (ZType t : values()) {
            if (week >= t.minWeek && (n -= t.weight) < 0) {
                return t;
            }
        }
        return WALKER;
    }
}
