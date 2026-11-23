package com.imba.undyingarcana;

import net.minecraft.item.ItemStack;

/**
 * Static data of the weapon skill system: which items have skills, their family / material / element, the names,
 * descriptions, icons and numbers of the three moves (Quick, Heavy, Art) of every weapon type.
 */
public final class SkillCatalog {
    public static final String[] BLADES = {"dagger", "parrying_dagger", "longsword", "vanilla_sword", "katana", "saber", "rapier"};
    public static final String[] HEAVIES = {"greatsword", "vanilla_axe", "hammer", "warhammer", "mace", "battleaxe"};
    public static final String[] POLES = {"spear", "pike", "lance", "halberd", "glaive", "staff"};
    public static final String[] BOWS = {"longbow", "crossbow", "bow", "switch"};
    public static final String[] GUNS = {"pistol", "shotgun", "rifle", "cannon"};
    public static final String[] ELEMENTS = {"magic", "fire", "ice", "lightning", "necromancy", "earth", "sorcery", "healing"};

    public static final String[] KIND = {"Quick", "Heavy", "Art"};
    public static final int QUICK_LEVEL = 4;
    public static final int ART_LEVEL = 16;

    private SkillCatalog() {
    }

    /** What a held item is, for the skill system. */
    public static final class Info {
        public final String type;    // e.g. "katana", "longbow", "wand_fire"
        public final String mat;     // wood / stone / gold / iron / diamond / leather / novice.. / legend
        public final String family;  // blade / heavy / polearm / bow / wand
        public final String legend;  // legend key or ""
        public final String id;

        Info(String id, String type, String mat, String legend) {
            this.id = id;
            this.type = type;
            this.mat = mat;
            this.legend = legend;
            this.family = familyOf(type);
        }

        public boolean isBow() {
            return "bow".equals(family);
        }

        public boolean isWand() {
            return "wand".equals(family);
        }

        public boolean isGun() {
            return "gun".equals(family);
        }

        /** Bows and guns: Archery weapons that deal arrow (projectile) damage. */
        public boolean isRanged() {
            return isBow() || isGun();
        }

        public boolean isMelee() {
            return !isBow() && !isWand() && !isGun();
        }

        /** Wand element ("fire"...), or "" for weapons. */
        public String wandElement() {
            return type.startsWith("wand_") ? type.substring(5) : "";
        }
    }

    public static String familyOf(String t) {
        String af = ArmoryMoves.family(t);
        if (af != null) {
            return af;
        }
        if (t.startsWith("wand_")) {
            return "wand";
        }
        for (String g : GUNS) {
            if (g.equals(t)) {
                return "gun";
            }
        }
        for (String s : BLADES) {
            if (s.equals(t)) {
                return "blade";
            }
        }
        for (String s : HEAVIES) {
            if (s.equals(t)) {
                return "heavy";
            }
        }
        for (String s : POLES) {
            if (s.equals(t)) {
                return "polearm";
            }
        }
        return "bow";
    }

    public static Info identify(ItemStack st) {
        if (st == null || st.isEmpty() || st.getItem().getRegistryName() == null) {
            return null;
        }
        return identify(st.getItem().getRegistryName().toString());
    }

