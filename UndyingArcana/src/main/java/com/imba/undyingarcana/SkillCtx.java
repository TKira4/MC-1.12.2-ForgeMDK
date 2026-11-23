package com.imba.undyingarcana;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Everything one skill cast needs: caster, weapon, scaling, element, ranks and build. */
public final class SkillCtx {
    public final EntityPlayerMP p;
    public final World w;
    public final SkillCatalog.Info in;
    public final int slot; // 0 Quick, 1 Heavy, 2 Art

    public String name = "";
    public String element = "steel"; // VFX element name
    public int el = Fx.STEEL;         // VFX element id
    public String status = "";        // status element applied on hit ("" = none)
    public boolean imbued;
    public boolean freeCast;          // Overcharge: no cooldown for this cast
    public boolean magic;             // deals spell damage (wands)
    public boolean projectile;        // counts as an arrow / bullet (bows, guns): Archery, Ranger and Focus bonuses apply
    public net.minecraft.entity.Entity direct; // projectile entity of the current hit (headshots)

    public int level = 1;             // Attack / Archery / Magic level of the weapon's skill
    public float lvlK = 0.2F;         // damage per level
    public float tierDmg = 1.0F;      // material
    public float dmgMult = 1.0F;      // ranks, build, preset, legend, charge
    public float size = 1.0F;         // radius / length multiplier
    public float speed = 1.0F;        // projectile speed multiplier
    public int tier = 1;              // Heavy charge tier 1..3
    public int rr;                    // Ranger rank (bows)
    public int er;                    // Warrior rank (melee) / Mage rank (wands)
    public String path = "";
    public String build = "";
    public String legend = "";
    public double lx, lz;             // horizontal facing
    public Vec3d look;
    public int hits;
    public int punch;                 // bow Punch level (extra knockback on skill arrows)
    public boolean flame;
    public int fusion = -1;           // Grimoire spell: fused element (SpellArts.FUSION), or -1
    public long lastDing;             // world tick of the last arrow hit ding (one per tick)             // bow Flame: skill arrows set enemies on fire
    public long now;

    public SkillCtx(EntityPlayerMP p, SkillCatalog.Info in, int slot) {
        this.p = p;
        this.w = p.world;
        this.in = in;
        this.slot = slot;
        this.look = p.getLookVec();
        double l = Math.sqrt(look.x * look.x + look.z * look.z);
        this.lx = l < 1e-4 ? 0 : look.x / l;
        this.lz = l < 1e-4 ? 1 : look.z / l;
        this.legend = in.legend;
        this.now = p.world.getTotalWorldTime();
    }

    /** Scaled damage of a move: (base + level bonus) x material x multipliers. Multi-hit moves get a smaller level bonus. */
    public float dmg(double base, boolean perHit) {
        double lv = lvlK * level * (perHit ? 0.3 : 1.0);
        return (float) ((base + lv) * tierDmg * dmgMult);
    }

    public double chestY() {
        return p.posY + 1.1;
    }

    public double eyeY() {
        return p.posY + p.getEyeHeight();
    }

    /** Point d blocks ahead (horizontal) at feet height. */
    public double fx(double d) {
        return p.posX + lx * d;
    }

    public double fz(double d) {
        return p.posZ + lz * d;
    }
}
