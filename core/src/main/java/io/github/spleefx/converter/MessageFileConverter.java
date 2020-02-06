/*
 * * Copyright 2020 github.com/ReflxctionDev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.spleefx.converter;

import io.github.spleefx.SpleefX;
import io.github.spleefx.util.code.MapBuilder;
import org.moltenjson.configuration.direct.DirectConfiguration;
import org.moltenjson.json.JsonFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageFileConverter implements Runnable {

    private static final Map<String, String> ECO = MapBuilder.of(new HashMap<String, String>())
            .put("moneyGiven", "&aYou have been given &e${value}&a.")
            .put("moneyTaken", "&e${value} &ahas been taken from you.")
            .put("boosterPaused", "&aThis booster has been paused.")
            .put("boosterGiven", "&aYou have been given a booster of type &e{booster_type_displayname}")
            .put("boosterActivated", "&aYour booster will be activated for &e{duration}&a.")
            .put("cannotActivateMoreBoosters", "&cYou have reached the maximum amount of boosters activated!")
            .put("boosterAlreadyActive", "&cThis booster is already active!")
            .put("itemPurchased", "&aYou have successfully bought {perk_displayname}&a!")
            .put("notEnoughCoins", "&cYou do not have enough coins to purchase this item!")
            .put("alreadyPurchased", "&cYou have already purchased this item!")
            .build();

    private static final Map<String, String> SPLEGG = MapBuilder.of(new HashMap<String, String>())
            .put("upgradeSelected", "&aYou have selected &e{upgrade_displayname}&a.")
            .put("notEnoughCoinsSplegg", "&cYou do not have enough coins to purchase this upgrade!")
            .put("upgradePurchased", "&aSuccessfully purchased and selected &e{upgrade_displayname}&a!")
            .put("mustPurchaseBefore", "&cYou must purchase previous abilities before buying this!")
            .build();

    private File file;

    public MessageFileConverter(File messagesFile) {
        file = messagesFile;
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        if (!file.exists()) return;
        DirectConfiguration messagesFile = DirectConfiguration.of(JsonFile.of(file));
        boolean changed = false;
        if (!messagesFile.contains("economy")) {
            messagesFile.set("economy", ECO);
        }
        if (!messagesFile.contains("splegg_upgrades")) {
            messagesFile.set("splegg_upgrades", SPLEGG);
        }
        if (changed) {
            messagesFile.save(Throwable::printStackTrace);
            SpleefX.logger().info("Successfully added the new messages to messages.json. Edit them through the in-game GUI!");
        }
    }
}