    public static Info identify(String id) {
        if (id.startsWith("undyingarcana:legend_")) {
            String k = id.substring(17);
            String t = legendType(k);
            return t.isEmpty() ? null : new Info(id, t, "legend", k);
        }
        int[] arm = Armory.parse(id);
        if (arm != null) {
            return new Info(id, Armory.MOVE[arm[0]], Armory.TIER[arm[1]], "");
        }
        if (id.startsWith("undyingarcana:gun_")) {
            String g = id.substring(14);
            for (String t : GUNS) {
                if (t.equals(g)) {
                    return new Info(id, t, "iron", "");
                }
            }
            return null;
        }
        if (id.startsWith("spartanweaponry:")) {
            String rest = id.substring(16);
            for (String m : new String[]{"wood", "stone", "gold", "iron", "diamond"}) {
                for (String t : BLADES) {
                    if (rest.equals(t + "_" + m)) {
                        return new Info(id, t, m, "");
                    }
                }
                for (String t : HEAVIES) {
                    if (rest.equals(t + "_" + m)) {
                        return new Info(id, t, m, "");
                    }
                }
                for (String t : POLES) {
                    if (rest.equals(t + "_" + m)) {
                        return new Info(id, t, m, "");
                    }
                }
            }
            for (String m : new String[]{"wood", "leather", "iron", "diamond"}) {
                if (rest.equals("longbow_" + m)) {
                    return new Info(id, "longbow", m, "");
                }
                if (rest.equals("crossbow_" + m)) {
                    return new Info(id, "crossbow", m, "");
                }
            }
            return null;
        }
        if (id.startsWith("minecraft:")) {
            String[] vm = {"wooden", "stone", "golden", "iron", "diamond"};
            String[] mm = {"wood", "stone", "gold", "iron", "diamond"};
            for (int i = 0; i < 5; i++) {
                if (id.equals("minecraft:" + vm[i] + "_sword")) {
                    return new Info(id, "vanilla_sword", mm[i], "");
                }
                if (id.equals("minecraft:" + vm[i] + "_axe")) {
                    return new Info(id, "vanilla_axe", mm[i], "");
                }
            }
            if (id.equals("minecraft:bow")) {
                return new Info(id, "bow", "leather", "");
            }
            return null;
        }
        if (id.equals("switchbow:switchbow") || id.equals("switchbow:switchcrossbow")) {
            return new Info(id, "switch", "iron", "");
        }
        // Wizardry add-ons: spell swords (Arcane Essentials, Ancient Spellcraft) and Wizardry Tales wands use wand skills.
        if (id.startsWith("arcane_essentials:") && id.endsWith("_sword")) {
            return new Info(id, "wand_" + addonElement(id), addonTier(id), "");
        }
        if (id.startsWith("ancientspellcraft:battlemage_sword_") && !id.endsWith("_hilt") && !id.endsWith("_blade")) {
            return new Info(id, "wand_" + addonElement(id), addonTier(id), "");
        }
        if (id.contains(":m_wand_") || id.endsWith(":m_wand")) {
            return new Info(id, "wand_" + addonElement(id), addonTier(id), "");
        }
        if (id.startsWith("ebwizardry:") && id.contains("_wand")) {
            if (id.equals("ebwizardry:festive_wand") || id.equals("ebwizardry:wand_point")) {
                return null;
            }
            String tier = "novice";
            for (String tr : new String[]{"apprentice", "advanced", "master"}) {
                if (id.contains(tr)) {
                    tier = tr;
                }
            }
            String el = "magic";
            for (String e : new String[]{"fire", "ice", "lightning", "necromancy", "earth", "sorcery", "healing"}) {
                if (id.contains("_" + e + "_wand")) {
                    el = e;
                }
            }
            return new Info(id, "wand_" + el, tier, "");
        }
        return null;
    }

    /** Wand tier from an add-on item id (novice / apprentice / advanced / master). */
    private static String addonTier(String id) {
        for (String t : new String[]{"master", "advanced", "apprentice"}) {
            if (id.contains(t)) {
                return t;
            }
        }
        return "novice";
    }

    /** Wand element from an add-on item id, mapping the add-ons' own element names. */
    private static String addonElement(String id) {
        String p = id.substring(id.indexOf(':') + 1);
        if (p.contains("fire")) {
            return "fire";
        }
        if (p.contains("_ice") || p.startsWith("ice") || p.contains("frost")) {
            return "ice";
        }
        if (p.contains("lightning") || p.contains("thunder") || p.contains("storm")) {
            return "lightning";
        }
        if (p.contains("necromancy") || p.contains("darkness")) {
            return "necromancy";
        }
        if (p.contains("earth") || p.contains("nature")) {
            return "earth";
        }
        if (p.contains("sorcery")) {
            return "sorcery";
        }
        if (p.contains("healing") || p.contains("light")) {
            return "healing";
        }
        return "magic";
    }

    public static String legendType(String k) {
        switch (k) {
            case "stormcaller": return "katana";
            case "earthbreaker": return "warhammer";
            case "bloodthirst": return "battleaxe";
            case "frostfang": return "rapier";
            case "aegis_edge": return "longsword";
            case "dawnbreaker": return "greatsword";
            case "gravewarden": return "halberd";
            case "shadowfang": return "dagger";
            case "windpiercer":
            case "starfall": return "longbow";
            case "thunderstring":
            case "hydra":
            case "venomstring": return "bow";
            case "phoenix": return "wand_fire";
            case "glacier": return "wand_ice";
            case "stormlord": return "wand_lightning";
            case "lich": return "wand_necromancy";
            case "gaia": return "wand_earth";
            case "archon": return "wand_sorcery";
            case "seraph": return "wand_healing";
            case "peacemaker": return "pistol";
            case "dragons_roar": return "shotgun";
            case "widowmaker": return "rifle";
            case "thunderclap": return "cannon";
            default: return "";
        }
    }

