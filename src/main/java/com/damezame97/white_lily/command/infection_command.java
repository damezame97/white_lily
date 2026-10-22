package com.damezame97.white_lily.command;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.infection.infection_service;
import com.damezame97.white_lily.infection.infection_sources;
import com.damezame97.white_lily.white_lily;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Collection;
import java.util.Locale;

/** 只在 infection 子树要求权限 2，不修改已有 tense 命令的权限设计。 */
@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class infection_command
{
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("white_lily")
                .then(Commands.literal("infection").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get").then(Commands.argument("targets", EntityArgument.players()).executes(infection_command::query)))
                        .then(change("set")).then(change("add")).then(change("reduce")).then(change("treat"))
                        .then(Commands.literal("clear").then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> edit(context, "set", 0))))
                        .then(Commands.literal("source").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(infection_sources.entries().keySet(), builder))
                                        .executes(infection_command::source))))
                        .then(Commands.literal("simulate").then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("probability", DoubleArgumentType.doubleArg(0, 1))
                                        .then(Commands.argument("base", DoubleArgumentType.doubleArg(0))
                                                .executes(infection_command::simulate)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String mode)
    {
        return Commands.literal(mode).then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("amount", "set".equals(mode) ? DoubleArgumentType.doubleArg() : DoubleArgumentType.doubleArg(0))
                        .executes(context -> edit(context, mode, DoubleArgumentType.getDouble(context, "amount")))));
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return EntityArgument.getPlayers(context, "targets");
    }

    private static int edit(CommandContext<CommandSourceStack> context, String mode, double amount) throws CommandSyntaxException
    {
        if (!Double.isFinite(amount)) return invalid(context);
        int changed = 0;
        for (ServerPlayer player : targets(context))
        {
            if (player_variables.infection(player).isEmpty()) continue;
            double before = infection_service.query(player);
            switch (mode)
            {
                case "set" -> infection_service.set(player, amount);
                case "add" -> infection_service.addDirect(player, amount);
                case "reduce" -> infection_service.reduce(player, amount);
                case "treat" -> infection_service.treat(player, amount);
                default -> throw new IllegalArgumentException(mode);
            }
            double after = infection_service.query(player);
            context.getSource().sendSuccess(() -> Component.translatable("command.white_lily.infection.changed",
                    player.getDisplayName(), number(after), number(after - before)), true);
            changed++;
        }
        return changed;
    }

    private static int query(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        int count = 0;
        for (ServerPlayer player : targets(context))
        {
            var data = player_variables.infection(player);
            if (data.isEmpty()) continue;
            var values = infection_service.conditions(player);
            context.getSource().sendSuccess(() -> Component.translatable("command.white_lily.infection.query",
                    player.getDisplayName(), number(data.get().value()),
                    values.validHealth() ? String.format(Locale.ROOT, "%.2f", values.healthRatio() * 100) : "--",
                    values.injury().multiplier, values.injury().passive, Double.toString(values.stamina()),
                    Double.toString(values.risk()), Double.toString(values.resistance()), data.get().passiveTicks(),
                    Config.infectionEnabled), false);
            count++;
        }
        return count;
    }

    private static int source(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        if (infection_sources.find(id).isEmpty())
        {
            context.getSource().sendFailure(Component.translatable("command.white_lily.infection.unknown", id));
            return 0;
        }
        for (ServerPlayer player : targets(context)) report(context, player, infection_service.trySource(player, id));
        return targets(context).size();
    }

    private static int simulate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        double probability = DoubleArgumentType.getDouble(context, "probability");
        double base = DoubleArgumentType.getDouble(context, "base");
        if (!Double.isFinite(probability) || !Double.isFinite(base)) return invalid(context);
        var source = new infection_sources.Source(ResourceLocation.fromNamespaceAndPath("white_lily", "debug"), probability, base);
        for (ServerPlayer player : targets(context)) report(context, player, infection_service.trySource(player, source, player.getRandom()));
        return targets(context).size();
    }

    private static void report(CommandContext<CommandSourceStack> context, ServerPlayer player, infection_service.Result result)
    {
        context.getSource().sendSuccess(() -> Component.translatable("command.white_lily.infection.result",
                player.getDisplayName(), Component.translatable("infection.white_lily.status." + result.status().name().toLowerCase(Locale.ROOT)),
                number(result.actualAdded()), number(result.after())), false);
    }

    private static int invalid(CommandContext<CommandSourceStack> context)
    {
        context.getSource().sendFailure(Component.translatable("command.white_lily.infection.invalid"));
        return 0;
    }

    private static String number(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private infection_command() { }
}
