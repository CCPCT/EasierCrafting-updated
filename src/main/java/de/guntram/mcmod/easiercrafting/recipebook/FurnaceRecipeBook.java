package de.guntram.mcmod.easiercrafting.recipebook;

import de.guntram.mcmod.easiercrafting.modConfig.ModConfig;
import de.guntram.mcmod.easiercrafting.recipe.RecipeTreeSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FurnaceRecipeBook extends AbstractRecipeBook {
    public static Item lastFuelUsed;
    protected final int FUEL_SLOT = 1;

    public FurnaceRecipeBook(AbstractContainerScreen<? extends AbstractContainerMenu> craftScreen, SlotDisplay craftingBlock) {
        super(craftScreen, 0, 1, 2, 3, craftingBlock);
    }

    @Override
    public void updateRecipes() {
        updateAvailableStacks();

        craftableRecipes.clear();
        allRecipes.clear();

        // add all and craftable recipes
        for (RecipeCollection collection : recipeBook.getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!(entry.display() instanceof FurnaceRecipeDisplay recipeDisplay)) continue;
                // its furnace recipe
                if (!this.canCraftScanned(entry)) continue;
                allRecipes.add(entry);
                for (ItemStack slotDisplay : recipeDisplay.ingredient().resolveForStacks(worldContext)) {
                    if (avaliableItemMap.containsKey(slotDisplay.getItem())) {
                        craftableRecipes.add(entry);
                    }
                }
            }
        }

    }

    @Override
    protected void onRecipeClicked(RecipeDisplayEntry entry, int mouseButton) {
        if (!(screenHandler instanceof FurnaceMenu container && entry.display() instanceof FurnaceRecipeDisplay recipe)) return;
        List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
        ItemStack fuelStack = container.slots.get(FUEL_SLOT).getItem();

        // retrieve/ throw smelt items
        if (container.slots.get(FIRST_RESULT_SLOT).hasItem() && !isHoldingButton(GLFW.GLFW_KEY_LEFT_CONTROL)) {
            slotClick(FIRST_RESULT_SLOT,1, isHoldingThrow() ? ContainerInput.THROW : ContainerInput.QUICK_MOVE);
        }

        // replenish fuel if possible, if fuel slot is empty let player decide what fuel to use
        if (ModConfig.get().refillFuel) if (fuelStack.getItem().equals(Items.BUCKET)){
            // just used lava as fuel...
            slotClick(FUEL_SLOT,0,ContainerInput.QUICK_MOVE);

            for (int slot = 0; slot < 36; slot++){
                ItemStack itemStack = inventory.get(slot);
                if (itemStack.getItem().equals(Items.LAVA_BUCKET)){
                    LOGGER.info("try to refill lava");
                    slotClick(slot,0,ContainerInput.QUICK_MOVE);
                    lastFuelUsed = Items.LAVA_BUCKET;
                    break;
                }
            }
        } else if (!fuelStack.isEmpty()) {
            // refill fuel
            if (avaliableItemMap.containsKey(fuelStack.getItem())){
                lastFuelUsed = fuelStack.getItem();
                LOGGER.info("try to refill fuel: {}", fuelStack.getItem().getDescriptionId());
                slotClick(FUEL_SLOT,0,ContainerInput.PICKUP);
                slotClick(FUEL_SLOT,0,ContainerInput.PICKUP_ALL);
                slotClick(FUEL_SLOT,0,ContainerInput.PICKUP);
            }
        } else if (lastFuelUsed!=null){
            // refill fuel by last used as empty
            LOGGER.info("try to refill memory: {}",lastFuelUsed.getDescriptionId());
            for (int slot = FIRST_INV_SLOT; slot < 36+ FIRST_INV_SLOT; slot++){
                ItemStack itemStack = container.slots.get(slot).getItem();
                if (itemStack.getItem().equals(lastFuelUsed)){
                    LOGGER.info("refilling memory: {}",lastFuelUsed.getDescriptionId());
                    slotClick(slot,0,ContainerInput.PICKUP);
                    slotClick(slot,0,ContainerInput.PICKUP_ALL);
                    slotClick(FUEL_SLOT,0,ContainerInput.PICKUP);
                    break;
                }
            }
        }

        // move items onto craft spot
        search:
        for (int slot = FIRST_INV_SLOT; slot < 36 + FIRST_INV_SLOT; slot++) {
            ItemStack slotContent = container.getSlot(slot).getItem();
            for (ItemStack ingredientStack : recipe.ingredient().resolveForStacks(worldContext)) {
                if (ingredientStack.getItem().equals(slotContent.getItem())){
                    // remove item if not match recipe
                    if (ingredientStack.getItem() != container.slots.get(FIRST_CRAFT_SLOT).getItem().getItem()){
                        slotClick(FIRST_CRAFT_SLOT,0,ContainerInput.QUICK_MOVE);
                    }
                    // move item up
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
    }

    // override as want to stack more items onto instead of take out everytime
    @Override
    public void mouseClicked(MouseButtonEvent click, boolean doubled, int guiLeft, int guiTop) {
        var mouseX = click.x();
        var mouseY = click.y();
        if (pattern != null) {
            boolean clickedPattern = pattern.mouseClicked(new MouseButtonEvent(mouseX-guiLeft,mouseY-guiTop,new MouseButtonInfo(0,0)), doubled);
            pattern.setFocused(clickedPattern);
            if (clickedPattern) {
                if (click.button() == 1) {
                    pattern.setValue("");
                    updatePatternMatch();
                }
                return;
            }
        }

        // Scroll bar area click
        if (mouseY > 0 && mouseY < 20 && mouseX > xOffset + containerLeft && mouseX < xOffset + containerLeft + textBoxWidth) {
            if (mouseX < xOffset + containerLeft + 20) scrollBy(-1);
            else if (mouseX > xOffset + containerLeft + textBoxWidth - 20) scrollBy(1);
            return;
        }

        if (underMouse == null) return;

        // dont craft uncraftable items
        if (!craftableRecipes.contains(underMouse)) return;

        // skip check -> implement check in onRecipeClicked
        onRecipeClicked(underMouse, click.button());
        queueUpdateRecipe();
    }

    @Override
    protected void drawRecipeGridOverlay(GuiGraphicsExtractor context) {
        if (!(underMouse.display() instanceof FurnaceRecipeDisplay recipe)) return;
        ItemStack result = recipe.result().resolveForFirstStack(worldContext).copy();
        boolean canCraft = canCraft(underMouse);
        if (canCraft){
            int i;
            Item item = null;
            if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)){
                for (i=0; i<recipe.ingredient().resolveForStacks(worldContext).size(); i++){
                    item = recipe.ingredient().resolveForStacks(worldContext).get(i).getItem();
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
            craftableCategories.computeIfAbsent(ModConfig.get().categorizeRecipes ?
                            getTranslatedItemGroup(entry) :
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
        if (!(entry.display() instanceof FurnaceRecipeDisplay recipe)) return false;
        for (ItemStack i : recipe.ingredient().resolveForStacks(worldContext)) {
            if (avaliableItemMap.containsKey(i.getItem())) return true;
        }
        return false;
    }

    protected List<ItemStack> getIngredients(RecipeDisplayEntry entry) {
        if (!(entry.display() instanceof FurnaceRecipeDisplay recipe)) return null;
        List<ItemStack> craftable = recipe.ingredient().resolveForStacks(worldContext).stream()
                .filter(stack -> getAvailableItemSet().contains(stack.getItem()))
                .toList();

        if (craftable.isEmpty()){
            return recipe.ingredient().resolveForStacks(worldContext);
        } else {
            return craftable;
        }
    }

}
