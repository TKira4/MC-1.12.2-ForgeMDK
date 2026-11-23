package com.imba.undyingarcana;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * Food blocks: the Icebox (27 slots, food inside spoils at a quarter of the speed) and the Cooking Pot (water + 2-4
 * foods over a heat source = a stew that combines their food value and nutrition, with a buff for 2+ groups).
 */
@Mod.EventBusSubscriber(modid = UndyingArcana.MODID)
public final class FoodBlocks {
    public static Block ICEBOX, POT;
    public static final PropertyBool FILLED = PropertyBool.create("filled");

    private FoodBlocks() {
    }

    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        ICEBOX = new BlockIcebox();
        POT = new BlockPot();
        for (Block b : new Block[]{ICEBOX, POT}) {
            b.setCreativeTab(UndyingArcana.TAB);
            event.getRegistry().register(b);
        }
        GameRegistry.registerTileEntity(TileIcebox.class, new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "icebox"));
        GameRegistry.registerTileEntity(TilePot.class, new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "cooking_pot"));
    }

    public static void registerItems(List<Item> all) {
        for (Block b : new Block[]{ICEBOX, POT}) {
            Item ib = new net.minecraft.item.ItemBlock(b);
            ib.setRegistryName(b.getRegistryName());
            all.add(ib);
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<net.minecraft.item.crafting.IRecipe> event) {
        net.minecraft.util.ResourceLocation g = new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "food");
        ShapedOreRecipe ice = new ShapedOreRecipe(g, new ItemStack(ICEBOX), "PSP", "SIS", "PSP", 'P', "plankWood", 'S', Blocks.SNOW,
                'I', "ingotIron");
        ice.setRegistryName(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "fd_icebox"));
        event.getRegistry().register(ice);
        ShapedOreRecipe pot = new ShapedOreRecipe(g, new ItemStack(POT), " S ", "I I", "III", 'S', "stickWood", 'I', "ingotIron");
        pot.setRegistryName(new net.minecraft.util.ResourceLocation(UndyingArcana.MODID, "fd_cooking_pot"));
        event.getRegistry().register(pot);
    }

    private static void named(Block b, String name) {
        b.setRegistryName(UndyingArcana.MODID, name);
        b.setUnlocalizedName(UndyingArcana.MODID + "." + name);
    }

    // ------------------------------------------------------------------ icebox
    public static class BlockIcebox extends Block implements ITileEntityProvider {
        public BlockIcebox() {
            super(Material.WOOD);
            named(this, "icebox");
            setHardness(2.0F);
            setResistance(5.0F);
            setSoundType(net.minecraft.block.SoundType.WOOD);
        }

        @Override
        public TileEntity createNewTileEntity(World w, int meta) {
            return new TileIcebox();
        }

        @Override
        public boolean onBlockActivated(World w, BlockPos pos, IBlockState state, EntityPlayer p, EnumHand hand, EnumFacing side,
                float hx, float hy, float hz) {
            if (!w.isRemote) {
                TileEntity te = w.getTileEntity(pos);
                if (te instanceof TileIcebox) {
                    p.displayGUIChest((TileIcebox) te);
                }
            }
            return true;
        }

        @Override
        public void breakBlock(World w, BlockPos pos, IBlockState state) {
            TileEntity te = w.getTileEntity(pos);
            if (te instanceof TileIcebox) {
                InventoryHelper.dropInventoryItems(w, pos, (TileIcebox) te);
            }
            super.breakBlock(w, pos, state);
        }
    }

    public static class TileIcebox extends TileEntity implements IInventory, ITickable {
        private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

        @Override
        public void update() {
            if (world == null || world.isRemote || world.getTotalWorldTime() % 20 != 3) {
                return;
            }
            long now = world.getTotalWorldTime();
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty() || Food.life(s) <= 0) {
                    continue;
                }
                if (!s.hasTagCompound() || !s.getTagCompound().hasKey(Food.BB)) {
                    Food.stamp(s, now, Food.life(s));
                }
                // a quarter of the speed: give back 15 of every 20 ticks
                s.getTagCompound().setLong(Food.BB, s.getTagCompound().getLong(Food.BB) + 15);
            }
            markDirty();
        }

        @Override
        public void readFromNBT(NBTTagCompound t) {
            super.readFromNBT(t);
            items = NonNullList.withSize(27, ItemStack.EMPTY);
            ItemStackHelper.loadAllItems(t, items);
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound t) {
            super.writeToNBT(t);
            ItemStackHelper.saveAllItems(t, items);
            return t;
        }

        @Override
        public int getSizeInventory() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : items) {
                if (!s.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getStackInSlot(int i) {
            return items.get(i);
        }

        @Override
        public ItemStack decrStackSize(int i, int n) {
            ItemStack s = ItemStackHelper.getAndSplit(items, i, n);
            if (!s.isEmpty()) {
                markDirty();
            }
            return s;
        }

        @Override
        public ItemStack removeStackFromSlot(int i) {
            return ItemStackHelper.getAndRemove(items, i);
        }

        @Override
        public void setInventorySlotContents(int i, ItemStack s) {
            items.set(i, s);
            if (s.getCount() > getInventoryStackLimit()) {
                s.setCount(getInventoryStackLimit());
            }
            markDirty();
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public boolean isUsableByPlayer(EntityPlayer p) {
            return world.getTileEntity(pos) == this && p.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
        }

        @Override
        public void openInventory(EntityPlayer p) {
        }

        @Override
        public void closeInventory(EntityPlayer p) {
        }

        @Override
        public boolean isItemValidForSlot(int i, ItemStack s) {
            return true;
        }

        @Override
        public int getField(int id) {
            return 0;
        }

        @Override
        public void setField(int id, int v) {
        }

        @Override
        public int getFieldCount() {
            return 0;
        }

        @Override
        public void clear() {
            items.clear();
        }

        @Override
        public String getName() {
            return "Icebox";
        }

        @Override
        public boolean hasCustomName() {
            return true;
        }

        @Override
        public ITextComponent getDisplayName() {
            return new TextComponentString("Icebox");
        }
    }

    // ------------------------------------------------------------------ cooking pot
    private static final AxisAlignedBB POT_BOX = new AxisAlignedBB(0.125, 0, 0.125, 0.875, 0.625, 0.875);

    public static class BlockPot extends Block implements ITileEntityProvider {
        public BlockPot() {
            super(Material.IRON);
            named(this, "cooking_pot");
            setHardness(2.0F);
            setResistance(5.0F);
            setSoundType(net.minecraft.block.SoundType.METAL);
            setDefaultState(blockState.getBaseState().withProperty(FILLED, false));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, FILLED);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(FILLED, meta == 1);
        }

        @Override
        public int getMetaFromState(IBlockState s) {
            return s.getValue(FILLED) ? 1 : 0;
        }

        @Override
        public boolean isOpaqueCube(IBlockState s) {
            return false;
        }

        @Override
        public boolean isFullCube(IBlockState s) {
            return false;
        }

        @Override
        public BlockRenderLayer getBlockLayer() {
            return BlockRenderLayer.CUTOUT;
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos pos) {
            return POT_BOX;
        }

        @Override
        public TileEntity createNewTileEntity(World w, int meta) {
            return new TilePot();
        }

        @Override
        public boolean onBlockActivated(World w, BlockPos pos, IBlockState state, EntityPlayer p, EnumHand hand, EnumFacing side,
                float hx, float hy, float hz) {
            if (hand != EnumHand.MAIN_HAND) {
                return true;
            }
            if (!w.isRemote) {
                TileEntity te = w.getTileEntity(pos);
                if (te instanceof TilePot) {
                    ((TilePot) te).use(p);
                }
            }
            return true;
        }

        @Override
        public void breakBlock(World w, BlockPos pos, IBlockState state) {
            TileEntity te = w.getTileEntity(pos);
            if (te instanceof TilePot) {
                for (ItemStack s : ((TilePot) te).ingredients) {
                    InventoryHelper.spawnItemStack(w, pos.getX(), pos.getY(), pos.getZ(), s);
                }
            }
            super.breakBlock(w, pos, state);
        }
    }

    public static class TilePot extends TileEntity implements ITickable {
        final List<ItemStack> ingredients = new ArrayList<>();
        boolean water;
        int cook, servings;
        NBTTagCompound result;

        private void msg(EntityPlayer p, String s) {
            p.sendStatusMessage(new TextComponentString(s), true);
        }

        private void setWater(boolean on) {
            water = on;
            IBlockState s = world.getBlockState(pos);
            if (s.getBlock() == POT && s.getValue(FILLED) != on) {
                world.setBlockState(pos, s.withProperty(FILLED, on), 3);
            }
            markDirty();
        }

        boolean heated() {
            IBlockState b = world.getBlockState(pos.down());
            Block k = b.getBlock();
            return k == Blocks.FIRE || k == Blocks.LAVA || k == Blocks.FLOWING_LAVA || k == Blocks.MAGMA || k == Blocks.LIT_FURNACE;
        }

        void use(EntityPlayer p) {
            ItemStack h = p.getHeldItemMainhand();
            boolean creative = p.capabilities.isCreativeMode;
            if (result != null) {
                if (h.getItem() == Items.BOWL) {
                    if (!creative) {
                        h.shrink(1);
                    }
                    ItemStack stew = new ItemStack(Food.STEW);
                    stew.setTagCompound(result.copy());
                    if (!p.inventory.addItemStackToInventory(stew)) {
                        p.dropItem(stew, false);
                    }
                    world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 0.8F, 0.8F);
                    if (--servings <= 0) {
                        result = null;
                    }
                    markDirty();
                } else {
                    msg(p, "\u00a76" + result.getString("name") + " \u00a77is ready: take it with a bowl (" + servings + " left).");
                }
                return;
            }
            if (!water) {
                if (h.getItem() == Items.WATER_BUCKET) {
                    if (!creative) {
                        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    }
                } else if (SurvivalItems.isWaterBottle(h)) {
                    if (!creative) {
                        h.shrink(1);
                        p.inventory.addItemStackToInventory(new ItemStack(Items.GLASS_BOTTLE));
                    }
                } else if (h.getItem() instanceof SurvivalItems.ItemFlask && SurvivalItems.ItemFlask.sips(h) >= 2) {
                    SurvivalItems.ItemFlask.set(h, SurvivalItems.ItemFlask.sips(h) - 2, SurvivalItems.ItemFlask.quality(h));
                } else {
                    msg(p, "\u00a77Fill the pot with water first (bucket, bottle or 2 sips from a flask).");
                    return;
                }
                world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.8F, 1.0F);
                setWater(true);
                return;
            }
            if (h.getItem() instanceof ItemFood && h.getItem() != Food.STEW && h.getItem() != Food.SPOILED) {
                if (ingredients.size() >= 4) {
                    msg(p, "\u00a77The pot is full (4 ingredients).");
                    return;
                }
                ItemStack one = h.copy();
                one.setCount(1);
                ingredients.add(one);
                if (!creative) {
                    h.shrink(1);
                }
                cook = 0;
                world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 0.4F, 1.4F);
                markDirty();
                msg(p, "\u00a77Ingredients: " + ingredients.size() + "/4" + (ingredients.size() < 2 ? " \u00a78(at least 2)"
                        : heated() ? " \u00a76- cooking..." : " \u00a78- put fire, lava or magma under the pot"));
                return;
            }
            if (h.isEmpty() && p.isSneaking() && !ingredients.isEmpty()) {
                ItemStack back = ingredients.remove(ingredients.size() - 1);
                if (!p.inventory.addItemStackToInventory(back)) {
                    p.dropItem(back, false);
                }
                cook = 0;
                markDirty();
                return;
            }
            msg(p, "\u00a77Water + " + ingredients.size() + "/4 ingredients. " + (ingredients.size() < 2 ? "Add at least 2 foods."
                    : heated() ? "\u00a76Cooking " + cook * 100 / 200 + "%" : "Needs fire, lava or magma under it."));
        }

        @Override
        public void update() {
            if (world == null || world.isRemote || !water || result != null || ingredients.size() < 2) {
                return;
            }
            if (!heated()) {
                return;
            }
            cook++;
            if (cook % 10 == 0 && world instanceof WorldServer) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 1,
                        0.15, 0.02, 0.15, 0.01);
            }
            if (cook >= 200) {
                finish();
            }
        }

        private void finish() {
            int food = 0, mask = 0;
            float sat = 0;
            int[] per = new int[Food.GROUP.length];
            for (ItemStack s : ingredients) {
                ItemFood f = (ItemFood) s.getItem();
                food += f.getHealAmount(s);
                sat += f.getSaturationModifier(s);
                int g = Food.groups(s);
                mask |= g;
                for (int i = 0; i < per.length; i++) {
                    if ((g & 1 << i) != 0) {
                        per[i] += f.getHealAmount(s);
                    }
                }
            }
            int n = ingredients.size();
            servings = n >= 4 ? 2 : 1;
            int groups = Integer.bitCount(mask);
            NBTTagCompound r = new NBTTagCompound();
            r.setInteger("food", Math.min(20, Math.round(food * 1.25F / servings)));
            r.setFloat("sat", Math.min(1.2F, sat / n * 1.3F));
            r.setInteger("g", mask);
            r.setInteger("buff", groups >= 3 ? 2 : groups == 2 ? 1 : 0);
            int top = 0;
            for (int i = 1; i < per.length; i++) {
                if (per[i] > per[top]) {
                    top = i;
                }
            }
            String[] names = {"Meat Stew", "Porridge", "Vegetable Soup", "Fruit Compote", "Sweet Pudding"};
            r.setString("name", groups >= 3 ? "Hearty Stew" : groups == 0 ? "Thin Soup" : names[top]);
            result = r;
            ingredients.clear();
            cook = 0;
            setWater(false);
            world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.7F, 1.2F);
        }

        @Override
        public void readFromNBT(NBTTagCompound t) {
            super.readFromNBT(t);
            ingredients.clear();
            NBTTagList l = t.getTagList("in", 10);
            for (int i = 0; i < l.tagCount(); i++) {
                ingredients.add(new ItemStack(l.getCompoundTagAt(i)));
            }
            water = t.getBoolean("water");
            cook = t.getInteger("cook");
            servings = t.getInteger("servings");
            result = t.hasKey("result") ? t.getCompoundTag("result") : null;
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound t) {
            super.writeToNBT(t);
            NBTTagList l = new NBTTagList();
            for (ItemStack s : ingredients) {
                l.appendTag(s.writeToNBT(new NBTTagCompound()));
            }
            t.setTag("in", l);
            t.setBoolean("water", water);
            t.setInteger("cook", cook);
            t.setInteger("servings", servings);
            if (result != null) {
                t.setTag("result", result);
            }
            return t;
        }

        @Override
        public boolean shouldRefresh(World w, BlockPos pos, IBlockState oldState, IBlockState newState) {
            return oldState.getBlock() != newState.getBlock();
        }
    }

    // ------------------------------------------------------------------ stew
    public static class ItemStew extends ItemFood {
        public ItemStew() {
            super(6, 0.6F, false);
        }

        @Override
        public int getHealAmount(ItemStack s) {
            return s.hasTagCompound() ? s.getTagCompound().getInteger("food") : 6;
        }

        @Override
        public float getSaturationModifier(ItemStack s) {
            return s.hasTagCompound() ? s.getTagCompound().getFloat("sat") : 0.6F;
        }

        @Override
        public String getItemStackDisplayName(ItemStack s) {
            return s.hasTagCompound() && !s.getTagCompound().getString("name").isEmpty() ? s.getTagCompound().getString("name")
                    : super.getItemStackDisplayName(s);
        }

        @Override
        protected void onFoodEaten(ItemStack s, World w, EntityPlayer p) {
            if (w.isRemote || !s.hasTagCompound()) {
                return;
            }
            int buff = s.getTagCompound().getInteger("buff");
            if (buff >= 1) {
                p.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, buff >= 2 ? 2400 : 1200, 0));
            }
            if (buff >= 2) {
                p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 300, 0));
            }
            Survival.drink(p, 2F, 1F, Survival.CLEAN);
        }

        @Override
        public ItemStack onItemUseFinish(ItemStack s, World w, net.minecraft.entity.EntityLivingBase e) {
            super.onItemUseFinish(s, w, e);
            if (e instanceof EntityPlayer && ((EntityPlayer) e).capabilities.isCreativeMode) {
                return s;
            }
            return new ItemStack(Items.BOWL);
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void addInformation(ItemStack s, World w, List<String> tip, net.minecraft.client.util.ITooltipFlag flag) {
            if (!s.hasTagCompound()) {
                return;
            }
            int buff = s.getTagCompound().getInteger("buff");
            tip.add("\u00a77Food " + getHealAmount(s) + ", +2 thirst, nutrition x1.5");
            if (buff >= 2) {
                tip.add("\u00a7aRegeneration 15s, Absorption 2 min");
            } else if (buff == 1) {
                tip.add("\u00a7aAbsorption 1 min");
            }
        }
    }
}