    /** Calling that can awaken a legend's skills. */
    public static String legendClass(String k) {
        switch (k) {
            case "stormcaller": case "earthbreaker": case "bloodthirst": case "frostfang": case "aegis_edge":
            case "dawnbreaker": case "gravewarden": case "shadowfang":
                return "warrior";
            case "windpiercer": case "starfall": case "thunderstring": case "hydra": case "venomstring":
            case "peacemaker": case "dragons_roar": case "widowmaker": case "thunderclap":
                return "ranger";
            default:
                return "mage";
        }
    }

    /** VFX element of a weapon's own skills (before any imbue). */
    public static String elementOf(Info in) {
        if (ArmoryMoves.has(in.type) && !in.isWand()) {
            return ArmoryMoves.elementOf(in.type);
        }
        if (in.isWand()) {
            return in.wandElement();
        }
        switch (in.legend) {
            case "stormcaller": case "thunderstring": return "lightning";
            case "earthbreaker": return "earth";
            case "bloodthirst": return "blood";
            case "frostfang": return "ice";
            case "aegis_edge": return "holy";
            case "dawnbreaker": case "windpiercer": return "fire";
            case "gravewarden": return "necromancy";
            case "shadowfang": return "shadow";
            case "starfall": return "magic";
            case "hydra": case "venomstring": return "poison";
            case "peacemaker": return "holy";
            case "dragons_roar": return "fire";
            case "widowmaker": return "sorcery";
            case "thunderclap": return "lightning";
            default: break;
        }
        switch (in.type) {
            case "dagger": return "shadow";
            case "battleaxe": return "blood";
            case "glaive": return "fire";
            case "staff": return "wind";
            default: return "steel";
        }
    }

    // ------------------------------------------------------------------ material
    public static float tierDamage(String m) {
        switch (m) {
            case "leather": return 0.85F;
            case "wood": return 0.6F;
            case "stone": return 0.8F;
            case "gold": return 0.9F;
            case "diamond": return 1.35F;
            case "steel": return 1.2F;
            case "arcane": return 1.45F;
            case "soulforged": return 1.7F;
            case "novice": return 0.8F;
            case "advanced": return 1.3F;
            case "master": return 1.7F;
            case "legend": return 1.8F;
            default: return 1.0F;
        }
    }

    public static float tierCooldown(String m) {
        switch (m) {
            case "gold": return 0.65F;
            case "diamond": return 0.85F;
            case "steel": return 0.95F;
            case "arcane": return 0.88F;
            case "soulforged": return 0.8F;
            case "advanced": return 0.9F;
            case "master": return 0.8F;
            case "legend": return 0.75F;
            default: return 1.0F;
        }
    }

    public static String tierColor(String m) {
        switch (m) {
            case "wood": case "novice": return "\u00a77";
            case "stone": return "\u00a78";
            case "leather": return "\u00a76";
            case "gold": return "\u00a7e";
            case "diamond": return "\u00a7b";
            case "steel": return "\u00a77";
            case "arcane": return "\u00a7d";
            case "soulforged": return "\u00a73";
            case "apprentice": return "\u00a7a";
            case "advanced": return "\u00a79";
            case "master": return "\u00a7d";
            case "legend": return "\u00a76";
            default: return "\u00a7f";
        }
    }

