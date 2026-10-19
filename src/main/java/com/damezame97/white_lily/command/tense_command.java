package com.damezame97.white_lily.command;

import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.capability.progressive_tense;
import com.damezame97.white_lily.white_lily;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Optional;

/**
 * 读写 {@link progressive_tense} 的游戏内指令。
 *
 * <h2>用法</h2>
 * <pre>{@code
 * /white_lily tense get @s                 // 看自己的 stage / tense
 * /white_lily tense get @a                 // 看所有人的
 * /white_lily tense set @s stage 1         // 把自己的 stage 设成 1
 * /white_lily tense set @a tense true      // 把所有人的 tense 设成 true
 * }</pre>
 * <p>{@code <targets>} 是原版实体选择器参数（{@code EntityArgument.players()}），
 * 所以 {@code @s}、{@code @a}、{@code @p}、{@code @r}、玩家名、{@code @a[distance=..10]} 这类写法全都支持。</p>
 *
 * <h2>关于权限</h2>
 * <p>这里<b>故意没有</b>加 {@code .requires(...)}：作弊没开的单人档里玩家权限等级只有 0，
 * 加了 {@code hasPermission(2)} 这种调试指令就根本用不了。要收紧的话，
 * 在 {@code Commands.literal("tense")} 后面接一个 {@code .requires(source -> source.hasPermission(2))} 即可。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class tense_command
{
    /** 指令根节点：{@code /white_lily ...}。 */
    public static final String ROOT = "white_lily";

    private static final String ARG_TARGETS = "targets";
    private static final String ARG_STAGE = "stage";
    private static final String ARG_TENSE = "tense";

    /** 指令注册入口（Forge 会在服务端启动时叫我们）。 */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal(ROOT)
                .then(Commands.literal("tense")
                        // /white_lily tense get <targets>
                        .then(Commands.literal("get")
                                .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                        .executes(ctx -> get(ctx,
                                                EntityArgument.getPlayers(ctx, ARG_TARGETS)))))
                        // /white_lily tense set <targets> stage <stage>
                        // /white_lily tense set <targets> tense <true|false>
                        .then(Commands.literal("set")
                                .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                        .then(Commands.literal("stage")
                                                .then(Commands.argument(ARG_STAGE, IntegerArgumentType.integer())
                                                        .executes(ctx -> setStage(ctx,
                                                                EntityArgument.getPlayers(ctx, ARG_TARGETS),
                                                                IntegerArgumentType.getInteger(ctx, ARG_STAGE)))))
                                        .then(Commands.literal("tense")
                                                .then(Commands.argument(ARG_TENSE, BoolArgumentType.bool())
                                                        .executes(ctx -> setTense(ctx,
                                                                EntityArgument.getPlayers(ctx, ARG_TARGETS),
                                                                BoolArgumentType.getBool(ctx, ARG_TENSE)))))))));
    }

    /** 把选中玩家当前的 stage / tense 念给执行者听。 */
    private static int get(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets)
    {
        int count = 0;
        for (ServerPlayer player : targets)
        {
            Optional<progressive_tense> vars = player_variables.get(player);
            if (vars.isEmpty())
            {
                continue;
            }

            count++;
            int stage = vars.get().getStage();
            boolean tense = vars.get().isTense();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + " → stage=" + stage + ", tense=" + tense), false);
        }
        return count;
    }

    private static int setStage(CommandContext<CommandSourceStack> ctx,
                                Collection<ServerPlayer> targets, int stage)
    {
        int count = 0;
        for (ServerPlayer player : targets)
        {
            Optional<progressive_tense> vars = player_variables.get(player);
            if (vars.isEmpty())
            {
                continue;
            }

            vars.get().setStage(stage);
            count++;
        }

        int changed = count;
        ctx.getSource().sendSuccess(
                () -> Component.literal("White Lily: 已把 " + changed + " 名玩家的 stage 设为 " + stage), true);
        return count;
    }

    private static int setTense(CommandContext<CommandSourceStack> ctx,
                                Collection<ServerPlayer> targets, boolean tense)
    {
        int count = 0;
        for (ServerPlayer player : targets)
        {
            Optional<progressive_tense> vars = player_variables.get(player);
            if (vars.isEmpty())
            {
                continue;
            }

            vars.get().setTense(tense);
            count++;
        }

        int changed = count;
        ctx.getSource().sendSuccess(
                () -> Component.literal("White Lily: 已把 " + changed + " 名玩家的 tense 设为 " + tense), true);
        return count;
    }

    private tense_command()
    {
    }
}
