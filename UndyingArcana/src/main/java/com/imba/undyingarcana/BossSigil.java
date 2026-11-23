package com.imba.undyingarcana;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/** Right-click in the boss's region to summon it 6 blocks ahead. Used up on success. */
public class BossSigil extends GlintItem {
    public final String boss;

    public BossSigil(String boss) {
        this.boss = boss;
        setMaxStackSize(1);
    }

    /** "" when the player stands in the right region, otherwise the reason. */
    private String regionProblem(World world, EntityPlayer p) {
        BlockPos pos = p.getPosition();
        int dim = world.provider.getDimension();
        if ("infernal_brute".equals(boss)) {
            return dim == -1 ? "" : "The Infernal Sigil only answers in the Nether.";
        }
        if ("frost_lich".equals(boss)) {
            return world.getBiome(pos).getTemperature(pos) < 0.2F ? "" : "The Frost Sigil only answers in a snowy biome.";
        }
        if ("void_stalker".equals(boss)) {
            return dim == 1 || pos.getY() < 20 ? "" : "The Void Sigil only answers in the End or below Y 20.";
        }
        net.minecraft.world.biome.Biome biome = world.getBiome(pos);
        if ("plague_mother".equals(boss)) {
            return biome instanceof net.minecraft.world.biome.BiomeSwamp ? "" : "The Plague Sigil only answers in a swamp.";
        }
        if ("bone_colossus".equals(boss)) {
            return biome instanceof net.minecraft.world.biome.BiomeDesert || biome instanceof net.minecraft.world.biome.BiomeMesa
                    ? "" : "The Bone Sigil only answers in a desert or a mesa.";
        }
        if ("thunder_warden".equals(boss)) {
            return biome instanceof net.minecraft.world.biome.BiomeHills || pos.getY() >= 100
                    ? "" : "The Thunder Sigil only answers in the mountains (or above Y 100).";
        }
        if ("undying_king".equals(boss)) {
            return dim == 0 && !world.isDaytime() ? "" : "The Crown of the Undying only answers at night in the Overworld.";
        }
        return "";
    }

    private EntityLSBoss create(World world) {
        switch (boss) {
            case "infernal_brute":
                return new EntityInfernalBrute(world);
            case "frost_lich":
                return new EntityFrostLich(world);
            case "void_stalker":
                return new EntityVoidStalker(world);
            case "plague_mother":
                return new EntityPlagueMother(world);
            case "bone_colossus":
                return new EntityBoneColossus(world);
            case "thunder_warden":
                return new EntityThunderWarden(world);
            default:
                return new EntityUndyingKing(world);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        String problem = regionProblem(world, player);
        if (!problem.isEmpty()) {
            player.sendStatusMessage(new TextComponentString(TextFormatting.GRAY + problem), true);
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        EntityLSBoss e = create(world);
        double lx = -Math.sin(Math.toRadians(player.rotationYaw));
        double lz = Math.cos(Math.toRadians(player.rotationYaw));
        double x = player.posX + lx * 6.0D, z = player.posZ + lz * 6.0D;
        BlockPos at = new BlockPos(x, player.posY, z);
        int tries = 0;
        while (!world.isAirBlock(at) && tries++ < 6) {
            at = at.up();
        }
        e.setLocationAndAngles(x, at.getY(), z, player.rotationYaw + 180F, 0F);
        e.onInitialSpawn(world.getDifficultyForLocation(at), null);
        world.spawnEntity(e);
        world.addWeatherEffect(new net.minecraft.entity.effect.EntityLightningBolt(world, x, at.getY(), z, true));
        world.playSound(null, x, at.getY(), z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0F, 1.0F);
        for (EntityPlayer p : world.playerEntities) {
            p.sendMessage(new TextComponentString(TextFormatting.DARK_RED + player.getName() + " summoned the " + e.getName() + "!"));
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