    // ------------------------------------------------------------------ Heavy (the signature skill, charged)
    /** [damage, radius, cooldownTicks, lift, push, fireSeconds]; damage 0 = buff-only. */
    public static double[] heavyStats(String t) {
        if (ArmoryMoves.has(t)) {
            return ArmoryMoves.heavyStats(t);
        }
        switch (t) {
            case "dagger": return new double[]{10, 4, 200, 0, 0, 0};
            case "parrying_dagger": return new double[]{0, 0, 240, 0, 0, 0};
            case "longsword": return new double[]{5, 4, 160, 0, 0.4, 0};
            case "vanilla_sword": return new double[]{4, 4, 160, 0, 0.4, 0};
            case "katana": return new double[]{8, 7, 160, 0, 0, 0};
            case "saber": return new double[]{0, 0, 300, 0, 0, 0};
            case "rapier": return new double[]{13.5, 5, 120, 0, 0, 0};
            case "greatsword": return new double[]{9, 5, 200, 0.2, 0.8, 0};
            case "vanilla_axe": return new double[]{6, 4, 200, 0.2, 0.8, 0};
            case "hammer": return new double[]{7, 5, 240, 0.6, 0, 0};
            case "warhammer": return new double[]{5, 7, 320, 0.9, 0, 0};
            case "mace": return new double[]{8, 4, 200, 0, 0, 0};
            case "battleaxe": return new double[]{0, 0, 400, 0, 0, 0};
            case "spear": return new double[]{7, 7, 160, 0, 0, 0};
            case "pike": return new double[]{3, 5, 200, 0.3, 1.6, 0};
            case "lance": return new double[]{8, 6, 240, 0, 0.6, 0};
            case "halberd": return new double[]{7, 5, 200, 0, 0.9, 0};
            case "glaive": return new double[]{7, 6, 240, 0, 0.3, 5};
            case "staff": return new double[]{0, 0, 200, 0, 0, 0};
            case "longbow": return new double[]{4.5, 0, 300, 0, 0, 0};
            case "crossbow": return new double[]{12, 10, 200, 0, 0.4, 0};
            case "bow": return new double[]{3.5, 0, 160, 0, 0, 0};
            case "switch": return new double[]{0, 0, 240, 0, 0, 0};
            case "pistol": return new double[]{6, 0, 200, 0, 0, 0};
            case "shotgun": return new double[]{3.5, 0, 220, 0, 0.6, 0};
            case "rifle": return new double[]{30, 0, 240, 0, 0.4, 0};
            case "cannon": return new double[]{10, 2.5, 260, 0, 0.5, 0};
            case "wand_magic": return new double[]{6, 4.5, 200, 0, 0.6, 0};
            case "wand_fire": return new double[]{5, 5, 240, 0, 0.2, 5};
            case "wand_ice": return new double[]{5, 5, 240, 0, 0, 0};
            case "wand_lightning": return new double[]{7, 6, 260, 0, 0.3, 0};
            case "wand_necromancy": return new double[]{5, 5, 240, 0, 0, 0};
            case "wand_earth": return new double[]{5, 5, 240, 0.6, 0, 0};
            case "wand_sorcery": return new double[]{6, 4, 200, 0, 0.4, 0};
            case "wand_healing": return new double[]{0, 8, 400, 0, 0, 0};
            default: return new double[]{0, 0, 160, 0, 0, 0};
        }
    }

    public static String heavyName(String t) {
        if (ArmoryMoves.has(t)) {
            return ArmoryMoves.heavyName(t);
        }
        switch (t) {
            case "dagger": return "Shadow Strike";
            case "parrying_dagger": return "Riposte";
            case "longsword": case "vanilla_sword": return "Whirlwind";
            case "katana": return "Iaido";
            case "saber": return "Flurry";
            case "rapier": return "Lunge";
            case "greatsword": case "vanilla_axe": return "Cleave";
            case "hammer": return "Ground Slam";
            case "warhammer": return "Earthquake";
            case "mace": return "Concuss";
            case "battleaxe": return "Berserk";
            case "spear": return "Impale";
            case "pike": return "Keep Away";
            case "lance": return "Charge";
            case "halberd": return "Sweep";
            case "glaive": return "Reaper's Arc";
            case "staff": return "Vault";
            case "longbow": return "Arrow Rain";
            case "crossbow": return "Piercing Bolt";
            case "bow": return "Hunter's Volley";
            case "switch": return "Evasive Roll";
            case "pistol": return "Fan the Hammer";
            case "shotgun": return "Dragon's Breath";
            case "rifle": return "Piercing Round";
            case "cannon": return "Grapeshot";
            case "wand_magic": return "Arcane Burst";
            case "wand_fire": return "Inferno";
            case "wand_ice": return "Frost Nova";
            case "wand_lightning": return "Chain Storm";
            case "wand_necromancy": return "Life Drain";
            case "wand_earth": return "Tremor";
            case "wand_sorcery": return "Arcane Blink";
            case "wand_healing": return "Sanctuary";
            default: return t;
        }
    }

