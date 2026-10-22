package com.damezame97.white_lily.research;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class research_capability
{
    public static final Capability<research_progress> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("white_lily", "research");
    public static Optional<research_progress> get(Player player)
    { return player.getCapability(CAPABILITY).resolve(); }

    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Entity> event)
    {
        if (!(event.getObject() instanceof Player)) return;
        Provider provider = new Provider();
        event.addCapability(ID, provider);
        event.addListener(provider::invalidate);
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event)
    {
        event.getOriginal().reviveCaps();
        try { get(event.getOriginal()).ifPresent(old -> get(event.getEntity()).ifPresent(data -> data.load(old.save()))); }
        finally { event.getOriginal().invalidateCaps(); }
    }
    private static final class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final research_progress data = new research_progress();
        private LazyOptional<research_progress> value = LazyOptional.of(() -> data);
        void invalidate() { value.invalidate(); }
        @Override public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side)
        {
            if (capability == CAPABILITY && !value.isPresent()) value = LazyOptional.of(() -> data);
            return CAPABILITY.orEmpty(capability, value);
        }
        @Override public CompoundTag serializeNBT() { return data.save(); }
        @Override public void deserializeNBT(CompoundTag tag) { data.load(tag); }
    }
    private research_capability() { }
}
