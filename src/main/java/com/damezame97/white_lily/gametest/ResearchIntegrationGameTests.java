package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.item.basic_material_item;
import com.damezame97.white_lily.item.detail_tooltip;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class ResearchIntegrationGameTests
{
    @GameTest(template="empty")
    public static void handbookCraftsInTwoByTwoWithoutDurabilityOrConsumption(GameTestHelper h)
    {
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"ResearchBook"));
        player.connection=new ServerGamePacketListenerImpl(player.server,new Connection(PacketFlow.SERVERBOUND),player)
        {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { }
        };
        var grid=(CraftingContainer)player.inventoryMenu.getSlot(1).container;
        player.inventoryMenu.getSlot(1).set(new ItemStack(Items.BOOK));
        player.inventoryMenu.getSlot(3).set(new ItemStack(items.BLANK_LABEL.get()));
        player.inventoryMenu.getSlot(4).set(new ItemStack(items.CHARCOAL_POWDER.get()));
        var recipe=(CraftingRecipe)h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("white_lily","investigation_handbook")).orElseThrow();
        yes(h,recipe.matches(grid,h.getLevel()),"书、标签、炭粉无序配方支持2x2");
        ItemStack book=recipe.assemble(grid,h.getLevel().registryAccess());
        yes(h,book.is(items.INVESTIGATION_HANDBOOK.get())&&book.getCount()==1&&book.getMaxStackSize()==1
                &&!book.isDamageableItem()&&!book.isEdible()&&!book.hasFoil(),"手册单本无耐久无食用或附魔光效");
        player.setItemInHand(InteractionHand.MAIN_HAND,book);
        book.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        yes(h,book.getCount()==1&&!player.isUsingItem()&&!book.hasTag(),"右键不消耗、不长时间使用、不把进度存入物品NBT");
        List<Component> lines=new ArrayList<>();
        book.getItem().appendHoverText(book,h.getLevel(),lines,TooltipFlag.Default.NORMAL);
        yes(h,lines.size()==1&&lines.get(0).equals(detail_tooltip.hint()),"复用现有Shift提示");
        lines.clear();((basic_material_item)book.getItem()).appendDescription(lines,true);
        yes(h,lines.size()==5,"展开风味三行和功能两行，不重复简介");
        for(var line:lines)yes(h,Objects.equals(line.getStyle().getColor(),TextColor.fromLegacyFormat(ChatFormatting.GRAY))
                &&!line.getString().contains("item.white_lily."),"手册说明有翻译并使用普通灰色");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void handbookTextureAndModelAreActualResources(GameTestHelper h) throws Exception
    {
        try(var png=ResearchIntegrationGameTests.class.getResourceAsStream("/assets/white_lily/textures/item/investigation_handbook.png"))
        {
            yes(h,png!=null,"正式手册PNG存在");
            var image=javax.imageio.ImageIO.read(png);
            yes(h,image.getWidth()==16&&image.getHeight()==16&&image.getColorModel().hasAlpha(),"正式PNG真实16x16且带透明通道");
            yes(h,(image.getRGB(0,0)>>>24)==0,"背景真实透明");
        }
        try(var stream=ResearchIntegrationGameTests.class.getResourceAsStream("/assets/white_lily/models/item/investigation_handbook.json"))
        {
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            yes(h,json.get("parent").getAsString().endsWith("item/generated")
                    &&json.getAsJsonObject("textures").get("layer0").getAsString().equals("white_lily:item/investigation_handbook"),"模型使用正式生成物品纹理");
        }
        h.succeed();
    }
}
