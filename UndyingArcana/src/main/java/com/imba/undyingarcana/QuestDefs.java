package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Quest definitions (shared by server and client). Loaded from config/undyingarcana/quests/*.json on the server and
 * sent to clients as JSON. Goal types: kill, craft, item, level, build, bloodmoon, biome.
 */
public final class QuestDefs {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String[] TABS = {"story", "trials", "mastery", "hunts", "exploration", "daily"};
    public static final String[] TAB_NAMES = {"Story", "Build trials", "Weapon mastery", "Hunts", "Exploration", "Daily"};

    public static class Goal {
        public String type = "kill";   // kill | craft | item | level | build | bloodmoon | biome
        public String target = "";     // kill: zombie, zombie:<type>, elite, boss, boss:<id>, <entity id>; craft/item: item id (* glob); biome: name part
        public String with = "";       // kill only: melee | bow | gun | wand | unarmed
        public int count = 1;
        public String text = "";       // shown text; generated when empty

        public String describe() {
            if (!text.isEmpty()) {
                return text;
            }
            switch (type) {
                case "kill": {
                    String n = pretty(target);
                    if (count > 1 && !n.endsWith("s")) {
                        n = n.equals("boss") ? "bosses" : n + "s";
                    }
                    return "Kill " + count + " " + n + (with.isEmpty() ? "" : " (" + with + ")");
                }
                case "craft": return "Craft " + count + " " + pretty(target);
                case "item": return "Have " + count + " " + pretty(target);
                case "level": return "Reach level " + count;
                case "build": return target.isEmpty() || "any".equals(target) ? "Have a build (level 16)" : "Become a " + target;
                case "bloodmoon": return "Survive " + count + " Blood Moon" + (count > 1 ? "s" : "");
                case "biome": return "Visit a " + pretty(target) + " biome";
                default: return type + " " + target;
            }
        }
    }

    public static class Quest {
        public String id = "";
        public String tab = "story";
        public String title = "";
        public String story = "";
        public String icon = "minecraft:book";
        public List<String> requires = new ArrayList<>();
        public List<Goal> goals = new ArrayList<>();
        public int xp;
        public List<String> items = new ArrayList<>(); // "modid:item*count" or "modid:item@meta*count"
        public String repeat = "";                     // "" or "daily"
    }

    public static class Pack {
        public List<Quest> quests = new ArrayList<>();
    }

    /** All quests by id, in file order. Replaced as a whole on load / sync. */
    public static volatile Map<String, Quest> ALL = new LinkedHashMap<>();

    private QuestDefs() {
    }

    public static String pretty(String id) {
        String s = id;
        int c = s.indexOf(':');
        if (c >= 0 && !s.startsWith("zombie:") && !s.startsWith("boss:")) {
            s = s.substring(c + 1);
        }
        s = s.replace("zombie:", "").replace("boss:", "").replace('_', ' ').replace("*", "");
        if ("zombie".equals(s)) {
            return "zombies";
        }
        return s.trim();
    }

    public static boolean glob(String pattern, String value) {
        if (pattern.endsWith("*")) {
            return value.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(value);
    }

    public static int tabIndex(String tab) {
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i].equals(tab)) {
                return i;
            }
        }
        return 0;
    }
}
