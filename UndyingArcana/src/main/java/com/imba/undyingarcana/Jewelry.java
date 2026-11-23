package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Uncut gems, cut jewels and enchant scrolls. */
public final class Jewelry {
    public static final Item[] GEMS = new Item[Gear.GEM.length];
    public static final Item[] JEWELS = new Item[Gear.GEM.length];
    public static Item SCROLL, CLEANSING, REFORGE;

    private Jewelry() {
    }

    static void register(List<Item> all) {
        for (int i = 0; i < Gear.GEM.length; i++) {
            GEMS[i] = named(new ItemGem(i), "gem_" + Gear.GEM[i], 64, all);
            JEWELS[i] = named(new ItemJewel(i), "jewel_" + Gear.GEM[i], 1, all);
        }
        SCROLL = named(new ItemEnchantScroll(), "enchant_scroll", 1, all);
        CLEANSING = named(new ItemUtilityScroll(false), "scroll_cleansing", 16, all);
        REFORGE = named(new ItemUtilityScroll(true), "scroll_reforge", 16, all);
        Magic.GRIMOIRE = named(new Magic.ItemGrimoire(), "grimoire", 1, all);
        Magic.PAGE = named(new Magic.ItemSpellPage(), "spell_blueprint", 1, all);
        SpellArts.SCROLL = named(new SpellArts.ItemArcaneScroll(), "arcane_scroll", 16, all);
        Power.MANA_POTION = named(new Power.ItemManaPotion(), "mana_potion", 16, all);
    }

    private static Item named(Item item, String name, int stack, List<Item> all) {
        item.setRegistryName(UndyingArcana.MODID, name);
        item.setUnlocalizedName(UndyingArcana.MODID + "." + name);
        item.setCreativeTab(UndyingArcana.TAB);
        item.setMaxStackSize(stack);
        all.add(item);
        return item;
    }

    public static int gemOf(ItemStack s) {
        for (int i = 0; i < GEMS.length; i++) {
            if (s.getItem() == GEMS[i]) {
                return i;
            }
        }
        return -1;
    }

    /** A cut jewel stack from its data compound {g, gr, sec, dr}. */
    public static ItemStack jewel(NBTTagCompound j) {
        ItemStack s = new ItemStack(JEWELS[j.getInteger("g")]);
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("jw", j.copy());
        s.setTagCompound(t);
        return s;
    }

    public static NBTTagCompound jewelData(ItemStack s) {
        if (!(s.getItem() instanceof ItemJewel)) {
            return null;
        }
        NBTTagCompound d = s.hasTagCompound() ? s.getTagCompound().getCompoundTag("jw").copy() : new NBTTagCompound();
        d.setInteger("g", ((ItemJewel) s.getItem()).gem);
        return d;
    }

