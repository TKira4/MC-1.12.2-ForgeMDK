package com.imba.undyingarcana;

/**
 * AO-style builds from the four stats (shared by server and client, no game classes).
 * Order of the stats everywhere: 0 Vitality, 1 Magic, 2 Strength, 3 Weapons.
 * Rules: from level 16, two stats at 40%+ each = hybrid; else one at 60%+ = pure; else all four at 15%+ = Savant.
 */
public final class Builds {
    public static final int VIT = 0, MAG = 1, STR = 2, WPN = 3, AGI = 4, DEF = 5, LUCK = 6, CORE = 4, COUNT = 7;
    public static final String[] STAT = {"Vitality", "Magic", "Strength", "Weapons", "Agility", "Defense", "Luck"};
    public static final String[] STAT_COLOR = {"\u00a7a", "\u00a79", "\u00a76", "\u00a7c", "\u00a7b", "\u00a77", "\u00a7e"};
    public static final int[] STAT_RGB = {0x55CC66, 0x6688FF, 0xFFAA22, 0xFF5544, 0x55DDDD, 0xB8BCC8, 0xF0E060};
    public static final String[] STAT_DESC = {
        "+0.5 health and 0.15% damage reduction per point",
        "Wand skills scale, +0.6% other magic damage per point",
        "+2% unarmed and +0.3% melee damage per point",
        "Weapon skills scale, +0.6% weapon damage per point",
        "+0.4% speed, +0.25% dodge (max 25%), -1% fall damage per point",
        "+0.15 armor and +0.05 armor toughness per point",
        "+0.3% critical hit x1.5 (max 30%), +1% XP, better drops per point"};

    /** Agility, Defense and Luck can be raised from the same points, but only the four core stats make the build. */
    public static float dodge(int agi) {
        return Math.min(0.25F, 0.0025F * agi);
    }

    public static float crit(int luck) {
        return Math.min(0.30F, 0.003F * luck);
    }

    public static final int BUILD_LEVEL = 16, MAX_LEVEL = 100;

    public static final int ADVENTURER = 0, WARDEN = 1, MAGE = 2, BERSERKER = 3, WARRIOR = 4, ROGUE = 5, GUARDIAN = 6, GAMBLER = 7,
            PALADIN = 8, JUGGERNAUT = 9, KNIGHT = 10, SURVIVOR = 11, BULWARK = 12, PILGRIM = 13,
            WARLOCK = 14, CONJURER = 15, TRICKSTER = 16, RUNEGUARD = 17, DIVINER = 18,
            WARLORD = 19, BRAWLER = 20, VANGUARD = 21, BRIGAND = 22,
            DUELIST = 23, SENTINEL = 24, MARKSMAN = 25, SKIRMISHER = 26, ASSASSIN = 27, TEMPLAR = 28,
            SAVANT = 29, UNBOUND = 30;
    public static final String[] NAME = {"Adventurer", "Warden", "Mage", "Berserker", "Warrior", "Rogue", "Guardian", "Gambler",
            "Paladin", "Juggernaut", "Knight", "Survivor", "Bulwark", "Pilgrim",
            "Warlock", "Conjurer", "Trickster", "Runeguard", "Diviner",
            "Warlord", "Brawler", "Vanguard", "Brigand",
            "Duelist", "Sentinel", "Marksman", "Skirmisher", "Assassin", "Templar", "Savant", "Unbound"};
    /** Signature of each build, in one line. */
    public static final String[] DESC = {
        "Spend freely: refunds are free until level 16",
        "Undying: a killing blow leaves you at 1 health, 3 s immune, heal 30% (every 3 min)",
        "Overcharge: every 4th wand skill is free and twice the size",
        "Bloodrage: up to +50% damage as your health drops; kills heal 5%",
        "Weapon Master: a skill within 3 s of switching weapon type deals +40%",
        "Shadowstep: after a dodge your next hit crits and zombies lose you",
        "Bastion: sneak to brace: -60% damage from the front, taunts zombies in 8 blocks",
        "Jackpot: 10% of crits deal x3 and burst gold; elites drop extra loot",
        "Sanctuary: spell hits heal you and allies nearby",
        "Unstoppable: no knockback or slow; sprinting bowls zombies over",
        "Riposte: a hit within 1 s after blocking deals +60%",
        "Second Wind: a big hit gives speed and regeneration for 4 s",
        "Fortress: stand still 2 s for +8 armor and regeneration",
        "Blessed: 10% chance to ignore a hit and heal 2",
        "Soul Fist: unarmed hits burst with magic",
        "Imbue: weapon skills take your wand's element",
        "Blink: double-tap sneak to teleport 6 blocks, leaving a decoy",
        "Ward: every 10 s a magic shield absorbs 20% of your health",
        "Foresight: elites glow through walls nearby; spell crits cut cooldowns",
        "Rally: kills give you and allies +10% damage (stacks 3)",
        "Combo: unarmed hits stack +10% (5 max); sprint punches lunge",
        "Breaker: hits sunder armor twice as fast and ignore riot shields",
        "Plunder: kills may drop extra loot; crits stagger",
        "Counter: hit back within 0.5 s of being hit for double damage",
        "Overwatch: shots at zombies that target an ally deal +30%",
        "Headhunter: headshots always crit; +10% crit with bows and guns",
        "Hit and Run: hits give +30% speed for 2 s; dodges harden you",
        "Execute: enemies under 25% health take crits and +50%",
        "Divine Shield: 15% chance to block a hit and reflect it",
        "Adaptive: the signatures of your two highest stats at half strength",
        "No build yet: 60% in one stat, 40% + 40% in two, or 8%+ in all seven"};
    public static final String[] COLOR = new String[NAME.length];
    /** Hybrid build for each stat pair (i < j). */
    private static final int[][] PAIR = new int[COUNT][COUNT];
    public static final int[] PURE = {WARDEN, MAGE, BERSERKER, WARRIOR, ROGUE, GUARDIAN, GAMBLER};

