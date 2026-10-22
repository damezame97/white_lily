package com.damezame97.white_lily.processing;

import com.damezame97.white_lily.block.medical_blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

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
    private long lastRegistration=Long.MIN_VALUE;
    @Nullable private final processing_block_entity table;

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
        this.table = table;
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
    }

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

    private static class MachineSlot extends SlotItemHandler
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
        if (!level.isClientSide && !stillValid(player)) return ItemStack.EMPTY;
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

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
        lastRegistration=tick;int added=table.registerLearned(player);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.white_lily.processing."+(added==0?"registered_none":"registered_added"),added),false);
        broadcastChanges();return true;
    }

    public int getProgress() { return Math.max(0, data.get(0)); }
    public int getTotal() { return Math.max(0, data.get(1)); }
    public int getStatus() { return data.get(2); }
    public int getToolDamage() { return Math.max(0, data.get(3)); }
    public int getToolMax() { return Math.max(0, data.get(4)); }
}
