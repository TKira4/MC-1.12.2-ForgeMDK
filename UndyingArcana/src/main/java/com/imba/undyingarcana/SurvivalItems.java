package com.imba.undyingarcana;

import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Survival items: Waterskin (4 sips) and Iron Canteen (8 sips), bottled drinks, and armor linings.
 * Flasks fill from any water block (dirty; sea water is salty) and are made clean with charcoal in the crafting grid.
 * Drinks: Purified Water (a water bottle in the furnace, or with charcoal), Apple / Melon Juice, Hot Cocoa (warm for
 * 3 min), Chilled Water (cool for 3 min). Linings: armor + 4 wool = warm, armor + 4 string = cool, armor alone removes it.
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class SurvivalItems {
    public static final String LINING = "ua_lining";
    public static final String[] DRINK = {"purified_water", "apple_juice", "melon_juice", "hot_cocoa", "chilled_water"};
    public static final int[] DRINK_COLOR = {0x6CB8FF, 0xE8B23C, 0xE2505E, 0x7A4A2A, 0xB8F0FF};
    /** thirst, hydration, food, food saturation, warm / cool ticks (+ warm, - cool) */
    private static final float[][] DRINK_STATS = {{6, 3, 0, 0, 0}, {8, 4, 2, 0.3F, 0}, {8, 3, 1, 0.2F, 0}, {6, 2, 1, 0.2F, 3600},
            {6, 2, 0, 0, -3600}};
    public static Item WATERSKIN, CANTEEN;
    public static final Item[] DRINKS = new Item[DRINK.length];

    private SurvivalItems() {
    }

    public static void register(List<Item> all) {
        WATERSKIN = named(new ItemFlask(4), "waterskin", all);
        CANTEEN = named(new ItemFlask(8), "iron_canteen", all);
        for (int i = 0; i < DRINK.length; i++) {
            DRINKS[i] = named(new ItemDrink(i), DRINK[i], all);
            DRINKS[i].setMaxStackSize(8);
        }
    }

    private static Item named(Item item, String name, List<Item> all) {
        item.setRegistryName(UndyingArcana.MODID, name);
        item.setUnlocalizedName(UndyingArcana.MODID + "." + name);
        item.setCreativeTab(UndyingArcana.TAB);
        all.add(item);
        return item;
    }

    private static void give(EntityPlayer p, ItemStack s) {
        if (!p.inventory.addItemStackToInventory(s)) {
            p.dropItem(s, false);
        }
    }

    static boolean isWaterBottle(ItemStack s) {
        return s.getItem() == Items.POTIONITEM && PotionUtils.getPotionFromItem(s) == PotionTypes.WATER;
    }

    // ------------------------------------------------------------------ flasks
    public static class ItemFlask extends Item {
        public final int cap;

        public ItemFlask(int cap) {
            this.cap = cap;
            setMaxStackSize(1);
            addPropertyOverride(new ResourceLocation(UndyingArcana.MODID, "full"), new net.minecraft.item.IItemPropertyGetter() {
                @Override
                public float apply(ItemStack s, World w, EntityLivingBase e) {
                    return sips(s) > 0 ? 1F : 0F;
                }
            });
        }

        public static int sips(ItemStack s) {
            return s.hasTagCompound() ? s.getTagCompound().getInteger("sips") : 0;
        }

        public static boolean clean(ItemStack s) {
            return s.hasTagCompound() && s.getTagCompound().getBoolean("clean");
        }

        /** Water quality inside: CLEAN, DIRTY or SALTY. */
        public static int quality(ItemStack s) {
            return clean(s) ? Survival.CLEAN : s.hasTagCompound() && s.getTagCompound().getBoolean("salty") ? Survival.SALTY : Survival.DIRTY;
        }

        static void set(ItemStack s, int sips, int quality) {
            NBTTagCompound t = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
            t.setInteger("sips", sips);
            t.setBoolean("clean", sips > 0 && quality == Survival.CLEAN);
            t.setBoolean("salty", sips > 0 && quality == Survival.SALTY);
            s.setTagCompound(t);
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            ItemStack s = p.getHeldItem(hand);
            RayTraceResult r = rayTrace(w, p, true);
            if (r != null && r.typeOfHit == RayTraceResult.Type.BLOCK && w.getBlockState(r.getBlockPos()).getMaterial() == Material.WATER
                    && sips(s) < cap) {
                if (!w.isRemote) {
                    int q = Survival.waterAt(p, 5.0);
                    set(s, cap, q < 0 ? Survival.DIRTY : q);
                    w.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 0.8F, 1.2F);
                }
                return new ActionResult<>(EnumActionResult.SUCCESS, s);
            }
            if (sips(s) > 0) {
                p.setActiveHand(hand);
                return new ActionResult<>(EnumActionResult.SUCCESS, s);
            }
            return new ActionResult<>(EnumActionResult.PASS, s);
        }

        @Override
        public int getMaxItemUseDuration(ItemStack s) {
            return 32;
        }

        @Override
        public EnumAction getItemUseAction(ItemStack s) {
            return EnumAction.DRINK;
        }

        @Override
        public ItemStack onItemUseFinish(ItemStack s, World w, EntityLivingBase e) {
            if (!w.isRemote && e instanceof EntityPlayerMP && sips(s) > 0) {
                EntityPlayerMP p = (EntityPlayerMP) e;
                if (Survival.thirst(p) >= Survival.MAX) {
                    p.sendStatusMessage(new TextComponentString("\u00a77You are not thirsty."), true);
                    return s;
                }
                int q = quality(s);
                Survival.drink(p, q == Survival.SALTY ? 1F : 3F, q == Survival.SALTY ? 0F : 2F, q);
                set(s, sips(s) - 1, q);
            }
            return s;
        }

        @Override
        public boolean showDurabilityBar(ItemStack s) {
            return sips(s) > 0;
        }

        @Override
        public double getDurabilityForDisplay(ItemStack s) {
            return 1.0 - sips(s) / (double) cap;
        }

        @Override
        public int getRGBDurabilityForDisplay(ItemStack s) {
            return clean(s) ? 0x3F9AFF : 0x9A8A48;
        }

        @Override
        public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean changed) {
            return changed || a.getItem() != b.getItem();
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
            int n = sips(s);
            if (n <= 0) {
                tip.add("\u00a77Empty. Right-click water to fill.");
            } else {
                int q = quality(s);
                tip.add("\u00a79Water " + n + "/" + cap + " \u00a78- " + (q == Survival.CLEAN ? "\u00a7bclean" : q == Survival.SALTY
                        ? "\u00a7csalty" : "\u00a76dirty"));
                if (q != Survival.CLEAN) {
                    tip.add("\u00a78Craft with charcoal to make it clean.");
                }
            }
        }
    }

    // ------------------------------------------------------------------ bottled drinks
    public static class ItemDrink extends Item {
        public final int kind;

        public ItemDrink(int kind) {
            this.kind = kind;
        }

        @Override
        public int getMaxItemUseDuration(ItemStack s) {
            return 32;
        }

        @Override
        public EnumAction getItemUseAction(ItemStack s) {
            return EnumAction.DRINK;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            p.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, p.getHeldItem(hand));
        }

        @Override
        public ItemStack onItemUseFinish(ItemStack s, World w, EntityLivingBase e) {
            if (!w.isRemote && e instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) e;
                float[] st = DRINK_STATS[kind];
                Survival.drink(p, st[0], st[1], Survival.CLEAN);
                if (st[2] > 0) {
                    p.getFoodStats().addStats((int) st[2], st[3]);
                }
                if (st[4] > 0) {
                    Survival.warm(p, (int) st[4]);
                } else if (st[4] < 0) {
                    Survival.cool(p, (int) -st[4]);
                }
                if (!p.capabilities.isCreativeMode) {
                    s.shrink(1);
                    give(p, new ItemStack(Items.GLASS_BOTTLE));
                }
            }
            return s;
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
            float[] st = DRINK_STATS[kind];
            tip.add("\u00a79+" + (int) st[0] + " thirst" + (st[2] > 0 ? "\u00a77, \u00a76+" + (int) st[2] + " food" : ""));
            if (st[4] > 0) {
                tip.add("\u00a76Warms you for 3 min");
            } else if (st[4] < 0) {
                tip.add("\u00a7bCools you for 3 min");
            }
        }
    }

    // ------------------------------------------------------------------ recipes
    /** Flask with water + charcoal = clean flask; water bottle + charcoal = Purified Water. */
    public static class Purify extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        private ItemStack[] scan(InventoryCrafting inv) {
            ItemStack water = ItemStack.EMPTY;
            int coal = 0, other = 0;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.getItem() == Items.COAL && s.getMetadata() == 1) {
                    coal++;
                } else if (water.isEmpty() && (s.getItem() instanceof ItemFlask && ItemFlask.sips(s) > 0 && !ItemFlask.clean(s)
                        || isWaterBottle(s))) {
                    water = s;
                } else {
                    other++;
                }
            }
            return coal == 1 && other == 0 && !water.isEmpty() ? new ItemStack[]{water} : null;
        }

        @Override
        public boolean matches(InventoryCrafting inv, World w) {
            return scan(inv) != null;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            ItemStack[] f = scan(inv);
            if (f == null) {
                return ItemStack.EMPTY;
            }
            if (isWaterBottle(f[0])) {
                return new ItemStack(DRINKS[0]);
            }
            ItemStack out = f[0].copy();
            ItemFlask.set(out, ItemFlask.sips(out), Survival.CLEAN);
            return out;
        }

        @Override
        public boolean canFit(int w, int h) {
            return w * h >= 2;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return new ItemStack(DRINKS[0]);
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    /** Armor + 4 wool = warm lining, armor + 4 string = cool lining, a lined armor piece alone = lining removed. */
    public static class Lining extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        private int[] scan(InventoryCrafting inv) {
            int armorAt = -1, wool = 0, string = 0, other = 0;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.getItem() instanceof ItemArmor && armorAt < 0) {
                    armorAt = i;
                } else if (s.getItem() == Item.getItemFromBlock(Blocks.WOOL)) {
                    wool++;
                } else if (s.getItem() == Items.STRING) {
                    string++;
                } else {
                    other++;
                }
            }
            if (armorAt < 0 || other > 0) {
                return null;
            }
            ItemStack a = inv.getStackInSlot(armorAt);
            int cur = a.hasTagCompound() ? a.getTagCompound().getInteger(LINING) : 0;
            if (wool == 4 && string == 0 && cur != 1) {
                return new int[]{armorAt, 1};
            }
            if (string == 4 && wool == 0 && cur != 2) {
                return new int[]{armorAt, 2};
            }
            if (wool == 0 && string == 0 && cur != 0) {
                return new int[]{armorAt, 0};
            }
            return null;
        }

        @Override
        public boolean matches(InventoryCrafting inv, World w) {
            return scan(inv) != null;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            int[] r = scan(inv);
            if (r == null) {
                return ItemStack.EMPTY;
            }
            ItemStack out = inv.getStackInSlot(r[0]).copy();
            out.setCount(1);
            if (r[1] == 0) {
                out.getTagCompound().removeTag(LINING);
            } else {
                if (!out.hasTagCompound()) {
                    out.setTagCompound(new NBTTagCompound());
                }
                out.getTagCompound().setInteger(LINING, r[1]);
            }
            return out;
        }

        @Override
        public boolean canFit(int w, int h) {
            return true;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isDynamic() {
            return true;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
            return NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        ResourceLocation g = new ResourceLocation(UndyingArcana.MODID, "survival");
        reg(event, new ShapedOreRecipe(g, new ItemStack(WATERSKIN), " S ", "L L", " L ", 'S', Items.STRING, 'L', Items.LEATHER), "waterskin");
        reg(event, new ShapedOreRecipe(g, new ItemStack(CANTEEN), " L ", "I I", " I ", 'L', Items.LEATHER, 'I', "ingotIron"), "iron_canteen");
        ItemStack pure = new ItemStack(DRINKS[0]);
        reg(event, new ShapelessOreRecipe(g, new ItemStack(DRINKS[1]), pure, Items.APPLE, Items.SUGAR), "apple_juice");
        reg(event, new ShapelessOreRecipe(g, new ItemStack(DRINKS[2]), pure, Items.MELON, Items.MELON), "melon_juice");
        reg(event, new ShapelessOreRecipe(g, new ItemStack(DRINKS[3]), pure, new ItemStack(Items.DYE, 1, 3), Items.SUGAR), "hot_cocoa");
        reg(event, new ShapelessOreRecipe(g, new ItemStack(DRINKS[4]), pure, Items.SNOWBALL, Items.SNOWBALL), "chilled_water");
        reg(event, new Purify(), "purify");
        reg(event, new Lining(), "lining");
        GameRegistry.addSmelting(new ItemStack(Items.POTIONITEM, 1, 0), new ItemStack(DRINKS[0]), 0.1F);
    }

    private static void reg(RegistryEvent.Register<IRecipe> event, IRecipe r, String name) {
        r.setRegistryName(new ResourceLocation(UndyingArcana.MODID, "sv_" + name));
        event.getRegistry().register(r);
    }
}
