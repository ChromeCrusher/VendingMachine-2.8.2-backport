package com.cubefury.vendingmachine.integration.nei;

import static codechicken.lib.gui.GuiDraw.changeTexture;
import static codechicken.lib.gui.GuiDraw.drawTexturedModalRect;
import static net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.api.trade.ICondition;
import com.cubefury.vendingmachine.integration.betterquesting.BqAdapter;
import com.cubefury.vendingmachine.integration.betterquesting.BqCondition;
import com.cubefury.vendingmachine.storage.NameCache;
import com.cubefury.vendingmachine.trade.CurrencyItem;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.BigItemStack;
import com.cubefury.vendingmachine.util.ItemPlaceholder;
import com.cubefury.vendingmachine.util.Translator;

import betterquesting.api.questing.IQuest;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.themes.gui_args.GArgsNone;
import betterquesting.api2.client.gui.themes.presets.PresetGUIs;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.GuiHome;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.client.gui2.GuiQuestLines;
import betterquesting.client.themes.ThemeRegistry;
import betterquesting.questing.QuestDatabase;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.API;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.registry.GameRegistry;

public class NeiRecipeHandler extends TemplateRecipeHandler {

    private static final int SLOT_SIZE = 18;
    private static final int GUI_WIDTH = 166;
    private static final int GRID_COUNT = 4;
    private static final int LINE_SPACE = GuiDraw.fontRenderer.FONT_HEIGHT + 1;
    private static final int CONDITIONS_START_Y = 63 + LINE_SPACE;
    private UUID currentPlayerId;
    private int textColorConditionDefault;
    private int textColorConditionSatisfied;
    private int textColorConditionUnsatisfied;

    private int lastHoveredRecipeIndex = -1;
    private Rectangle lastHoveredTextArea = null;
    private UUID lastHoveredQuestId = null;

    public NeiRecipeHandler() {}

    public void addHandler() {
        FMLInterModComms.sendRuntimeMessage(
            FMLCommonHandler.instance()
                .findContainerFor(VendingMachine.MODID),
            "NEIPlugins",
            "register-crafting-handler",
            "vendingmachine@" + this.getRecipeName() + "@" + this.getOverlayIdentifier());
        GuiCraftingRecipe.craftinghandlers.add(this);
        API.registerUsageHandler(this);
    }

    private UUID getCurrentPlayerUUID() {
        if (currentPlayerId == null) {
            currentPlayerId = NameCache.INSTANCE.getUUIDFromPlayer(Minecraft.getMinecraft().thePlayer);
        }
        return currentPlayerId;
    }

    private void setTextColors() {
        textColorConditionDefault = Translator.getColor("vendingmachine.gui.neiColor.conditionDefault");
        textColorConditionSatisfied = Translator.getColor("vendingmachine.gui.neiColor.conditionSatisfied");
        textColorConditionUnsatisfied = Translator.getColor("vendingmachine.gui.neiColor.conditionUnsatisfied");
    }

    @Override
    public String getOverlayIdentifier() {
        return "vendingmachine";
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId.equals(getOverlayIdentifier())) {
            setTextColors();
            for (NeiRecipeCache.CacheEntry entry : NeiRecipeCache.recipeCache) {
                try {
                    this.arecipes.add(new CachedTradeRecipe(entry.trade(), entry.requirements()));
                } catch (Throwable t) {
                    VendingMachine.LOG.warn(
                        "Skipping NEI recipe entry for trade because a stack in it is not NEI-safe: " + entry.trade(),
                        t);
                }
            }
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        setTextColors();
        for (NeiRecipeCache.CacheEntry entry : NeiRecipeCache.recipeCache) {
            try {
                for (BigItemStack compareTo : entry.trade().toItems) {
                    if (matchStack(result, compareTo)) {
                        this.arecipes.add(new CachedTradeRecipe(entry.trade(), entry.requirements()));
                        break;
                    }
                }
            } catch (Throwable t) {
                VendingMachine.LOG.warn(
                    "Skipping NEI output recipe entry because a stack in it is not NEI-safe: " + entry.trade(),
                    t);
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        setTextColors();
        for (NeiRecipeCache.CacheEntry entry : NeiRecipeCache.recipeCache) {
            try {
                for (BigItemStack compareTo : entry.trade().fromItems) {
                    if (matchStack(ingredient, compareTo)) {
                        this.arecipes.add(new CachedTradeRecipe(entry.trade(), entry.requirements()));
                        break;
                    }
                }
            } catch (Throwable t) {
                VendingMachine.LOG
                    .warn("Skipping NEI usage recipe entry because a stack in it is not NEI-safe: " + entry.trade(), t);
            }
        }
    }

    public static List<ItemStack> extractStacks(BigItemStack bigStack) {
        if (bigStack == null) {
            return Collections.emptyList();
        }

        if (bigStack.hasOreDict()) {
            List<ItemStack> ret = new ArrayList<>();
            for (ItemStack s : bigStack.getOreIngredient()
                .getMatchingStacks()) {
                if (s == null) {
                    continue;
                }

                ItemStack copy = s.copy();
                copy.stackSize = bigStack.stackSize;
                ret.add(sanitizeNeiStack(copy));
            }

            if (!ret.isEmpty()) {
                return ret;
            }
        }

        return Collections.singletonList(translateBigStack(bigStack));
    }

    public static ItemStack translateBigStack(BigItemStack bigStack) {
        ItemStack stack = bigStack.getBaseStack()
            .copy();
        stack.stackSize = bigStack.stackSize;
        return sanitizeNeiStack(stack);
    }

    private static ItemStack sanitizeNeiStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return stack;
        }

        ItemStack copy = stack.copy();

        try {
            GameRegistry.getFuelValue(copy);
            return copy;
        } catch (Throwable t) {
            String regName = Item.itemRegistry.getNameForObject(copy.getItem());
            if (regName == null) {
                regName = copy.getItem()
                    .getClass()
                    .getName();
            }

            VendingMachine.LOG.warn(
                "Replacing NEI-unsafe stack in vending machine recipe view: " + regName + "@" + copy.getItemDamage(),
                t);

            return createNeiPlaceholder(copy, regName);
        }
    }