    public static String heavyDesc(String t) {
        if (ArmoryMoves.has(t)) {
            return ArmoryMoves.heavyDesc(t);
        }
        switch (t) {
            case "dagger": return "Strike every enemy around you, then vanish (Invisibility 3s).";
            case "parrying_dagger": return "Brace yourself: Resistance III for 2s and Strength II for 3s.";
            case "longsword": case "vanilla_sword": return "Spin and slash all enemies around you, pushing them back.";
            case "katana": return "A lightning-fast draw: dash through and cut everything around you.";
            case "saber": return "Enter a flurry: Haste III and Strength I for 5s, with a burst of cuts.";
            case "rapier": return "A precise critical thrust with very high damage.";
            case "greatsword": case "vanilla_axe": return "A heavy cleave that lifts enemies and hurls them back.";
            case "hammer": return "Leap and slam the ground, launching nearby enemies.";
            case "warhammer": return "Three quake waves: launch enemies high and slow them.";
            case "mace": return "A stunning blow: Weakness II and Slowness II, enemies are staggered.";
            case "battleaxe": return "Sacrifice 2 hearts for Strength II and Speed I (8s).";
            case "spear": return "A long thrust that pierces everything in a line.";
            case "pike": return "Sweep enemies away with a huge knockback.";
            case "lance": return "Charge forward: Speed III and Resistance I, trampling enemies.";
            case "halberd": return "A wide sweeping blow that pushes enemies back.";
            case "glaive": return "A blazing arc that sets enemies on fire.";
            case "staff": return "Vault into the air with a gust that knocks enemies away.";
            case "longbow": return "A dense downpour of arrows on the area you aim at.";
            case "crossbow": return "A piercing bolt that hits every enemy in a long line.";
            case "bow": return "12 homing arrows curve out and converge on your target.";
            case "switch": return "Roll away: Speed III, Invisibility and Jump Boost II for 2s.";
            case "pistol": return "Fan the hammer: six fast shots.";
            case "shotgun": return "A cone of burning pellets.";
            case "rifle": return "A high-velocity round that pierces 8 enemies.";
            case "cannon": return "Five exploding shells in a fan.";
            case "wand_magic": return "A burst of raw magic that hits and pushes back every enemy around you.";
            case "wand_fire": return "Two waves of fire burn every enemy around you.";
            case "wand_ice": return "A freezing blast: enemies around you are chilled hard.";
            case "wand_lightning": return "Lightning arcs to every enemy within a wide range.";
            case "wand_necromancy": return "Drain enemies around you and heal for part of the damage.";
            case "wand_earth": return "The ground heaves: enemies around you are launched.";
            case "wand_sorcery": return "Blink forward and blast enemies where you land.";
            case "wand_healing": return "Heal yourself and every player within 8 blocks, plus Regeneration and Resistance.";
            default: return "";
        }
    }

    /** Evolved Heavy names at rank II / IV (Warrior and Mage ranks; Ranger has its own). */
    public static String[] evoNames(String t) {
        if (ArmoryMoves.has(t)) {
            return ArmoryMoves.evoNames(t);
        }
        switch (t) {
            case "dagger": return new String[]{"Phantom Strike", "Death Lotus"};
            case "parrying_dagger": return new String[]{"Perfect Riposte", "Mirror Blade"};
            case "longsword": case "vanilla_sword": return new String[]{"Tempest", "Cyclone of Blades"};
            case "katana": return new String[]{"Thousand Cuts", "Heaven Splitter"};
            case "saber": return new String[]{"Blade Storm", "Crimson Dance"};
            case "rapier": return new String[]{"Piercing Comet", "Flash Thrust"};
            case "greatsword": case "vanilla_axe": return new String[]{"Sundering Cleave", "Titan Cleave"};
            case "hammer": return new String[]{"Meteor Slam", "World Breaker"};
            case "warhammer": return new String[]{"Cataclysm Quake", "Tectonic Fury"};
            case "mace": return new String[]{"Shatter", "Skullcrusher"};
            case "battleaxe": return new String[]{"Bloodrage", "Avatar of War"};
            case "spear": return new String[]{"Skewer", "Dragon Lance"};
            case "pike": return new String[]{"Iron Wall", "Fortress"};
            case "lance": return new String[]{"Thunder Charge", "Juggernaut"};
            case "halberd": return new String[]{"Crescent Sweep", "Moon Reaver"};
            case "glaive": return new String[]{"Soul Reaper", "Grim Harvest"};
            case "staff": return new String[]{"Sky Vault", "Ascent"};
            case "wand_magic": return new String[]{"Arcane Nova", "Singularity"};
            case "wand_fire": return new String[]{"Hellfire", "Sunfall"};
            case "wand_ice": return new String[]{"Absolute Zero", "Eternal Winter"};
            case "wand_lightning": return new String[]{"Thunder God", "Storm Sovereign"};
            case "wand_necromancy": return new String[]{"Soul Harvest", "Reaper's Feast"};
            case "wand_earth": return new String[]{"Earthshatter", "Gaia's Wrath"};
            case "wand_sorcery": return new String[]{"Rift Walk", "Void Step"};
            case "wand_healing": return new String[]{"Divine Grace", "Miracle"};
            case "longbow": return new String[]{"Meteor Rain", "Cataclysm"};
            case "bow": return new String[]{"Fan of Blades", "Hydra's Fan"};
            case "crossbow": return new String[]{"Rail Shot", "Ion Lance"};
            case "pistol": return new String[]{"Hammer Storm", "Six-Gun Requiem"};
            case "shotgun": return new String[]{"Hellfire Spread", "Inferno Breath"};
            case "rifle": return new String[]{"Railshot", "Judgment Round"};
            case "cannon": return new String[]{"Cluster Barrage", "Broadside"};
            default: return new String[]{"Greater " + heavyName(t), "Ultimate " + heavyName(t)};
        }
    }

