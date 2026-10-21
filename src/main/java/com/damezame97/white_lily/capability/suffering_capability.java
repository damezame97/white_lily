package com.damezame97.white_lily.capability;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class suffering_capability
{
    public static final Capability<suffering_data> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(white_lily.MODID, "stored_suffering");

    public static Optional<suffering_data> get(Player player)
    {
        return player.getCapability(CAPABILITY).resolve();
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player)
        {
            Provider provider = new Provider();
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event)
    {
        // Forge 在 Clone 事件前已失效旧能力，读取期间必须暂时恢复。
        event.getOriginal().reviveCaps();
        try
        {
            if (!event.isWasDeath())
            {
                get(event.getOriginal()).ifPresent(old -> get(event.getEntity())
                        .ifPresent(current -> current.deserializeNBT(old.serializeNBT())));
            }
            else
            {
                get(event.getEntity()).ifPresent(suffering_data::drain);
            }
        }
        finally
        {
            event.getOriginal().invalidateCaps();
        }
    }

    private static class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final suffering_data data = new suffering_data();
        private LazyOptional<suffering_data> value = LazyOptional.of(() -> data);

        private void invalidate()
        {
            value.invalidate();
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side)
        {
            // Entity.reviveCaps 只恢复外层查询开关；为 Clone 期间的读取恢复内部句柄。
            if (capability == CAPABILITY && !value.isPresent())
            {
                value = LazyOptional.of(() -> data);
            }
            return CAPABILITY.orEmpty(capability, value);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag)
        {
            data.deserializeNBT(tag);
        }
    }

    private suffering_capability()
    {
    }
}
