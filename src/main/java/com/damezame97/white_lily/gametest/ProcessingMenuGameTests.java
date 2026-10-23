package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.processing.processing_block_entity;
import com.damezame97.white_lily.processing.processing_menu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public final class ProcessingMenuGameTests
{
    @GameTest(template = "empty")
    public static void shiftTransfersAreBoundedAndCapacityQueriesDoNotMutateInputs(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            Fixture f = new Fixture(h);
            ItemStack mortar = new ItemStack(items.STONE_MORTAR.get());
            mortar.setHoverName(Component.literal("Named mortar"));
            mortar.getOrCreateTag().putString("processing_test", "preserve");
            var originalTag = mortar.getTag().copy();
            f.table.getInventory().setStackInSlot(0, mortar);
            f.table.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 2));
            f.tick(7);
            yes(h, f.menu.getProgress() == 7, "真实服务端菜单读取累计加工进度");
            for (int i = 0; i < 3; i++)
            {
                yes(h, f.menu.getSlot(0).getMaxStackSize(mortar) == 1, "工具槽容量为1");
                f.menu.getSlot(1).getMaxStackSize(new ItemStack(items.HEMP_STALK.get()));
            }
            yes(h, f.menu.getProgress() == 7 && Objects.equals(originalTag, mortar.getTag()),
                    "容量查询不临时取走工具、不重置进度或修改NBT");

            f.player.getInventory().setItem(9, new ItemStack(items.HEMP_STALK.get(), 3));
            f.menu.clicked(8, 0, ClickType.QUICK_MOVE, f.player);
            yes(h, f.table.getInventory().getStackInSlot(1).getCount() == 5
                    && f.player.getInventory().getItem(9).isEmpty() && f.menu.getProgress() == 7,
                    "Shift增加同种原料保留进度并精确转移数量");
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            yes(h, f.table.getInventory().getStackInSlot(0).isEmpty() && f.menu.getProgress() == 0,
                    "Shift取走工具立即清空进度");
            yes(h, f.player.getInventory().countItem(items.STONE_MORTAR.get()) == 1,
                    "工具恰好返还一次");
            ItemStack returnedTool = f.player.getInventory().items.stream()
                    .filter(stack -> stack.is(items.STONE_MORTAR.get())).findFirst().orElseThrow();
            yes(h, Objects.equals(originalTag, returnedTool.getTag()), "Shift保留工具名称和全部NBT");

            fillPlayer(f.player);
            f.player.getInventory().setItem(9, new ItemStack(items.PLANT_FIBER.get(), 63));
            f.table.getInventory().setStackInSlot(5, new ItemStack(items.PLANT_FIBER.get(), 10));
            f.menu.clicked(5, 0, ClickType.QUICK_MOVE, f.player);
            yes(h, f.player.getInventory().getItem(9).getCount() == 64
                    && f.table.getInventory().getStackInSlot(5).getCount() == 9,
                    "背包仅剩1空间时只取出1件成品，保留其余9件");
            f.menu.clicked(5, 0, ClickType.QUICK_MOVE, f.player);
            yes(h, f.table.getInventory().getStackInSlot(5).getCount() == 9,
                    "满背包重复Shift不能吞掉或复制输出");

            for (int output = 5; output <= 7; output++)
            {
                yes(h, !f.menu.getSlot(output).mayPlace(new ItemStack(items.PLANT_FIBER.get())),
                        "三种输出槽均禁止插入");
            }
            f.menu.setCarried(new ItemStack(items.PLANT_FIBER.get(), 3));
            f.menu.clicked(6, 0, ClickType.PICKUP, f.player);
            yes(h, f.menu.getCarried().getCount() == 3 && f.table.getInventory().getStackInSlot(6).isEmpty(),
                    "真实点击不能向空返还槽手动放入物品");
            f.menu.setCarried(ItemStack.EMPTY);

            fillPlayer(f.player);
            f.player.getInventory().setItem(9, new ItemStack(items.HEMP_STALK.get(), 5));
            f.table.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 63));
            f.table.getInventory().setStackInSlot(2, new ItemStack(Items.DIRT, 64));
            f.table.getInventory().setStackInSlot(3, new ItemStack(Items.DIRT, 64));
            f.menu.clicked(8, 0, ClickType.QUICK_MOVE, f.player);
            yes(h, f.table.getInventory().getStackInSlot(1).getCount() == 64
                    && f.player.getInventory().getItem(9).getCount() == 4,
                    "原料槽只剩1空间时保留玩家手中剩余4份");
            f.menu.clicked(8, 0, ClickType.QUICK_MOVE, f.player);
            yes(h, f.player.getInventory().getItem(9).getCount() == 4,
                    "机器与背包均满时转移安全停止");
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void twoViewersShareOneProcessAndDistanceOrRemovalClosesAccess(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            Fixture f = new Fixture(h);
            f.table.getInventory().setStackInSlot(0, new ItemStack(items.STONE_MORTAR.get()));
            f.table.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 2));
            f.tick(10);
            Player second = ProcessingTestPlayers.create(h);
            second.setPos(f.pos.getX() + 0.5, f.pos.getY() + 0.5, f.pos.getZ() + 0.5);
            processing_menu other = new processing_menu(23, second.getInventory(), f.table);
            yes(h, other.getProgress() == 10 && f.menu.getProgress() == 10,
                    "两个菜单读取同一方块实体进度");
            for (int i = 0; i < 4; i++)
            {
                f.menu.broadcastChanges();
                other.broadcastChanges();
            }
            yes(h, f.menu.getProgress() == 10 && other.getProgress() == 10,
                    "菜单同步自身不会额外推进或结算加工");
            f.tick(30);
            yes(h, f.table.getInventory().getStackInSlot(5).getCount() == 2
                    && f.table.getInventory().getStackInSlot(1).getCount() == 1
                    && f.table.getInventory().getStackInSlot(0).getDamageValue() == 1,
                    "双人查看仍只完成一次40刻加工并扣一次耐久");
            yes(h, f.menu.stillValid(f.player) && other.stillValid(second), "近距离且方块存在时允许访问");
            second.setPos(f.pos.getX() + 10.5, f.pos.getY() + 0.5, f.pos.getZ() + 0.5);
            yes(h, !other.stillValid(second), "超出8格访问距离后菜单失效");
            h.setBlock(f.relative, Blocks.AIR);
            yes(h, !f.menu.stillValid(f.player), "方块被移除后菜单失效");
            h.setBlock(f.relative, medical_blocks.SIMPLE_PROCESSING_TABLE.get());
            processing_block_entity replacement = (processing_block_entity) h.getLevel().getBlockEntity(f.pos);
            yes(h, replacement != f.table && !f.menu.stillValid(f.player),
                    "同位置重放同种工作台后旧菜单仍失效，不能访问孤立库存");
            f.player.getInventory().setItem(9, new ItemStack(items.HEMP_STALK.get(), 5));
            yes(h, f.menu.quickMoveStack(f.player, 8).isEmpty()
                    && f.player.getInventory().getItem(9).getCount() == 5
                    && f.table.getInventory().getStackInSlot(1).isEmpty()
                    && replacement.getInventory().getStackInSlot(1).isEmpty(),
                    "旧菜单直接Shift调用被拒绝，不改变玩家、新台或已移除旧台库存");
            processing_menu reopened = new processing_menu(24, f.player.getInventory(), replacement);
            yes(h, reopened.stillValid(f.player) && reopened.getSlot(0).getItem().isEmpty()
                    && reopened.getSlot(5).getItem().isEmpty(),
                    "新菜单绑定新方块实体，原工作台库存不会迁入或复制");
            h.succeed();
        }
    }

    private static void fillPlayer(Player player)
    {
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
    }

    private static final class Fixture
    {
        final GameTestHelper helper;
        final BlockPos relative = new BlockPos(1, 1, 1);
        final BlockPos pos;
        final processing_block_entity table;
        final Player player;
        final processing_menu menu;

        Fixture(GameTestHelper h)
        {
            helper = h;
            h.setBlock(relative, medical_blocks.SIMPLE_PROCESSING_TABLE.get());
            pos = h.absolutePos(relative);
            table = (processing_block_entity) h.getLevel().getBlockEntity(pos);
            player = ProcessingTestPlayers.create(h);
            player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            menu = new processing_menu(22, player.getInventory(), table);
            player.containerMenu = menu;
            table.authorizeInput(player);
        }

        void tick(int count)
        {
            for (int i = 0; i < count; i++) processing_block_entity.serverTick(
                    helper.getLevel(), pos, helper.getLevel().getBlockState(pos), table);
        }
    }
}
