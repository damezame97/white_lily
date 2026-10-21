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
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * 把 {@link progressive_tense} 挂到玩家身上，并负责它跟着玩家存档走。
 *
 * <h2>三件事</h2>
 * <ol>
 *     <li><b>声明能力</b>：{@link #PROGRESSIVE_TENSE}。{@code CapabilityManager.get(...)} 本身
 *         <b>不会</b>注册能力（它内部传的是 no-auto-register），所以这里加了
 *         {@code @AutoRegisterCapability}——Forge 在合适的时候会扫到这个注解并帮我们注册，
 *         不用自己写 {@code RegisterCapabilitiesEvent}。</li>
 *     <li><b>只挂在玩家身上</b>：{@link #attach} 里判断 {@code instanceof Player}，
 *         所以怪物/动物身上压根没有这个能力（拿到的永远是空 Optional）。</li>
 *     <li><b>跟着存档走</b>：Provider 实现了 {@link ICapabilitySerializable}，
 *         Forge 会把它序列化进实体的数据里（玩家的就是 playerdata 里的 {@code ForgeCaps} 段），
 *         读档时自动调 {@code deserializeNBT} 还原。
 *         另外死亡重生/换维度会换成新的玩家实体，Forge <b>不会</b>替你搬值，
 *         所以 {@link #clone} 里手动拷一次。</li>
 * </ol>
 *
 * <h2>怎么用</h2>
 * <pre>{@code
 * player_variables.get(player).ifPresent(vars -> vars.setStage(vars.getStage() + 1));
 * }</pre>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class player_variables
{
    /**
     * 玩家身上的「独立变量」能力。
     *
     * <p>能力的<b>注册</b>不在这一行：`CapabilityManager.get(...)` 内部传的是 no-auto-register，
     * 真正的注册是 Forge 扫到 {@link progressive_tense} 类上的 {@code @AutoRegisterCapability} 之后
     * 用<b>类名</b>做的（见 {@code CapabilityManager#injectCapabilities}）——两边名字能对上，
     * 所以这里不用写 {@code RegisterCapabilitiesEvent}。</p>
     *
     * <p>{@code public static final} + 匿名 {@code CapabilityToken}：泛型信息记在字段签名里，
     * Forge 靠它知道这个能力装的是什么类型。</p>
     */
    public static final Capability<progressive_tense> PROGRESSIVE_TENSE =
            CapabilityManager.get(new CapabilityToken<>()
            {
            });

    /** 能力在实体数据里的标识（存档里就写这个键）。 */
    public static final ResourceLocation PROGRESSIVE_TENSE_ID =
            new ResourceLocation(white_lily.MODID, "progressive_tense");

    /**
     * 玩家身上的 {@code Happy_mode}「消耗计数器」（见 {@link hunger_state}）。
     * 注册方式和 {@link #PROGRESSIVE_TENSE} 完全一样，靠类上的注解。
     */
    public static final Capability<hunger_state> HUNGER_STATE =
            CapabilityManager.get(new CapabilityToken<>()
            {
            });

    /** 消耗计数器在实体数据里的标识。 */
    public static final ResourceLocation HUNGER_STATE_ID =
            new ResourceLocation(white_lily.MODID, "hunger_state");

    /** 理智值的运行状态（受伤计时器 + 融毁罚值，见 {@link san_state}）。 */
    public static final Capability<san_state> SAN_STATE =
            CapabilityManager.get(new CapabilityToken<>()
            {
            });

    /** 理智状态在实体数据里的标识。 */
    public static final ResourceLocation SAN_STATE_ID =
            new ResourceLocation(white_lily.MODID, "san_state");

    /** 取玩家身上的理智状态；没挂着时返回空。 */
    public static Optional<san_state> san(Player player)
    {
        return player.getCapability(SAN_STATE).resolve();
    }

    public static final Capability<infection_state> INFECTION_STATE =
            CapabilityManager.get(new CapabilityToken<>() { });
    public static final ResourceLocation INFECTION_STATE_ID =
            ResourceLocation.fromNamespaceAndPath(white_lily.MODID, "infection_state");

    public static Optional<infection_state> infection(Player player)
    {
        return player.getCapability(INFECTION_STATE).resolve();
    }

    /** 取玩家身上的消耗计数器；没挂着时返回空。 */
    public static Optional<hunger_state> hunger(Player player)
    {
        return player.getCapability(HUNGER_STATE).resolve();
    }

    /** 取玩家身上的变量包；玩家没挂着（理论上不该发生）时返回空。 */
    public static Optional<progressive_tense> get(Player player)
    {
        return player.getCapability(PROGRESSIVE_TENSE).resolve();
    }

    /**
     * 图省事的写法：拿不到就给一个临时新实例（<b>改动它不会存下来</b>，别拿它做写入）。
     */
    public static progressive_tense of(Player player)
    {
        return get(player).orElseGet(progressive_tense::new);
    }

    /** 给每个玩家实体挂上能力（只认 Player）。 */
    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player)
        {
            event.addCapability(PROGRESSIVE_TENSE_ID, new Provider());
            event.addCapability(HUNGER_STATE_ID, new HungerStateProvider());
            event.addCapability(SAN_STATE_ID, new SanStateProvider());
            InfectionStateProvider infection = new InfectionStateProvider();
            event.addCapability(INFECTION_STATE_ID, infection);
            event.addListener(infection::invalidate);
        }
    }

    /** 死亡重生 / 换维度会换一个玩家实体，把旧的值拷过去。 */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event)
    {
        event.getOriginal().reviveCaps();
        try
        {
            Optional<progressive_tense> old = get(event.getOriginal());
            Optional<progressive_tense> current = get(event.getEntity());
            if (old.isPresent() && current.isPresent()) current.get().copyFrom(old.get());
            Optional<hunger_state> oldHunger = hunger(event.getOriginal());
            Optional<hunger_state> currentHunger = hunger(event.getEntity());
            if (oldHunger.isPresent() && currentHunger.isPresent()) currentHunger.get().copyFrom(oldHunger.get());
            Optional<san_state> oldSan = san(event.getOriginal());
            Optional<san_state> currentSan = san(event.getEntity());
            if (event.isWasDeath())
            {
                com.damezame97.white_lily.procedures.san_procedures.resetForDeath(event.getEntity());
            }
            else if (oldSan.isPresent() && currentSan.isPresent())
            {
                currentSan.get().copyFrom(oldSan.get());
                var previous = event.getOriginal().getAttribute(com.damezame97.white_lily.attribute.attributes.SAN.get());
                var replacement = event.getEntity().getAttribute(com.damezame97.white_lily.attribute.attributes.SAN.get());
                if (previous != null && replacement != null) replacement.replaceFrom(previous);
                for (var effect : java.util.List.of(
                        com.damezame97.white_lily.white_lilybuff.mob_effects.BANDAGED.get(),
                        com.damezame97.white_lily.white_lilybuff.mob_effects.WOUND_SUPPRESSION.get()))
                {
                    var instance = event.getOriginal().getEffect(effect);
                    if (instance != null) event.getEntity().addEffect(new net.minecraft.world.effect.MobEffectInstance(instance));
                }
            }
        }
        finally { event.getOriginal().invalidateCaps(); }
    }

    /**
     * {@link san_state} 的能力提供者（结构同上，只是装的东西不同）。
     */
    private static class SanStateProvider implements ICapabilityProvider, ICapabilitySerializable<CompoundTag>
    {
        private final san_state instance = new san_state();
        private final LazyOptional<san_state> optional = LazyOptional.of(() -> instance);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
        {
            return SAN_STATE.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            instance.deserializeNBT(nbt);
        }
    }

    private player_variables()
    {
    }

    private static final class InfectionStateProvider implements ICapabilitySerializable<CompoundTag>
    {
        private final infection_state data = new infection_state();
        private LazyOptional<infection_state> value = LazyOptional.of(() -> data);

        private void invalidate() { value.invalidate(); }

        @Override
        public <T> @org.jetbrains.annotations.NotNull LazyOptional<T> getCapability(
                @org.jetbrains.annotations.NotNull Capability<T> capability, @org.jetbrains.annotations.Nullable Direction side)
        {
            // reviveCaps 只恢复外层开关；Clone 读取时也需要恢复内部句柄。
            if (capability == INFECTION_STATE && !value.isPresent()) value = LazyOptional.of(() -> data);
            return INFECTION_STATE.orEmpty(capability, value);
        }

        @Override
        public CompoundTag serializeNBT() { return data.serializeNBT(); }
        @Override
        public void deserializeNBT(CompoundTag tag) { data.deserializeNBT(tag); }
    }

    /**
     * 能力提供者：实例 + {@code LazyOptional} 包装 + 存档读写。
     */
    private static class Provider implements ICapabilityProvider, ICapabilitySerializable<CompoundTag>
    {
        private final progressive_tense instance = new progressive_tense();
        private final LazyOptional<progressive_tense> optional = LazyOptional.of(() -> instance);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
        {
            return PROGRESSIVE_TENSE.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            instance.deserializeNBT(nbt);
        }
    }

    /**
     * {@link hunger_state} 的能力提供者（和上面那个 {@link Provider} 结构一样，只是装的东西不同）。
     */
    private static class HungerStateProvider implements ICapabilityProvider, ICapabilitySerializable<CompoundTag>
    {
        private final hunger_state instance = new hunger_state();
        private final LazyOptional<hunger_state> optional = LazyOptional.of(() -> instance);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
        {
            return HUNGER_STATE.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            instance.deserializeNBT(nbt);
        }
    }
}
