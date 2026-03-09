package com.cubefury.vendingmachine.blocks.gui;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.value.IValue;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.gui.GuiTextures;
import com.cubefury.vendingmachine.trade.FavouritesTracker;
import com.cubefury.vendingmachine.util.Translator;

public class TradeItemDisplayWidget extends ItemDisplayWidget implements Interactable {

    private final MTEVendingMachine vm;
    private TradeMainPanel rootPanel;
    private boolean pressed = false;
    private IValue<ItemStack> value;
    public final DisplayType displayType;

    private TradeItemDisplay display;

    public TradeItemDisplayWidget(TradeItemDisplay display, MTEVendingMachine base, DisplayType displayType) {
        this.vm = base;
        this.displayType = displayType;

        if (displayType == DisplayType.TILE) {
            height(MTEVendingMachineGui.TILE_ITEM_HEIGHT);
            width(MTEVendingMachineGui.TILE_ITEM_WIDTH);
            background(
                new DynamicDrawable(
                    () -> this.pressed ? GuiTextures.TILE_TRADE_BUTTON_PRESSED
                        : GuiTextures.TILE_TRADE_BUTTON_UNPRESSED));
        } else if (displayType == DisplayType.LIST) {
            height(MTEVendingMachineGui.LIST_ITEM_HEIGHT);
            width(MTEVendingMachineGui.LIST_ITEM_WIDTH);
            background(
                new DynamicDrawable(
                    () -> this.pressed ? GuiTextures.LIST_TRADE_BUTTON_PRESSED
                        : GuiTextures.LIST_TRADE_BUTTON_UNPRESSED));
        }

        this.display = display;
        this.item((ItemStack) null);
    }

    public void setDisplay(TradeItemDisplay display) {
        this.display = display;
        this.item(display == null ? null : display.display);
    }

    private boolean checkVmActive() {
        return this.vm != null && this.vm.getActive();
    }

    public TradeItemDisplay getDisplay() {
        return this.display;
    }

    @Override
    public @NotNull Interactable.Result onMousePressed(int mouseButton) {
        if (this.display == null || this.rootPanel == null) {
            return Result.IGNORE;
        }

        if (this.rootPanel.shiftHeld == this.rootPanel.ctrlHeld) {
            return Result.IGNORE;
        }

        if (this.rootPanel.shiftHeld) {
            if (!this.checkVmActive() || !this.display.enabled || this.display.hasCooldown) {
                return Result.IGNORE;
            }

            this.rootPanel.attemptPurchase(this.display);
            this.pressed = true;
            return Result.SUCCESS;
        }

        if (this.rootPanel.ctrlHeld) {
            FavouritesTracker.INSTANCE.toggleFavourites(this.display.tgID, this.display.tradeGroupOrder);
            FavouritesTracker.INSTANCE.saveFavourites();
            this.rootPanel.forceGuiRefresh();
            return Result.SUCCESS;
        }

        return Result.IGNORE;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetTheme widgetTheme) {
        int textColor = Translator.getColor("vendingmachine.gui.display_text_color");
        ItemStack item = this.value == null ? null : this.value.getValue();

        if (!Platform.isStackEmpty(item) && this.display != null) {
            if (this.displayType == DisplayType.TILE) {
                GuiDraw.drawText(" " + this.display.display.stackSize, 4, 9, 1.0f, textColor, false);
                GuiDraw.drawItem(item, 26, 4, 16, 16, context.getCurrentDrawingZ());

                if (this.display.tradeableNow) {
                    GuiDraw.drawOutline(1, 1, 45, 23, 0x883CFF00, 2);
                }

                if (!this.checkVmActive() || this.display.hasCooldown || !this.display.enabled) {
                    GuiDraw.drawRoundedRect(
                        1,
                        1,
                        MTEVendingMachineGui.TILE_ITEM_WIDTH - 2,
                        MTEVendingMachineGui.TILE_ITEM_HEIGHT - 2,
                        0xBB000000,
                        1,
                        1);
                }

                if (this.display.hasCooldown) {
                    GuiDraw.drawText(this.display.cooldownText, 4, 18, 0.8f, 0xFFFFFFFF, false);
                }

                if (this.display.isFavourite) {
                    GuiTextures.FAVOURITE_SPRITE.draw(context, 4, 4, 6, 6);
                }
            } else if (this.displayType == DisplayType.LIST) {
                GuiDraw.drawText("" + this.display.display.stackSize, 6, 4, 0.9f, textColor, false);
                GuiDraw.drawItem(item, 24, 2, 9, 9, context.getCurrentDrawingZ());

                String displayName = this.display.display.getDisplayName();
                GuiDraw.drawText(
                    displayName.length() > 21 ? displayName.substring(0, 21) + "..." : displayName,
                    36,
                    4,
                    0.9f,
                    textColor,
                    false);

                GuiDraw.drawRect(
                    1,
                    1,
                    3,
                    MTEVendingMachineGui.LIST_ITEM_HEIGHT - 3,
                    this.display.tradeableNow ? 0x883CFF00 : 0x88333333);

                if (!this.checkVmActive() || this.display.hasCooldown || !this.display.enabled) {
                    GuiDraw.drawRect(
                        1,
                        1,
                        MTEVendingMachineGui.LIST_ITEM_WIDTH - 2,
                        MTEVendingMachineGui.LIST_ITEM_HEIGHT - 2,
                        0xBB000000);
                }

                if (this.display.hasCooldown && this.display.enabled) {
                    GuiDraw.drawText(this.display.cooldownText, 110, 4, 0.9f, 0xFFFFFFFF, false);
                }

                if (this.display.isFavourite) {
                    GuiTextures.FAVOURITE_SPRITE.draw(context, 139, 2, 10, 10);
                }
            }
        }
    }

    @Override
    public boolean isValidSyncHandler(SyncHandler syncHandler) {
        if (syncHandler instanceof GenericSyncValue<?>genericSyncValue && genericSyncValue.isOfType(ItemStack.class)) {
            this.value = genericSyncValue.cast();
            return true;
        }
        return false;
    }

    public ItemDisplayWidget item(IValue<ItemStack> itemSupplier) {
        this.value = itemSupplier;
        this.setValue(itemSupplier);
        return this;
    }

    @Override
    public void onMouseEndHover() {
        this.pressed = false;
        super.onMouseEndHover();
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        this.pressed = false;
        return true;
    }

    public void setRootPanel(TradeMainPanel rootPanel) {
        this.rootPanel = rootPanel;
    }
}
