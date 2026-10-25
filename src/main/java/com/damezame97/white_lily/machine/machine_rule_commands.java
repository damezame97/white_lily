package com.damezame97.white_lily.machine;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "white_lily")
public final class machine_rule_commands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("white_lily_machine_rules").requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    machine_rules.state(ctx.getSource().getServer().getRecipeManager()).report().forEach((device, info) -> ctx.getSource().sendSuccess(
                            () -> Component.literal(device + ": " + info), false));
                    return machine_rules.state(ctx.getSource().getServer().getRecipeManager()).report().size();
                }).then(Commands.argument("device", ResourceLocationArgument.id())
                        .then(Commands.argument("recipe", ResourceLocationArgument.id()).executes(ctx -> {
                            ResourceLocation device = ResourceLocationArgument.getId(ctx, "device");
                            ResourceLocation recipe = ResourceLocationArgument.getId(ctx, "recipe");
                            boolean allowed = machine_rules.recipes(ctx.getSource().getServer().getRecipeManager(),device).stream().anyMatch(r -> r.getId().equals(recipe));
                            String text = device + " / " + recipe + ": " + (allowed ? "ALLOWED" : "DENIED (not in final device recipe table)")
                                    + "; " + machine_rules.state(ctx.getSource().getServer().getRecipeManager()).report().getOrDefault(device, "No active policy; device not configured or policy rejected");
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return allowed ? 1 : 0;
                        }))));
    }
}