    /** Ticks to reach charge tier I (tier II at x1.5, tier III at x2). */
    public static int chargeTicks(String t) {
        if (ArmoryMoves.has(t)) {
            return ArmoryMoves.chargeTicks(t);
        }
        switch (t) {
            case "dagger": case "parrying_dagger": case "rapier": case "saber": return 12;
            case "switch": return 10;
            case "greatsword": case "vanilla_axe": case "hammer": case "warhammer": case "battleaxe": case "halberd": return 24;
            case "pistol": return 10;
            case "shotgun": return 14;
            case "rifle": return 18;
            case "cannon": return 20;
            case "longbow": return 16;
            case "crossbow": return 15;
            case "bow": return 13;
            default: return t.startsWith("wand_") ? 18 : 16;
        }
    }

    // ------------------------------------------------------------------ Quick and Art
    /** Quick: [damage, size, cooldownTicks]. */
    public static double[] quickStats(Info in) {
        if (ArmoryMoves.has(in.type)) {
            return ArmoryMoves.quickStats(in.type);
        }
        switch (in.family) {
            case "blade": return new double[]{5, 5, 80};
            case "heavy": return new double[]{5, 5, 100};
            case "polearm": return new double[]{6, 7, 80};
            case "bow": return new double[]{10, 0, 60};
            case "gun": return new double[]{0, 0, 100};
            default: break;
        }
        switch (in.wandElement()) {
            case "fire": return new double[]{5, 2.5, 70};
            case "ice": return new double[]{6, 0, 60};
            case "lightning": return new double[]{5, 3, 60};
            case "necromancy": return new double[]{5.5, 0, 70};
            case "earth": return new double[]{6, 9, 80};
            case "sorcery": return new double[]{6, 0, 70};
            case "healing": return new double[]{4, 0, 100};
            default: return new double[]{7, 0, 60};
        }
    }

    /** Art: [damage (per hit for multi-hit arts), size, cooldownTicks]. Every weapon type has its own Art. */
    public static double[] artStats(Info in) {
        if (ArmoryMoves.has(in.type)) {
            return ArmoryMoves.artStats(in.type);
        }
        switch (in.type) {
            case "dagger": return new double[]{18, 5, 1200};
            case "parrying_dagger": return new double[]{8, 3, 1200};
            case "longsword": case "vanilla_sword": return new double[]{22, 5, 1200};
            case "katana": return new double[]{4.5, 4, 1200};
            case "saber": return new double[]{9, 8, 1200};
            case "rapier": return new double[]{3.5, 5, 1200};
            case "greatsword": case "vanilla_axe": return new double[]{24, 15, 1200};
            case "hammer": return new double[]{28, 6, 1200};
            case "warhammer": return new double[]{20, 16, 1200};
            case "mace": return new double[]{8, 6, 1200};
            case "battleaxe": return new double[]{3, 5, 1200};
            case "spear": return new double[]{24, 30, 1200};
            case "pike": return new double[]{5, 7, 1200};
            case "lance": return new double[]{14, 12, 1200};
            case "halberd": return new double[]{15, 6, 1200};
            case "glaive": return new double[]{20, 6, 1200};
            case "staff": return new double[]{6, 6, 1200};
            case "longbow": return new double[]{5, 5, 1200};
            case "crossbow": return new double[]{45, 40, 1200};
            case "bow": return new double[]{4, 20, 1200};
            case "switch": return new double[]{3.5, 7, 1200};
            case "pistol": return new double[]{26, 6, 1200};
            case "shotgun": return new double[]{34, 7, 1200};
            case "rifle": return new double[]{60, 64, 1200};
            case "cannon": return new double[]{18, 4, 1200};
            default: break;
        }
        switch (in.wandElement()) {
            case "fire": return new double[]{18, 6, 1200};
            case "ice": return new double[]{22, 5, 1200};
            case "lightning": return new double[]{4, 24, 1200};
            case "necromancy": return new double[]{4, 7, 1200};
            case "earth": return new double[]{13, 7, 1200};
            case "sorcery": return new double[]{20, 6, 1200};
            case "healing": return new double[]{3, 8, 1200};
            default: return new double[]{4, 20, 1200};
        }
    }