    private static ItemStack createNeiPlaceholder(ItemStack original, String regName) {
        ItemStack placeholder = new ItemStack(ItemPlaceholder.placeholder, Math.max(1, original.stackSize), 0);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("orig_id", regName);
        tag.setInteger("orig_meta", original.getItemDamage());

        if (original.hasTagCompound()) {
            tag.setTag(
                "orig_tag",
                original.getTagCompound()
                    .copy());
        }

        placeholder.setTagCompound(tag);
        return placeholder;
    }

    private static boolean matchStack(ItemStack compared, BigItemStack bigStackCompareTo) {
        for (ItemStack compareTo : extractStacks(bigStackCompareTo)) {
            if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(compared, compareTo)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getRecipeName() {
        return VendingMachine.NAME;
    }

    @Override
    public String getGuiTexture() {
        return "vendingmachine:textures/gui/nei.png";
    }

    @Override
    public void drawBackground(int recipe) {
        GL11.glColor4f(1, 1, 1, 1);
        changeTexture(getGuiTexture());
        drawTexturedModalRect(0, 0, 0, 0, GUI_WIDTH, 140);
    }

    public boolean isMouseOverBqCondition(int recipeIndex, int curY, UUID questId, String text) {
        this.lastHoveredRecipeIndex = -1;
        this.lastHoveredTextArea = null;
        this.lastHoveredQuestId = null;
        return false;
    }

    public boolean isMouseOnLastHovered(GuiRecipe<?> gui, int recipeIndex) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipeIndex) {
        return super.mouseClicked(gui, button, recipeIndex);
    }

    @Optional.Method(modid = "betterquesting")
    public void processBqGui() {
        GuiScreen parentScreen;
        if (GuiHome.bookmark instanceof GuiQuest && BQ_Settings.useBookmark) {
            // back to GuiQuestLines
            parentScreen = ((GuiScreenCanvas) GuiHome.bookmark).parent;
        } else if (GuiHome.bookmark instanceof GuiScreenCanvas && BQ_Settings.useBookmark) {
            parentScreen = GuiHome.bookmark;
        } else {
            // init quest screen
            parentScreen = ThemeRegistry.INSTANCE.getGui(PresetGUIs.HOME, GArgsNone.NONE);
            if (BQ_Settings.useBookmark && BQ_Settings.skipHome) {
                parentScreen = new GuiQuestLines(parentScreen);
            }
        }
        GuiQuest toDisplay = new GuiQuest(parentScreen, lastHoveredQuestId);
        toDisplay.setPreviousScreen(Minecraft.getMinecraft().currentScreen);
        Minecraft.getMinecraft()
            .displayGuiScreen(toDisplay);
        if (BQ_Settings.useBookmark) {
            GuiHome.bookmark = toDisplay;
        }
    }

    @Override
    public void drawExtras(int recipeIndex) {
        CachedTradeRecipe recipe = (CachedTradeRecipe) this.arecipes.get(recipeIndex);

        float scale = 0.5f;
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1);
        for (PositionedStack ps : recipe.ncInputs) {
            GuiDraw.fontRenderer.drawString(
                "NC",
                (int) (ps.relx / scale),
                (int) (ps.rely / scale),
                Translator.getColor("vendingmachine.gui.nc_inputs_overlay_color"),
                false);
        }
        GL11.glPopMatrix();

        GuiDraw.drawString(
            Translator.translate("vendingmachine.gui.requirementHeader"),
            2,
            63,
            textColorConditionDefault,
            false);
        int y = CONDITIONS_START_Y;
        for (ICondition condition : recipe.requirements) {
            StringBuilder requirementString = new StringBuilder();
            int color = textColorConditionDefault;
            if (VendingMachine.isBqLoaded && condition instanceof BqCondition) {
                StringBuilder unformatted = new StringBuilder(
                    Translator.translate("vendingmachine.gui.requirement.betterquesting"));
                unformatted.append(": ");
                UUID questId = ((BqCondition) condition).getQuestId();
                IQuest quest = QuestDatabase.INSTANCE.get(questId);
                if (quest == null) {
                    requirementString
                        .append(Translator.translate("vendingmachine.gui.requirement.betterquesting.missing"));
                } else {
                    String translatedQuestKey = getTextWithoutFormattingCodes(
                        QuestTranslation.translateQuestName(questId, quest));
                    unformatted.append(
                        translatedQuestKey.length() <= 18 ? translatedQuestKey
                            : translatedQuestKey.substring(0, 18) + "...");
                    requirementString.append(unformatted);
                }
                color = BqAdapter.INSTANCE.checkPlayerCompletedQuest(getCurrentPlayerUUID(), questId)
                    ? textColorConditionSatisfied
                    : textColorConditionUnsatisfied;
            } else {
                requirementString.append(Translator.translate("vendingmachine.gui.requirement.unknown"));
            }

            List<String> requirementsArray = GuiDraw.fontRenderer
                .listFormattedStringToWidth(requirementString.toString(), GUI_WIDTH);
            for (String line : requirementsArray) {
                GuiDraw.drawString(line, 2, y, color, false);
                y += LINE_SPACE;
            }
        }
    }

