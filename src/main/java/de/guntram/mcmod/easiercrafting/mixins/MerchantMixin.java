package de.guntram.mcmod.easiercrafting.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.guntram.mcmod.easiercrafting.modConfig.ModConfig;
import de.guntram.mcmod.easiercrafting.recipebook.AbstractRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantScreen.class)
public abstract class MerchantMixin extends Screen {
    @Shadow
    private int shopItem;

    protected MerchantMixin(Component title) {
        super(title);
    }

    @Inject(
            method = "mouseClicked",
            at = @At(value = "TAIL")
    )
    private void onTradeSelected(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        // Only trigger on left-click (button 0) to avoid ghost actions on right/middle click
        if (event.button() != AbstractRecipeBook.LMB) return;

        Window window = Minecraft.getInstance().getWindow();
        Minecraft client = Minecraft.getInstance();

        if (!ModConfig.get().enableTrading
                || InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
                || client.player.containerMenu.getSlot(2).getItem().isEmpty()) {
            return;
        }

        // GUI dimension offsets (ai = leftPos, aj = topPos)
        int ai = (this.width - 276) / 2;
        int aj = (this.height - 166) / 2;

        int listStartX = ai + 5;
        int listEndX = ai + 5 + 88;
        int listStartY = aj + 16;
        int listEndY = aj + 16 + 140;

        boolean isOverTradeTab = event.x() >= listStartX && event.x() <= listEndX &&
                event.y() >= listStartY && event.y() <= listEndY;

        if (!isOverTradeTab) return;

        AbstractContainerMenu currentScreenHandler = client.player.containerMenu;
        var syncId = currentScreenHandler.containerId;
        boolean holdingQ = Minecraft.getInstance().options.keyDrop.isDown();

        // 1. Shift Click Behavior (Restored Q-throw functionality)
        if (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)) {
            ContainerInput action = holdingQ ? ContainerInput.THROW : ContainerInput.QUICK_MOVE;
            // For throwing a whole stack, standard click data parameter is 1 instead of 0
            int clickData = holdingQ ? 1 : 0;
            client.gameMode.handleContainerInput(syncId, 2, clickData, action, client.player);
        }
        // 2. Regular Click Behavior
        else {
            // Direct Q-press click without shift
            if (holdingQ) {
                client.gameMode.handleContainerInput(syncId, 2, 0, ContainerInput.THROW, client.player);
                // Pull excess ingredients back out of the villager trade slots automatically
                client.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.QUICK_MOVE, client.player);
                client.gameMode.handleContainerInput(syncId, 1, 0, ContainerInput.QUICK_MOVE, client.player);
                return;
            }

            // Normal click: Smart transfer items into player inventory matching stack sizes
            ItemStack fromStack = currentScreenHandler.getSlot(2).getItem().copy();
            client.gameMode.handleContainerInput(syncId, 2, 0, ContainerInput.PICKUP, client.player);

            for (int i = 3; i < 39; i++) {
                ItemStack targetStack = currentScreenHandler.getSlot(i).getItem();
                if (targetStack.isEmpty()) {
                    client.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.PICKUP, client.player);
                    break;
                }
                if (fromStack.getItem() != targetStack.getItem()) continue;
                if (targetStack.getCount() == targetStack.getMaxStackSize()) continue;

                fromStack.setCount(Math.max(0, targetStack.getCount() + fromStack.getCount() - targetStack.getMaxStackSize()));
                client.gameMode.handleContainerInput(syncId, i, 0, ContainerInput.PICKUP, client.player);
                if (fromStack.getCount() == 0) break;
            }

            // Clean up left-over trade item inputs
            client.gameMode.handleContainerInput(syncId, 0, 0, ContainerInput.QUICK_MOVE, client.player);
            client.gameMode.handleContainerInput(syncId, 1, 0, ContainerInput.QUICK_MOVE, client.player);
        }
    }
}