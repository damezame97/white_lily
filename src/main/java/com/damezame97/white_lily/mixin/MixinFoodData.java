package com.damezame97.white_lily.mixin;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.gamerule.eating_player;
import com.damezame97.white_lily.gamerule.happy_mode;
import com.damezame97.white_lily.gamerule.happy_mode_food;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Happy_mode 开启时，用本模组的规则替换原版的自然恢复。
 *
 * <h2>改的是哪儿</h2>
 * <p>原版 {@code FoodData#tick(Player)} 的结构是：先把「消耗」做掉（疲劳值 &gt; 4 就从
 * 饱和度里扣 1 点、没饱和度就扣 1 点饥饿值），再跑三条<b>恢复/饿死</b>分支之一。
 * 开启 {@code Happy_mode} 后我们在 {@code HEAD} 处整段接管：</p>
 * <ul>
 *     <li>原版那个「疲劳值累加器」被本模组的「消耗计数器」取代
 *         （见 {@code com.damezame97.white_lily.gamerule.hunger_activity}），
 *         所以这里不再走原版那段消耗，并把 {@code exhaustionLevel} 钉在 0；</li>
 *     <li>恢复按 {@link happy_mode_food} 的规则自己算；</li>
 *     <li>{@code ci.cancel()} 把原版那三条分支整段跳过——包括<b>饥饿值归零时每 80 刻扣 1 血的饿死伤害</b>，
 *         因为需求要的是「取消原版那种伤害方式，改由计数器那套负面效果处理」，两种不能叠加。</li>
 * </ul>
 *
 * <p><b>不接管</b>的情况（原版原样跑）：没开 {@code Happy_mode}、以及客户端侧。</p>
 *
 * <h2>另外还改了 Building_endurance：把「储存饱和度」的上限从饥饿值抬高（仅 Happy_mode 开启时）</h2>
 * <p>原版 {@code FoodData#eat(int, float)} 里那句
 * {@code this.saturationLevel = Math.min(this.saturationLevel + 食物增益, (float)this.foodLevel);}
 * 把饱和度硬夹到饥饿值，所以吃饱了再怎么吃，饱和度都不会超过 20。
 * 这里拦 {@code Math.min(FF)F} 这个调用点——它正好能同时拿到「加完增益的饱和度」和「原本的上限」，
 * 把上限换成 {@code 饥饿值 + Building_endurance} 即可。</p>
 * <p>玩家从 {@link eating_player} 拿：那边由 {@code MixinPlayer} 在
 * {@code Player#eat} 的 HEAD/TAIL 登记/清除（在那上面捕获参数有坑，所以那两个注入什么都不捕）。</p>
 */
@Mixin(FoodData.class)
public abstract class MixinFoodData
{
    @Shadow
    private int foodLevel;

    @Shadow
    private float saturationLevel;

    @Shadow
    private float exhaustionLevel;

    @Shadow
    private int tickTimer;

    @Shadow
    private int lastFoodLevel;

    /** stamina ≥ 0 时，累计了几次「还没付饱和度」的恢复。 */
    @Unique
    private int whiteLily$pendingHeals;

    @Inject(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true)
    private void whiteLily$happyModeTick(Player player, CallbackInfo ci)
    {
        if (player.level().isClientSide()
                || !player.level().getGameRules().getBoolean(happy_mode.RULE))
        {
            return;   // 交给原版：没开 Happy_mode、或客户端侧
        }

        // 原版那个疲劳值累加器被本模组的「消耗计数器」取代（见 hunger_activity），
        // 所以这里不再自己走那段消耗，并把累加器钉在 0，免得它悄悄攒起来。
        this.lastFoodLevel = this.foodLevel;
        this.exhaustionLevel = 0.0F;

        whiteLily$regenerate(player);

        // 整段跳过原版的恢复/饿死分支（含饥饿值归零时每 80 刻扣 1 血那种饿死伤害）。
        ci.cancel();
    }

    /** 按 {@link happy_mode_food} 的规则计时恢复。 */
    @Unique
    private void whiteLily$regenerate(Player player)
    {
        // 满血不跑：和原版一样，没掉血就什么都不扣
        if (!player.isHurt() || this.foodLevel < happy_mode_food.LOW_FOOD_CUTOFF)
        {
            this.tickTimer = 0;
            return;
        }

        // 只有饥饿值（没饱和度）时：把饥饿值当饱和度用，固定间隔、固定数值、不吃属性
        boolean hungerOnly = this.saturationLevel <= 0.0F;
        int intervalTicks;
        float health;
        if (hungerOnly)
        {
            intervalTicks = happy_mode_food.BASE_INTERVAL_TICKS;
            health = (float) happy_mode_food.DEFAULT_RESTORE_HEALTH;
        }
        else
        {
            intervalTicks = happy_mode_food.intervalTicks(
                    player.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),
                    this.foodLevel < 20);
            health = (float) player.getAttributeValue(attributes.RESTORE_HEALTH.get());
        }

        if (++this.tickTimer < intervalTicks)
        {
            return;
        }
        this.tickTimer = 0;

        whiteLily$applyHealth(player, health);

        if (hungerOnly)
        {
            this.foodLevel = Math.max(this.foodLevel - 1, 0);
            return;
        }

        float cost = happy_mode_food.saturationCost(
                player.getAttributeValue(attributes.STAMINA.get()), this.whiteLily$pendingHeals + 1);
        if (cost <= 0.0F)
        {
            this.whiteLily$pendingHeals++;
            return;
        }

        this.whiteLily$pendingHeals = 0;
        this.saturationLevel = Math.max(this.saturationLevel - cost, 0.0F);
    }

    /**
     * 把 {@code FoodData#eat(int, float)} 里饱和度那一夹的上限换掉。
     *
     * <p>调用点两个入参正好是：{@code gained} = 加完食物增益的饱和度、
     * {@code hungerCap} = 原版的上限（饥饿值）。</p>
     */
    @Redirect(method = "eat(IF)V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"))
    private float whiteLily$raiseSaturationCap(float gained, float hungerCap)
    {
        Player player = eating_player.current();
        if (player == null || player.getFoodData() != (FoodData) (Object) this)
        {
            return Math.min(gained, hungerCap);
        }

        // 只有 Happy_mode 开着才抬高上限；关掉的世界里饱和度上限就该和原版一样是饥饿值。
        if (!player.level().getGameRules().getBoolean(happy_mode.RULE))
        {
            return Math.min(gained, hungerCap);
        }

        double buildingEndurance = player.getAttributeValue(attributes.BUILDING_ENDURANCE.get());
        if (buildingEndurance <= 0.0D)
        {
            return Math.min(gained, hungerCap);
        }

        return Math.min(gained, hungerCap + (float) buildingEndurance);
    }

    /**
     * 正数走 {@link LivingEntity#heal(float)}（会发 LivingHealEvent，别的模组能看见）；
     * 负数直接改血量——Forge 给 {@code heal} 打的补丁会把非正数直接丢掉，扣不了血。
     */
    @Unique
    private static void whiteLily$applyHealth(Player player, float amount)
    {
        if (amount >= 0.0F)
        {
            player.heal(amount);
        }
        else
        {
            player.setHealth(Math.max(player.getHealth() + amount, 0.0F));
        }
    }
}
