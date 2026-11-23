package com.imba.undyingarcana;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

/**
 * Gun rules: magazine and reload (NBT "Mag", "Next", "Reload"), ammo from the inventory, Archery requirement,
 * fire rate, bullets (EntitySkillBolt "bullet" / "pellet" / "rifle" / "shell"), muzzle flash, recoil and sounds.
 * Bullets deal arrow damage, so Archery, Ranger, Focus and headshot bonuses apply.
 */
public final class GunLogic {
    private GunLogic() {
    }

    public static int magSize(String t) {
        switch (t) {
            case "shotgun": return 4;
            case "rifle": return 5;
            case "cannon": return 1;
            default: return 8;
        }
    }

    public static int interval(String t) {
        switch (t) {
            case "shotgun": return 16;
            case "rifle": return 22;
            case "cannon": return 30;
            default: return 6;
        }
    }

    public static int reloadTicks(String t) {
        switch (t) {
            case "shotgun": return 44;
            case "rifle": return 40;
            case "cannon": return 40;
            default: return 30;
        }
    }

    public static int needLevel(String t) {
        switch (t) {
            case "shotgun": return 10;
            case "rifle": return 16;
            case "cannon": return 24;
            default: return 4;
        }
    }

    public static double baseDamage(String t) {
        switch (t) {
            case "shotgun": return 3.0;
            case "rifle": return 15.0;
            case "cannon": return 12.0;
            default: return 6.0;
        }
    }

    public static Item ammoItem(String t) {
        switch (t) {
            case "shotgun": return ModItems.AMMO_SHELL;
            case "cannon": return ModItems.AMMO_BALL;
            default: return ModItems.AMMO_BULLET;
        }
    }

    public static String ammoName(String t) {
        switch (t) {
            case "shotgun": return "Shotgun Shells";
            case "cannon": return "Cannonballs";
            default: return "Bullets";
        }
    }

    private static NBTTagCompound tag(ItemStack st) {
        if (!st.hasTagCompound()) {
            st.setTagCompound(new NBTTagCompound());
        }
        return st.getTagCompound();
    }

    public static int mag(ItemStack st) {
        return st.hasTagCompound() ? st.getTagCompound().getInteger("Mag") : 0;
    }

    public static long reloadLeft(EntityPlayer p, ItemStack st) {
        if (!st.hasTagCompound()) {
            return 0;
        }
        return Math.max(0, st.getTagCompound().getLong("Reload") - p.world.getTotalWorldTime());
    }

