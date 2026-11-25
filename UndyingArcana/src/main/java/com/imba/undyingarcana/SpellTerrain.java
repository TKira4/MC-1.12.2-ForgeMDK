package com.imba.undyingarcana;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

/**
 * Spell explosions break blocks: a crater of about 60% of the blast radius (max 4 blocks), like a weak TNT blast.
 * Every block is asked through a Forge BreakEvent for the caster, so claim / protection mods (and the coming base cores)
 * can refuse it. Skipped: unbreakable and very hard blocks (hardness 10+, e.g. obsidian), blocks with a tile entity
 * (chests, furnaces, spawners) and liquids. 30% of broken blocks drop as items.
 * Game rule uaSpellGrief (default true) turns it off.
 */
public final class SpellTerrain {
    public static final String RULE = "uaSpellGrief";
    private static final double MAX_R = 4.0;
    private static final int MAX_BLOCKS = 220, MAX_FX = 24;

    private SpellTerrain() {
    }

    public static boolean on(World w) {
        GameRules r = w.getGameRules();
        if (!r.hasRule(RULE)) {
            r.addGameRule(RULE, "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
        return r.getBoolean(RULE) && Stats.on(w);
    }

    /** Blast the terrain at a spell explosion of radius r (the damage radius). */
    public static void blast(SkillCtx c, Vec3d at, double r) {
        if (c == null || c.p == null || c.w.isRemote || r < 2 || !on(c.w)) {
            return;
        }
        double cr = Math.min(MAX_R, r * 0.6);
        int ri = MathHelper.ceil(cr);
        BlockPos center = new BlockPos(at);
        java.util.Random rnd = c.w.rand;
        int broken = 0, fx = 0;
        for (int dy = ri; dy >= -ri && broken < MAX_BLOCKS; dy--) {
            for (int dx = -ri; dx <= ri && broken < MAX_BLOCKS; dx++) {
                for (int dz = -ri; dz <= ri && broken < MAX_BLOCKS; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    // ragged edge: the outer shell breaks only sometimes
                    if (d > cr || d > cr - 1 && rnd.nextFloat() < 0.5F) {
                        continue;
                    }
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!c.w.isBlockLoaded(pos) || pos.getY() < 1) {
                        continue;
                    }
                    IBlockState st = c.w.getBlockState(pos);
                    Block b = st.getBlock();
                    if (b.isAir(st, c.w, pos) || st.getMaterial().isLiquid() || st.getMaterial() == Material.PORTAL
                            || b.hasTileEntity(st)) {
                        continue;
                    }
                    float hard = st.getBlockHardness(c.w, pos);
                    if (hard < 0 || hard >= 10F) {
                        continue;
                    }
                    BlockEvent.BreakEvent ev = new BlockEvent.BreakEvent(c.w, pos, st, c.p);
                    if (MinecraftForge.EVENT_BUS.post(ev)) {
                        continue;
                    }
                    if (fx++ < MAX_FX) {
                        c.w.playEvent(2001, pos, Block.getStateId(st));
                    }
                    b.dropBlockAsItemWithChance(c.w, pos, st, 0.3F, 0);
                    c.w.setBlockToAir(pos);
                    broken++;
                }
            }
        }
    }
}
