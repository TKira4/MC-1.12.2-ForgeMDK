package com.imba.undyingarcana;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Catalysts: wands, staffs, orbs, scepters and rune foci amplify Grimoire spells (they are no longer a separate magic).
 *
 * Holding one (main hand or off hand) lets spells hit at full strength: x tier (Iron 1.0 .. Soulforged 1.5, legend 1.6)
 * x quality (+4% per step), +15% when the spell's element matches the catalyst's attuned element (sneak + right-click to
 * attune). Without a catalyst spells deal 70%. Each type adds its own style:
 * Wand - rings form 30% faster, instant spells cost 20% less mana; Staff - +1 ring, +30% per ring instead of +25%;
 * Orb - +30 max mana, mana regenerates 40% faster; Scepter - spells 25% larger; Rune Focus - +1 rune slot, +4 blocks.
 * Left-click with a catalyst fires a small free bolt of the attuned element. Z / X / C keep the catalyst's own arts (mana).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Catalyst {
    public static final int NONE = 0, WAND = 1, STAFF = 2, ORB = 3, SCEPTER = 4, RUNE = 5;
    public static final String[] KIND_NAME = {"", "Wand", "Staff", "Orb", "Scepter", "Rune Focus"};
    public static final String[] KIND_BONUS = {"", "Rings form 30% faster; instant spells cost 20% less mana",
            "+1 magic ring; +30% damage per ring", "+30 max mana; mana regenerates 40% faster", "Spells are 25% larger",
            "+1 rune slot; +4 spell blocks"};
    private static final float[] TIER_MULT = {1.0F, 1.15F, 1.3F, 1.5F};

    private Catalyst() {
    }

    /** Catalyst kind of an item (0 = not a catalyst). */
    public static int kind(ItemStack s) {
        SkillCatalog.Info in = SkillCatalog.identify(s);
        if (in == null) {
            return NONE;
        }
        switch (in.type) {
            case "m_wand": return WAND;
            case "m_staff": return STAFF;
            case "m_orb": return ORB;
            case "m_scepter": return SCEPTER;
            case "m_rune": return RUNE;
            default: return in.type.startsWith("wand_") ? WAND : NONE; // legend wands
        }
    }

    /** The catalyst the player holds: main hand first, then off hand (EMPTY when none). */
    public static ItemStack held(EntityPlayer p) {
        ItemStack m = p.getHeldItemMainhand();
        if (kind(m) != NONE) {
            return m;
        }
        ItemStack o = p.getHeldItemOffhand();
        return kind(o) != NONE ? o : ItemStack.EMPTY;
    }

    public static int heldKind(EntityPlayer p) {
        return kind(held(p));
    }

    /** Attuned element of a catalyst (-1 none). Legend wands are bound to their own element. */
    public static int element(ItemStack s) {
        SkillCatalog.Info in = SkillCatalog.identify(s);
        if (in != null && in.type.startsWith("wand_")) {
            switch (in.type.substring(5)) {
                case "fire": return 0;
                case "ice": return 1;
                case "lightning": return 2;
                case "earth": return 4;
                case "necromancy": return 5;
                case "healing": return 6;
                default: return -1;
            }
        }
        return s.hasTagCompound() && s.getTagCompound().hasKey("ua_el") ? s.getTagCompound().getInteger("ua_el") : -1;
    }

    private static float tierMult(ItemStack s) {
        SkillCatalog.Info in = SkillCatalog.identify(s);
        if (in == null) {
            return 1F;
        }
        if (!in.legend.isEmpty()) {
            return 1.6F;
        }
        int[] arm = s.getItem().getRegistryName() == null ? null : Armory.parse(s.getItem().getRegistryName().toString());
        return arm == null ? 1F : TIER_MULT[Math.max(0, Math.min(3, arm[1]))];
    }

    /** Spell damage multiplier from the held catalyst for a spell of element el (70% without a catalyst). */
    public static float spellMult(EntityPlayer p, int el) {
        ItemStack c = held(p);
        if (c.isEmpty()) {
            return 0.7F;
        }
        float m = tierMult(c) * (1F + 0.04F * Gear.quality(c));
        int a = element(c);
        if (a >= 0 && a == el) {
            m *= 1.15F;
        }
        return m;
    }

    public static int extraRings(EntityPlayer p) {
        return heldKind(p) == STAFF ? 1 : 0;
    }

    public static float ringTickMult(EntityPlayer p) {
        return heldKind(p) == WAND ? 0.7F : 1F;
    }

    public static float perRing(EntityPlayer p) {
        return heldKind(p) == STAFF ? 0.30F : 0.25F;
    }

    public static float sizeMult(EntityPlayer p) {
        return heldKind(p) == SCEPTER ? 1.25F : 1F;
    }

    public static int extraMana(EntityPlayer p) {
        return heldKind(p) == ORB ? 30 : 0;
    }

    public static float regenMult(EntityPlayer p) {
        return heldKind(p) == ORB ? 1.4F : 1F;
    }

    /** Magic value used for the spell block limit (Rune Focus: +4 blocks = +32 Magic). */
    public static int blockMagic(EntityPlayer p) {
        return Stats.stats(p)[Builds.MAG] + (heldKind(p) == RUNE ? 32 : 0);
    }

    public static int extraRunes(EntityPlayer p) {
        return heldKind(p) == RUNE ? 1 : 0;
    }

    // ------------------------------------------------------------------ attune (sneak + right-click)
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem e) {
        EntityPlayer p = e.getEntityPlayer();
        ItemStack s = e.getItemStack();
        if (!p.isSneaking() || kind(s) == NONE || !Stats.on(p.world)) {
            return;
        }
        SkillCatalog.Info in = SkillCatalog.identify(s);
        if (in != null && in.type.startsWith("wand_")) {
            return; // legend wands are bound
        }
        e.setCanceled(true);
        if (p.world.isRemote) {
            return;
        }
        // cycle: none -> each learned element -> none
        int cur = element(s);
        int next = -1;
        boolean passed = cur < 0;
        for (int i = 0; i < Magic.MAX_ELEMENTS; i++) {
            int el = Magic.element(p, i);
            if (el < 0) {
                continue;
            }
            if (passed) {
                next = el;
                break;
            }
            if (el == cur) {
                passed = true;
            }
        }
        if (!s.hasTagCompound()) {
            s.setTagCompound(new NBTTagCompound());
        }
        s.getTagCompound().setInteger("ua_el", next);
        p.sendStatusMessage(new TextComponentString(next < 0 ? "\u00a77Catalyst attuned to nothing"
                : "\u00a7dCatalyst attuned to " + Magic.ELEMENT_NAME[next] + " \u00a77(+15% " + Magic.ELEMENT_NAME[next] + " spells)"), true);
        Fx.sound(p, "minecraft:block.enchantment_table.use", 0.6F, 1.5F);
    }

    // ------------------------------------------------------------------ left-click bolt
    /** A small free bolt of the attuned element (or the first learned one); 0.5 s between bolts. */
    public static void basicBolt(EntityPlayerMP p) {
        if (!Stats.on(p.world) || kind(p.getHeldItemMainhand()) == NONE) {
            return;
        }
        NBTTagCompound d = Stats.data(p);
        long now = p.world.getTotalWorldTime();
        if (now < d.getLong("cat_bolt")) {
            return;
        }
        d.setLong("cat_bolt", now + 10);
        int el = element(p.getHeldItemMainhand());
        if (el < 0 || Magic.slotOf(p, el) < 0) {
            el = Magic.element(p, 0);
        }
        if (el < 0) {
            p.sendStatusMessage(new TextComponentString("\u00a77Learn an element in the Grimoire to fire bolts."), true);
            return;
        }
        Magic.Spell sp = new Magic.Spell();
        sp.shape = 0; // Bolt
        SkillCtx c = Magic.prepareEl(p, 0, sp, el, -1);
        if (c == null) {
            return;
        }
        c.dmgMult *= 0.35F;
        c.size *= 0.6F;
        p.swingArm(EnumHand.MAIN_HAND);
        SpellForge.castShape(c, sp, "", null, null, null);
    }
}
