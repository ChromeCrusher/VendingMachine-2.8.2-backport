package com.cubefury.vendingmachine.trade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay;
import com.cubefury.vendingmachine.handlers.SaveLoadHandler;
import com.cubefury.vendingmachine.storage.NameCache;
import com.cubefury.vendingmachine.util.NBTConverter;

public class FavouritesTracker {

    public static final FavouritesTracker INSTANCE = new FavouritesTracker();

    private boolean dirty = false;
    public final Set<Pair<UUID, Integer>> favourites = new HashSet<>();

    private FavouritesTracker() {}

    public void toggleFavourites(UUID tradeGroupId, int tradeGroupOrder) {
        Pair<UUID, Integer> pair = new ImmutablePair<>(tradeGroupId, tradeGroupOrder);
        if (this.favourites.contains(pair)) {
            this.favourites.remove(pair);
        } else {
            this.favourites.add(pair);
        }
        this.dirty = true;
    }

    private static boolean isPrivateAddress(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
            || host.startsWith("192.168")
            || host.startsWith("10.")
            || host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }

    public String computeWorldKey() {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.isSingleplayer()) {
            return "SP_" + mc.getIntegratedServer()
                .getFolderName();
        }

        ServerData data = mc.func_147104_D();
        if (data == null) {
            return null;
        }

        String ip = data.serverIP;
        String host = ip;
        String port = "";

        int colon = ip.indexOf(':');
        if (colon >= 0) {
            host = ip.substring(0, colon);
            port = ip.substring(colon);
        }

        if (isPrivateAddress(host)) {
            return "LAN_" + host;
        }

        return "MP_" + host + port;
    }

    public void saveFavourites() {
        if (this.dirty) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                SaveLoadHandler.INSTANCE
                    .writeFavourites(NameCache.INSTANCE.getUUIDFromPlayer(mc.thePlayer), computeWorldKey());
            }
        }
        this.dirty = false;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList favouritesNbt = new NBTTagList();

        for (Pair<UUID, Integer> fave : this.favourites) {
            NBTTagCompound faveNbt = new NBTTagCompound();
            NBTConverter.UuidValueType.TRADEGROUP.writeId(fave.getLeft(), faveNbt);
            faveNbt.setInteger("tgOrder", fave.getRight());
            favouritesNbt.appendTag(faveNbt);
        }

        nbt.setTag("favourites", favouritesNbt);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt, boolean merge) {
        if (!merge) {
            this.favourites.clear();
        }

        NBTTagList faveListNbt = nbt.getTagList("favourites", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < faveListNbt.tagCount(); i++) {
            NBTTagCompound faveNbt = faveListNbt.getCompoundTagAt(i);
            this.favourites.add(
                new ImmutablePair<>(
                    NBTConverter.UuidValueType.TRADEGROUP.readId(faveNbt),
                    faveNbt.getInteger("tgOrder")));
        }

        VendingMachine.LOG.info("Loaded {} favourite trades", this.favourites.size());
    }

    public List<TradeItemDisplay> filterTrades(List<TradeItemDisplay> trades) {
        List<TradeItemDisplay> filteredTrades = new ArrayList<>();
        if (trades == null) {
            return filteredTrades;
        }

        for (TradeItemDisplay trade : trades) {
            if (this.favourites.contains(new ImmutablePair<>(trade.tgID, trade.tradeGroupOrder))) {
                filteredTrades.add(trade);
            }
        }

        return filteredTrades;
    }

    public boolean isFavourite(TradeItemDisplay display) {
        return display != null && this.favourites.contains(new ImmutablePair<>(display.tgID, display.tradeGroupOrder));
    }

    public boolean hasFavourites() {
        return !this.favourites.isEmpty();
    }

    public void clearFavourites() {
        this.favourites.clear();
    }
}
