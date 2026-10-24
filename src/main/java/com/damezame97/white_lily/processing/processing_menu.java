package com.damezame97.white_lily.processing;

import com.damezame97.white_lily.block.medical_blocks;
import net.minecraft.core.BlockPos;
import com.damezame97.white_lily.recipebook.transfer_reason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** 加工台菜单仅转移物品和同步显示数据；加工始终由服务端方块实体执行。 */
public final class processing_menu extends AbstractContainerMenu
{
    public static final int MACHINE_SLOTS = 8;
    private static final int PLAYER_MAIN_END = MACHINE_SLOTS + 27;
    private static final int PLAYER_END = PLAYER_MAIN_END + 9;
    private static final int DATA_COUNT = 5;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final Level level;
    private final BlockPos position;
    private long lastRegistration=Long.MIN_VALUE;
    private long lastTransfer=Long.MIN_VALUE;
    @Nullable private final processing_block_entity table;
    private final Player viewer;

    public processing_menu(int id, Inventory inventory, FriendlyByteBuf buffer)
    {
        this(id, inventory, buffer.readBlockPos());
    }

    private processing_menu(int id, Inventory inventory, BlockPos pos)
    {
        this(id, inventory, pos, inventory.player.level().getBlockEntity(pos) instanceof processing_block_entity table
                ? table : null);
    }

    public processing_menu(int id, Inventory inventory, processing_block_entity table)
    {
        this(id, inventory, table.getBlockPos(), table);
    }