    public static String jewelLine(NBTTagCompound j) {
        int g = j.getInteger("g"), gr = j.getInteger("gr");
        float[] st = new float[Gear.STATS];
        Gear.jewelStats(j, st);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < Gear.STATS; k++) {
            if (st[k] != 0) {
                sb.append(sb.length() > 0 ? ", " : "").append(st[k] > 0 ? "+" : "").append((int) st[k]).append(' ').append(Gear.STAT[k]);
            }
        }
        if (Gear.GEM_EFFECT[g] > 0) {
            String fx = g >= Gear.FIRST_LEGEND_GEM ? Gear.EFFECT[Gear.GEM_EFFECT[g]]
                    : Gear.EFFECT[Gear.GEM_EFFECT[g]] + " " + Gear.EFFECT_CHANCE[Math.max(0, Math.min(2, gr))] + "%";
            sb.insert(0, sb.length() > 0 ? fx + ", " : fx);
        }
        return Gear.GRADE[gr] + " " + Gear.GEM_NAME[g] + ": " + sb;
    }

    // ------------------------------------------------------------------ jewel
    public static class ItemJewel extends Item {
        final int gem;

        ItemJewel(int gem) {
            this.gem = gem;
        }

        @Override
        public String getItemStackDisplayName(ItemStack s) {
            NBTTagCompound d = jewelData(s);
            return "\u00a7d" + Gear.GRADE[d.getInteger("gr")] + " " + Gear.GEM_NAME[gem] + " Jewel";
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
            NBTTagCompound d = jewelData(s);
            float[] st = new float[Gear.STATS];
            Gear.jewelStats(d, st);
            for (int k = 0; k < Gear.STATS; k++) {
                if (st[k] != 0) {
                    tip.add((st[k] > 0 ? "\u00a7a+" : "\u00a7c") + (int) st[k] + " " + Gear.STAT[k]);
                }
            }
            if (Gear.GEM_EFFECT[gem] > 0) {
                tip.add("\u00a76" + effectText(gem, d.getInteger("gr")));
            }
            tip.add("\u00a78Put it in a socket at the Jeweler's Bench.");
        }
    }

    // ------------------------------------------------------------------ enchant scroll
    public static class ItemEnchantScroll extends Item {
        ItemEnchantScroll() {
            addPropertyOverride(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "cursed"),
                    (s, w, e) -> Gear.cursed(kind(s)) ? 1.0F : 0.0F);
        }

        public static ItemStack of(int kind, int tier) {
            ItemStack s = new ItemStack(SCROLL);
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("k", kind);
            t.setInteger("t", tier);
            s.setTagCompound(t);
            return s;
        }

        static int kind(ItemStack s) {
            return s.hasTagCompound() ? Math.max(0, Math.min(Gear.ENCHANT.length - 1, s.getTagCompound().getInteger("k"))) : 0;
        }

        private static int tier(ItemStack s) {
            return s.hasTagCompound() ? Math.max(1, Math.min(3, s.getTagCompound().getInteger("t"))) : 1;
        }

        @Override
        public String getItemStackDisplayName(ItemStack s) {
            int k = kind(s);
            return Gear.cursed(k) ? "\u00a74Cursed Scroll of " + Gear.ENCHANT[k]
                    : "\u00a7bScroll of " + Gear.ENCHANT[k] + " " + new String[]{"I", "II", "III"}[tier(s) - 1];
        }

        @Override
        public boolean hasEffect(ItemStack s) {
            return true;
        }

        @Override
        public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
            if (isInCreativeTab(tab)) {
                for (int k = 0; k < Gear.ENCHANT.length; k++) {
                    for (int t = 1; t <= (Gear.cursed(k) ? 1 : 3); t++) {
                        items.add(of(k, t));
                    }
                }
            }
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
            int k = kind(s);
            tip.add((Gear.cursed(k) ? "\u00a7c" : "\u00a77") + Gear.enchantDesc(k, tier(s)));
            tip.add("\u00a78For " + (Gear.ENCHANT_ARMOR[k] ? "armor" : "weapons") + ". Hold the item in your off hand,");
            tip.add("\u00a78the scroll in your main hand, and right-click. Replaces the old enchant.");
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack scroll = p.getHeldItem(hand);
            if (hand != EnumHand.MAIN_HAND) {
                return new ActionResult<>(EnumActionResult.PASS, scroll);
            }
            ItemStack gear = p.getHeldItemOffhand();
            int k = kind(scroll);
            boolean ok = Gear.ENCHANT_ARMOR[k] ? Gear.isArmor(gear) : Gear.isWeapon(gear);
            if (!ok) {
                if (!w.isRemote) {
                    p.sendMessage(new TextComponentString("\u00a7cHold " + (Gear.ENCHANT_ARMOR[k] ? "an armor piece" : "a weapon") + " in your off hand."));
                }
                return new ActionResult<>(EnumActionResult.FAIL, scroll);
            }
            if (!w.isRemote) {
                NBTTagCompound e = new NBTTagCompound();
                e.setInteger("k", k);
                e.setInteger("t", tier(scroll));
                Gear.tag(gear).setTag("e", e);
                if (!p.capabilities.isCreativeMode) {
                    scroll.shrink(1);
                }
                Fx.at(w, Fx.TIERUP, Fx.ARCANE, p.posX, p.posY + 1, p.posZ, 1.0);
                Fx.sound(w, p.posX, p.posY, p.posZ, "minecraft:block.enchantment_table.use", 1.0F, 1.0F);
                p.sendMessage(new TextComponentString("\u00a7b" + gear.getDisplayName() + " now has " + Gear.enchantName(k, tier(scroll)) + "\u00a7b."));
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, scroll);
        }
    }

    static String effectText(int gem, int gr) {
        int g = Math.max(0, Math.min(2, gr)), c = Gear.EFFECT_CHANCE[g];
        switch (Gear.GEM_EFFECT[gem]) {
            case 1: return "Weapon: " + c + "% to burn on hit. Armor: burns attackers.";
            case 2: return "Weapon: " + c + "% to slow on hit. Armor: slows attackers.";
            case 3: return "Weapon: " + c + "% chain lightning. Armor: shocks attackers.";
            case 4: return "Weapon: " + c + "% to poison on hit. Armor: poisons attackers.";
            case 5: return "Weapon: " + 2 * c + "% on kill: heal 3 and a Soul Coin. Armor: " + c + "% absorption when hit.";
            case 6: return "Weapon: " + c + "% to pull nearby enemies to the target. Armor: " + c + "% to blast attackers away.";
            default: return "";
        }
    }

    // ------------------------------------------------------------------ uncut gem
    public static class ItemGem extends Item {
        final int gem;

        ItemGem(int gem) {
            this.gem = gem;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
            if (Gear.GEM_STAT[gem] >= 0) {
                tip.add("\u00a77Main stat: \u00a7a" + Gear.STAT[Gear.GEM_STAT[gem]] + " \u00a78(" + Gear.STAT_HELP[Gear.GEM_STAT[gem]] + " each)");
                tip.add("\u00a78Rough +" + Gear.GRADE_MAIN[0] + ", Fine +" + Gear.GRADE_MAIN[1] + ", Perfect +" + Gear.GRADE_MAIN[2]);
            } else {
                tip.add("\u00a76" + effectText(gem, 0).replace(Gear.EFFECT_CHANCE[0] + "%", "10-20%"));
            }
            if (gem >= Gear.FIRST_LEGEND_GEM) {
                tip.add("\u00a76Legendary: one per item, cutting needs a Relic Shard.");
            }
            tip.add("\u00a78Cut it at the Jeweler's Bench.");
        }

        @Override
        public boolean hasEffect(ItemStack s) {
            return gem >= Gear.FIRST_LEGEND_GEM;
        }
    }

    // ------------------------------------------------------------------ cleansing / reforge
    /** Cleansing removes the enchant; Reforge rerolls quality (jewels that no longer fit come back). Item in the off hand. */
    public static class ItemUtilityScroll extends Item {
        final boolean reforge;

        ItemUtilityScroll(boolean reforge) {
            this.reforge = reforge;
        }

        @Override
        public boolean hasEffect(ItemStack s) {
            return true;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag flag) {
            tip.add(reforge ? "\u00a77Rerolls the quality of a weapon or armor piece." : "\u00a77Removes the enchant (cursed ones too).");
            tip.add(reforge ? "\u00a78Jewels stay; extra ones come back if sockets drop." : "\u00a78The item keeps its quality and jewels.");
            tip.add("\u00a78Item in the off hand, scroll in the main hand, right-click.");
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack scroll = p.getHeldItem(hand);
            ItemStack gear = p.getHeldItemOffhand();
            if (hand != EnumHand.MAIN_HAND) {
                return new ActionResult<>(EnumActionResult.PASS, scroll);
            }
            if (!Gear.isGear(gear) || !Gear.rolled(gear) || (!reforge && Gear.enchant(gear) == null)) {
                if (!w.isRemote) {
                    p.sendMessage(new TextComponentString(reforge ? "\u00a7cHold a weapon or armor piece in your off hand."
                            : "\u00a7cHold an enchanted weapon or armor piece in your off hand."));
                }
                return new ActionResult<>(EnumActionResult.FAIL, scroll);
            }
            if (!w.isRemote) {
                NBTTagCompound tag = Gear.tag(gear);
                if (reforge) {
                    tag.removeTag("q");
                    Gear.roll(gear, p.getRNG(), Stats.stats(p)[Builds.LUCK]);
                    net.minecraft.nbt.NBTTagList list = tag.getTagList("j", 10);
                    while (list.tagCount() > Gear.sockets(gear)) {
                        ItemStack back = jewel(list.getCompoundTagAt(list.tagCount() - 1));
                        list.removeTag(list.tagCount() - 1);
                        if (!p.inventory.addItemStackToInventory(back)) {
                            p.dropItem(back, false);
                        }
                    }
                    tag.setTag("j", list);
                    int q = Gear.quality(gear);
                    p.sendMessage(new TextComponentString("\u00a7eReforged: " + Gear.QUALITY_COLOR[q] + Gear.QUALITY[q]));
                } else {
                    tag.removeTag("e");
                    p.sendMessage(new TextComponentString("\u00a7eThe enchant fades."));
                }
                if (!p.capabilities.isCreativeMode) {
                    scroll.shrink(1);
                }
                Fx.at(w, Fx.FLASH, Fx.HOLY, p.posX, p.posY + 1, p.posZ, 0.8);
                Fx.sound(w, p.posX, p.posY, p.posZ, "minecraft:block.anvil.use", 0.6F, 1.4F);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, scroll);
        }
    }
}
