package de.guntram.mcmod.easiercrafting.recipebook;

import de.guntram.mcmod.easiercrafting.modConfig.ModConfig;
import de.guntram.mcmod.easiercrafting.recipe.RecipeTreeSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class StonecutterRecipeBook extends AbstractRecipeBook {

    public StonecutterRecipeBook(AbstractContainerScreen<? extends AbstractContainerMenu> craftScreen) {
        super(craftScreen, 0, 1, 1, 2,  getSlotDisplay(Items.STONECUTTER));
    }


    @Override
    public void updateRecipes() {
        updateAvailableStacks();

        craftableRecipes.clear();
        allRecipes.clear();

        // add all recipes
        for (RecipeCollection collection : recipeBook.getCollection(RecipeBookCategories.STONECUTTER)){
            allRecipes.addAll(collection.getRecipes());
        }

        // add craftable recipes
        for (RecipeDisplayEntry entry : allRecipes){
            if (!(entry.display() instanceof StonecutterRecipeDisplay) || !this.canCraftScanned(entry)) continue;
            craftableRecipes.add(entry);
        }
    }


    @Override
    protected void onRecipeClicked(RecipeDisplayEntry entry, int mouseButton) {
        MultiPlayerGameMode interactionManager = client.gameMode;
        if (!(screenHandler instanceof StonecutterMenu container)||!(entry.display() instanceof StonecutterRecipeDisplay recipe)) {
            return;
        }

        // no item -> return
        if (!canCraft(entry)) return;

        // move item to crafting slot
        search:
        for (int slot = FIRST_INV_SLOT; slot < 36 + FIRST_INV_SLOT; slot++) {
            ItemStack slotContent = container.getSlot(slot).getItem();
            for (ItemStack ingredientStack : recipe.input().resolveForStacks(worldContext)) {
                if (ingredientStack.getItem().equals(slotContent.getItem())){
                    if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                        slotClick(slot, 0, ContainerInput.PICKUP);
                        slotClick(slot, 0, ContainerInput.PICKUP_ALL);
                        slotClick(FIRST_CRAFT_SLOT, 0, ContainerInput.PICKUP);
                        slotClick(slot, 0, ContainerInput.PICKUP);
                    } else {
                        slotClick(slot, 0, ContainerInput.PICKUP);
                        slotClick(FIRST_CRAFT_SLOT, 1, ContainerInput.PICKUP);
                        slotClick(slot, 0, ContainerInput.PICKUP);
                    }
                    break search;
                }
            }
        }

        // click the recipe button (select the recipe)
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> available = container.getVisibleRecipes().entries();
        int buttonIndex = -1;

        for (int i = 0; i < available.size(); i++) {
            // Compare the recipe entries directly.
            if (available.get(i).recipe().optionDisplay().equals(recipe.result())) {
                buttonIndex = i;
                break;
            }
        }

        if (buttonIndex != -1 && interactionManager != null) {
            // 3. Select the recipe by clicking the button with the index
            interactionManager.handleInventoryButtonClick(container.containerId, buttonIndex);

            // 4. Take the result from the output slot (slot 1) to complete the craft
            if (isHoldingButton(GLFW.GLFW_KEY_LEFT_CONTROL)) return;
            slotClick(1, 0, isHoldingThrow() ? ContainerInput.THROW : ContainerInput.QUICK_MOVE);
        }
    }

    @Override
    protected void drawRecipeGridOverlay(GuiGraphicsExtractor context) {
        if (!(underMouse.display() instanceof StonecutterRecipeDisplay recipe)) return;
        ItemStack result = recipe.result().resolveForFirstStack(worldContext).copy();
        boolean canCraft = canCraft(underMouse);
        if (canCraft){
            int i;
            Item item = null;
            if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)){
                for (i=0; i<recipe.input().resolveForStacks(worldContext).size(); i++){
                    item = recipe.input().resolveForStacks(worldContext).get(i).getItem();
                    if (avaliableItemMap.containsKey(item)){
                        result.setCount(Math.min(avaliableItemMap.getInt(item)*result.getCount(),item.getDefaultMaxStackSize()));
                        break;
                    }
                }
            }
            assert item != null;

        }

        // draw result
        Slot resultSlot = screenHandler.getSlot(FIRST_RESULT_SLOT);
        drawHoloItem(context,resultSlot,result);

        if (!canCraft) context.fill(resultSlot.x-2,resultSlot.y-2,resultSlot.x+ ITEM_SIZE +2,resultSlot.y+ ITEM_SIZE +2,0x60FF0000);

        renderIngredient(context, getIngredients(underMouse), screenHandler.getSlot(FIRST_CRAFT_SLOT));
    }

    @Override
    protected boolean refreshCategories() {
        craftableCategories.clear();
        int tempHash = 0;
        for (RecipeDisplayEntry entry : craftableRecipes) {
            if (!(entry.display() instanceof StonecutterRecipeDisplay recipe)) continue;
            craftableCategories.computeIfAbsent(ModConfig.get().categorizeRecipes ?
                            I18n.get(recipe.input().resolveForFirstStack(worldContext).getItem().getDescriptionId()) :
                            DEFAULT_CAT,
                    k -> new RecipeTreeSet()).add(entry);
            tempHash^=entry.id().index();
        }
        boolean changed = tempHash==categoryHash;
        categoryHash=tempHash;
        recalcListSize();
        return changed;
    }

    @Override
    protected boolean canCraftScanned(RecipeDisplayEntry entry) {
        if (!(entry.display() instanceof StonecutterRecipeDisplay recipe)) return false;
        for (ItemStack i : recipe.input().resolveForStacks(worldContext)) {
            if (avaliableItemMap.containsKey(i.getItem())) return true;
        }
        return false;
    }

    protected List<ItemStack> getIngredients(RecipeDisplayEntry entry) {
        if (!(entry.display() instanceof StonecutterRecipeDisplay recipe)) return null;
        List<ItemStack> craftable = recipe.input().resolveForStacks(worldContext).stream()
                .filter(stack -> getAvailableItemSet().contains(stack.getItem()))
                .toList();

        if (craftable.isEmpty()){
            return recipe.input().resolveForStacks(worldContext);
        } else {
            return craftable;
        }
    }

}
