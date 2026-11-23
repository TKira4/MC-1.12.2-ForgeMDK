package com.imba.undyingarcana;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Food (game rule uaSurvival): nutrition and spoilage.
 *
 * Nutrition: 5 groups (Protein, Grain, Vegetable, Fruit, Sweet), 0..100 each, start at 50, -1 per minute. Eating adds
 * to the food's groups (food points x 7, split between its groups). At 60+: Protein +8% damage, Grain +12% mining
 * speed, Vegetable -15% thirst, Fruit +5% speed, Sweet +20% mana regen. All five at 40+ (balanced diet): +4 max health.
 * Each group at 0: -2 max health.
 *
 * Spoilage: food gets a best-before time when it enters a player's inventory (raw meat 3 days, cooked 5, grain 6,
 * sweets 10, fruit and vegetables 8, root crops 12; a day is 20 minutes). Stale food (last half) may cause hunger;
 * expired food turns into Spoiled Food. Salt (a water bucket in the furnace) makes food last 3x longer; the Icebox
 * slows spoiling to a quarter.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class Food {
    public static final String[] GROUP = {"Protein", "Grain", "Vegetable", "Fruit", "Sweet"};
    public static final String[] GROUP_COLOR = {"\u00a7c", "\u00a76", "\u00a7a", "\u00a7d", "\u00a7e"};
    public static final int[] GROUP_RGB = {0xE05A4A, 0xE0A040, 0x6CCB5A, 0xD070D0, 0xF0E060};
    public static final int PROTEIN = 0, GRAIN = 1, VEG = 2, FRUIT = 3, SWEET = 4;
    public static final String TAG = "ua_food", BB = "ua_bb", LIFE = "ua_lf", SALT = "ua_salt";
    public static final long DAY = 24000L, BUCKET = 6000L;
    private static final UUID HP_ID = UUID.fromString("0b7c2f4e-1d3a-4c5b-9e8f-7a6b5c4d3e21");
    private static final UUID SPEED_ID = UUID.fromString("1c8d3f5a-2e4b-4d6c-8f9a-8b7c6d5e4f32");
    private static final Map<Item, Integer> GROUPS = new HashMap<>();
    public static Item SALT_ITEM, SPOILED, STEW;

    private static final String[][] WORDS = {
            {"beef", "pork", "chicken", "mutton", "rabbit", "fish", "cod", "salmon", "meat", "egg", "steak", "venison", "jerky",
                    "flesh", "bacon", "crab", "squid", "tentacle", "fillet", "ham", "sausage", "turkey", "duck", "shrimp", "clam"},
            {"bread", "wheat", "cookie", "cake", "pie", "dough", "rice", "oat", "cereal", "pasta", "noodle", "toast", "cracker",
                    "corn", "porridge", "biscuit", "bun", "flour"},
            {"carrot", "potato", "beet", "cabbage", "lettuce", "tomato", "onion", "vegetable", "mushroom", "salad", "pumpkin",
                    "kelp", "seaweed", "pepper", "bean", "pea", "cucumber", "garlic", "spinach"},
            {"apple", "melon", "berry", "cherry", "fruit", "orange", "banana", "grape", "chorus", "peach", "pear", "lemon",
                    "plum", "coconut", "mango", "fig", "kiwi", "pineapple"},
            {"sugar", "honey", "candy", "chocolate", "cocoa", "cookie", "cake", "pie", "jam", "sweet", "milk", "cheese",
                    "cream", "pudding", "syrup", "fudge"}};

    private Food() {
    }

    // ------------------------------------------------------------------ classification
    /** Group bit mask of a food item (bit = group index). */
    public static int groups(ItemStack s) {
        if (s.isEmpty()) {
            return 0;
        }
        if (s.getItem() == STEW && s.hasTagCompound()) {
            return s.getTagCompound().getInteger("g");
        }
        Item it = s.getItem();
        if (it == Items.FISH || it == Items.COOKED_FISH) {
            return 1 << PROTEIN;
        }
        Integer g = GROUPS.get(it);
        if (g == null) {
            g = classify(it);
            GROUPS.put(it, g);
        }
        return g;
    }

    private static int classify(Item it) {
        if (it == Items.PUMPKIN_PIE) {
            return 1 << VEG | 1 << GRAIN | 1 << SWEET;
        }
        if (it == Items.MUSHROOM_STEW || it == Items.BEETROOT_SOUP) {
            return 1 << VEG;
        }
        if (it == Items.RABBIT_STEW) {
            return 1 << PROTEIN | 1 << VEG;
        }
        if (it == Items.GOLDEN_APPLE || it == Items.CHORUS_FRUIT) {
            return 1 << FRUIT;
        }
        if (it == Items.GOLDEN_CARROT) {
            return 1 << VEG;
        }
        if (it == Items.SPIDER_EYE || it == Items.POISONOUS_POTATO) {
            return 0;
        }
        ResourceLocation rl = it.getRegistryName();
        String n = rl == null ? "" : rl.getResourcePath().toLowerCase();
        int g = 0;
        for (int i = 0; i < WORDS.length; i++) {
            for (String w : WORDS[i]) {
                if (n.contains(w)) {
                    g |= 1 << i;
                    break;
                }
            }
        }
        if (g == 0 && it instanceof ItemFood && ((ItemFood) it).isWolfsFavoriteMeat()) {
            g = 1 << PROTEIN;
        }
        return g;
    }

    /** Shelf life in ticks, or 0 when the item does not spoil. */
    public static long life(ItemStack s) {
        Item it = s.getItem();
        if (!(it instanceof ItemFood) || it == SPOILED || it == Items.GOLDEN_APPLE || it == Items.GOLDEN_CARROT || it == Items.ROTTEN_FLESH
                || it == Items.SPIDER_EYE || it == Items.POISONOUS_POTATO || it == Items.CHORUS_FRUIT) {
            return 0;
        }
        ResourceLocation rl = it.getRegistryName();
        String n = rl == null ? "" : rl.getResourcePath().toLowerCase();
        for (String w : new String[]{"jerky", "dried", "salted", "canned", "candy", "honey", "cracker", "hardtack", "golden"}) {
            if (n.contains(w)) {
                return 0;
            }
        }
        if (it == STEW) {
            return 4 * DAY;
        }
        if (it == Items.CARROT || it == Items.POTATO || it == Items.BEETROOT || it == Items.BAKED_POTATO) {
            return 12 * DAY;
        }
        int g = groups(s);
        boolean raw = n.contains("raw") || it == Items.BEEF || it == Items.PORKCHOP || it == Items.CHICKEN || it == Items.MUTTON
                || it == Items.RABBIT || it == Items.FISH;
        long l = 8 * DAY;
        if ((g & 1 << PROTEIN) != 0) {
            l = Math.min(l, raw ? 3 * DAY : 5 * DAY);
        }
        if ((g & 1 << GRAIN) != 0) {
            l = Math.min(l, 6 * DAY);
        }
        if ((g & (1 << VEG | 1 << FRUIT)) == 0 && (g & 1 << SWEET) != 0) {
            l = 10 * DAY;
        }
        return l;
    }

    /** Remaining fraction of the shelf life (1 fresh .. 0 spoiled), or -1 when not tracked. */
    public static float freshness(ItemStack s, long now) {
        if (!s.hasTagCompound() || !s.getTagCompound().hasKey(BB)) {
            return -1;
        }
        NBTTagCompound t = s.getTagCompound();
        long lf = Math.max(1, t.getLong(LIFE));
        return Math.max(0, Math.min(1, (t.getLong(BB) - now) / (float) lf));
    }

    /** Stamps a best-before time (rounded so food made close together still stacks). */
    static void stamp(ItemStack s, long now, long life) {
        NBTTagCompound t = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
        t.setLong(BB, (now + life + BUCKET - 1) / BUCKET * BUCKET);
        t.setLong(LIFE, life);
        s.setTagCompound(t);
    }

    // ------------------------------------------------------------------ player nutrition
    static NBTTagCompound data(EntityPlayer p) {
        NBTTagCompound d = p.getEntityData();
        if (!d.hasKey(TAG)) {
            NBTTagCompound t = new NBTTagCompound();
            for (int i = 0; i < GROUP.length; i++) {
                t.setFloat("n" + i, 50F);
            }
            d.setTag(TAG, t);
        }
        return d.getCompoundTag(TAG);
    }

    public static float group(EntityPlayer p, int g) {
        return data(p).getFloat("n" + g);
    }

    public static byte[] nutrition(EntityPlayer p) {
        byte[] b = new byte[GROUP.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Math.round(group(p, i));
        }
        return b;
    }

    /** Thirst drain multiplier from the Vegetable bonus. */
    public static float thirstMul(EntityPlayer p) {
        return Survival.on(p.world) && group(p, VEG) >= 60 ? 0.85F : 1F;
    }

    public static float manaMul(EntityPlayer p) {
        return Survival.on(p.world) && group(p, SWEET) >= 60 ? 1.2F : 1F;
    }

    private static void eat(EntityPlayer p, int mask, int foodPoints, float mul) {
        int n = Integer.bitCount(mask);
        if (n == 0) {
            return;
        }
        float each = foodPoints * 7F * mul / n;
        NBTTagCompound d = data(p);
        for (int i = 0; i < GROUP.length; i++) {
            if ((mask & 1 << i) != 0) {
                d.setFloat("n" + i, Math.min(100F, d.getFloat("n" + i) + each));
            }
        }
    }

    @SubscribeEvent
    public static void onFinish(LivingEntityUseItemEvent.Finish e) {
        EntityLivingBase ent = e.getEntityLiving();
        if (!(ent instanceof EntityPlayerMP) || !Survival.on(ent.world)) {
            return;
        }
        EntityPlayer p = (EntityPlayer) ent;
        ItemStack s = e.getItem();
        if (!(s.getItem() instanceof ItemFood)) {
            return;
        }
        int pts = ((ItemFood) s.getItem()).getHealAmount(s);
        eat(p, groups(s), pts, s.getItem() == STEW ? 1.5F : 1F);
        float f = freshness(s, p.world.getTotalWorldTime());
        if (f >= 0 && f < 0.5F && p.getRNG().nextFloat() < 0.3F) {
            p.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 300, 0));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote || !(e.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        if (p.ticksExisted % 40 != 11) {
            return;
        }
        boolean on = Survival.on(p.world);
        NBTTagCompound d = data(p);
        boolean act = on && !p.capabilities.isCreativeMode && !p.isSpectator();
        if (act && p.ticksExisted % 1200 == 11) {
            for (int i = 0; i < GROUP.length; i++) {
                d.setFloat("n" + i, Math.max(0, d.getFloat("n" + i) - 1F));
            }
        }
        int zero = 0, balanced = 0;
        for (int i = 0; i < GROUP.length; i++) {
            float v = d.getFloat("n" + i);
            if (v <= 0) {
                zero++;
            }
            if (v >= 40) {
                balanced++;
            }
        }
        float hp = !on ? 0 : (balanced == GROUP.length ? 4F : 0F) - 2F * Math.min(3, zero);
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH), HP_ID, "UA diet", hp, 0);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
        Stats.setModifier(p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED), SPEED_ID, "UA fruit",
                on && d.getFloat("n" + FRUIT) >= 60 ? 0.05 : 0, 1);
        if (on) {
            spoil(p);
        }
    }

    /** Stamps new food, turns expired food into Spoiled Food (main inventory and off hand). */
    private static void spoil(EntityPlayerMP p) {
        long now = p.world.getTotalWorldTime();
        List<ItemStack> inv = p.inventory.mainInventory;
        boolean changed = false;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack r = check(inv.get(i), now);
            if (r != null) {
                inv.set(i, r);
                changed = true;
            }
        }
        ItemStack off = p.inventory.offHandInventory.get(0);
        ItemStack r = check(off, now);
        if (r != null) {
            p.inventory.offHandInventory.set(0, r);
            changed = true;
        }
        if (changed) {
            p.inventoryContainer.detectAndSendChanges();
        }
    }

    /** Null = unchanged, else the stack to put back. */
    static ItemStack check(ItemStack s, long now) {
        if (s.isEmpty()) {
            return null;
        }
        long life = life(s);
        if (life <= 0) {
            return null;
        }
        if (!s.hasTagCompound() || !s.getTagCompound().hasKey(BB)) {
            ItemStack c = s.copy();
            stamp(c, now, life);
            return c;
        }
        if (now >= s.getTagCompound().getLong(BB)) {
            return new ItemStack(SPOILED, s.getCount());
        }
        return null;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getTrueSource() instanceof EntityPlayerMP) {
            EntityPlayer p = (EntityPlayer) e.getSource().getTrueSource();
            if (Survival.on(p.world) && group(p, PROTEIN) >= 60) {
                e.setAmount(e.getAmount() * 1.08F);
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(BreakSpeed e) {
        EntityPlayer p = e.getEntityPlayer();
        if (Survival.on(p.world) && p.getEntityData().hasKey(TAG) && group(p, GRAIN) >= 60) {
            e.setNewSpeed(e.getNewSpeed() * 1.12F);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        e.player.getEntityData().removeTag(TAG);
    }

    /** Salt: give the bucket back when it is taken out of the furnace. */
    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent e) {
        if (!e.player.world.isRemote && e.smelting.getItem() == SALT_ITEM) {
            ItemStack b = new ItemStack(Items.BUCKET, e.smelting.getCount());
            if (!e.player.inventory.addItemStackToInventory(b)) {
                e.player.dropItem(b, false);
            }
        }
    }

    // ------------------------------------------------------------------ items
    public static void register(List<Item> all) {
        SALT_ITEM = named(new Item(), "salt", all);
        SPOILED = named(new ItemSpoiled(), "spoiled_food", all);
        STEW = named(new FoodBlocks.ItemStew(), "stew", all);
        STEW.setMaxStackSize(1);
    }

    private static Item named(Item item, String name, List<Item> all) {
        item.setRegistryName(UndyingArcana.MODID, name);
        item.setUnlocalizedName(UndyingArcana.MODID + "." + name);
        item.setCreativeTab(UndyingArcana.TAB);
        all.add(item);
        return item;
    }

    public static class ItemSpoiled extends ItemFood {
        public ItemSpoiled() {
            super(1, 0F, false);
            setAlwaysEdible();
        }

        @Override
        protected void onFoodEaten(ItemStack s, World w, EntityPlayer p) {
            if (!w.isRemote) {
                p.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 600, 1));
                p.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 300, 0));
            }
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
            tip.add("\u00a78Gone bad. 4 make a bone meal.");
        }
    }

    /** Up to 8 of the same food + 1 salt = the same food salted (lasts 3x longer). */
    public static class Salting extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        private ItemStack scan(InventoryCrafting inv, int[] count) {
            ItemStack food = ItemStack.EMPTY;
            int salt = 0;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.getItem() == SALT_ITEM) {
                    salt++;
                } else if (life(s) > 0 && !(s.hasTagCompound() && s.getTagCompound().getBoolean(SALT))
                        && (food.isEmpty() || ItemStack.areItemsEqual(food, s) && ItemStack.areItemStackTagsEqual(food, s))) {
                    food = s;
                    count[0]++;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            return salt == 1 ? food : ItemStack.EMPTY;
        }

        @Override
        public boolean matches(InventoryCrafting inv, World w) {
            return !scan(inv, new int[1]).isEmpty();
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            int[] n = new int[1];
            ItemStack f = scan(inv, n);
            if (f.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack out = f.copy();
            out.setCount(n[0]);
            NBTTagCompound t = out.hasTagCompound() ? out.getTagCompound() : new NBTTagCompound();
            t.setBoolean(SALT, true);
            long life = life(out) * 3;
            if (t.hasKey(BB)) {
                long now = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance() == null ? 0
                        : net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld().getTotalWorldTime();
                long left = Math.max(0, t.getLong(BB) - now);
                t.setLong(BB, now + left * 3);
                t.setLong(LIFE, life);
            } else {
                t.setLong(LIFE, life);
            }
            out.setTagCompound(t);
            return out;
        }

        @Override
        public boolean canFit(int w, int h) {
            return w * h >= 2;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        Salting s = new Salting();
        s.setRegistryName(new ResourceLocation(UndyingArcana.MODID, "fd_salting"));
        event.getRegistry().register(s);
        ResourceLocation g = new ResourceLocation(UndyingArcana.MODID, "food");
        net.minecraftforge.oredict.ShapelessOreRecipe bone = new net.minecraftforge.oredict.ShapelessOreRecipe(g,
                new ItemStack(Items.DYE, 1, 15), SPOILED, SPOILED, SPOILED, SPOILED);
        bone.setRegistryName(new ResourceLocation(UndyingArcana.MODID, "fd_spoiled_bonemeal"));
        event.getRegistry().register(bone);
        GameRegistry.addSmelting(Items.WATER_BUCKET, new ItemStack(SALT_ITEM, 2), 0.1F);
    }
}
