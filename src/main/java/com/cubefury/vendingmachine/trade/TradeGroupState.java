package com.cubefury.vendingmachine.trade;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.cubefury.vendingmachine.api.trade.ICondition;

public class TradeGroupState {

    public TradeGroup tradeGroup;

    private final Map<UUID, Set<ICondition>> playerSatisfied = new HashMap<>();
    private final Map<UUID, TradeHistory> tradeState = new HashMap<>();

    public TradeGroupState(TradeGroup tradeGroup) {
        this.tradeGroup = tradeGroup;
    }

    public void clearTradeState(@Nullable UUID player) {
        if (player == null) {
            tradeState.clear();
        } else {
            tradeState.remove(player);
        }
    }

    public void setTradeState(@Nonnull UUID player, TradeHistory history) {
        tradeState.put(player, history);
    }

    public TradeHistory getTradeState(@Nonnull UUID player) {
        return tradeState.getOrDefault(player, new TradeHistory());
    }

    public void addConditionSatisfied(@Nonnull UUID player, ICondition c) {
        playerSatisfied.putIfAbsent(player, new HashSet<>());
        playerSatisfied.get(player)
            .add(c);
    }

    public void removeConditionSatisfied(@Nullable UUID player, ICondition c) {
        if (player == null) {
            for (Set<ICondition> conditions : playerSatisfied.values()) {
                conditions.remove(c);
            }
        } else if (playerSatisfied.containsKey(player)) {
            playerSatisfied.get(player)
                .remove(c);
        }
    }

    public boolean satisfiesTrade(@Nonnull UUID player) {
        return playerSatisfied.getOrDefault(player, Collections.emptySet())
            .equals(tradeGroup.getRequirements());
    }

    public Set<UUID> getPlayersWithConditionData() {
        return playerSatisfied.keySet();
    }
}
