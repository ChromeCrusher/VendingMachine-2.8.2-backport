package com.cubefury.vendingmachine.integration.betterquesting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.trade.TradeManager;
import com.google.common.collect.ImmutableMap;

import betterquesting.api.questing.IQuest;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BqAdapter {

    public static final BqAdapter INSTANCE = new BqAdapter();

    private final Map<UUID, Set<TradeGroup>> questUpdateTriggers = new HashMap<>();
    private final Map<UUID, Set<UUID>> playerSatisfiedCache = new HashMap<>();

    private BqAdapter() {}

    public void resetQuestTriggers(@Nullable UUID quest) {
        if (quest == null) {
            questUpdateTriggers.clear();
        } else {
            questUpdateTriggers.remove(quest);
        }
    }

    public void addQuestTrigger(UUID quest, TradeGroup tg) {
        if (!questUpdateTriggers.containsKey(quest) || questUpdateTriggers.get(quest) == null) {
            questUpdateTriggers.put(quest, new HashSet<>());
        }
        questUpdateTriggers.get(quest)
            .add(tg);
    }

    public Map<UUID, Set<UUID>> getPlayerSatisfiedCache() {
        synchronized (playerSatisfiedCache) {
            return ImmutableMap.copyOf(playerSatisfiedCache);
        }
    }

    @SideOnly(Side.CLIENT)
    public void setPlayerSatisfiedCache(Map<UUID, Set<UUID>> newCache) {
        synchronized (playerSatisfiedCache) {
            playerSatisfiedCache.clear();
            playerSatisfiedCache.putAll(newCache);
        }
    }

    public void setQuestFinished(UUID player, UUID quest) {
        if (!questUpdateTriggers.containsKey(quest)) {
            return;
        }
        for (TradeGroup tradeGroup : questUpdateTriggers.get(quest)) {
            TradeManager.INSTANCE.addSatisfiedCondition(tradeGroup, player, new BqCondition(quest));
        }
        synchronized (playerSatisfiedCache) {
            playerSatisfiedCache.putIfAbsent(player, new HashSet<>());
            playerSatisfiedCache.get(player)
                .add(quest);
        }
    }

    public void setQuestUnfinished(UUID player, UUID quest) {
        if (!questUpdateTriggers.containsKey(quest)) {
            return;
        }
        for (TradeGroup tradeGroup : questUpdateTriggers.get(quest)) {
            TradeManager.INSTANCE.removeSatisfiedCondition(tradeGroup, player, new BqCondition(quest));
        }
        synchronized (playerSatisfiedCache) {
            if (playerSatisfiedCache.get(player) != null) {
                playerSatisfiedCache.get(player)
                    .remove(quest);
            }
        }
    }

    public void resetQuests(UUID player) {
        for (Map.Entry<UUID, Set<TradeGroup>> entry : questUpdateTriggers.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            for (TradeGroup tg : entry.getValue()) {
                TradeManager.INSTANCE.removeSatisfiedCondition(tg, player, new BqCondition(entry.getKey()));
            }
        }
        synchronized (playerSatisfiedCache) {
            if (player == null) {
                playerSatisfiedCache.clear();
            } else {
                playerSatisfiedCache.remove(player);
            }
        }
    }

    public void syncQuestState(UUID player, UUID quest) {
        if (quest == null) {
            return;
        }

        IQuest bqQuest = QuestDatabase.INSTANCE.get(quest);
        if (bqQuest != null && bqQuest.isComplete(player)) {
            setQuestFinished(player, quest);
        } else {
            setQuestUnfinished(player, quest);
        }
    }

    public void syncAllQuestStates(UUID player) {
        resetQuests(player);

        for (UUID quest : questUpdateTriggers.keySet()) {
            IQuest bqQuest = QuestDatabase.INSTANCE.get(quest);
            if (bqQuest != null && bqQuest.isComplete(player)) {
                setQuestFinished(player, quest);
            }
        }
    }

    public boolean checkPlayerCompletedQuest(UUID player, UUID quest) {
        synchronized (playerSatisfiedCache) {
            return playerSatisfiedCache.get(player) != null && playerSatisfiedCache.get(player)
                .contains(quest);
        }
    }

    @SideOnly(Side.CLIENT)
    public Set<UUID> getTrades(UUID quest) {
        Set<UUID> output = new HashSet<>();
        if (questUpdateTriggers.get(quest) == null) {
            return output;
        }

        for (TradeGroup tradeGroup : questUpdateTriggers.get(quest)) {
            output.add(tradeGroup.getId());
        }
        return output;
    }

    @SideOnly(Side.CLIENT)
    public boolean questHasTrades(UUID quest) {
        return questUpdateTriggers.get(quest) != null && !questUpdateTriggers.get(quest)
            .isEmpty();
    }
}