    public class CachedTradeRecipe extends CachedRecipe {

        private final List<PositionedStack> inputs = new ArrayList<>();
        private final List<PositionedStack> ncInputs = new ArrayList<>();
        private final List<PositionedStack> outputs = new ArrayList<>();
        private final List<ICondition> requirements = new ArrayList<>();

        private CachedTradeRecipe(Trade trade, List<ICondition> requirements) {
            loadInputs(trade);
            loadOutputs(trade);

            this.requirements.addAll(requirements);
        }

        private void loadInputs(Trade trade) {
            int xOffset = 3, y = 7;
            int index = 0;
            for (BigItemStack stack : trade.fromItems) {
                if (index >= GRID_COUNT) {
                    y += SLOT_SIZE;
                    index = 0;
                }
                int x = xOffset + index * SLOT_SIZE;
                inputs.add(new PositionedStack(extractStacks(stack), x, y));
                index++;
            }

            for (CurrencyItem ci : trade.fromCurrency) {
                if (index >= GRID_COUNT) {
                    y += SLOT_SIZE;
                    index = 0;
                }
                int x = xOffset + index * SLOT_SIZE;
                inputs.add(new PositionedStack(ci.getItemRepresentation(), x, y));
                index++;
            }

            y += SLOT_SIZE;
            index = 0;
            for (BigItemStack stack : trade.nonConsumedItems) {
                if (index >= GRID_COUNT) {
                    y += SLOT_SIZE;
                    index = 0;
                }
                int x = xOffset + index * SLOT_SIZE;
                ncInputs.add(new PositionedStack(extractStacks(stack), x, y));
                index++;
            }

        }

        private void loadOutputs(Trade trade) {
            int xOffset = 93, y = 7;
            int index = 0;
            for (BigItemStack stack : trade.toItems) {
                if (index >= GRID_COUNT) {
                    break;
                }
                int x = xOffset + index * SLOT_SIZE;
                outputs.add(new PositionedStack(extractStacks(stack), x, y));
                index++;
            }
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            List<PositionedStack> allInputs = new ArrayList<>();
            allInputs.addAll(inputs);
            allInputs.addAll(ncInputs);
            return getCycledIngredients(cycleticks / 20, allInputs);
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return outputs;
        }
    }

    @Override
    public void loadTransferRects() {
        transferRects.add(new RecipeTransferRect(new Rectangle(75, 5, 16, 55), getOverlayIdentifier()));
    }
}
