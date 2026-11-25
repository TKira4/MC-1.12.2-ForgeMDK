package com.imba.undyingarcana;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Survival needs (game rule uaSurvival, default on): thirst and body temperature. Replaces SimpleDifficulty.
 *
 * Thirst 0..20 with a hydration buffer like food saturation. It drains slowly at rest and faster when sprinting,
 * jumping, fighting, mining or overheated; Vitality slows it (up to -30%). At 6 or less the player cannot sprint, at 3
 * or less is slowed, at 0 takes damage. Drink by sneak + right-click on water (any item in hand) or at the rain sky,
 * from a Waterskin / Iron Canteen, or bottled drinks. River and lake water is dirty (30% to get sick: nausea and faster
 * thirst), sea water worse; a charcoal filter or a furnace makes it clean.
 *
 * Body temperature 0..25 (12.5 = comfortable) drifts toward the place's temperature: biome, height, day / night,
 * rain and snow, being wet or in water, heat and cold blocks nearby, armor and linings, sprinting, burning, warm and
 * cool drinks. Cold (5 or less): slowness, then mining fatigue, then damage at 1 or less. Hot (20+): faster thirst,
 * weakness at 22, damage at 24.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Survival {
    public static final String RULE = "uaSurvival", TAG = "ua_sv";
    public static final float MAX = 20F, NEUTRAL = 12.5F, TMAX = 25F;
    public static final DamageSource THIRST = new DamageSource("ua_thirst").setDamageBypassesArmor().setDamageIsAbsolute();
    public static final DamageSource COLD = new DamageSource("ua_cold").setDamageBypassesArmor();
    public static final DamageSource HEAT = new DamageSource("ua_heat").setDamageBypassesArmor();
    /** Water quality of a drink: clean, dirty (river / lake), salty (sea). */
    public static final int CLEAN = 0, DIRTY = 1, SALTY = 2;

    private Survival() {
    }

    public static boolean on(World w) {
        GameRules r = w.getGameRules();
        return !r.hasRule(RULE) || r.getBoolean(RULE);
    }

    static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(TAG)) {
            NBTTagCompound t = new NBTTagCompound();
            t.setFloat("th", MAX);
            t.setFloat("hy", 5F);
            t.setFloat("t", NEUTRAL);
            t.setFloat("tt", NEUTRAL);
            d.setTag(TAG, t);
        }
        return d.getCompoundTag(TAG);
    }

    public static float thirst(EntityPlayer p) {
        return data(p).getFloat("th");
    }

    public static float temp(EntityPlayer p) {
        return data(p).getFloat("t");
    }

    private static boolean active(EntityPlayer p) {
        return !p.capabilities.isCreativeMode && !p.isSpectator() && p.world.getDifficulty() != EnumDifficulty.PEACEFUL;
    }

    /** Adds thirst exhaustion (4 = one thirst point, hydration first). */
    public static void exhaust(EntityPlayer p, float v) {
        if (!active(p)) {
            return;
        }
        NBTTagCompound d = data(p);
        d.setFloat("tx", d.getFloat("tx") + v);
    }

    /** Drink: amount of thirst, hydration, water quality. */
    public static void drink(EntityPlayer p, float amount, float hydration, int quality) {
        NBTTagCompound d = data(p);
        float th = Math.min(MAX, d.getFloat("th") + amount);
        d.setFloat("th", th);
        d.setFloat("hy", Math.min(th, d.getFloat("hy") + hydration));
        float risk = quality == SALTY ? 0.7F : quality == DIRTY ? 0.3F : 0F;
        if (risk > 0 && p.getRNG().nextFloat() < risk && !p.capabilities.isCreativeMode) {
            d.setInteger("sick", 600);
            p.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 160, 0));
            p.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 300, 0));
            p.sendStatusMessage(new TextComponentString("\u00a72The water made you sick. \u00a77Filter it with charcoal or boil it."), true);
        }
        if (p instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) p, true);
        }
    }

    public static void warm(EntityPlayer p, int ticks) {
        data(p).setInteger("warm", ticks);
        data(p).setInteger("cool", 0);
    }

    public static void cool(EntityPlayer p, int ticks) {
        data(p).setInteger("cool", ticks);
        data(p).setInteger("warm", 0);
    }

    // ------------------------------------------------------------------ drinking from the world
    /** Quality of the water block the player looks at, or -1. */
    public static int waterAt(EntityPlayer p, double reach) {
        Vec3d eye = p.getPositionEyes(1F);
        Vec3d end = eye.add(p.getLook(1F).scale(reach));
        RayTraceResult r = p.world.rayTraceBlocks(eye, end, true, false, false);
        if (r == null || r.typeOfHit != RayTraceResult.Type.BLOCK) {
            return -1;
        }
        IBlockState s = p.world.getBlockState(r.getBlockPos());
        if (s.getMaterial() != Material.WATER) {
            return -1;
        }
        Biome b = p.world.getBiome(r.getBlockPos());
        return BiomeDictionary.hasType(b, BiomeDictionary.Type.OCEAN) || BiomeDictionary.hasType(b, BiomeDictionary.Type.BEACH) ? SALTY : DIRTY;
    }

    /** Client asked to drink: 0 = water block in view, 1 = rain from the sky. */
    public static void drinkRequest(EntityPlayerMP p, int what) {
        if (!on(p.world) || p.isSpectator()) {
            return;
        }
        NBTTagCompound d = data(p);
        long now = p.world.getTotalWorldTime();
        if (now - d.getLong("sip") < 12) {
            return;
        }
        if (d.getFloat("th") >= MAX) {
            p.sendStatusMessage(new TextComponentString("\u00a77You are not thirsty."), true);
            return;
        }
        if (what == 0) {
            int q = waterAt(p, 5.0);
            if (q < 0) {
                return;
            }
            d.setLong("sip", now);
            drink(p, q == SALTY ? 0.5F : 2F, q == SALTY ? 0F : 0.5F, q);
        } else {
            BlockPos head = new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ);
            if (!p.world.isRainingAt(head.up()) || p.rotationPitch > -50) {
                return;
            }
            d.setLong("sip", now);
            drink(p, 1F, 0.5F, CLEAN);
        }
        p.world.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.5F,
                p.world.rand.nextFloat() * 0.1F + 0.9F);
        if (p.world instanceof WorldServer) {
            Vec3d at = p.getPositionEyes(1F).add(p.getLook(1F).scale(1.2));
            ((WorldServer) p.world).spawnParticle(EnumParticleTypes.WATER_SPLASH, at.x, at.y - 0.3, at.z, 6, 0.15, 0.05, 0.15, 0.0);
        }
    }

    // ------------------------------------------------------------------ temperature
    /** Place temperature for this player right now (0..25). */
    public static float target(EntityPlayer p) {
        World w = p.world;
        BlockPos pos = new BlockPos(p.posX, p.posY + 0.5, p.posZ);
        Biome b = w.getBiome(pos);
        float bt = b.getTemperature(pos);
        float t = NEUTRAL + (bt - 0.8F) * 7F;
        if (pos.getY() > 90) {
            t -= (pos.getY() - 90) / 15F; // thin cold air up high
        }
        boolean sky = w.canSeeSky(pos.up());
        boolean nether = w.provider.isNether();
        if (!sky && pos.getY() < 56 && !nether) {
            t = t + (NEUTRAL - t) * 0.7F; // caves stay mild
        }
        if (sky && w.provider.isSurfaceWorld()) {
            if (!w.isDaytime()) {
                t -= bt >= 1.5F ? 6F : 2.5F; // deserts get cold at night
            } else if (bt >= 1.5F) {
                t += 1F;
            }
        }
        if (w.isRainingAt(pos.up(2))) {
            t -= b.getEnableSnow() || bt < 0.15F ? 4F : 2F;
        }
        NBTTagCompound d = data(p);
        if (p.isInWater()) {
            t -= 4F;
        }
        if (d.getInteger("wet") > 0) {
            t -= 1.5F;
        }
        t += d.getFloat("near");
        int warmPieces = 0;
        float armor = 0;
        boolean sun = sky && w.isDaytime() && bt >= 1.5F;
        for (ItemStack a : p.inventory.armorInventory) {
            if (a.isEmpty()) {
                continue;
            }
            int lining = a.hasTagCompound() ? a.getTagCompound().getInteger(SurvivalItems.LINING) : 0;
            if (lining == 1) {
                armor += 1.5F;
            } else if (lining == 2) {
                armor -= 1.5F;
            }
            if (a.getItem() instanceof ItemArmor) {
                ItemArmor.ArmorMaterial m = ((ItemArmor) a.getItem()).getArmorMaterial();
                if (m == ItemArmor.ArmorMaterial.LEATHER) {
                    warmPieces++;
                } else if (sun) {
                    armor += 0.5F; // metal in the desert sun
                } else {
                    armor += 0.3F; // any armor keeps a little warmth
                }
            }
        }
        t += armor + warmPieces * 1F;
        if (p.isSprinting()) {
            t += 1.5F;
        }
        if (p.isBurning()) {
            t += 8F;
        }
        if (d.getInteger("warm") > 0 && t < NEUTRAL) {
            t = Math.min(NEUTRAL, t + 6F);
        }
        if (d.getInteger("cool") > 0 && t > NEUTRAL) {
            t = Math.max(NEUTRAL, t - 6F);
        }
        t = Relics.temperature(p, t, NEUTRAL);
        if (p.isPotionActive(MobEffects.FIRE_RESISTANCE) && t > NEUTRAL) {
            t = NEUTRAL + (t - NEUTRAL) * 0.2F;
        }
        return Math.max(0, Math.min(TMAX, t));
    }

    /** Heat (+) and cold (-) blocks within 3 blocks, with distance falloff. */
    private static float nearBlocks(EntityPlayer p) {
        World w = p.world;
        BlockPos c = new BlockPos(p.posX, p.posY + 0.5, p.posZ);
        float heat = 0, cold = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    m.setPos(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    IBlockState s = w.getBlockState(m);
                    float v = blockHeat(s);
                    if (v == 0) {
                        continue;
                    }
                    float f = Math.max(0.2F, 1F - (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 5F);
                    if (v > 0) {
                        heat += v * f;
                    } else {
                        cold += v * f;
                    }
                }
            }
        }
        return Math.min(10F, heat) + Math.max(-6F, cold);
    }

    private static float blockHeat(IBlockState s) {
        net.minecraft.block.Block b = s.getBlock();
        if (b == Blocks.LAVA || b == Blocks.FLOWING_LAVA) {
            return 3F;
        }
        if (b == Blocks.FIRE || b == Blocks.LIT_FURNACE) {
            return 2.5F;
        }
        if (b == Blocks.MAGMA) {
            return 1.5F;
        }
        if (b == Blocks.TORCH || b == Blocks.LIT_PUMPKIN || b == Blocks.GLOWSTONE) {
            return 0.4F;
        }
        if (b == Blocks.ICE || b == Blocks.PACKED_ICE) {
            return -0.8F;
        }
        if (b == Blocks.SNOW) {
            return -0.5F;
        }
        return 0;
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote || !(e.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        if (!on(p.world) || p.isDead) {
            return;
        }
        NBTTagCompound d = data(p);
        boolean act = active(p);
        float t = d.getFloat("t");
        if (act) {
            float x = 0.0033F;
            if (p.isSprinting()) {
                x += 0.011F;
            }
            if (p.isInWater()) {
                x += 0.004F;
            }
            if (t >= 22F) {
                x *= 2F;
            } else if (t >= 19.5F) {
                x *= 1.5F;
            }
            if (d.getInteger("sick") > 0) {
                x *= 2F;
            }
            x *= (1F - Math.min(0.3F, Stats.stats(p)[Builds.VIT] * 0.006F)) * Food.thirstMul(p)
                    * (Relics.has(p, Relics.WATERSKIN_BELT) ? 0.7F : 1F);
            float tx = d.getFloat("tx") + x;
            while (tx >= 4F) {
                tx -= 4F;
                float hy = d.getFloat("hy");
                if (hy > 0) {
                    d.setFloat("hy", Math.max(0, hy - 1F));
                } else {
                    d.setFloat("th", Math.max(0, d.getFloat("th") - 1F));
                }
            }
            d.setFloat("tx", tx);
        }
        for (String k : new String[]{"sick", "warm", "cool"}) {
            int v = d.getInteger(k);
            if (v > 0) {
                d.setInteger(k, v - 1);
            }
        }
        int wet = d.getInteger("wet");
        if (p.isWet()) {
            d.setInteger("wet", 200);
        } else if (wet > 0) {
            d.setInteger("wet", Math.max(0, wet - (d.getFloat("near") > 1 ? 4 : 1)));
        }
        if (p.ticksExisted % 20 != 0) {
            return;
        }
        if (p.ticksExisted % 40 == 0) {
            d.setFloat("near", nearBlocks(p));
        }
        float tt = target(p);
        d.setFloat("tt", tt);
        float diff = tt - t;
        float rate = (0.04F + 0.02F * Math.abs(diff)) * (p.isInWater() ? 2F : 1F);
        t = Math.abs(diff) <= rate ? tt : t + Math.signum(diff) * rate;
        d.setFloat("t", t);
        if (act) {
            effects(p, d, t);
        }
        sync(p, false);
    }

    private static void effects(EntityPlayerMP p, NBTTagCompound d, float t) {
        float th = d.getFloat("th");
        if (th <= 3F) {
            effect(p, MobEffects.SLOWNESS, 0);
        }
        if (th <= 0F && p.ticksExisted % 80 == 0) {
            p.attackEntityFrom(THIRST, 1F);
        }
        if (t <= 5F) {
            effect(p, MobEffects.SLOWNESS, t <= 3F ? 1 : 0);
            if (t <= 3F) {
                effect(p, MobEffects.MINING_FATIGUE, 0);
            }
            if (t <= 1F && p.ticksExisted % 60 == 0) {
                p.attackEntityFrom(COLD, 1F);
            }
        }
        if (t >= 22F) {
            effect(p, MobEffects.WEAKNESS, 0);
            if (t >= 24F && p.ticksExisted % 60 == 0) {
                p.attackEntityFrom(HEAT, 1F);
            }
        }
    }

    private static void effect(EntityPlayer p, Potion pot, int amp) {
        PotionEffect cur = p.getActivePotionEffect(pot);
        if (cur == null || cur.getDuration() < 30 || cur.getAmplifier() < amp) {
            p.addPotionEffect(new PotionEffect(pot, 50, amp, true, false));
        }
    }

    /** Sends thirst / temperature to the client when they changed (or every 5 s). */
    public static void sync(EntityPlayerMP p, boolean force) {
        NBTTagCompound d = data(p);
        SkillNet.MsgSurv m = new SkillNet.MsgSurv();
        m.on = on(p.world);
        m.thirst = d.getFloat("th");
        m.hydration = d.getFloat("hy");
        m.temp = d.getFloat("t");
        m.target = d.getFloat("tt");
        m.nutrition = Food.nutrition(p);
        m.flags = (d.getInteger("sick") > 0 ? 1 : 0) | (d.getInteger("warm") > 0 ? 2 : 0) | (d.getInteger("cool") > 0 ? 4 : 0)
                | (d.getInteger("wet") > 0 ? 8 : 0);
        int hash = (m.on ? 1 : 0) + Math.round(m.thirst * 4) * 3 + Math.round(m.hydration) * 211 + Math.round(m.temp * 10) * 4093
                + Math.round(m.target) * 131071 + m.flags * 7 + java.util.Arrays.hashCode(m.nutrition) * 31;
        if (force || d.getInteger("sent") != hash || p.ticksExisted % 100 == 0) {
            d.setInteger("sent", hash);
            SkillNet.NET.sendTo(m, p);
        }
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        if (e.getEntityLiving() instanceof EntityPlayerMP) {
            exhaust((EntityPlayer) e.getEntityLiving(), e.getEntityLiving().isSprinting() ? 0.1F : 0.08F);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (!e.getEntityPlayer().world.isRemote) {
            exhaust(e.getEntityPlayer(), 0.08F);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() != null && !e.getWorld().isRemote) {
            exhaust(e.getPlayer(), 0.02F);
        }
    }

    /** Vanilla drinks and juicy foods quench thirst too. */
    @SubscribeEvent
    public static void onFinish(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP) || !on(e.getEntityLiving().world)) {
            return;
        }
        EntityPlayer p = (EntityPlayer) e.getEntityLiving();
        ItemStack s = e.getItem();
        net.minecraft.item.Item it = s.getItem();
        if (it == Items.POTIONITEM) {
            boolean water = PotionUtils.getPotionFromItem(s) == PotionTypes.WATER;
            drink(p, water ? 3F : 2F, 1F, water ? DIRTY : CLEAN);
        } else if (it == Items.MILK_BUCKET) {
            drink(p, 4F, 2F, CLEAN);
        } else if (it == Items.MELON) {
            drink(p, 2F, 0.5F, CLEAN);
        } else if (it == Items.APPLE || it == Items.CHORUS_FRUIT) {
            drink(p, 1F, 0F, CLEAN);
        } else if (it == Items.MUSHROOM_STEW || it == Items.RABBIT_STEW || it == Items.BEETROOT_SOUP) {
            drink(p, 3F, 1F, CLEAN);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            e.player.getEntityData().removeTag(TAG);
            sync((EntityPlayerMP) e.player, true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) e.player, true);
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load e) {
        World w = e.getWorld();
        if (!w.isRemote && w.provider.getDimension() == 0 && !w.getGameRules().hasRule(RULE)) {
            w.getGameRules().addGameRule(RULE, "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
    }
}
