package com.cubefury.vendingmachine.handlers;

import static com.cubefury.vendingmachine.util.FileIO.CopyPaste;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;

import com.cubefury.vendingmachine.VMConfig;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.storage.NameCache;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.TradeManager;
import com.cubefury.vendingmachine.util.FileIO;
import com.cubefury.vendingmachine.util.JsonHelper;
import com.cubefury.vendingmachine.util.NBTConverter;

public class SaveLoadHandler {

    public static SaveLoadHandler INSTANCE = new SaveLoadHandler();

    private File fileDatabase = null;
    private File fileNames = null;
    private File dirTradeState = null;
    private File dirBackupTradeState = null;

    private SaveLoadHandler() {}

    public void init(MinecraftServer server) {
        if (VendingMachine.proxy.isClient()) {
            VMConfig.world_dir = server.getFile("saves/" + server.getFolderName() + "/" + VMConfig.developer.data_dir);
        } else {
            VMConfig.world_dir = server.getFile(server.getFolderName() + "/" + VMConfig.developer.data_dir);
        }

        fileDatabase = new File(VMConfig.developer.trade_db_dir, "tradeDatabase.json");
        dirTradeState = new File(VMConfig.world_dir, "tradeState");
        dirBackupTradeState = new File(VMConfig.world_dir, "backup/tradeState");
        fileNames = new File(VMConfig.world_dir, "names.json");

        createFilesAndDirectories();

        unloadAll();

        loadDatabase();
        loadTradeState();
        loadNames();
    }

    public void createFilesAndDirectories() {
        if (!fileNames.exists()) {
            try {
                if (fileNames.createNewFile()) {
                    VendingMachine.LOG.info("Created new name cache file");
                }
            } catch (Exception ignored) {
                VendingMachine.LOG.warn("Could not create new name cache file");
            }
        }

        if (dirTradeState.mkdirs()) {
            VendingMachine.LOG.info("Created trade state directory");
        }

        File dbParent = fileDatabase.getParentFile();
        if (dbParent != null && dbParent.mkdirs()) {
            VendingMachine.LOG.info("Created trade database directory");
        }

        ensureTradeDatabaseExists();
    }

    private void ensureTradeDatabaseExists() {
        if (fileDatabase.exists() && fileDatabase.length() > 0) {
            return;
        }

        try (InputStream in = SaveLoadHandler.class
            .getResourceAsStream("/assets/vendingmachine/defaults/tradeDatabase.json")) {
            if (in == null) {
                VendingMachine.LOG.warn("Could not find bundled default trade database");
                return;
            }

            Files.copy(in, fileDatabase.toPath(), StandardCopyOption.REPLACE_EXISTING);
            VendingMachine.LOG.info("Created trade database file from bundled default");
        } catch (IOException e) {
            VendingMachine.LOG.warn("Could not create trade database file from bundled default", e);
        }
    }

    public void loadDatabase() {
        JsonHelper.populateTradeDatabaseFromFile(fileDatabase);
    }

    public Future<Void> writeDatabase() {
        CopyPaste(fileDatabase, new File(VMConfig.developer.trade_db_dir + "/backup", "tradeDatabase.json"));
        return FileIO.WriteToFile(
            fileDatabase,
            out -> NBTConverter.NBTtoJSON_Compound(TradeDatabase.INSTANCE.writeToNBT(new NBTTagCompound()), out, true));
    }

    public void loadTradeState() {
        if (dirTradeState.exists()) {
            File[] fileList = dirTradeState.listFiles();

            if (fileList != null) {
                JsonHelper.populateTradeStateFromFiles(
                    Arrays.stream(fileList)
                        .filter(
                            f -> f.getName()
                                .endsWith(".json"))
                        .collect(Collectors.toList()));
            } else {
                JsonHelper.populateTradeStateFromFiles(Collections.emptyList());
            }
        }
    }

    public List<Future<Void>> writeTradeState(Collection<UUID> players) {
        if (!dirBackupTradeState.exists()) {
            CopyPaste(dirTradeState, dirBackupTradeState);
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (UUID player : players) {
            File playerFile = new File(dirTradeState, player.toString() + ".json");
            CopyPaste(playerFile, new File(dirBackupTradeState, player.toString() + ".json"));
            NBTTagCompound state = TradeManager.INSTANCE.writeTradeStateToNBT(new NBTTagCompound(), player);
            futures.add(FileIO.WriteToFile(playerFile, out -> NBTConverter.NBTtoJSON_Compound(state, out, true)));
        }
        return futures;
    }

    public void loadNames() {
        JsonHelper.populateNameCacheFromFile(fileNames);
    }

    public Future<Void> writeNames() {
        NBTTagCompound json = new NBTTagCompound();
        json.setTag("nameCache", NameCache.INSTANCE.writeToNBT(new NBTTagList(), null));
        return FileIO.WriteToFile(fileNames, out -> NBTConverter.NBTtoJSON_Compound(json, out, true));
    }

    public void unloadAll() {
        NameCache.INSTANCE.clear();
        TradeDatabase.INSTANCE.clear();
        TradeManager.INSTANCE.clearTradeState(null);
    }

    public void reloadDatabase() {
        TradeDatabase.INSTANCE.clear();
        TradeManager.INSTANCE.clearTradeState(null);

        loadDatabase();
        loadTradeState();
    }

}