    public static int reserve(EntityPlayer p, String type) {
        Item a = ammoItem(type);
        int n = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == a) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int take(EntityPlayer p, String type, int want) {
        if (p.capabilities.isCreativeMode) {
            return want;
        }
        Item a = ammoItem(type);
        int got = 0;
        for (int i = 0; i < p.inventory.getSizeInventory() && got < want; i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == a) {
                int k = Math.min(want - got, s.getCount());
                s.shrink(k);
                got += k;
            }
        }
        return got;
    }

    private static boolean ranger(EntityPlayer p) {
        return Stats.isRanger(p);
    }

    public static void startReload(EntityPlayerMP p, ItemStack st, String type) {
        NBTTagCompound t = tag(st);
        if (t.getLong("Reload") > p.world.getTotalWorldTime() || t.getInteger("Mag") >= magSize(type)) {
            return;
        }
        if (!p.capabilities.isCreativeMode && reserve(p, type) <= 0) {
            SkillServer.msg(p, "\u00a7cNo ammo \u00a77(" + ammoName(type) + ")");
            Fx.sound(p, "minecraft:block.dispenser.fail", 0.6F, 1.4F);
            return;
        }
        int ticks = reloadTicks(type);
        if (ranger(p)) {
            ticks = ticks * 3 / 4;
        }
        t.setLong("Reload", p.world.getTotalWorldTime() + ticks);
        t.setInteger("ReloadT", ticks);
        Fx.sound(p, "minecraft:block.iron_trapdoor.open", 0.6F, 1.6F);
        SkillServer.msg(p, "\u00a77Reloading...");
    }

    /** Fills the magazine right now (Tactical Roll). */
    public static void instantReload(EntityPlayerMP p, ItemStack st, String type) {
        NBTTagCompound t = tag(st);
        int need = magSize(type) - t.getInteger("Mag");
        if (need > 0) {
            t.setInteger("Mag", t.getInteger("Mag") + take(p, type, need));
        }
        t.setLong("Reload", 0);
        Fx.sound(p, "minecraft:block.iron_trapdoor.close", 0.7F, 1.8F);
    }

    public static void tick(EntityPlayerMP p, ItemStack st, boolean selected) {
        if (!st.hasTagCompound()) {
            return;
        }
        NBTTagCompound t = st.getTagCompound();
        long r = t.getLong("Reload");
        if (r <= 0) {
            return;
        }
        if (!selected) {
            t.setLong("Reload", 0);
            return;
        }
        if (p.world.getTotalWorldTime() >= r && st.getItem() instanceof ItemGun) {
            instantReload(p, st, ((ItemGun) st.getItem()).type);
        }
    }

    public static void trigger(EntityPlayerMP p, ItemStack st) {
        if (!(st.getItem() instanceof ItemGun)) {
            return;
        }
        ItemGun gun = (ItemGun) st.getItem();
        SkillCatalog.Info in = SkillCatalog.identify(st);
        if (in == null) {
            return;
        }
        if (!gun.legend.isEmpty() && !ranger(p)) {
            SkillServer.msg(p, "\u00a7cOnly a ranger can wield this legend.");
            return;
        }
        int lv = Math.max(1, p.getEntityData().getInteger("za_lv_arc"));
        if (lv < needLevel(gun.type) && gun.legend.isEmpty()) {
            SkillServer.msg(p, "\u00a7cNeeds Archery " + needLevel(gun.type) + " \u00a77(you have " + lv + ")");
            return;
        }
        NBTTagCompound t = tag(st);
        long now = p.world.getTotalWorldTime();
        if (t.getLong("Reload") > now) {
            return;
        }
        if (p.isSneaking()) {
            startReload(p, st, gun.type);
            return;
        }
        if (now < t.getLong("Next")) {
            return;
        }
        if (t.getInteger("Mag") <= 0) {
            startReload(p, st, gun.type);
            return;
        }
        t.setInteger("Mag", t.getInteger("Mag") - 1);
        int iv = interval(gun.type);
        if (ranger(p)) {
            iv = Math.max(3, iv * 85 / 100);
        }
        t.setLong("Next", now + iv);
        fire(p, in, gun.type);
        if (!p.capabilities.isCreativeMode) {
            st.damageItem(1, p);
        }
    }

    /** One trigger pull. Normal shots use the Quick-slot scaling with the per-hit level bonus. */
    static void fire(EntityPlayerMP p, SkillCatalog.Info in, String type) {
        SkillCtx c = SkillServer.prepare(p, in, 0);
        float dmg = c.dmg(baseDamage(type), true);
        Vec3d look = p.getLookVec();
        Vec3d muzzle = new Vec3d(p.posX + look.x * 0.9, p.posY + p.getEyeHeight() - 0.15 + look.y * 0.9, p.posZ + look.z * 0.9);
        switch (type) {
            case "shotgun":
                for (int i = 0; i < 8; i++) {
                    SkillMoves.bullet(c, "pellet", muzzle, spread(p, look, 0.11), 3.6, dmg, 0, 7);
                }
                Fx.sound(p, "minecraft:entity.generic.explode", 0.6F, 1.5F);
                Fx.sound(p, "minecraft:entity.firework.large_blast", 0.9F, 0.8F);
                recoil(p, 0.9F, -0.25);
                break;
            case "rifle":
                SkillMoves.bullet(c, "rifle", muzzle, look, 6.0, dmg, 2, 14);
                Fx.sound(p, "minecraft:entity.firework.large_blast", 1.0F, 1.5F);
                Fx.sound(p, "minecraft:entity.generic.explode", 0.4F, 1.9F);
                Fx.send(p.world, Fx.BEAM, c.el, muzzle.x, muzzle.y, muzzle.z, muzzle.x + look.x * 12, muzzle.y + look.y * 12, muzzle.z + look.z * 12, 0.12, 0, 3, -1);
                recoil(p, 1.1F, 0);
                break;
            case "cannon": {
                EntitySkillBolt b = SkillMoves.bullet(c, "shell", muzzle, look.addVector(0, 0.04, 0), 2.2, dmg, 0, 30);
                b.radius = 2.8;
                Fx.sound(p, "minecraft:entity.generic.explode", 1.0F, 0.9F);
                Fx.at(p.world, Fx.EXPLOSION, c.el, muzzle.x, muzzle.y - 0.5, muzzle.z, 0.6);
                recoil(p, 1.6F, -0.5);
                break;
            }
            default:
                SkillMoves.bullet(c, "bullet", muzzle, spread(p, look, 0.012), 4.5, dmg, 0, 10);
                Fx.sound(p, "minecraft:entity.firework.blast", 0.9F, 1.6F);
                Fx.sound(p, "minecraft:entity.generic.explode", 0.25F, 2.0F);
                recoil(p, 0.45F, 0);
                break;
        }
        Fx.at(p.world, Fx.FLASH, c.el == Fx.STEEL ? Fx.FIRE : c.el, muzzle.x, muzzle.y, muzzle.z, 0.5);
        ZombieDirector.noise(p.world, p.posX, p.posY, p.posZ, "cannon".equals(type) ? 56.0D : 40.0D, p, true);
    }

    static Vec3d spread(EntityPlayer p, Vec3d dir, double s) {
        return dir.addVector((p.getRNG().nextDouble() - 0.5) * 2 * s, (p.getRNG().nextDouble() - 0.5) * 2 * s, (p.getRNG().nextDouble() - 0.5) * 2 * s).normalize();
    }

    /** Camera kick for the shooter only, plus an optional push backwards. */
    static void recoil(EntityPlayerMP p, float power, double push) {
        SkillNet.NET.sendTo(new SkillNet.MsgShake(power, 4), p);
        if (push != 0) {
            Vec3d l = p.getLookVec();
            SkillShapes.launch(p, p.motionX + l.x * push, p.motionY, p.motionZ + l.z * push);
        }
    }
}