    private processing_menu(int id, Inventory inventory, BlockPos pos, @Nullable processing_block_entity table)
    {
        super(processing_registry.TABLE_MENU.get(), id);
        level = inventory.player.level();
        viewer = inventory.player;
        this.table = table;
        position = pos;
        access = ContainerLevelAccess.create(level, pos);
        // 打开包可能先于客户端区块实体到达；槽位与数据随后由原版菜单同步填充。
        ItemStackHandler handler = table == null ? new ItemStackHandler(MACHINE_SLOTS) : table.getInventory();
        data = table == null || level.isClientSide ? new SimpleContainerData(DATA_COUNT) : table.getData();
        checkContainerDataCount(data, DATA_COUNT);

        addSlot(new MachineSlot(handler, table, 0, 12, 40)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return processing_recipe.isSupportedTool(stack, level) && super.mayPlace(stack);
            }

            @Override
            public int getMaxStackSize()
            {
                return 1;
            }
        });
        addSlot(new MachineSlot(handler, table, 1, 60, 30));
        addSlot(new MachineSlot(handler, table, 2, 78, 30));
        addSlot(new MachineSlot(handler, table, 3, 96, 30));
        addSlot(new MachineSlot(handler, table, 4, 78, 64)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return processing_recipe.isAllowedContainer(stack, level) && super.mayPlace(stack);
            }
        });
        addOutputSlot(handler, table, 5, 172, 30);
        addOutputSlot(handler, table, 6, 158, 64);
        addOutputSlot(handler, table, 7, 194, 64);

        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new Slot(inventory, column + row * 9 + 9, 34 + column * 18, 130 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(inventory, column, 34 + column * 18, 188));
        }
        addDataSlots(data);
        // The browser needs the machine's exact capability set while this menu session lives.
        if (!level.isClientSide && table != null && viewer instanceof ServerPlayer server)
            processing_network.send(server, this);
    }

    @Nullable public processing_block_entity table() { return table; }
    public BlockPos machinePos() { return position; }
    /** Server-side exact capability IDs: base recipes plus valid recipes registered on this machine. */
    public Set<ResourceLocation> machineRecipes() { return table == null ? Set.of() : table.contextRecipes(); }

    private void addOutputSlot(ItemStackHandler handler, @Nullable processing_block_entity table, int index, int x, int y)
    {
        addSlot(new MachineSlot(handler, table, index, x, y)
        {
            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return false;
            }
        });
    }

    private class MachineSlot extends SlotItemHandler
    {
        @Nullable private final processing_block_entity table;

        MachineSlot(ItemStackHandler handler, @Nullable processing_block_entity table, int index, int x, int y)
        {
            super(handler, index, x, y);
            this.table = table;
        }

        @Override
        public int getMaxStackSize(ItemStack stack)
        {
            // Forge 默认实现会临时清空真实库存来模拟容量；本机不需要这种有副作用的查询。
            return Math.min(getMaxStackSize(), stack.getMaxStackSize());
        }

        @Override
        public void setChanged()
        {
            super.setChanged();
            // 原版 Shift 会原地修改堆叠；补走槽回调，必要原料被取走时立即清进度。
            if (table != null) table.getInventory().setStackInSlot(getSlotIndex(), getItem());
        }

        @Override public boolean mayPlace(ItemStack stack)
        {
            return (level.isClientSide || table != null && table.canUse(viewer)) && super.mayPlace(stack);
        }

        @Override public boolean mayPickup(Player player)
        {
            if (level.isClientSide) return true;
            return table != null && table.canUse(player);
        }

        @Override public void onTake(Player player, ItemStack stack)
        {
            super.onTake(player, stack);
        }
    }

    @Override public void clicked(int slot, int button, ClickType action, Player player)
    {
        if (!level.isClientSide && (player != viewer || player.containerMenu != this || !stillValid(player)
                || slot >= 0 && slot < MACHINE_SLOTS && !getSlot(slot).mayPickup(player))) return;
        super.clicked(slot, button, action, player);
    }

    @Override
    public boolean stillValid(Player player)
    {
        // 同位置重放相同方块不能让旧菜单重新获得访问已移除库存的权限。
        if (!level.isClientSide && (table == null || table.isRemoved() || table.getLevel() != level
                || level.getBlockEntity(table.getBlockPos()) != table)) return false;
        return stillValid(access, player, medical_blocks.SIMPLE_PROCESSING_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        if (!level.isClientSide && (player != viewer || player.containerMenu != this || !stillValid(player))) return ItemStack.EMPTY;
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS)
        {
            moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_END, true);
        }
        else
        {
            if (processing_recipe.isSupportedTool(stack, level))
            {
                moveItemStackTo(stack, 0, 1, false);
            }
            else if (processing_recipe.isAllowedContainer(stack, level))
            {
                moveItemStackTo(stack, 4, 5, false);
            }
            else
            {
                moveItemStackTo(stack, 1, 4, false);
            }
            // 目标槽满时，剩余部分仍可在玩家主背包与快捷栏之间正常移动。
            if (!stack.isEmpty())
            {
                if (index < PLAYER_MAIN_END) moveItemStackTo(stack, PLAYER_MAIN_END, PLAYER_END, false);
                else moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_MAIN_END, false);
            }
        }

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    /** Vanilla menu-button packet carries only container ID + button ID; no remote position/list. */
    @Override public boolean clickMenuButton(Player player,int button)
    {
        if(button!=0 || level.isClientSide || table==null || player.containerMenu!=this || !stillValid(player))return false;
        long tick=level.getGameTime();if(lastRegistration!=Long.MIN_VALUE && tick>=lastRegistration && tick-lastRegistration<10)return false;
        if (!table.canUse(player)) return false;
        lastRegistration=tick;int added=table.registerLearned(player);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.white_lily.processing."+(added==0?"registered_none":"registered_added"),added),false);
        broadcastChanges();return true;
    }

    public boolean authorizeRecipeTransfer(ServerPlayer player, ResourceLocation id)
    {
        return id != null && player == viewer && player.containerMenu == this && !level.isClientSide && stillValid(player)
                && table != null && level.getRecipeManager().byKey(id).orElse(null) instanceof processing_recipe recipe
                && table.canProcess(recipe) && table.canUse(player);
    }

    /** Server-authoritative, atomic transfer: recover old inputs, then allocate real inventory stacks. */
    public boolean fillRecipe(ServerPlayer player, ResourceLocation id, boolean maximum)
    {
        return transferRecipe(player,id,maximum,true)==transfer_reason.OK;
    }

    /** The same planner supports client prediction and server validation; preview never mutates slots. */
    public transfer_reason previewRecipe(Player player,ResourceLocation id)
    { return transferRecipe(player,id,false,false); }

    public transfer_reason transferRecipe(Player player, ResourceLocation id, boolean maximum, boolean commit)
    {
        if(player.containerMenu!=this || !player.isAlive() || !stillValid(player))return transfer_reason.MENU_INVALID;
        if(!(level.getRecipeManager().byKey(id).orElse(null) instanceof processing_recipe recipe))return transfer_reason.DATA_CHANGED;
        if(!level.isClientSide && (table==null || !table.canUse(player)))return transfer_reason.MENU_INVALID;
        if(!level.isClientSide && !table.canProcess(recipe))return transfer_reason.UNREGISTERED;
        long tick = level.getGameTime();
        if (commit && lastTransfer != Long.MIN_VALUE && tick >= lastTransfer && tick - lastTransfer < 10) return transfer_reason.COOLDOWN;
        if(commit)lastTransfer = tick;
        List<ItemStack> recovered = new ArrayList<>();
        for (int index = 0; index < 36; index++) recovered.add(player.getInventory().getItem(index).copy());
        for (int index = 0; index < processing_block_entity.RESULT; index++)
            if (!returnStack(recovered, getSlot(index).getItem())) return transfer_reason.INVENTORY_FULL;

        int rawCount = recipe.materials().stream().mapToInt(processing_recipe.Material::count).sum();
        int batches = maximum ? Math.min(64, 192 / rawCount) : 1;
        if (recipe.container() != null) batches = Math.min(batches, 64 / recipe.container().count());
        List<ItemStack> stock = groups(recovered);
        if(stock.stream().noneMatch(tool->recipe.tool().test(tool)&&processing_recipe.isSupportedTool(tool.copyWithCount(1),level)))return transfer_reason.MISSING_TOOL;
        if(recipe.container()!=null && stock.stream().filter(stack->recipe.container().ingredient().test(stack)).mapToInt(ItemStack::getCount).sum()<recipe.container().count())return transfer_reason.MISSING_CONTAINER;
        for(var material:recipe.materials())if(stock.stream().filter(stack->material.ingredient().test(stack)).mapToInt(ItemStack::getCount).sum()<material.count())return transfer_reason.MISSING_MATERIALS;
        List<Integer> availableSlots = new ArrayList<>();
        for (ItemStack stack : stock)
            if (recipe.materials().stream().anyMatch(material -> material.ingredient().test(stack)))
            {
                int limit = Math.min(64, stack.getMaxStackSize()), count = stack.getCount();
                for (int slot = 0; slot < 3 && count > 0; slot++)
                { int take = Math.min(count, limit); availableSlots.add(take); count -= take; }
            }
        availableSlots.sort(java.util.Comparator.reverseOrder());
        batches = Math.min(batches, availableSlots.stream().limit(3).mapToInt(Integer::intValue).sum() / rawCount);
        for (processing_recipe.Material material : recipe.materials())
            batches = Math.min(batches, stock.stream().filter(stack -> material.ingredient().test(stack)).mapToInt(ItemStack::getCount).sum() / material.count());
        for (int batch = batches; batch >= 1; batch--)
            for (ItemStack tool : stock)
            {
                if (!recipe.tool().test(tool) || !processing_recipe.isSupportedTool(tool.copyWithCount(1), level)) continue;
                List<ItemStack> afterTool = copies(recovered);
                removeStack(afterTool, tool, 1);
                List<ItemStack> containers = recipe.container() == null ? List.of(ItemStack.EMPTY) : groups(afterTool);
                for (ItemStack container : containers)
                {
                    int containerCount = recipe.container() == null ? 0 : recipe.container().count() * batch;
                    if (containerCount > 0 && (!recipe.container().ingredient().test(container) || container.getCount() < containerCount)) continue;
                    List<ItemStack> remaining = copies(afterTool);
                    if (containerCount > 0) removeStack(remaining, container, containerCount);
                    List<ItemStack> materials = allocateTransfer(recipe, remaining, batch);
                    if (materials == null) continue;
                    for (ItemStack material : materials) removeStack(remaining, material, material.getCount());
                    if(!commit)return transfer_reason.OK;
                    if (!stillValid(player) || !table.canUse(player) || !table.canProcess(recipe)) return transfer_reason.MENU_INVALID;
                    for (int index = 0; index < 36; index++) player.getInventory().setItem(index, remaining.get(index));
                    table.getInventory().setStackInSlot(0, tool.copyWithCount(1));
                    for (int index = 0; index < 3; index++) table.getInventory().setStackInSlot(index + 1,
                            index < materials.size() ? materials.get(index) : ItemStack.EMPTY);
                    table.getInventory().setStackInSlot(4, containerCount > 0 ? container.copyWithCount(containerCount) : ItemStack.EMPTY);
                    player.getInventory().setChanged();
                    broadcastChanges();
                    return transfer_reason.OK;
                }
            }
        return transfer_reason.ALLOCATION;
    }

    private static List<ItemStack> copies(List<ItemStack> stacks)
    { return new ArrayList<>(stacks.stream().map(ItemStack::copy).toList()); }

    private static List<ItemStack> groups(List<ItemStack> inventory)
    {
        List<ItemStack> groups = new ArrayList<>();
        for (ItemStack stack : inventory)
        {
            if (stack.isEmpty()) continue;
            ItemStack same = groups.stream().filter(other -> ItemStack.isSameItemSameTags(other, stack)).findFirst().orElse(null);
            if (same == null) groups.add(stack.copy()); else same.grow(stack.getCount());
        }
        return groups;
    }

    private static void removeStack(List<ItemStack> inventory, ItemStack example, int count)
    {
        for (ItemStack stack : inventory)
            if (count > 0 && ItemStack.isSameItemSameTags(stack, example))
            { int take = Math.min(count, stack.getCount()); stack.shrink(take); count -= take; }
        if (count != 0) throw new IllegalStateException("Validated processing transfer lost its inventory allocation");
    }

    private static boolean returnStack(List<ItemStack> inventory, ItemStack value)
    {
        ItemStack remaining = value.copy();
        for (ItemStack stack : inventory)
            if (!remaining.isEmpty() && !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, remaining))
            { int add = Math.min(remaining.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount())); stack.grow(add); remaining.shrink(add); }
        for (int index = 0; index < inventory.size() && !remaining.isEmpty(); index++)
            if (inventory.get(index).isEmpty())
            { int add = Math.min(remaining.getCount(), remaining.getMaxStackSize()); inventory.set(index, remaining.copyWithCount(add)); remaining.shrink(add); }
        return remaining.isEmpty();
    }

    @Nullable private static List<ItemStack> allocateTransfer(processing_recipe recipe, List<ItemStack> inventory, int batches)
    {
        List<ItemStack> candidates = groups(inventory).stream().filter(stack -> recipe.materials().stream()
                .anyMatch(material -> material.ingredient().test(stack))).toList();
        for (int first = 0; first < candidates.size(); first++)
            for (int second = first; second < candidates.size(); second++)
                for (int third = second; third < candidates.size(); third++)
                {
                    int[] picks = {first, second, third};
                    int[] capacityByGroup = new int[candidates.size()];
                    for (int pick : picks) capacityByGroup[pick] += Math.min(64, candidates.get(pick).getMaxStackSize());
                    List<Integer> selected = new ArrayList<>();
                    for (int group = 0; group < candidates.size(); group++) if (capacityByGroup[group] > 0) selected.add(group);
                    int sink = selected.size() + recipe.materials().size() + 1;
                    int[][] flow = new int[sink + 1][sink + 1];
                    int needed = 0;
                    for (int group = 0; group < selected.size(); group++)
                    {
                        ItemStack stack = candidates.get(selected.get(group));
                        flow[0][group + 1] = Math.min(stack.getCount(), capacityByGroup[selected.get(group)]);
                        for (int ingredient = 0; ingredient < recipe.materials().size(); ingredient++)
                            if (recipe.materials().get(ingredient).ingredient().test(stack)) flow[group + 1][selected.size() + 1 + ingredient] = 192;
                    }
                    for (int ingredient = 0; ingredient < recipe.materials().size(); ingredient++)
                    { int count = recipe.materials().get(ingredient).count() * batches; flow[selected.size() + 1 + ingredient][sink] = count; needed += count; }
                    int[] initial = new int[selected.size()];
                    for (int group = 0; group < selected.size(); group++) initial[group] = flow[0][group + 1];
                    int allocated = 0;
                    while (allocated < needed)
                    {
                        int[] previous = new int[sink + 1]; Arrays.fill(previous, -1); previous[0] = 0;
                        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(0);
                        while (!queue.isEmpty() && previous[sink] < 0)
                        {
                            int from = queue.remove();
                            for (int to = 1; to <= sink; to++) if (previous[to] < 0 && flow[from][to] > 0)
                            { previous[to] = from; queue.add(to); }
                        }
                        if (previous[sink] < 0) break;
                        int amount = needed - allocated;
                        for (int to = sink; to != 0; to = previous[to]) amount = Math.min(amount, flow[previous[to]][to]);
                        for (int to = sink; to != 0; to = previous[to]) { flow[previous[to]][to] -= amount; flow[to][previous[to]] += amount; }
                        allocated += amount;
                    }
                    if (allocated != needed) continue;
                    List<ItemStack> result = new ArrayList<>();
                    for (int group = 0; group < selected.size(); group++)
                    {
                        ItemStack example = candidates.get(selected.get(group)); int count = initial[group] - flow[0][group + 1];
                        while (count > 0) { int take = Math.min(count, Math.min(64, example.getMaxStackSize())); result.add(example.copyWithCount(take)); count -= take; }
                    }
                    return result;
                }
        return null;
    }

    public int getProgress() { return Math.max(0, data.get(0)); }
    public int getTotal() { return Math.max(0, data.get(1)); }
    public int getStatus() { return data.get(2); }
    public int getToolDamage() { return Math.max(0, data.get(3)); }
    public int getToolMax() { return Math.max(0, data.get(4)); }
}