    /** Multi-hit Arts get a smaller level bonus per hit. */
    public static boolean artPerHit(Info in) {
        return artStats(in)[0] < 10 || "dagger".equals(in.type);
    }

    public static String quickName(Info in) {
        if (ArmoryMoves.has(in.type)) {
            return ArmoryMoves.quickName(in.type);
        }
        switch (in.type) {
            case "dagger": return "Shadowstep";
            case "parrying_dagger": return "Counter Step";
            case "katana": return "Flash Draw";
            case "saber": return "Cutting Rush";
            case "rapier": return "Fleche";
            case "staff": return "Gust Strike";
            default: break;
        }
        switch (in.family) {
            case "blade": return "Dash Slash";
            case "heavy": return "Shockwave Stomp";
            case "polearm": return "Lunge Thrust";
            case "bow": return "Snap Shot";
            case "gun": return "Tactical Roll";
            default: break;
        }
        switch (in.wandElement()) {
            case "fire": return "Fireball";
            case "ice": return "Ice Shard";
            case "lightning": return "Spark Bolt";
            case "necromancy": return "Soul Bolt";
            case "earth": return "Stone Spikes";
            case "sorcery": return "Void Bolt";
            case "healing": return "Mending Light";
            default: return "Arcane Bolt";
        }
    }

    public static String quickDesc(Info in) {
        if (ArmoryMoves.has(in.type)) {
            return ArmoryMoves.quickDesc(in.type);
        }
        switch (in.family) {
            case "blade": return "Dash forward, cutting every enemy on the way.";
            case "heavy": return "Stomp: a shockwave ring knocks nearby enemies into the air.";
            case "polearm": return "A fast piercing thrust that hits everything in a line.";
            case "bow": return "A fast piercing shot; you hop back from the recoil.";
            case "gun": return "Roll away and reload instantly (uses ammo from your inventory).";
            default: break;
        }
        switch (in.wandElement()) {
            case "fire": return "A fireball that explodes and burns (Charred).";
            case "ice": return "A homing shard that chills (3 Chill = Frozen).";
            case "lightning": return "A fast bolt that chains to 2 more enemies (Shock).";
            case "necromancy": return "A bolt that drains life (Drain).";
            case "earth": return "A line of stone spikes bursts from the ground (Stagger).";
            case "sorcery": return "A void bolt that pulls enemies together.";
            case "healing": return "Heal yourself and the nearest ally, and smite a nearby enemy.";
            default: return "A seeking bolt of raw magic.";
        }
    }

    public static String artName(Info in) {
        if (ArmoryMoves.has(in.type)) {
            return ArmoryMoves.artName(in.type);
        }
        if (!in.legend.isEmpty() && !LegendArts.name(in.legend).isEmpty()) {
            return LegendArts.name(in.legend);
        }
        switch (in.type) {
            case "dagger": return "Shadow Execution";
            case "parrying_dagger": return "Perfect Counter";
            case "longsword": case "vanilla_sword": return "Sword of Judgment";
            case "katana": return "Thousand Edges";
            case "saber": return "Crimson Tempest";
            case "rapier": return "Hundred Thrusts";
            case "greatsword": case "vanilla_axe": return "Titan Cleave";
            case "hammer": return "Meteor Drop";
            case "warhammer": return "Earthsplitter";
            case "mace": return "Bell of Doom";
            case "battleaxe": return "Blood Cyclone";
            case "spear": return "Dragon Lance";
            case "pike": return "Phalanx Wall";
            case "lance": return "Cavalry Charge";
            case "halberd": return "Moon Reaver";
            case "glaive": return "Reaper's Harvest";
            case "staff": return "Tempest Ascent";
            case "longbow": return "Starfall Barrage";
            case "crossbow": return "Ballista";
            case "bow": return "Thousand Arrows";
            case "switch": return "Arrow Dance";
            case "pistol": return "Dead Eye";
            case "shotgun": return "Breaching Blast";
            case "rifle": return "Sniper's Mark";
            case "cannon": return "Siege Barrage";
            default: break;
        }
        switch (in.wandElement()) {
            case "fire": return "Meteor Strike";
            case "ice": return "Glacial Prison";
            case "lightning": return "Storm Beam";
            case "necromancy": return "Grave Tide";
            case "earth": return "Rising Spires";
            case "sorcery": return "Rift Collapse";
            case "healing": return "Divine Halo";
            default: return "Arcane Beam";
        }
    }

