package com.banktaglayouts;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class PotionStorage {
    static final int COMPONENTS_PER_POTION = 5;

    private final Client client;
    private final ItemManager itemManager;

    private Potion[] potions;
    boolean cachePotions;
    private boolean layout;
    private Set<Integer> potionStoreVars;

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (cachePotions) {
            log.debug("Rebuilding potions");
            cachePotions = false;
            rebuildPotions();

            Widget w = client.getWidget(ComponentID.BANK_POTIONSTORE_CONTENT);

            if (w != null && potionStoreVars == null) {
                int[] trigger = w.getVarTransmitTrigger();
                potionStoreVars = new HashSet<>();
                Arrays.stream(trigger).forEach(potionStoreVars::add);
            }

            if (layout) {
                layout = false;
                // Implement layout logic if needed
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        if (potionStoreVars != null && potionStoreVars.contains(varbitChanged.getVarpId())) {
            cachePotions = true;
            layout = true;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.BANK && event.isUnload()) {
            log.debug("Invalidating potions");
            potions = null;
        }
    }

    private void rebuildPotions() {
        EnumComposition potionStorePotions = client.getEnum(EnumID.POTIONSTORE_POTIONS);
        potions = new Potion[potionStorePotions.size()];
        int potionsIdx = 0;
        for (int potionEnumId : potionStorePotions.getIntVals()) {
            EnumComposition potionEnum = client.getEnum(potionEnumId);
            client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnumId);
            int doses = client.getIntStack()[0];
            client.runScript(ScriptID.POTIONSTORE_WITHDRAW_DOSES, potionEnumId);
            int withdrawDoses = client.getIntStack()[0];

            if (doses > 0 && withdrawDoses > 0) {
                Potion p = new Potion();
                p.potionEnum = potionEnum;
                p.itemId = potionEnum.getIntValue(withdrawDoses);
                p.doses = doses;
                p.withdrawDoses = withdrawDoses;
                potions[potionsIdx] = p;

                if (log.isDebugEnabled()) {
                    log.debug("Potion store has {} doses of {}", p.doses, itemManager.getItemComposition(p.itemId).getName());
                }
            }

            ++potionsIdx;
        }
    }

    public int matches(Set<Integer> bank, int itemId) {
        if (potions == null) {
            return -1;
        }

        for (Potion potion : potions) {
            if (potion == null) {
                continue;
            }

            EnumComposition potionEnum = potion.potionEnum;
            int potionItemId1 = potionEnum.getIntValue(1);
            int potionItemId2 = potionEnum.getIntValue(2);
            int potionItemId3 = potionEnum.getIntValue(3);
            int potionItemId4 = potionEnum.getIntValue(4);

            if (potionItemId1 == itemId || potionItemId2 == itemId || potionItemId3 == itemId || potionItemId4 == itemId) {
                int potionStoreItem = potionEnum.getIntValue(potion.withdrawDoses);

                if (log.isDebugEnabled()) {
                    log.debug("Item {} matches a potion from potion store {}", itemId, itemManager.getItemComposition(potionStoreItem).getName());
                }

                return potionStoreItem;
            }
        }

        return -1;
    }

    public int count(int itemId) {
        if (potions == null) {
            return 0;
        }

        for (Potion potion : potions) {
            if (potion != null && potion.itemId == itemId) {
                return potion.doses / potion.withdrawDoses;
            }
        }
        return 0;
    }

    public int find(int itemId) {
        if (potions == null) {
            return -1;
        }

        int potionIdx = 0;
        for (Potion potion : potions) {
            ++potionIdx;
            if (potion != null && potion.itemId == itemId) {
                return potionIdx - 1;
            }
        }
        return -1;
    }

    void prepareWidgets()
    {
        // if the potion store hasn't been opened yet, the client components won't have been made yet.
        // they need to exist for the click to work correctly.
        Widget potStoreContent = client.getWidget(ComponentID.BANK_POTIONSTORE_CONTENT);
        if (potStoreContent.getChildren() == null)
        {
            int childIdx = 0;
            for (int i = 0; i < potions.length; ++i)
            {
                for (int j = 0; j < COMPONENTS_PER_POTION; ++j)
                {
                    potStoreContent.createChild(childIdx++, WidgetType.GRAPHIC);
                }
            }
        }
    }
}