    static {
        int b = PALADIN;
        for (int i = 0; i < COUNT; i++) {
            for (int j = i + 1; j < COUNT; j++) {
                PAIR[i][j] = PAIR[j][i] = b++;
            }
        }
        COLOR[ADVENTURER] = "\u00a77";
        COLOR[SAVANT] = "\u00a7f";
        COLOR[UNBOUND] = "\u00a78";
        for (int i = 0; i < COUNT; i++) {
            COLOR[PURE[i]] = STAT_COLOR[i];
            for (int j = i + 1; j < COUNT; j++) {
                COLOR[PAIR[i][j]] = "\u00a7d";
            }
        }
    }

    private Builds() {
    }

    public static int points(int level) {
        return 2 * Math.max(1, Math.min(MAX_LEVEL, level));
    }

    /** XP needed to go from this level to the next. */
    public static int need(int level) {
        return 20 + 6 * level;
    }

    /** The two stats of a hybrid build (null for others). */
    public static int[] pair(int build) {
        for (int i = 0; i < COUNT; i++) {
            for (int j = i + 1; j < COUNT; j++) {
                if (PAIR[i][j] == build) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    public static int build(int level, int[] s) {
        if (level < BUILD_LEVEL) {
            return ADVENTURER;
        }
        int total = 0;
        for (int i = 0; i < COUNT; i++) {
            total += s[i];
        }
        if (total <= 0) {
            return UNBOUND;
        }
        int a = -1, b = -1, n40 = 0;
        for (int i = 0; i < COUNT; i++) {
            if (s[i] * 100 >= total * 40) {
                n40++;
                if (a < 0) {
                    a = i;
                } else {
                    b = i;
                }
            }
        }
        if (n40 == 2) {
            return PAIR[a][b];
        }
        for (int i = 0; i < COUNT; i++) {
            if (s[i] * 100 >= total * 60) {
                return PURE[i];
            }
        }
        boolean all = true;
        for (int i = 0; i < COUNT; i++) {
            all &= s[i] * 100 >= total * 8;
        }
        return all ? SAVANT : UNBOUND;
    }

    /**
     * Strength (0..1) of a build signature for a player with this build and these stats: 1 for the build itself,
     * 0.5 for the two pure signatures of a Savant's highest stats.
     */
    public static float sig(int build, int[] s, int signature) {
        if (build == signature) {
            return 1.0F;
        }
        if (build == SAVANT) {
            int top = 0, second = -1;
            for (int i = 1; i < COUNT; i++) {
                if (s[i] > s[top]) {
                    top = i;
                }
            }
            for (int i = 0; i < COUNT; i++) {
                if (i != top && (second < 0 || s[i] > s[second])) {
                    second = i;
                }
            }
            return PURE[top] == signature || PURE[second] == signature ? 0.5F : 0.0F;
        }
        return 0.0F;
    }

    /** Skill level (1..32, the old Reskillable scale) from points in the stat. */
    public static int skillLevel(int points) {
        return 1 + Math.round(points * 31 / 100.0F);
    }

    /** Skill rank 0..5 (replaces the old Rank quests): one per 16 points. */
    public static int rank(int points) {
        return Math.min(5, points / 16);
    }

    public static boolean uses(int build, int stat) {
        if (build == SAVANT) {
            return true;
        }
        if (build >= WARDEN && build <= GAMBLER) {
            return PURE[stat] == build;
        }
        int[] p = pair(build);
        return p != null && (p[0] == stat || p[1] == stat);
    }

    /** Damage taken multiplier from Vitality. */
    public static float damageTaken(int vit) {
        return 1.0F - Math.min(0.3F, 0.0015F * vit);
    }
}
