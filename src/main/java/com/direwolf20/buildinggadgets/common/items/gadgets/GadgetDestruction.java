package com.direwolf20.buildinggadgets.common.items.gadgets;

import com.direwolf20.buildinggadgets.api.building.IPositionPlacementSequence;
import com.direwolf20.buildinggadgets.api.building.Region;
import com.direwolf20.buildinggadgets.client.gui.GuiMod;
import com.direwolf20.buildinggadgets.common.blocks.ConstructionBlockTileEntity;
import com.direwolf20.buildinggadgets.common.config.Config;
import com.direwolf20.buildinggadgets.common.entities.BlockBuildEntity;
import com.direwolf20.buildinggadgets.common.registry.objects.BGBlocks;
import com.direwolf20.buildinggadgets.common.util.CapabilityUtil.EnergyUtil;
import com.direwolf20.buildinggadgets.common.util.GadgetUtils;
import com.direwolf20.buildinggadgets.common.util.blocks.RegionSnapshot;
import com.direwolf20.buildinggadgets.common.util.exceptions.PaletteOverflowException;
import com.direwolf20.buildinggadgets.common.util.helpers.NBTHelper;
import com.direwolf20.buildinggadgets.common.util.helpers.NBTHelper.ITagSerializable;
import com.direwolf20.buildinggadgets.common.util.helpers.VectorHelper;
import com.direwolf20.buildinggadgets.common.util.lang.Styles;
import com.direwolf20.buildinggadgets.common.util.lang.TooltipTranslation;
import com.direwolf20.buildinggadgets.common.util.ref.NBTKeys;
import com.direwolf20.buildinggadgets.common.world.WorldSave;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class GadgetDestruction extends GadgetSwapping {

    public static class SetBackedPlacementSequence implements IPositionPlacementSequence, ITagSerializable<SetBackedPlacementSequence> {

        public static final String POS_SET = "Pos";

        public static SetBackedPlacementSequence deserializeFrom(NBTTagCompound tag) {
            HashSet<BlockPos> set = new HashSet<>();
            NBTHelper.deserializeSet(tag.getList(POS_SET, Constants.NBT.TAG_COMPOUND), set, nbt -> NBTUtil.readBlockPos((NBTTagCompound) nbt));

            Region boundingBox = Region.deserializeFrom(tag);

            return new SetBackedPlacementSequence(set, boundingBox);
        }

        private final Region boundingBox;
        private final HashSet<BlockPos> internalSet;

        public SetBackedPlacementSequence(HashSet<BlockPos> internalSet, Region boundingBox) {
            this.internalSet = internalSet;
            this.boundingBox = boundingBox;
        }

        @Nonnull
        @Override
        public Iterator<BlockPos> iterator() {
            return internalSet.iterator();
        }

        @Nonnull
        @Override
        public Region getBoundingBox() {
            return boundingBox;
        }

        /**
         * @deprecated Use {@link #contains(int, int, int)} instead.
         */
        @Deprecated
        @Override
        public boolean mayContain(int x, int y, int z) {
            return contains(new BlockPos(x, y, z));
        }

        public boolean contains(int x, int y, int z) {
            return contains(new BlockPos(x, y, z));
        }

        public boolean contains(BlockPos pos) {
            return internalSet.contains(pos);
        }

        /**
         * Warning: this method uses the copy constructor of {@link HashSet}, therefore it does not guarantee it will
         * return the same type of set.
         */
        @Deprecated
        @Nonnull
        @Override
        public IPositionPlacementSequence copy() {
            return new SetBackedPlacementSequence(new HashSet<>(internalSet), boundingBox);
        }

        public HashSet<BlockPos> getInternalSet() {
            return internalSet;
        }

        @Override
        public SetBackedPlacementSequence deserialize(NBTTagCompound tag) {
            return deserializeFrom(tag);
        }

        @Override
        public NBTTagCompound serializeTo(NBTTagCompound tag) {
            tag.setTag(POS_SET, NBTHelper.serializeSet(internalSet, NBTUtil::writeBlockPos));
            boundingBox.serializeTo(tag);
            return tag;
        }

    }

    public static void restoreSnapshotWithBuilder(World world, RegionSnapshot snapshot) {
        Set<BlockPos> pastePositions = snapshot.getTileData().stream()
                .map(Pair::getLeft)
                .collect(Collectors.toSet());
        int index = 0;
        for (BlockPos pos : snapshot.getPositions()) {
            // TODO remove spawnBy field in BlockBuildEntity
            snapshot.getBlockStates().get(index).ifPresent(state -> world.spawnEntity(new BlockBuildEntity(world, pos, null, state, BlockBuildEntity.Mode.PLACE, pastePositions.contains(pos))));
            index++;
        }
    }

    public GadgetDestruction(Properties builder) {
        super(builder);
    }

    @Override
    public int getEnergyMax() {
        return Config.GADGETS.GADGET_DESTRUCTION.maxEnergy.get();
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return Config.GADGETS.poweredByFE.get() ? 0 : Config.GADGETS.GADGET_DESTRUCTION.durability.get();
    }

    @Override
    public int getEnergyCost(ItemStack tool) {
        return Config.GADGETS.GADGET_DESTRUCTION.energyCost.get() * getCostMultiplier(tool);
    }

    @Override
    public int getDamageCost(ItemStack tool) {
        return Config.GADGETS.GADGET_DESTRUCTION.durabilityCost.get() * getCostMultiplier(tool);
    }

    private int getCostMultiplier(ItemStack tool) {
        return (int) (Config.GADGETS.poweredByFE.get() && !getFuzzy(tool) ? Config.GADGETS.GADGET_DESTRUCTION.nonFuzzyMultiplier.get() : 1);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add(TooltipTranslation.GADGET_DESTROYWARNING
                .componentTranslation()
                .setStyle(Styles.RED));
        tooltip.add(TooltipTranslation.GADGET_DESTROYSHOWOVERLAY
                .componentTranslation(String.valueOf(getOverlay(stack)))
                .setStyle(Styles.AQUA));
        tooltip.add(TooltipTranslation.GADGET_BUILDING_PLACE_ATOP
                .componentTranslation(String.valueOf(getConnectedArea(stack)))
                .setStyle(Styles.YELLOW));
        if (Config.isServerConfigLoaded() && Config.GADGETS.GADGET_DESTRUCTION.nonFuzzyEnabled.get())
            tooltip.add(TooltipTranslation.GADGET_FUZZY
                    .componentTranslation(String.valueOf(getFuzzy(stack)))
                    .setStyle(Styles.GOLD));

        addInformationRayTraceFluid(tooltip, stack);
        addEnergyInformation(tooltip, stack);
    }

    public static String getUUID(ItemStack stack) {
        NBTTagCompound tag = NBTHelper.getOrNewTag(stack);
        String uuid = tag.getString(NBTKeys.GADGET_UUID);
        if (uuid.isEmpty()) {
            UUID uid = UUID.randomUUID();
            tag.setString(NBTKeys.GADGET_UUID, uid.toString());
            stack.setTag(tag);
            uuid = uid.toString();
        }
        return uuid;
    }

    public static void setAnchor(ItemStack stack, BlockPos pos) {
        GadgetUtils.writePOSToNBT(stack, pos, NBTKeys.GADGET_ANCHOR);
    }

    public static BlockPos getAnchor(ItemStack stack) {
        return GadgetUtils.getPOSFromNBT(stack, NBTKeys.GADGET_ANCHOR);
    }

    public static void setAnchorSide(ItemStack stack, EnumFacing side) {
        NBTTagCompound tagCompound = stack.getTag();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        if (side == null) {
            if (tagCompound.getTag(NBTKeys.GADGET_ANCHOR_SIDE) != null) {
                tagCompound.removeTag(NBTKeys.GADGET_ANCHOR_SIDE);
                stack.setTag(tagCompound);
            }
            return;
        }
        tagCompound.setString(NBTKeys.GADGET_ANCHOR_SIDE, side.getName());
        stack.setTag(tagCompound);
    }

    public static EnumFacing getAnchorSide(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTag();
        if (tagCompound == null) {
            return null;
        }
        String facing = tagCompound.getString(NBTKeys.GADGET_ANCHOR_SIDE);
        if (facing.isEmpty()) return null;
        return EnumFacing.byName(facing);
    }

    public static void setToolValue(ItemStack stack, int value, String valueName) {
        //Store the tool's range in NBT as an Integer
        NBTTagCompound tagCompound = stack.getTag();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        tagCompound.setInt(valueName, value);
        stack.setTag(tagCompound);
    }

    public static int getToolValue(ItemStack stack, String valueName) {
        //Store the tool's range in NBT as an Integer
        NBTTagCompound tagCompound = stack.getTag();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        return tagCompound.getInt(valueName);
    }

    public static boolean getOverlay(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTag();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            tagCompound.setBoolean(NBTKeys.GADGET_OVERLAY, true);
            tagCompound.setBoolean(NBTKeys.GADGET_FUZZY, true);
            stack.setTag(tagCompound);
            return true;
        }
        if (tagCompound.hasKey(NBTKeys.GADGET_OVERLAY)) {
            return tagCompound.getBoolean(NBTKeys.GADGET_OVERLAY);
        }
        tagCompound.setBoolean(NBTKeys.GADGET_OVERLAY, true);
        stack.setTag(tagCompound);
        return true;
    }

    public static void setOverlay(ItemStack stack, boolean showOverlay) {
        NBTTagCompound tagCompound = stack.getTag();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
        }
        tagCompound.setBoolean(NBTKeys.GADGET_OVERLAY, showOverlay);
        stack.setTag(tagCompound);
    }

    public static void switchOverlay(EntityPlayer player, ItemStack stack) {
        boolean overlay = !getOverlay(stack);
        setOverlay(stack, overlay);
        player.sendStatusMessage(TooltipTranslation.GADGET_DESTROYSHOWOVERLAY
                .componentTranslation(String.valueOf(overlay)).setStyle(Styles.AQUA), true);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        if (!world.isRemote) {
            if (!player.isSneaking()) {
                RayTraceResult lookingAt = VectorHelper.getLookingAt(player, stack);
                if (lookingAt == null && getAnchor(stack) == null) { //If we aren't looking at anything, exit
                    return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
                }
                BlockPos startBlock = (getAnchor(stack) == null) ? lookingAt.getBlockPos() : getAnchor(stack);
                EnumFacing sideHit = (getAnchorSide(stack) == null) ? lookingAt.sideHit : getAnchorSide(stack);
                clearArea(world, startBlock, sideHit, player, stack);
                if (getAnchor(stack) != null) {
                    setAnchor(stack, null);
                    setAnchorSide(stack, null);
                    player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorremove").getUnformattedComponentText()), true);
                }
            } else {
                //TODO Remove debug code
                EnergyUtil.getCap(stack).ifPresent(energy -> energy.receiveEnergy(105000, false));
            }
        } else {
            if (player.isSneaking()) {
                GuiMod.DESTRUCTION.openScreen(player);
                return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
            }
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    public static void anchorBlocks(EntityPlayer player, ItemStack stack) {
        BlockPos currentAnchor = getAnchor(stack);
        if (currentAnchor == null) {
            RayTraceResult lookingAt = VectorHelper.getLookingAt(player, stack);
            if (lookingAt == null) {
                return;
            }
            currentAnchor = lookingAt.getBlockPos();
            setAnchor(stack, currentAnchor);
            setAnchorSide(stack, lookingAt.sideHit);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorrender").getUnformattedComponentText()), true);
        } else {
            setAnchor(stack, null);
            setAnchorSide(stack, null);
            player.sendStatusMessage(new TextComponentString(TextFormatting.AQUA + new TextComponentTranslation("message.gadget.anchorremove").getUnformattedComponentText()), true);
        }
    }

    public static IPositionPlacementSequence getClearingPositions(World world, BlockPos pos, EnumFacing incomingSide, EntityPlayer player, ItemStack stack) {
        if (GadgetGeneric.getConnectedArea(stack)) {
            Region boundary = getClearingRegion(pos, incomingSide, player, stack);
            HashSet<BlockPos> voidPositions = new HashSet<>();
            int depth = getToolValue(stack, NBTKeys.GADGET_VALUE_DEPTH);
            if (depth == 0)
                return new SetBackedPlacementSequence(voidPositions, boundary);

            BlockPos startPos = (getAnchor(stack) == null) ? pos : getAnchor(stack);
            IBlockState stateTarget = !Config.GADGETS.GADGET_DESTRUCTION.nonFuzzyEnabled.get() || GadgetGeneric.getFuzzy(stack) ? null : world.getBlockState(pos);
            addConnectedCoordinates(world, player, startPos, stateTarget, voidPositions,
                    boundary);

            return new SetBackedPlacementSequence(voidPositions, boundary);
        } else {
            return getClearingRegion(pos, incomingSide, player, stack);
        }
    }

    public static SortedSet<BlockPos> getClearingPositionsSet(World world, BlockPos pos, EnumFacing incomingSide, EntityPlayer player, ItemStack stack) {
        return getClearingPositions(world, pos, incomingSide, player, stack).stream()
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator
                        .comparingInt(Vec3i::getX)
                        .thenComparingInt(Vec3i::getY)
                        .thenComparingInt(Vec3i::getZ))));
    }

    private static void addConnectedCoordinates(World world, EntityPlayer player, BlockPos pos, IBlockState state, Set<BlockPos> coords, Region boundary) {
        if (coords.contains(pos) || !boundary.contains(pos))
            return;
        if (!isValidBlock(world, pos, player, state))
            return;

        coords.add(pos);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    addConnectedCoordinates(world, player, pos.add(x, y, z), state, coords, boundary);
                }
            }
        }
    }

    public static boolean isValidBlock(World world, BlockPos voidPos, EntityPlayer player, @Nullable IBlockState stateTarget) {
        IBlockState currentBlock = world.getBlockState(voidPos);
        if (stateTarget != null && currentBlock != stateTarget) return false;
        TileEntity te = world.getTileEntity(voidPos);
        if (currentBlock.getBlock().isAir(currentBlock, world, voidPos)) return false;
        //if (currentBlock.getBlock().getMaterial(currentBlock).isLiquid()) return false;
        if (currentBlock.equals(BGBlocks.effectBlock.getDefaultState())) return false;
        if ((te != null) && !(te instanceof ConstructionBlockTileEntity)) return false;
        if (currentBlock.getBlockHardness(world, voidPos) < 0) return false;

        ItemStack tool = getGadget(player);
        if (tool.isEmpty()) return false;

        if (!player.isAllowEdit()) {
            return false;
        }
        if (!world.isBlockModifiable(player, voidPos)) {
            return false;
        }
        if (!world.isRemote) {
            BlockSnapshot blockSnapshot = BlockSnapshot.getBlockSnapshot(world, voidPos);
            if (ForgeEventFactory.onBlockPlace(player, blockSnapshot, EnumFacing.UP)) {
                return false;
            }
            BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(world, voidPos, currentBlock, player);
            if (MinecraftForge.EVENT_BUS.post(e)) {
                return false;
            }
        }
        return true;
    }

    public static Region getClearingRegion(BlockPos pos, EnumFacing side, EntityPlayer player, ItemStack stack) {
        EnumFacing depth = side.getOpposite();
        boolean vertical = side.getAxis().isVertical();
        EnumFacing up = vertical ? player.getHorizontalFacing() : EnumFacing.UP;
        EnumFacing down = up.getOpposite();
        EnumFacing left = vertical ? up.rotateY() : side.rotateYCCW();
        EnumFacing right = left.getOpposite();

        BlockPos first = pos.offset(left, getToolValue(stack, NBTKeys.GADGET_VALUE_LEFT))
                .offset(up, getToolValue(stack, NBTKeys.GADGET_VALUE_UP));
        BlockPos second = pos.offset(right, getToolValue(stack, NBTKeys.GADGET_VALUE_RIGHT))
                .offset(down, getToolValue(stack, NBTKeys.GADGET_VALUE_DOWN))
                .offset(depth, getToolValue(stack, NBTKeys.GADGET_VALUE_DEPTH) - 1);
        // The number are not necessarily sorted min and max, but the constructor will do it for us
        return new Region(
                first.getX(),
                first.getY(),
                first.getZ(),
                second.getX(),
                second.getY(),
                second.getZ());
    }

    public void clearArea(World world, BlockPos pos, EnumFacing side, EntityPlayer player, ItemStack stack) {
        IPositionPlacementSequence positions = getClearingPositions(world, pos, side, player, stack);
        RegionSnapshot snapshot;
        try {
            snapshot = RegionSnapshot.select(world, positions)
                    .excludeAir()
                    .checkTiles((p, state, tile) -> state.getBlock() == BGBlocks.constructionBlock && tile instanceof ConstructionBlockTileEntity)
                    .build();
        } catch (PaletteOverflowException e) {
            player.sendMessage(new TextComponentTranslation(TooltipTranslation.GADGET_PALETTE_OVERFLOW.getTranslationKey()));
            return;
        }
        positions.forEach(voidPos -> destroyBlock(world, voidPos, player));

        WorldSave worldSave = WorldSave.getWorldSaveDestruction(world);
        worldSave.addToMap(getUUID(stack), snapshot.serialize());
    }

    public static void undo(EntityPlayer player, ItemStack stack) {
        World world = player.world;
        WorldSave worldSave = WorldSave.getWorldSaveDestruction(world);

        NBTTagCompound serializedSnapshot = worldSave.getCompoundFromUUID(getUUID(stack));
        if (serializedSnapshot.isEmpty())
            return;

        RegionSnapshot snapshot = RegionSnapshot.deserialize(serializedSnapshot);
        restoreSnapshotWithBuilder(world, snapshot);
        worldSave.addToMap(getUUID(stack), new NBTTagCompound());
        worldSave.markDirty();
    }

    private boolean destroyBlock(World world, BlockPos voidPos, EntityPlayer player) {
        ItemStack tool = getGadget(player);
        if (tool.isEmpty())
            return false;

        if (!this.canUse(tool, player))
            return false;

        if (world.isAirBlock(voidPos))
            return false;

        this.applyDamage(tool, player);

        world.spawnEntity(new BlockBuildEntity(world, voidPos, player, world.getBlockState(voidPos), BlockBuildEntity.Mode.REMOVE, false));
        return true;
    }

    public static ItemStack getGadget(EntityPlayer player) {
        ItemStack stack = GadgetGeneric.getGadget(player);
        if (!(stack.getItem() instanceof GadgetDestruction))
            return ItemStack.EMPTY;

        return stack;
    }

}
