package com.damezame97.white_lily.processing;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Comparator;
import java.util.Set;
import java.util.LinkedHashSet;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import com.damezame97.white_lily.research.research_catalog;
import com.damezame97.white_lily.research.research_service;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** 仅服务端推进的八槽加工台；不公开物品能力，第一版不接受漏斗自动化。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class processing_block_entity extends BlockEntity implements MenuProvider
{
    public static final int TOOL = 0, CONTAINER = 4, RESULT = 5, RETURNED = 6, BYPRODUCT = 7;
    public static final int IDLE = 0, WORKING = 1, BLOCKED = 2, UNREGISTERED = 3, LOCKED = 4, OWNER_OFFLINE = 5;
    private final Set<ResourceLocation> registeredRecipes=new LinkedHashSet<>();
    private int progress;
    private int total;
    private int status;
    @Nullable private ResourceLocation recipeId;
    private String recipeFingerprint = "";
    @Nullable private processing_recipe activeRecipe;
    private ItemStack toolSnapshot = ItemStack.EMPTY;
    private boolean internalMutation;
    private boolean dropped;
    @Nullable private UUID operator;
    @Nullable private UUID outputOwner;
    @Nullable private ResourceLocation outputRecipe;

    private final ItemStackHandler inventory = new ItemStackHandler(8)
    {
        @Override public int getSlotLimit(int slot) { return slot == TOOL ? 1 : 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack)
        {
            if (slot >= RESULT) return false;
            if (slot == TOOL) return level != null && processing_recipe.isSupportedTool(stack, level);
            if (slot == CONTAINER) return level != null && processing_recipe.isAllowedContainer(stack, level);
            return true;
        }
        @Override protected void onContentsChanged(int slot)
        {
            if (!internalMutation && !isRemoved())
            {
                // 工具槽的外部替换即便 NBT 相同也重新开始；内部正常损耗不走此分支。
                if (slot == TOOL) resetProcessing();
                else if (slot < RESULT) validateChangedInputs();
            }
            if (slot >= RESULT && !hasOutputs()) { outputOwner = null; outputRecipe = null; }
            setChanged();
        }
    };

    private final ContainerData data = new ContainerData()
    {
        @Override public int get(int index)
        {
            ItemStack tool = inventory.getStackInSlot(TOOL);
            return switch (index)
            {
                case 0 -> progress;
                case 1 -> total;
                case 2 -> status;
                case 3 -> tool.isEmpty() ? 0 : tool.getDamageValue();
                case 4 -> tool.isEmpty() ? 0 : tool.getMaxDamage();
                default -> 0;
            };
        }
        @Override public void set(int index, int value)
        {
            switch (index)
            {
                case 0 -> progress = Math.max(0, value);
                case 1 -> total = Math.max(0, value);
                case 2 -> status = value;
                default -> { }
            }
        }
        @Override public int getCount() { return 5; }
    };

    public processing_block_entity(BlockPos pos, BlockState state)
    { super(processing_registry.TABLE_ENTITY.get(), pos, state); }

    public ItemStackHandler getInventory() { return inventory; }
    public Set<ResourceLocation> registeredRecipes() { return Set.copyOf(registeredRecipes); }
    public boolean canProcess(processing_recipe recipe)
    {
        return canProcessRecipe(recipe.getId(),recipe.researchNode(),registeredRecipes.contains(recipe.getId()));
    }

    /** Machine registration never substitutes for the current player's personal recipe/research progress. */
    public boolean canProcess(Player player, processing_recipe recipe)
    {
        return canProcess(recipe) && recipe_unlocks.isUnlocked(player, recipe.getId())
                && research_service.canCraft(player, recipe.getId());
    }

    private boolean hasOutputs()
    {
        return !inventory.getStackInSlot(RESULT).isEmpty() || !inventory.getStackInSlot(RETURNED).isEmpty()
                || !inventory.getStackInSlot(BYPRODUCT).isEmpty();
    }

    public boolean canEditInputs(Player player)
    {
        return level != null && !isRemoved() && player instanceof ServerPlayer && player.level() == level
                && player.isAlive() && !player.isRemoved() && (!hasOutputs() || outputOwner == null
                || outputOwner.equals(player.getUUID()));
    }

    /** Called only from an authenticated server menu operation, before changing any input slot. */
    public boolean authorizeInput(Player player)
    {
        if (!canEditInputs(player)) return false;
        if (!player.getUUID().equals(operator))
        {
            resetProcessing();
            operator = player.getUUID();
            setChanged();
        }
        return true;
    }

    @Nullable private ServerPlayer currentOperator()
    {
        if (level == null || operator == null) return null;
        Player player = level.getPlayerByUUID(operator);
        return player instanceof ServerPlayer server && server.isAlive() && !server.isRemoved() ? server : null;
    }

    public boolean canTakeOutputs(Player player)
    {
        if (level == null || level.isClientSide || isRemoved() || !(player instanceof ServerPlayer)
                || player.level() != level || !player.isAlive() || player.isRemoved()) return false;
        if (!hasOutputs()) return true;
        // Legacy output has no provenance: it may be recovered, but cannot authorize another batch.
        if (outputOwner == null || outputRecipe == null) return true;
        return outputOwner.equals(player.getUUID()) && recipe_unlocks.isUnlocked(player, outputRecipe)
                && research_service.canCraft(player, outputRecipe);
    }

    /**
     * 一台机器能不能跑某条配方。
     *
     * <p>三种情况：</p>
     * <ol>
     *     <li>配方没绑节点（{@code recipeOwners} 里没有），也没声明 {@code research}：普通机器配方，放行；</li>
     *     <li>配方绑了节点：必须声明 {@code research}、与节点的归属一致、且<b>本机已记录</b>；</li>
     *     <li>其他（绑了节点却没写 {@code research}、或写了 {@code research} 但没被任何节点登记）：
     *         属于配置写错，<b>一律不放行</b>——以前第 3 种会被 {@code researchNode()==null} 短路成免费配方。</li>
     * </ol>
     *
     * @param researchNode 配方 JSON 里的 {@code research} 字段（可为 null）
     * @param registered   本机是否已记录这条配方（{@link #registerLearned}）
     */
    public static boolean canProcessRecipe(ResourceLocation id,@Nullable ResourceLocation researchNode,boolean registered)
    {
        if(id==null)return false;
        ResourceLocation owner=research_catalog.recipeOwners().get(id);
        if(owner==null) return researchNode==null;
        return researchNode!=null && researchNode.equals(owner) && registered;
    }
    /** Records only the actual player's current knowledge, never another machine's records. */
    public int registerLearned(Player player)
    {
        if(level==null || level.isClientSide || player.level()!=level || isRemoved())return 0;
        int added=0;
        for(processing_recipe recipe:level.getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()))
            if(recipe.researchNode()!=null && recipe.researchNode().equals(research_catalog.recipeOwners().get(recipe.getId()))
                    && recipe_unlocks.isUnlocked(player,recipe.getId()) && research_service.canCraft(player,recipe.getId())
                    && registeredRecipes.add(recipe.getId()))added++;
        if(added>0)setChanged();return added;
    }
    public ContainerData getData() { return data; }
    @Override public Component getDisplayName() { return Component.translatable("block.white_lily.simple_processing_table"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player)
    { return new processing_menu(id, playerInventory, this); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, processing_block_entity table)
    {
        if (level.isClientSide || table.isRemoved()) return;
        table.tickProcessing();
    }

    private void validateChangedInputs()
    {
        if (activeRecipe != null)
        {
            if (!activeRecipe.matchesInputs(inventory)) resetProcessing();
            return;
        }
        if (recipeId == null) return;
        // 加载后尚未经过首个 tick 也可能先收到菜单操作，不能借取走并放回材料保留旧进度。
        if (level == null || !(level.getRecipeManager().byKey(recipeId).orElse(null) instanceof processing_recipe restored)
                || !restored.fingerprint().equals(recipeFingerprint)
                || !ItemStack.matches(toolSnapshot, inventory.getStackInSlot(TOOL)) || !restored.matchesInputs(inventory))
            resetProcessing();
    }

    private void tickProcessing()
    {
        if (level == null) return;
        if (inventory.getStackInSlot(TOOL).isEmpty()
                || inventory.getStackInSlot(1).isEmpty() && inventory.getStackInSlot(2).isEmpty() && inventory.getStackInSlot(3).isEmpty())
        {
            if (recipeId != null || progress != 0 || status != IDLE) resetProcessing();
            return;
        }
        ServerPlayer authorizer = currentOperator();
        processing_recipe chosen = level.getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()).stream()
                .filter(recipe -> recipe.matchesInputs(inventory) && authorizer != null && canProcess(authorizer, recipe))
                .min(Comparator.comparing(recipe -> recipe.getId().toString())).orElse(null);
        if (chosen == null)
        {
            // Preserve a loaded old recipe/progress while waiting for registration; no input is consumed.
            processing_recipe restricted=level.getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()).stream()
                    .filter(recipe->recipe.matchesInputs(inventory))
                    .min(Comparator.comparing(recipe->recipe.getId().toString())).orElse(null);
            if(restricted!=null)
            {
                int restrictedStatus = !canProcess(restricted) ? UNREGISTERED : authorizer == null ? OWNER_OFFLINE : LOCKED;
                if(status!=restrictedStatus || total!=restricted.duration())
                {status=restrictedStatus;total=restricted.duration();setChanged();}
            }
            else if(recipeId!=null || progress!=0 || status!=IDLE)resetProcessing();
            return;
        }

        // 配方对象会在资源重载时替换；只在对象变化时重新构造完整指纹。
        if (activeRecipe != chosen)
        {
            String fingerprint = chosen.fingerprint();
            boolean compatible = chosen.getId().equals(recipeId) && fingerprint.equals(recipeFingerprint)
                    && ItemStack.matches(toolSnapshot, inventory.getStackInSlot(TOOL));
            if (!compatible) progress = 0;
            recipeId = chosen.getId();
            recipeFingerprint = fingerprint;
            activeRecipe = chosen;
            total = chosen.duration();
            progress = Math.min(progress, total);
            toolSnapshot = inventory.getStackInSlot(TOOL).copy();
            setChanged();
        }
        else if (!ItemStack.matches(toolSnapshot, inventory.getStackInSlot(TOOL)))
        {
            progress = 0;
            toolSnapshot = inventory.getStackInSlot(TOOL).copy();
            setChanged();
        }

        if (!outputsFit(chosen))
        {
            if (status != BLOCKED) { status = BLOCKED; setChanged(); }
            return;
        }
        status = WORKING;
        progress++;
        setChanged(); // 包括区块卸载前最后不足一批的已累计进度。
        if (progress >= total) complete(chosen);
    }

    private boolean outputsFit(processing_recipe recipe)
    {
        return (!hasOutputs() || outputOwner == null || outputOwner.equals(operator) && recipe.getId().equals(outputRecipe))
                && outputFits(RESULT, recipe.result()) && outputFits(RETURNED, recipe.returned())
                && outputFits(BYPRODUCT, recipe.byproduct());
    }

    private boolean outputFits(int slot, ItemStack addition)
    {
        if (addition.isEmpty()) return true;
        ItemStack existing = inventory.getStackInSlot(slot);
        int limit = Math.min(inventory.getSlotLimit(slot), addition.getMaxStackSize());
        return addition.getCount() <= limit && (existing.isEmpty()
                || ItemStack.isSameItemSameTags(existing, addition) && existing.getCount() <= limit - addition.getCount());
    }

    private ItemStack merged(int slot, ItemStack addition)
    {
        ItemStack existing = inventory.getStackInSlot(slot);
        if (addition.isEmpty()) return existing.copy();
        if (existing.isEmpty()) return addition.copy();
        ItemStack merged = existing.copy();
        merged.grow(addition.getCount());
        return merged;
    }

    private void complete(processing_recipe recipe)
    {
        // 在同一个服务端刻中先重验并模拟全部槽，任何失败均不能发生部分扣料。
        int[] consumed = recipe.allocateInputs(inventory);
        ServerPlayer authorizer = currentOperator();
        if (consumed == null || authorizer == null || !canProcess(authorizer, recipe)
                || !recipe.matchesInputs(inventory) || !outputsFit(recipe)) return;
        ItemStack[] next = new ItemStack[8];
        for (int slot = 0; slot < next.length; slot++) next[slot] = inventory.getStackInSlot(slot).copy();
        for (int raw = 0; raw < 3; raw++) next[raw + 1].shrink(consumed[raw]);
        if (recipe.container() != null) next[CONTAINER].shrink(recipe.container().count());
        long damage = (long) next[TOOL].getDamageValue() + recipe.toolDamage();
        if (damage >= next[TOOL].getMaxDamage()) next[TOOL] = ItemStack.EMPTY;
        else next[TOOL].setDamageValue((int) damage); // 不调用 hurt，耐久附魔不能免除固定损耗。
        next[RESULT] = merged(RESULT, recipe.result());
        next[RETURNED] = merged(RETURNED, recipe.returned());
        next[BYPRODUCT] = merged(BYPRODUCT, recipe.byproduct());

        internalMutation = true;
        try
        {
            for (int slot = 0; slot < next.length; slot++) inventory.setStackInSlot(slot, next[slot]);
        }
        finally { internalMutation = false; }
        outputOwner = authorizer.getUUID();
        outputRecipe = recipe.getId();
        progress = 0;
        toolSnapshot = inventory.getStackInSlot(TOOL).copy();
        if (!recipe.matchesInputs(inventory)) resetProcessing();
        setChanged();
    }

    private void resetProcessing()
    {
        progress = 0;
        total = 0;
        status = IDLE;
        recipeId = null;
        recipeFingerprint = "";
        activeRecipe = null;
        toolSnapshot = ItemStack.EMPTY;
        setChanged();
    }

    /** 仅方块被替换/破坏时调用；正常区块卸载不掉落。先清空再生成实体防止重复入口。 */
    public void dropContents()
    {
        if (level == null || level.isClientSide || dropped) return;
        dropped = true;
        ItemStack[] contents = new ItemStack[8];
        internalMutation = true;
        try
        {
            for (int slot = 0; slot < contents.length; slot++)
            {
                contents[slot] = inventory.getStackInSlot(slot).copy();
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        finally { internalMutation = false; }
        for (ItemStack stack : contents)
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        resetProcessing();
    }

    @Override protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        ListTag registered=new ListTag();registeredRecipes.stream().sorted().forEach(id->registered.add(StringTag.valueOf(id.toString())));tag.put("RegisteredRecipes",registered);
        tag.putInt("Progress", progress);
        if (operator != null) tag.putUUID("Operator", operator);
        if (outputOwner != null) tag.putUUID("OutputOwner", outputOwner);
        if (outputRecipe != null) tag.putString("OutputRecipe", outputRecipe.toString());
        if (recipeId != null)
        {
            tag.putString("Recipe", recipeId.toString());
            tag.putString("RecipeFingerprint", recipeFingerprint);
            tag.put("ToolSnapshot", toolSnapshot.save(new CompoundTag()));
        }
    }

    @Override public void load(CompoundTag tag)
    {
        super.load(tag);
        registeredRecipes.clear();
        ListTag registered=tag.getList("RegisteredRecipes",8);
        for(int i=0;i<Math.min(registered.size(),16384);i++){ResourceLocation id=ResourceLocation.tryParse(registered.getString(i));if(id!=null)registeredRecipes.add(id);}
        internalMutation = true;
        try
        {
            CompoundTag savedInventory = tag.getCompound("Inventory").copy();
            savedInventory.putInt("Size", 8); // 不信任外部存档中的 Size，机器始终严格八槽。
            inventory.deserializeNBT(savedInventory);
            // NBT 载入不是玩家背包：拒绝继承其他容器的超大堆叠。
            for (int slot = 0; slot < 8; slot++)
            {
                ItemStack stack = inventory.getStackInSlot(slot);
                int maximum = Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
                if (stack.getCount() > maximum) stack.setCount(maximum);
            }
        }
        finally { internalMutation = false; }
        recipeId = ResourceLocation.tryParse(tag.getString("Recipe"));
        recipeFingerprint = tag.getString("RecipeFingerprint");
        toolSnapshot = ItemStack.of(tag.getCompound("ToolSnapshot"));
        progress = Math.max(0, Math.min(processing_recipe.MAX_PROCESSING_TIME, tag.getInt("Progress")));
        total = 0;
        status = IDLE;
        activeRecipe = null; // 首个服务端刻重新验证配方、工具快照及资源指纹。
        operator = tag.hasUUID("Operator") ? tag.getUUID("Operator") : null;
        outputOwner = hasOutputs() && tag.hasUUID("OutputOwner") ? tag.getUUID("OutputOwner") : null;
        outputRecipe = hasOutputs() ? ResourceLocation.tryParse(tag.getString("OutputRecipe")) : null;
        dropped = false;
    }
}
