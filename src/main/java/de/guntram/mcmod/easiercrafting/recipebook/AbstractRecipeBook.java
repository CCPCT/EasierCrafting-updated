package de.guntram.mcmod.easiercrafting.recipebook;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.guntram.mcmod.easiercrafting.EasierCrafting;
import de.guntram.mcmod.easiercrafting.modConfig.ModConfig;
import de.guntram.mcmod.easiercrafting.recipe.RecipeTreeSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class AbstractRecipeBook {

    protected final Logger LOGGER;
    static final Object2IntOpenHashMap<Item> avaliableItemMap = new Object2IntOpenHashMap<>(36);
    ContextMap worldContext;
    public static final ContextMap EMPTY_CONTEXT = new ContextMap.Builder().create(new ContextKeySet.Builder().build());



    // Protected fields for subclasses
    public final AbstractContainerScreen<? extends AbstractContainerMenu> screen;
    protected final int FIRST_CRAFT_SLOT;
    protected final int GRID_SIZE;
    protected final int FIRST_RESULT_SLOT;
    protected final int FIRST_INV_SLOT;
    protected final SlotDisplay CRAFTING_STATION;
    protected ClientRecipeBook recipeBook;
    protected final Font textRenderer;
    public int screenYOffset = 0;
    protected MultiPlayerGameMode interactionManager;
    protected int categoryHash = 0;
    public final String DEFAULT_CAT = I18n.get("easiercrafting.category.possible");
    List<RecipeBookCategory> RecipeBookCats;

    protected final Window window;

    public final ObjectArrayList<RecipeDisplayEntry> craftableRecipes = new ObjectArrayList<>();
    public final ObjectArrayList<RecipeDisplayEntry> allRecipes = new ObjectArrayList<>();
    public final TreeMap<String, RecipeTreeSet> craftableCategories = new TreeMap<>();
    public RecipeDisplayEntry underMouse;

    protected final Minecraft client;
    protected final LocalPlayer player;
    protected final ClientLevel world;
    protected final AbstractContainerMenu screenHandler;

    // Layout
    public static final int ITEM_SIZE = 16;
    public static int itemDisplaySpacing = ModConfig.get().itemDisplaySpacing;
    public static int displayItemSize = ITEM_SIZE +itemDisplaySpacing*2;
    protected final int itemLift = 5;
    protected int listSize;
    protected int itemsPerRow;
    protected int xOffset;
    protected int mouseScroll;
    protected int minYtoDraw = 26;
    protected int textBoxWidth;
    protected int containerLeft;
    protected int containerTop;
    protected final int CANT_CRAFT_COLOUR = 0x60FF0000;

    // Search & Updates
    protected long recipeUpdateTime = 0;
    protected long recipeFadeTime = 0;
    public EditBox pattern;
    public RecipeTreeSet patternMatchingRecipes;
    public int patternListSize;

    /**
     * Factory method to create the correct RecipeBook instance.
     */

    protected AbstractRecipeBook(AbstractContainerScreen<? extends AbstractContainerMenu> craftScreen, int firstCraftSlotNo, int gridsize, int resultSlot, int firstInventorySlot, SlotDisplay craftingBlock) {
        this.client = Minecraft.getInstance();
        this.world = client.level;
        assert client.player != null;
        assert world != null;

        this.screen = craftScreen;
        this.textRenderer = client.font;
        this.FIRST_CRAFT_SLOT = firstCraftSlotNo;
        this.GRID_SIZE = gridsize;
        this.FIRST_RESULT_SLOT = resultSlot;
        this.FIRST_INV_SLOT = firstInventorySlot;
        this.pattern = new EditBox(textRenderer, 0, screenYOffset, 10, 20, Component.empty()); // update width later
        this.underMouse = null;
        this.player = client.player;
        this.worldContext = SlotDisplayContext.fromLevel(world);
        this.LOGGER = LogManager.getLogger(craftScreen.getMenu());
        this.CRAFTING_STATION = craftingBlock;
        this.recipeBook = player.getRecipeBook();
        this.screenHandler = screen.getMenu();
        this.window = client.getWindow();
        this.interactionManager = client.gameMode;
        itemDisplaySpacing = ModConfig.get().itemDisplaySpacing;
        displayItemSize = ITEM_SIZE +itemDisplaySpacing*2;
    }

    // --- Abstract Methods to be implemented by subclasses ---

    /**
     * Called to update all avaliable and craftable recipes (the 2 sets). Return if not updated anything/ remain unchanged
     */
    public abstract void updateRecipes();

    /**
     * Called when a recipe in the list is clicked.
     */
    protected abstract void onRecipeClicked(RecipeDisplayEntry entry, int mouseButton);

    /**
     * Called to draw the overlay (e.g. 3x3 grid) when hovering over a recipe.
     */
    protected abstract void drawRecipeGridOverlay(GuiGraphicsExtractor context);
    /**
     * refresh display catagory of craftable.
     */
    protected abstract boolean refreshCategories();

    // see if recipe can actually be crafted (not by checking can craft tab)
    protected abstract boolean canCraftScanned(RecipeDisplayEntry entry);


    // draw outputs... and set undermouse
    protected int drawSetOfRecipes(GuiGraphicsExtractor context, RecipeTreeSet treeSet, int xpos, int ypos, int screenBottom, int mouseX, int mouseY) {
        if (treeSet == null || treeSet.isEmpty()) return ypos;

        if (ModConfig.get().debug) {
            context.outline(0,0,100,100,0xFFFF0000);
            context.fill(0,0,50,50,0xFFFF0000);
        }

        mouseX+=containerLeft;
        mouseY+=containerTop;
        for (RecipeDisplayEntry recipe : treeSet) {
            if (ypos>screenBottom) return ypos;
            if (ypos >= minYtoDraw) {
                int x = xOffset + xpos;
                int y = ypos - itemLift;
                boolean canCraft = canCraft(recipe);
                if (!canCraft) {
                    // if cant craft draw red background on the result
                    context.fill(x-itemDisplaySpacing,y-itemDisplaySpacing,x+ ITEM_SIZE +itemDisplaySpacing,y+ ITEM_SIZE +itemDisplaySpacing,CANT_CRAFT_COLOUR);
                }

                renderSingleRecipeOutput(context, textRenderer, recipe.display().result().resolveForFirstStack(worldContext), x, y);

                if (mouseX >= x &&
                        mouseX <= x + displayItemSize - 1 &&
                        mouseY >= y &&
                        mouseY <= y + displayItemSize - 1)
                {
                    underMouse = recipe;
                    // render background behind hovered item
                    context.fill(x-itemDisplaySpacing,y-itemDisplaySpacing,x+ ITEM_SIZE +itemDisplaySpacing,y+ ITEM_SIZE +itemDisplaySpacing,0x50E0E0E0);
                    // render recipe overlay
                    drawRecipeGridOverlay(context);
                }
            }
            xpos += displayItemSize;
            if (xpos >= displayItemSize * itemsPerRow) {
                ypos += displayItemSize;
                xpos = 0;
            }
        }
        if (xpos != 0) ypos += displayItemSize;
        return ypos;
    }

    // --- Common Logic ---

    public void afterInitGui() {
        final int distanceFromGui = 25;
        this.containerLeft = (screen.width - 176) / 2;
        this.containerTop = (screen.height - 166) / 2;

        int tempItemsPerRow = ModConfig.get().itemsPerRow; // max item per row
        int tempXOffset = -displayItemSize * tempItemsPerRow - distanceFromGui;
        if (tempXOffset + containerLeft < 0) {
            tempItemsPerRow = (containerLeft - distanceFromGui) / displayItemSize;
            tempXOffset = -displayItemSize * tempItemsPerRow - distanceFromGui;
        }
        if (ModConfig.get().showGuiRight)
            tempXOffset = 176 + distanceFromGui;
        if (tempItemsPerRow < 2) {
            LOGGER.warn("forcing tempItemsPerRow to 2 when it's {}", tempItemsPerRow);
            tempItemsPerRow = 2;
        }
        this.itemsPerRow = tempItemsPerRow;
        this.xOffset = tempXOffset + containerLeft;
        updatePatternMatch();
        mouseScroll = 0;
        updateRecipes();
        refreshCategories();

        pattern.setX(xOffset);
        textBoxWidth =itemsPerRow*displayItemSize;
        pattern.setWidth(textBoxWidth);

    }

    public void drawAllRecipe(GuiGraphicsExtractor context, int left, int height, int mouseX, int mouseY) {
        if (pattern == null && ModConfig.get().autoFocusSearch) {
            pattern.setFocused(true);
        }

        // --- 1. Recipe Update Queue Watchers ---
        if (recipeUpdateTime != 0 && System.currentTimeMillis() > recipeUpdateTime) {
            recipeUpdateTime = 0;
            updateRecipes();
            if (!refreshCategories()){
                LOGGER.info("Update recipe");
                mouseScroll = 0;
                if (ModConfig.get().fadeOutTime > 0) {
                    recipeFadeTime = System.currentTimeMillis() + ModConfig.get().fadeOutTime * 50L;
                }
            }
        }

        if (recipeFadeTime > 0) {
            if (System.currentTimeMillis() < recipeFadeTime) {
                underMouse = null;
                return;
            } else {
                recipeFadeTime = 0;
            }
        }

        underMouse = null;

        // Setting this to 1 provides a clean 1-pixel flush alignment.
        int ypos = 5;

        // Set your clipping boundary to match the top of the frame
        minYtoDraw = 26;

        int screenBottom = context.guiHeight() - 5;

        // Draw Background panel spanning from the top down to the bottom
        if (ModConfig.get().recipeBackground){
            context.fill(xOffset - 5, ypos, xOffset + textBoxWidth + 5, screenBottom, 0x50505050);
        }

        // Draw Search Input Box directly at the top line
        pattern.setY(ypos);
        pattern.extractWidgetRenderState(context, mouseX, mouseY, 0f);

        // Step layout pointer below the input box frame
        ypos += displayItemSize * 3 / 2;
        ypos -= mouseScroll * displayItemSize;

        // Draw Content Containers
        ypos = drawSetOfRecipes(context, patternMatchingRecipes, 0, ypos, screenBottom - displayItemSize, mouseX, mouseY);

        for (String category : craftableCategories.keySet()) {
            if (ypos > screenBottom - displayItemSize) return;

            if (ypos >= minYtoDraw) {
                context.text(textRenderer, category, xOffset, ypos, 0xFFFFFF00, true);
            }
            ypos += displayItemSize;
            ypos = drawSetOfRecipes(context, craftableCategories.get(category), 0, ypos, screenBottom - displayItemSize, mouseX, mouseY);
        }
    }

    protected void populateAllRecipe(){
        assert player != null;
        for (RecipeBookCategory cat : RecipeBookCats) {
            for (RecipeCollection result : player.getRecipeBook().getCollection(cat)) {
                allRecipes.addAll(result.getRecipes());
            }
        }
    }

    public void updateAvailableStacks() {
        avaliableItemMap.clear();
        if (player==null) return;
        // Iterate through slots (usually 0-35 for player inventory)
        for (ItemStack itemStack : player.getInventory().getNonEquipmentItems()) {
            if (itemStack.isEmpty()) continue;
            avaliableItemMap.merge(itemStack.getItem(), itemStack.getCount(), Integer::sum);
        }
        // populate creative item group so recipes can be grouped
        CreativeModeTabs.tryRebuildTabContents(player.connection.enabledFeatures(), true, world.registryAccess());
        //ItemGroups.updateDisplayContext(player.networkHandler.getEnabledFeatures(), true, world.getRegistryManager());
    }

    public void renderSingleRecipeOutput(GuiGraphicsExtractor context, Font fontRenderer, ItemStack items, int x, int y) {
        context.item(items, x, y);
        context.itemDecorations(fontRenderer, items, x, y);
    }

    public void renderIngredient(GuiGraphicsExtractor context, List<ItemStack> stacks, Slot slot) {
        if (stacks.isEmpty()) return;
        int x = slot.x+containerLeft;
        int y = slot.y+containerTop;
        if (!getAvailableItemSet().contains(stacks.getFirst().getItem())){
            // no recipe found
            context.fill(x,y,x+ ITEM_SIZE,y+ ITEM_SIZE,CANT_CRAFT_COLOUR);
        }

        int toRender = 0;
        if (stacks.size() > 1)
            toRender = (int) ((System.currentTimeMillis() / 333) % stacks.size());
        drawHoloItem(context,slot,stacks.get(toRender));
        context.itemDecorations(textRenderer,stacks.get(toRender),x,y);
    }

    public void recalcListSize() {
        listSize = craftableCategories.size();
        for (RecipeTreeSet tree : craftableCategories.values())
            listSize += ((tree.size() + (itemsPerRow - 1)) / itemsPerRow);
        listSize *= displayItemSize;
    }

    public void updatePatternMatch() {
        patternListSize = 0;
        patternMatchingRecipes = new RecipeTreeSet();
        String patternText = (pattern == null) ? "" : pattern.getValue();

        if (patternText.isEmpty()) return;

        try {
            Pattern regex = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
            for (RecipeDisplayEntry entry : (ModConfig.get().showAllRecipes ? allRecipes : craftableRecipes)) {
                List<ItemStack> results = getCraftingResult(entry);
                if (results.isEmpty() || results.getFirst().isEmpty()) continue;
                // if raw name or translated name match (support other languages)
                if (regex.matcher(results.getFirst().getItem().getDescriptionId()).find() || regex.matcher(results.getFirst().getDisplayName().toString()).find()) {
                    patternMatchingRecipes.add(entry);
                }
            }
        } catch (PatternSyntaxException ex) {
            // Ignore
        }
        recalcPatternMatchSize();
    }

    public void recalcPatternMatchSize() {
        patternListSize = ((patternMatchingRecipes.size() + (itemsPerRow - 1)) / itemsPerRow) * displayItemSize;
        mouseScroll = 0;
    }

    public void scrollBy(int ticks) {
        int maxScrollPos = ((listSize + patternListSize - screen.height + (displayItemSize * 2)) / displayItemSize) + 1;
        mouseScroll = clamp(mouseScroll - ticks, 0, Math.max(0, maxScrollPos));
    }

    int clamp(int val, int a, int b) {
        return Math.min(Math.max(val,a),b);
    }

    public void mouseClicked(MouseButtonEvent click, boolean doubled, int guiLeft, int guiTop) {
        if (pattern != null) {
            boolean clickedPattern = pattern.mouseClicked(new MouseButtonEvent(click.x(),click.y(),new MouseButtonInfo(0,0)), doubled);
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
        // goodbye scroll bar idk if u existed

        if (underMouse == null) return;
        EasierCrafting.updateAllowed = false;

        // dont craft uncraftable items
        if (!this.canCraft(underMouse)) return;

        // Ensure grid is empty (common check, though subclasses might override behaviour)
        for (int craftslot = 0; craftslot < GRID_SIZE * GRID_SIZE; craftslot++) {
            ItemStack stack = screen.getMenu().getSlot(craftslot + FIRST_CRAFT_SLOT).getItem();
            if (!stack.isEmpty()) {
                slotClick(craftslot+ FIRST_CRAFT_SLOT, 0, ContainerInput.QUICK_MOVE);
                if (!stack.isEmpty()) return; // can't move item away (inventory full or locked) stop crafting
            }
        }

        onRecipeClicked(underMouse, click.button());
        queueUpdateRecipe();
        EasierCrafting.updateAllowed = true;
    }

    public boolean keyPressed(KeyEvent input) {
        if (pattern == null) return false;
        if (input.isConfirmation() || input.isEscape()) {
            pattern.setFocused(false);
            updatePatternMatch();
        } else if (pattern.isFocused()) {
            pattern.keyPressed(input);
            updatePatternMatch();
        } else if (EasierCrafting.refreshRecipeKey.isDown()){
            // pressed refresh key
            recipeUpdateTime = System.currentTimeMillis();
        } else {
            return false;
        }
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean charTyped(CharacterEvent input) {
        if (pattern != null && pattern.isFocused()) {
            // TextFieldWidget.charTyped returns true if it added a character to the text
            if (pattern.charTyped(input)) {
                updatePatternMatch();
                return true;
            }
        }
        return false;
    }

    // --- Helper Methods for Subclasses ---

    protected void slotClick(int slot, int mouseButton, ContainerInput clickType) {
        interactionManager.handleContainerInput(screenHandler.containerId, slot,mouseButton,clickType,player);
    }

    protected void drawHoloItem(GuiGraphicsExtractor context, int x, int y, ItemStack stack){
        context.item(stack, x, y);
        context.itemDecorations(textRenderer,stack,x,y);
        context.fill(x, y, x+ ITEM_SIZE, y+ ITEM_SIZE, 0x808b8b8b);
    }


    protected void drawHoloItem(GuiGraphicsExtractor context, Slot slot, ItemStack stack){
        drawHoloItem(context,slot.x + containerLeft,slot.y + containerTop,stack);
    }

    // other getters
    static Set<Item> getAvailableItemSet() {
        return avaliableItemMap.keySet();
    }
    static Map<Item,Integer> getAvailableItemMap(){
        return avaliableItemMap;
    }
    protected List<ItemStack> getCraftableStacks(SlotDisplay ingredient){
        return ingredient.resolveForStacks(worldContext).stream()
                .filter(stack -> getAvailableItemSet().contains(stack.getItem()))
                .toList();
    }

    public static Identifier getCat(RecipeDisplayEntry entry){
        var world = Minecraft.getInstance().level;
        assert world != null;
        if (BuiltInRegistries.RECIPE_BOOK_CATEGORY.keySet().isEmpty()) return null;
        return BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(entry.category());
    }

    public String recipeDisplayName(RecipeDisplayEntry entry) {
        return getCraftingResult(entry).getFirst().getItem().getDescriptionId();
    }

    protected List<ItemStack> getCraftingResult(RecipeDisplayEntry recipe) {
        return recipe.resultItems(worldContext);
    }

    protected ItemStack getFirstCraftingResult(RecipeDisplayEntry recipe) {
        return recipe.display().result().resolveForFirstStack(worldContext);
    }

    protected CreativeModeTab getItemGroup(RecipeDisplayEntry entry) {
        // get the group of result item
        Item resultItem = getFirstCraftingResult(entry).getItem();

        for (CreativeModeTab group : CreativeModeTabs.allTabs()) {
            // dont want to be generic "searched"
            if (group.getType() == CreativeModeTab.Type.SEARCH) continue;

            // We check the "display stacks" of the group to see if our item is there
            if (group.contains(resultItem.getDefaultInstance())) {
                return group; // Found the Creative Tab!
            }
        }
        LOGGER.warn("Cant find group for: {}", resultItem.getDescriptionId());
        return CreativeModeTabs.getDefaultTab();
    }

    protected String getTranslatedItemGroup(RecipeDisplayEntry entry){
        return I18n.get(getItemGroup(entry).getDisplayName().getString());
    }

    protected void queueUpdateRecipe(){
        recipeUpdateTime = System.currentTimeMillis() + ModConfig.get().autoUpdateRecipeTimer * 50L;
    }

    public static SlotDisplay getSlotDisplay(Item item){
        return new SlotDisplay.ItemSlotDisplay(item);
    }

    public static SlotDisplay getSlotDisplay(ItemStack stack){
        return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
    }

    protected boolean canCraft(RecipeDisplayEntry entry){
        return craftableRecipes.contains(entry);
    }

    public boolean isHoldingButton(int button){
        return InputConstants.isKeyDown(window, button);
    }


}

