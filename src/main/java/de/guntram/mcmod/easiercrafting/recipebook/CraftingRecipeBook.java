package de.guntram.mcmod.easiercrafting.recipebook;

import de.guntram.mcmod.easiercrafting.EasierCrafting;
import de.guntram.mcmod.easiercrafting.extendedScreen.ExtendedInventoryScreen;
import de.guntram.mcmod.easiercrafting.modConfig.ModConfig;
import de.guntram.mcmod.easiercrafting.recipe.RecipeTreeSet;
import de.guntram.mcmod.easiercrafting.recipe.RepairCraftingRecipeDisplay;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static de.guntram.mcmod.easiercrafting.EasierCrafting.SPECIAL_CAT;

public class CraftingRecipeBook extends AbstractRecipeBook {


    public CraftingRecipeBook(AbstractContainerScreen<? extends AbstractContainerMenu> craftScreen, int FIRST_CRAFT_SLOT, int GRID_SIZE, int resultSlot, int firstInventorySlot, SlotDisplay craftingBlock) {
        super(craftScreen, FIRST_CRAFT_SLOT, GRID_SIZE, resultSlot, firstInventorySlot, craftingBlock);
        RecipeBookCats = List.of(RecipeBookCategories.CRAFTING_MISC, RecipeBookCategories.CRAFTING_REDSTONE, RecipeBookCategories.CRAFTING_EQUIPMENT, RecipeBookCategories.CRAFTING_BUILDING_BLOCKS);
    }


    @Override
    public void updateRecipes() {
        assert player != null;

        LOGGER.info("called updateRecipes");

        updateAvailableStacks();

        craftableRecipes.clear();
        allRecipes.clear();

        populateAllRecipe();

        for (RecipeDisplayEntry entry : allRecipes) {
            // craftable entries
            // in player inventory... filter out big recipes
            if (screen instanceof ExtendedInventoryScreen && entry.display() instanceof ShapedCraftingRecipeDisplay recipe && (recipe.width() > GRID_SIZE || recipe.height() > GRID_SIZE)) {
                continue;
            }

            if (entry.display() instanceof ShapelessCraftingRecipeDisplay recipe && recipe.ingredients().size() > GRID_SIZE * GRID_SIZE) {
                continue;
            }

            if (canCraftScanned(entry)) {
                craftableRecipes.add(entry);
            }
        }

        if (ModConfig.get().allowGeneratedRecipes) {
            addUnusualRecipe();
            allRecipes.removeIf(entry ->
                    entry.category() == RecipeBookCategories.CRAFTING_MISC && entry.display().result().resolveForFirstStack(worldContext).getItem()==Items.FIREWORK_ROCKET
            );
            craftableRecipes.removeIf(entry ->
                    entry.category() == RecipeBookCategories.CRAFTING_MISC && entry.display().result().resolveForFirstStack(worldContext).getItem()==Items.FIREWORK_ROCKET
            );
        }
    }

    @Override
    protected boolean refreshCategories() {
        LOGGER.info("called refreshCategories");
        craftableCategories.clear();
        int tempHash = 0;
        for (RecipeDisplayEntry entry : craftableRecipes) {
            String category;
            if (!ModConfig.get().categorizeRecipes) {
                // dont categorize recipes
                category = DEFAULT_CAT;
            } else if (Objects.requireNonNull(getCat(entry)).getNamespace().startsWith(EasierCrafting.MODID)) {
                // generated recipe
                if (entry.display() instanceof RepairCraftingRecipeDisplay){
                    category = "Repair";
                } else {
                    category = I18n.get("easiercrafting.category.special");
                }
            } else {
                category = getTranslatedItemGroup(entry);
            }
            //LOGGER.info("added: {} to category {}", entry.display().result().getFirst(worldContext).getName().getString(), category);
            craftableCategories.computeIfAbsent(category, k -> new RecipeTreeSet()).add(entry);
            tempHash^=entry.id().index();
        }
        recalcListSize();
        boolean changed = tempHash==categoryHash;
        categoryHash=tempHash;
        return changed;
    }