    public static String artDesc(Info in) {
        if (ArmoryMoves.has(in.type)) {
            return ArmoryMoves.artDesc(in.type);
        }
        if (!in.legend.isEmpty() && !LegendArts.desc(in.legend).isEmpty()) {
            return LegendArts.desc(in.legend);
        }
        switch (in.type) {
            case "dagger": return "Blink behind up to 5 enemies and strike each; x3 on enemies below 30% health.";
            case "parrying_dagger": return "A 3s counter stance: every hit you take is blocked and answered with a x2 counter.";
            case "longsword": case "vanilla_sword": return "A giant spectral sword falls at your aim and sends 4 shockwaves out.";
            case "katana": return "Vanish into a storm of cuts while enemies ahead freeze, then a final cross slash.";
            case "saber": return "Three crimson crescents fly out in a fan; you heal from their damage.";
            case "rapier": return "Twelve lightning thrusts, then a piercing comet.";
            case "greatsword": case "vanilla_axe": return "A titanic crescent rolls 15 blocks forward, splitting the ground.";
            case "hammer": return "Leap high and crash down: a huge explosion launches everything around.";
            case "warhammer": return "A fissure runs 16 blocks ahead and stone spires burst out of it.";
            case "mace": return "Three stunning tolls of a spectral bell, then a deafening blast.";
            case "battleaxe": return "A 3s cyclone of blood: pulls enemies in, hits harder each turn, heals you.";
            case "spear": return "Hurl a spectral dragon lance that pierces everything and explodes.";
            case "pike": return "Raise a wall of spears for 5s that holds the horde back.";
            case "lance": return "Charge for 1 second, trampling everything, and finish with a shock blast.";
            case "halberd": return "Two full moon slashes, the second one wider.";
            case "glaive": return "A blazing 270-degree reap; every kill shortens the cooldown.";
            case "staff": return "Vault up and land in a tornado that lifts enemies, then slam them down.";
            case "longbow": return "120 falling star arrows rain on your aim, then a starburst.";
            case "crossbow": return "Aim for 1 second, then a ballista bolt pierces 40 blocks of enemies.";
            case "bow": return "Leap and hover, firing 80 homing arrows at every enemy around, then a giant arrow strikes the strongest.";
            case "switch": return "Three acrobatic rolls, each firing 14 homing arrows.";
            case "pistol": return "Mark up to 6 enemies, then shoot each one in a single heartbeat.";
            case "shotgun": return "A point-blank blast that blows enemies away.";
            case "rifle": return "Hold your breath for 1.5s, then a shot that pierces everything in a 64-block line.";
            case "cannon": return "Call down five artillery shells on your aim.";
            default: break;
        }
        switch (in.wandElement()) {
            case "fire": return "Call a meteor down at your aim; the ground keeps burning.";
            case "ice": return "Freeze every enemy in an area, then shatter the prison.";
            case "lightning": return "A long lightning beam that chains between enemies.";
            case "necromancy": return "A tide of death around you drains enemies and heals you.";
            case "earth": return "Three rings of stone spires erupt around you.";
            case "sorcery": return "Open a rift at your aim: it drags enemies in and collapses.";
            case "healing": return "A halo heals allies and burns enemies for 6s.";
            default: return "A long beam of arcane force that burns through enemies.";
        }
    }

    public static String icon(Info in, int slot) {
        if (ArmoryMoves.has(in.type)) {
            return ArmoryMoves.icon(in.type, slot);
        }
        if (slot == 1) {
            return "h_" + in.type;
        }
        if (slot == 2 && !in.legend.isEmpty() && !LegendArts.name(in.legend).isEmpty()) {
            return "la_" + in.legend;
        }
        if (slot == 2) {
            return "a_" + (in.isWand() ? in.wandElement() : in.type);
        }
        return "q_" + (in.isWand() ? in.wandElement() : in.family);
    }

    public static String[] presetNames() {
        return new String[]{"Balanced", "Power", "Size", "Speed"};
    }

    public static String buildName(String b) {
        switch (b) {
            case "warrior": return "Warrior";
            case "marksman": return "Marksman";
            case "mage": return "Mage";
            case "warden": return "Warden";
            case "spellblade": return "Spellblade";
            case "arcane_archer": return "Arcane Archer";
            case "vanguard": return "Vanguard";
            case "paladin": return "Paladin";
            case "juggernaut": return "Juggernaut";
            case "sentinel": return "Sentinel";
            case "adept": return "Adept";
            default: return "";
        }
    }
}
