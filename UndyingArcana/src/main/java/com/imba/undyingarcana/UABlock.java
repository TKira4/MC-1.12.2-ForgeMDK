package com.imba.undyingarcana;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** Simple work blocks that open a screen: 0 = Jeweler's Bench, 1 = Soul Exchange. */
public class UABlock extends Block {
    public static final int JEWELER = 0, EXCHANGE = 1, SHRINE = 3, FORGE = 4; // 2 = Grimoire screen (MsgOpen kind); 4 = Relic Forge (Relic Altar)
    private final int kind;

    public UABlock(int kind) {
        super(Material.ROCK);
        this.kind = kind;
        setHardness(3.0F);
        setResistance(20.0F);
        setLightLevel(kind == JEWELER ? 0.2F : 0.5F);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public boolean onBlockActivated(World w, BlockPos pos, IBlockState state, EntityPlayer p, EnumHand hand, EnumFacing side,
                                    float hx, float hy, float hz) {
        if (!w.isRemote && p instanceof EntityPlayerMP) {
            if (!Stats.on(w)) {
                p.sendMessage(new TextComponentString("\u00a77This works in the new world (game rule uaBuilds)."));
                return true;
            }
            SkillNet.NET.sendTo(new SkillNet.MsgOpen(kind, kind == EXCHANGE ? Shop.json : kind == SHRINE ? Econ.state(p) : ""),
                    (EntityPlayerMP) p);
            if (kind == EXCHANGE) {
                Econ.sendState((EntityPlayerMP) p); // black market and services
            }
        }
        return true;
    }
}