    @Override
    protected void drawRecipeGridOverlay(GuiGraphicsExtractor context) {
        // get info
        int recipeWidth;
        List<SlotDisplay> ingredients;
        int maxCraftableStacks=1;
        Slot resultSlot = screenHandler.getSlot(FIRST_RESULT_SLOT);
        switch (underMouse.display()) {
            case ShapedCraftingRecipeDisplay shaped -> {
                recipeWidth = shaped.width();
                ingredients = shaped.ingredients();
                if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)) maxCraftableStacks = getMaxCraftable(ingredients);
            }
            case ShapelessCraftingRecipeDisplay shapeless -> {
                ingredients = shapeless.ingredients();
                recipeWidth = GRID_SIZE;
                if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)) maxCraftableStacks = getMaxCraftable(ingredients);
            }
            case RepairCraftingRecipeDisplay repairDisplay -> {
                // repair formular: durability = min(Item A uses + Item B uses + floor(Max uses / 20), Max uses) (from wiki)
                // damage = max(max-((max-damage a)+(max-damage b)+floor(Max/20)),0)
                // damage = max(damage a + damage b - floor(Max/20) - max , 0)
                // so best and easiest approach would be adding items with min durability
                drawHoloItem(context, resultSlot, repairDisplay.result().resolveForFirstStack(worldContext));
                drawHoloItem(context, screenHandler.getSlot(FIRST_CRAFT_SLOT), repairDisplay.ingredients().getFirst().resolveForFirstStack(worldContext));
                drawHoloItem(context, screenHandler.getSlot(FIRST_CRAFT_SLOT +1), repairDisplay.ingredients().get(1).resolveForFirstStack(worldContext));

                return;
            }
            default -> {
                return;
            }
        }

        // render result
        ItemStack resultStack = underMouse.display().result().resolveForFirstStack(worldContext).copy();
        if (!(underMouse.display() instanceof RepairCraftingRecipeDisplay)) {
            resultStack.setCount(maxCraftableStacks*resultStack.getCount());
        }

        boolean canCraft = canCraft(underMouse);
        drawHoloItem(context, resultSlot, resultStack);
        if (!canCraft) context.fill(
                resultSlot.x-2+containerLeft,
                resultSlot.y-2+containerTop,
                resultSlot.x+ ITEM_SIZE +2+containerLeft,
                resultSlot.y+ ITEM_SIZE +2+containerTop,
                CANT_CRAFT_COLOUR);

        Object2IntOpenHashMap<Item> tempMap = avaliableItemMap.clone();

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < recipeWidth; x++) {
                if (y*recipeWidth+x >= ingredients.size()) return;
                SlotDisplay ingredient = ingredients.get(y*recipeWidth+x);
                if (ingredient.resolveForFirstStack(worldContext).isEmpty()) continue;
                if (!canCraft){
                    renderIngredient(context, ingredient.resolveForStacks(worldContext), screenHandler.getSlot(FIRST_CRAFT_SLOT + y* GRID_SIZE +x));
                    continue;
                }
                for (ItemStack stack : ingredient.resolveForStacks(worldContext)){
                    // cant craft
                    if (tempMap.getInt(stack.getItem())<=0) continue;
                    tempMap.addTo(stack.getItem(),-1);
                    renderCraftingIngredient(context,stack,screenHandler.getSlot(FIRST_CRAFT_SLOT + y* GRID_SIZE +x));
                    break;
                }
            }
        }
    }

    public void renderCraftingIngredient(GuiGraphicsExtractor context, ItemStack stack, Slot slot) {
        if (stack.isEmpty()) return;
        drawHoloItem(context,slot,stack);
    }

    @Override
    protected void onRecipeClicked(RecipeDisplayEntry entry, int mouseButton) {
        List<Ingredient> recipeInput = entry.craftingRequirements().orElse(Collections.emptyList());
        if (recipeInput.isEmpty()) return;

        var resultStack = entry.display().result().resolveForFirstStack(worldContext);
        Item resultItem = resultStack.getItem();
        int maxCraftableStacks = 1;
        int rowAdjust = 0;
        int recipeWidth;
        List<SlotDisplay> ingredients;

        switch (entry.display()) {
            case ShapedCraftingRecipeDisplay shaped -> {
                recipeWidth = shaped.width();
                ingredients = shaped.ingredients();
                if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)) maxCraftableStacks = getMaxCraftable(ingredients);
            }
            case ShapelessCraftingRecipeDisplay shapeless -> {
                ingredients = shapeless.ingredients();
                recipeWidth = ingredients.size() <= 4 ? 2 : 3;
                if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)) maxCraftableStacks = getMaxCraftable(ingredients);
            }
            case RepairCraftingRecipeDisplay repairDisplay -> {
                ingredients = repairDisplay.ingredients();
                recipeWidth = 2;
            }
            default -> {
                return;
            }
        }

        if (recipeWidth > GRID_SIZE) return;

        // remove leftovers e.g. bucket/ glass bottles
        boolean[] removal = new boolean[ingredients.size()];

        // move item on crafting grid
        boolean repairOccupied = false;
        for (int i = 0; i < ingredients.size(); i++) {
            int remaining = maxCraftableStacks;
            SlotDisplay ingredient = ingredients.get(i);
            if (ingredient.resolveForStacks(worldContext).isEmpty()) continue;

            for (int slot = FIRST_INV_SLOT; remaining > 0 && slot < 36 + FIRST_INV_SLOT; slot++) {
                ItemStack slotcontent = screenHandler.getSlot(slot).getItem();
                // yes awful nested if statements... find a better logic for me then... but bear in mind mine works :3
                if (entry.display() instanceof RepairCraftingRecipeDisplay recipe){
                    if (slotcontent.getItem()!=recipe.result().resolveForFirstStack(worldContext).getItem()) continue;
                    if (slotcontent.getDamageValue()>=recipe.ingredients().get(1).resolveForFirstStack(worldContext).getDamageValue()){
                        // not as damage as most damaged
                        if (slotcontent.getDamageValue()<recipe.ingredients().getFirst().resolveForFirstStack(worldContext).getDamageValue()){
                            if (repairOccupied) continue;
                            repairOccupied = true;
                        }
                    } else continue;
                }
                else if (!canActAsIngredient(ingredient, slotcontent)) continue;
                transfer(slot, i + FIRST_CRAFT_SLOT + rowAdjust, remaining);
                ItemStack inCraftSlot = screenHandler.getSlot(i + FIRST_CRAFT_SLOT + rowAdjust).getItem();
                remaining = maxCraftableStacks - inCraftSlot.getCount();
                if (inCraftSlot.getCraftingRemainder()!=null && inCraftSlot.getCraftingRemainder().count()>0) {
                    removal[i] = true;
                }

            }
            if (recipeWidth > 0 && (i + 1) % recipeWidth == 0) {
                rowAdjust += GRID_SIZE - recipeWidth;
            }
        }

        // actually craft item: hold control or right click to not instantly craft, hold q to drop
        if (mouseButton == 0 && !isHoldingButton(GLFW.GLFW_KEY_LEFT_CONTROL)) {
            craft:
            if (isHoldingButton(GLFW.GLFW_KEY_Q)){
                if (isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                    // icl but lazy method works well...
                    LOGGER.info("throw craft all: {} {}", maxCraftableStacks, resultStack.getCount());

                    int totalCraft = resultStack.getCount()*maxCraftableStacks;
                    int resultMaxStackSize = resultStack.getMaxStackSize();

                    if (totalCraft <= resultMaxStackSize && screenHandler.getSlot(FIRST_INV_SLOT +35).getItem().getItem()!=resultStack.getItem()) {
                        // special case where dont need to repeat throw
                        LOGGER.info("trying quick method of quick craft");
                        slotClick(FIRST_RESULT_SLOT, 0, ContainerInput.PICKUP);
                        slotClick(FIRST_INV_SLOT +35, 0, ContainerInput.PICKUP);
                        slotClick(FIRST_RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
                        slotClick(FIRST_INV_SLOT +35, 0, ContainerInput.PICKUP);
                        // -999 = outside screen
                        slotClick(-999, 0, ContainerInput.PICKUP);

                        break craft;
                    }

                    // another shortcut
                    // celling div
                    int requiringSlotsCount = (totalCraft+resultMaxStackSize-1)/resultMaxStackSize;
                    boolean alreadyHad = false;
                    NonNullList<Slot> slots = screenHandler.slots;
                    final IntArrayList emptySlots = new IntArrayList(slots.size()-FIRST_INV_SLOT);
                    for (int i = FIRST_INV_SLOT, slotsSize = slots.size(); i < slotsSize; i++) {
                        var slot = slots.get(i);
                        if (slot.getItem().getItem() == resultItem) {
                            alreadyHad = true;
                            break;
                        }
                        if (slot.getItem().isEmpty()) {
                            emptySlots.add(i);
                            if (emptySlots.size() >= requiringSlotsCount) {
                                break;
                            }
                        }
                    }
                    if (!alreadyHad && requiringSlotsCount <= emptySlots.size()) {
                        for (int i = 0, limit = (requiringSlotsCount + resultStack.getCount() - 1) / resultStack.getCount(); i < limit; i++) {
                            slotClick(FIRST_RESULT_SLOT, 0, ContainerInput.PICKUP);
                        }
                        for (int i = 0; i < requiringSlotsCount-1; i++) {
                            var slotIndex = emptySlots.getInt(i);
                            slotClick(slotIndex, 1, ContainerInput.PICKUP);
                        }

                        slotClick(emptySlots.getInt(requiringSlotsCount-1), 0, ContainerInput.PICKUP);
                        slotClick(FIRST_RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);

                        for (int i = 0; i < requiringSlotsCount; i++) {
                            var slotIndex = emptySlots.getInt(i);
                            LOGGER.info("extra slot: {}",slotIndex);
                            slotClick(slotIndex, 1, ContainerInput.THROW);
                        }
                        break craft;
                    }


                    // lazy default
                    for (int i = 0; i < maxCraftableStacks; i++) {
                        slotClick(FIRST_RESULT_SLOT, 1, ContainerInput.THROW);
                    }
                } else {
                    slotClick(FIRST_RESULT_SLOT, 0, ContainerInput.THROW);
                }
            } else {
                slotClick(FIRST_RESULT_SLOT, 1, ContainerInput.QUICK_MOVE);
            }
            queueUpdateRecipe();

            // remove leftover
            removeLeftover(recipeWidth, removal);
        } else if (isHoldingButton(GLFW.GLFW_KEY_Q) && isHoldingButton(GLFW.GLFW_KEY_LEFT_SHIFT)) {
            LOGGER.info(resultItem.getDescriptionId());
            slotClick(FIRST_RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
            for (var slot : screenHandler.slots) {
                if (slot.index <= FIRST_INV_SLOT) {
                    continue;
                }
                if (slot.getItem().getItem() == resultItem) {
                    slotClick(slot.index, 1, ContainerInput.THROW);
                }
            }

            // remove leftover
            removeLeftover(recipeWidth, removal);
        }
        // prevent shift craft multiple stack last stack craft wrong
        updateRecipes();
    }

    private void removeLeftover(int recipeWidth, boolean[] removal) {
        int rowAdjust = 0;
        for (int i = 0; i < removal.length; i++) {
            if (removal[i]) {
                slotClick(FIRST_CRAFT_SLOT + i + rowAdjust, 0, ContainerInput.QUICK_MOVE);
            }
            if (recipeWidth > 0 && (i + 1) % recipeWidth == 0) {
                rowAdjust += GRID_SIZE - recipeWidth;
            }
        }
    }

    private boolean canActAsIngredient(SlotDisplay ingredient, ItemStack inventoryItem) {
        if (inventoryItem.isEmpty()) return false;
        List<ItemStack> validStacks = ingredient.resolveForStacks(worldContext);
        return validStacks.stream().anyMatch(validStack -> validStack.getItem() == inventoryItem.getItem());
    }

    public void transfer(int from, int to, int amount) {
        Slot fromSlot = screenHandler.getSlot(from);
        ItemStack fromContent = fromSlot.getItem();

        if (amount >= fromSlot.getItem().getCount()) {
            slotClick(from, 0, ContainerInput.PICKUP);
            slotClick(to, 0, ContainerInput.PICKUP);
            return;
        }

        int transfer;
        while (amount >= (transfer = ((fromSlot.getItem().getCount() + 1) / 2))) {
            slotClick(from, 1, ContainerInput.PICKUP);
            slotClick(to, 0, ContainerInput.PICKUP);
            amount -= transfer;
        }

        if (amount > 0) {
            int prevCount = fromContent.getCount();
            slotClick(from, 0, ContainerInput.PICKUP);
            for (int i = 0; i < amount; i++)
                slotClick(to, 1, ContainerInput.PICKUP);
            if (prevCount > amount)
                slotClick(from, 0, ContainerInput.PICKUP);
        }
    }

    private int getMaxCraftable(List<SlotDisplay> ingredients){
        int maxCraftableStacks = 64;
        Map<Item, Integer> ingredientMap = new HashMap<>();
        for (SlotDisplay ingredient : ingredients){
            List<ItemStack> chosenList = getCraftableStacks(ingredient);
            if (chosenList.isEmpty()) continue;
            Item chosenItem = chosenList.getFirst().getItem();

            // If chosenItem exists, add 1 to the current value.
            // If it doesn't exist, set the value to 1.
            ingredientMap.merge(chosenItem, 1, Integer::sum);
        }

        for (Map.Entry<Item, Integer> ingredientSet : ingredientMap.entrySet()) {
            maxCraftableStacks = Math.min(Math.min(maxCraftableStacks, avaliableItemMap.getInt(ingredientSet.getKey())/ingredientSet.getValue()),ingredientSet.getKey().getDefaultMaxStackSize());
        }
        return maxCraftableStacks;
    }

    @Override
    protected boolean canCraftScanned(RecipeDisplayEntry entry) {
        updateAvailableStacks();
        Object2IntOpenHashMap<Item> tempMap = avaliableItemMap.clone();
        if (entry.craftingRequirements().isEmpty()) return false;
        List<Ingredient> ingredients= entry.craftingRequirements().get();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            boolean canCraft = false;
            for (ItemStack stack : ingredient.display().resolveForStacks(worldContext)) {
                // cant craft
                if (tempMap.getInt(stack.getItem()) <= 0) continue;
                canCraft = true;
                tempMap.addTo(stack.getItem(), -1);
                break;
            }
            if (!canCraft) return false;
        }

        return true;
    }

    private void addUnusualRecipe(){
        addFireworkRecipe();
        addRepairRecipe();
    }
    private void addFireworkRecipe(){
        int gunPowerCount = avaliableItemMap.getInt(Items.GUNPOWDER);
        if (avaliableItemMap.containsKey(Items.PAPER) && gunPowerCount > 0) {
            // power one is already there
            for (int power = 1; power <= 3; power++) {
                if (gunPowerCount >= power) {
                    // 2. Prepare the Result Item
                    ItemStack resultItem = new ItemStack(Items.FIREWORK_ROCKET, 3);

                    // In 1.21, FireworksComponent is a record: (int flightDuration, List<FireworkExplosionComponent> explosions)
                    // We pass an empty list for explosions if there are none.
                    resultItem.set(DataComponents.FIREWORKS, new Fireworks(power, List.of()));
                    resultItem.set(DataComponents.CUSTOM_NAME, Component.literal("Firework strength: " + power));

                    // 3. Prepare the Ingredients List for the Display
                    List<SlotDisplay> ingredientsDisplay = new ArrayList<>();
                    List<Ingredient> ingredients = new ArrayList<>();
                    ingredients.add(Ingredient.of(Items.PAPER));
                    ingredientsDisplay.add(getSlotDisplay(Items.PAPER));
                    for (int k = 0; k < power; k++) {
                        ingredientsDisplay.add(getSlotDisplay(Items.GUNPOWDER));
                        ingredients.add(Ingredient.of(Items.GUNPOWDER));
                    }



                    // 4. Create the Display logic
                    ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(
                            ingredientsDisplay,
                            getSlotDisplay(resultItem),
                            CRAFTING_STATION
                    );

                    // 5. Add to your results list
                    RecipeDisplayEntry entry = new RecipeDisplayEntry(
                            new RecipeDisplayId(670 + power), // Use unique IDs to avoid UI glitches
                            display,
                            OptionalInt.empty(),
                            SPECIAL_CAT,
                            Optional.of(ingredients)
                    );

                    allRecipes.add(entry);
                    craftableRecipes.add(entry);
                }
            }
        }

    }

    private void addRepairRecipe(){
        Object2IntOpenHashMap<Item> item = new Object2IntOpenHashMap<>(16);
        for (ItemStack itemStack : player.getInventory().getNonEquipmentItems()){
            if (itemStack.isEmpty()|| !itemStack.isDamaged() || itemStack.isEnchanted()) continue;
            // have breaking item
            item.addTo(itemStack.getItem(), 1);
        }

        for (Map.Entry<Item, Integer> set : item.object2IntEntrySet()) {
            if (set.getValue()<2) continue;

            Item ingredient = set.getKey();
            ItemStack resultStack = ingredient.getDefaultInstance();
            ItemStack ingredientA = ingredient.getDefaultInstance();
            ItemStack ingredientB = ingredient.getDefaultInstance();

            int damageA = 0;
            int damageB = 0;
            // find 2 slot with max damage
            for (int slot = FIRST_INV_SLOT; slot < 36 + FIRST_INV_SLOT; slot++) {
                ItemStack slotContent = screenHandler.getSlot(slot).getItem();
                if (slotContent.getItem()!=ingredient||slotContent.isEnchanted()) continue;
                if (slotContent.getDamageValue() > damageA) {
                    damageB = damageA;
                    damageA = slotContent.getDamageValue();
                } else if (slotContent.getDamageValue() > damageB) {
                    damageB = slotContent.getDamageValue();
                }
            }

            ingredientA.setDamageValue(damageA);
            ingredientB.setDamageValue(damageB);
            resultStack.setDamageValue(Integer.max(damageA + damageB - resultStack.getMaxDamage()/20 - resultStack.getMaxDamage() , 0));


            // add the repairRecipe
            RepairCraftingRecipeDisplay display = new RepairCraftingRecipeDisplay(
                    List.of(getSlotDisplay(ingredientA),getSlotDisplay(ingredientB)),
                    getSlotDisplay(resultStack),
                    CRAFTING_STATION
            );

            RecipeDisplayEntry entry = new RecipeDisplayEntry(
                    // use hash code of the item
                    new RecipeDisplayId(set.getKey().hashCode()+resultStack.getDamageValue()),
                    display,
                    OptionalInt.empty(),
                    SPECIAL_CAT,
                    Optional.of(Collections.nCopies(2, Ingredient.of(set.getKey())))
            );

            allRecipes.add(entry);
            craftableRecipes.add(entry);
        }

    }
}
