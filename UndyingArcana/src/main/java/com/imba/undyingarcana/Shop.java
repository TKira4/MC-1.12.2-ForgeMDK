package com.imba.undyingarcana;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;

/**
 * Soul Exchange offers, from config/undyingarcana/shop.json (written with defaults on first start).
 * An offer is an item spec ("modid:item*count", or "scroll:<enchant>:<tier>") and a price in Soul Coins.
 */
public final class Shop {
    public static class Offer {
        public String item = "";
        public int price = 1;
    }

    public static class Offers {
        public List<Offer> offers = new ArrayList<>();
    }

    static Offers offers = new Offers();
    static String json = "{\"offers\":[]}";

    private Shop() {
    }

    private static Offer o(String item, int price) {
        Offer f = new Offer();
        f.item = item;
        f.price = price;
        return f;
    }

    private static Offers defaults() {
        Offers d = new Offers();
        for (int g = 0; g < Gear.NORMAL_GEMS; g++) {
            d.offers.add(o(UndyingArcana.MODID + ":gem_" + Gear.GEM[g], Gear.GEM_STAT[g] >= 0 ? 25 : 40));
        }
        for (int k = 0; k < Gear.CURSED; k++) {
            d.offers.add(o("scroll:" + k + ":1", 60));
            d.offers.add(o("scroll:" + k + ":2", 150));
        }
        for (int k = Gear.CURSED; k < Gear.ENCHANT.length; k++) {
            d.offers.add(o("scroll:" + k + ":1", 200));
        }
        d.offers.add(o(UndyingArcana.MODID + ":scroll_cleansing", 40));
        d.offers.add(o(UndyingArcana.MODID + ":scroll_reforge", 120));
        d.offers.add(o(UndyingArcana.MODID + ":mana_potion", 15));
        d.offers.add(o(UndyingArcana.MODID + ":grimoire", 150));
        d.offers.add(o(UndyingArcana.MODID + ":relic_shard", 40));
        d.offers.add(o(UndyingArcana.MODID + ":scroll_rebirth", 300));
        d.offers.add(o(UndyingArcana.MODID + ":ammo_bullet*16", 10));
        d.offers.add(o(UndyingArcana.MODID + ":ammo_shell*8", 12));
        d.offers.add(o("minecraft:golden_apple", 30));
        d.offers.add(o(UndyingArcana.MODID + ":waterskin", 10));
        d.offers.add(o(UndyingArcana.MODID + ":iron_canteen", 40));
        d.offers.add(o(UndyingArcana.MODID + ":purified_water*4", 8));
        d.offers.add(o(UndyingArcana.MODID + ":hot_cocoa*2", 10));
        d.offers.add(o(UndyingArcana.MODID + ":chilled_water*2", 10));
        d.offers.add(o(UndyingArcana.MODID + ":salt*8", 6));
        for (int k = 0; k < SpellArts.ART.length; k++) {
            d.offers.add(o("arcane:" + k, 350));
        }
        for (int k = 1; k < Martial.ID.length; k++) {
            d.offers.add(o("manual:" + k, 300));
        }
        for (Relics.Def r : Relics.DEFS) {
            // relics: quality rolls when bought; actives cost more
            d.offers.add(o(UndyingArcana.MODID + ":" + r.id, r.kind == Relics.RELIC ? 450 : r.kind == Relics.RING ? 180 : 260));
        }
        d.offers.add(o("minecraft:experience_bottle*4", 20));
        List<String> specs = new ArrayList<>();
        List<Integer> prices = new ArrayList<>();
        Armory.shopOffers(specs, prices);
        for (int i = 0; i < specs.size(); i++) {
            d.offers.add(o(specs.get(i), prices.get(i)));
        }
        return d;
    }

    public static void load() {
        File f = new File(Loader.instance().getConfigDir(), "undyingarcana/shop.json");
        try {
            String cur = f.isFile() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8).toString() : "";
            if (f.isFile() && (!cur.contains(":arm_") || !cur.contains(":waterskin") || !cur.contains("arcane:") || !cur.contains(":ring_power"))) {
                // shop.json from an older version (no armory weapons / survival items yet): keep it as shop.json.old, write new defaults
                Files.move(f.toPath(), new File(f.getParentFile(), "shop.json.old").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!f.isFile()) {
                f.getParentFile().mkdirs();
                try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
                    QuestDefs.GSON.toJson(defaults(), w);
                }
            }
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                offers = QuestDefs.GSON.fromJson(r, Offers.class);
            }
        } catch (Exception e) {
            LogManager.getLogger("UndyingArcana-Shop").error("Bad shop.json, using defaults: " + e.getMessage());
            offers = defaults();
        }
        json = new com.google.gson.Gson().toJson(offers);
    }

    public static ItemStack stack(String spec) {
        if (spec.startsWith("arcane:")) {
            return SpellArts.scroll(Integer.parseInt(spec.substring(7).trim()));
        }
        if (spec.startsWith("relic:")) {
            // relic:<id>:<quality>
            String[] p = spec.split(":");
            for (Relics.Def d : Relics.DEFS) {
                if (d.id.equals(p[1])) {
                    ItemStack s = new ItemStack(Relics.ITEMS[java.util.Arrays.asList(Relics.DEFS).indexOf(d)]);
                    Gear.setQuality(s, p.length > 2 ? Integer.parseInt(p[2]) : 0);
                    return s;
                }
            }
            return ItemStack.EMPTY;
        }
        if (spec.startsWith("manual:")) {
            return Martial.manual(Integer.parseInt(spec.substring(7).trim()));
        }
        if (spec.startsWith("scroll:")) {
            String[] p = spec.split(":");
            return Jewelry.ItemEnchantScroll.of(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        }
        return Quests.parseStack(spec);
    }

    public static void buy(EntityPlayerMP p, int index, int times) {
        if (!Stats.on(p.world) || index < 0 || index >= offers.offers.size()) {
            return;
        }
        Offer o = offers.offers.get(index);
        times = Math.max(1, Math.min(16, times));
        int cost = o.price * times;
        if (!Coins.spend(p, cost)) {
            p.sendMessage(new TextComponentString("\u00a7cYou need " + cost + " Soul Coins."));
            return;
        }
        for (int i = 0; i < times; i++) {
            ItemStack s = stack(o.item);
            if (!p.inventory.addItemStackToInventory(s) && !s.isEmpty()) {
                p.dropItem(s, false);
            }
        }
        p.inventoryContainer.detectAndSendChanges();
        Fx.sound(p.world, p.posX, p.posY, p.posZ, "minecraft:entity.villager.yes", 0.8F, 1.0F);
        Econ.sendState(p);
    }
}
