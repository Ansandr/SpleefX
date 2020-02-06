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

import com.google.gson.JsonObject;
import io.github.spleefx.SpleefX;
import io.github.spleefx.arena.ArenaStage;
import io.github.spleefx.arena.api.ArenaData;
import io.github.spleefx.extension.GameExtension.SenderType;
import io.github.spleefx.extension.ability.DoubleJumpHandler.DataHolder;
import io.github.spleefx.extension.ability.TripleArrowsAbility.Settings;
import io.github.spleefx.scoreboard.ScoreboardHolder;
import io.github.spleefx.util.code.MapBuilder;
import org.apache.commons.io.FilenameUtils;
import org.moltenjson.configuration.direct.DirectConfiguration;
import org.moltenjson.json.JsonFile;
import org.moltenjson.utils.JsonUtils;
import org.moltenjson.utils.ReflectiveTypes;

import java.io.File;
import java.util.*;

/**
 * A task that converts files from the old extensions format to the newer one. Useful when updating to avoid breaking
 * backwards compatibility
 */
public class LegacyExtensionConverter implements Runnable {

    private File extensionsDirectory;

    //<editor-fold desc="Upgrades JSON" defaultstate="collapsed">
    private static final JsonObject UPGRADES = JsonUtils.getObjectFromString("{\"woodenShovel\": {\n" +
            "      \"key\": \"woodenShovel\",\n" +
            "      \"delay\": 1.0,\n" +
            "      \"default\": true,\n" +
            "      \"price\": 0,\n" +
            "      \"requiredUpgradesBefore\": []\n" +
            "    },\n" +
            "    \"stoneShovel\": {\n" +
            "      \"key\": \"stoneShovel\",\n" +
            "      \"delay\": 0.8,\n" +
            "      \"default\": false,\n" +
            "      \"price\": 1000,\n" +
            "      \"requiredUpgradesBefore\": [\n" +
            "        \"woodenShovel\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"ironShovel\": {\n" +
            "      \"key\": \"ironShovel\",\n" +
            "      \"delay\": 0.6,\n" +
            "      \"default\": false,\n" +
            "      \"price\": 2000,\n" +
            "      \"requiredUpgradesBefore\": [\n" +
            "        \"stoneShovel\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"goldenShovel\": {\n" +
            "      \"key\": \"goldenShovel\",\n" +
            "      \"delay\": 0.4,\n" +
            "      \"default\": false,\n" +
            "      \"price\": 3000,\n" +
            "      \"requiredUpgradesBefore\": [\n" +
            "        \"ironShovel\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"diamondShovel\": {\n" +
            "      \"key\": \"diamondShovel\",\n" +
            "      \"delay\": 0.2,\n" +
            "      \"default\": false,\n" +
            "      \"price\": 5000,\n" +
            "      \"requiredUpgradesBefore\": [\n" +
            "        \"stoneShovel\"\n" +
            "      ]\n" +
            "    }}", ArenaData.GSON);
    private static final JsonObject SPLEGG_SHOP = JsonUtils.getObjectFromString(" {\n" +
            "    \"title\": \"&2Splegg Upgrades\",\n" +
            "    \"rows\": 1,\n" +
            "    \"items\": {\n" +
            "      \"0\": {\n" +
            "        \"purchaseUpgrade\": \"woodenShovel\",\n" +
            "        \"type\": \"wooden_shovel\",\n" +
            "        \"count\": 1,\n" +
            "        \"enchantments\": [\n" +
            "          \"infinity:1\"\n" +
            "        ],\n" +
            "        \"displayName\": \"&eWooden Shovel\",\n" +
            "        \"lore\": [\n" +
            "          \"&eDelay: &a{upgrade_delay}\",\n" +
            "          \"&bPrice: &d{upgrade_price}\",\n" +
            "          \"{upgrade_purchased}\"\n" +
            "        ],\n" +
            "        \"itemFlags\": [\n" +
            "          \"HIDE_ENCHANTS\"\n" +
            "        ],\n" +
            "        \"unbreakable\": false\n" +
            "      },\n" +
            "      \"2\": {\n" +
            "        \"purchaseUpgrade\": \"stoneShovel\",\n" +
            "        \"type\": \"stone_shovel\",\n" +
            "        \"count\": 1,\n" +
            "        \"enchantments\": [\n" +
            "          \"infinity:1\"\n" +
            "        ],\n" +
            "        \"displayName\": \"&7Stone Shovel\",\n" +
            "        \"lore\": [\n" +
            "          \"&eDelay: &a{upgrade_delay}\",\n" +
            "          \"&bPrice: &d{upgrade_price}\",\n" +
            "          \"{upgrade_purchased}\"\n" +
            "        ],\n" +
            "        \"itemFlags\": [\n" +
            "          \"HIDE_ENCHANTS\"\n" +
            "        ],\n" +
            "        \"unbreakable\": false\n" +
            "      },\n" +
            "      \"4\": {\n" +
            "        \"purchaseUpgrade\": \"ironShovel\",\n" +
            "        \"type\": \"iron_shovel\",\n" +
            "        \"count\": 1,\n" +
            "        \"enchantments\": [\n" +
            "          \"infinity:1\"\n" +
            "        ],\n" +
            "        \"displayName\": \"&7Iron Shovel\",\n" +
            "        \"lore\": [\n" +
            "          \"&eDelay: &a{upgrade_delay}\",\n" +
            "          \"&bPrice: &d{upgrade_price}\",\n" +
            "          \"{upgrade_purchased}\"\n" +
            "        ],\n" +
            "        \"itemFlags\": [\n" +
            "          \"HIDE_ENCHANTS\"\n" +
            "        ],\n" +
            "        \"unbreakable\": false\n" +
            "      }\n" +
            "    },\n" +
            "    \"4\": {\n" +
            "      \"purchaseUpgrade\": \"ironShovel\",\n" +
            "      \"type\": \"iron_shovel\",\n" +
            "      \"count\": 1,\n" +
            "      \"enchantments\": [\n" +
            "        \"infinity:1\"\n" +
            "      ],\n" +
            "      \"displayName\": \"&7Iron Shovel\",\n" +
            "      \"lore\": [\n" +
            "        \"&eDelay: &a{upgrade_delay}\",\n" +
            "        \"&bPrice: &d{upgrade_price}\",\n" +
            "        \"{upgrade_purchased}\"\n" +
            "      ],\n" +
            "      \"itemFlags\": [\n" +
            "        \"HIDE_ENCHANTS\"\n" +
            "      ],\n" +
            "      \"unbreakable\": false\n" +
            "    },\n" +
            "    \"6\": {\n" +
            "      \"purchaseUpgrade\": \"goldenShovel\",\n" +
            "      \"type\": \"golden_shovel\",\n" +
            "      \"count\": 1,\n" +
            "      \"enchantments\": [\n" +
            "        \"infinity:1\"\n" +
            "      ],\n" +
            "      \"displayName\": \"&6Golden Shovel\",\n" +
            "      \"lore\": [\n" +
            "        \"&eDelay: &a{upgrade_delay}\",\n" +
            "        \"&bPrice: &d{upgrade_price}\",\n" +
            "        \"{upgrade_purchased}\"\n" +
            "      ],\n" +
            "      \"itemFlags\": [\n" +
            "        \"HIDE_ENCHANTS\"\n" +
            "      ],\n" +
            "      \"unbreakable\": false\n" +
            "    },\n" +
            "    \"8\": {\n" +
            "      \"purchaseUpgrade\": \"diamondShovel\",\n" +
            "      \"type\": \"diamond_shovel\",\n" +
            "      \"count\": 1,\n" +
            "      \"enchantments\": [\n" +
            "        \"infinity:1\"\n" +
            "      ],\n" +
            "      \"displayName\": \"&bDiamond Shovel\",\n" +
            "      \"lore\": [\n" +
            "        \"&eDelay: &a{upgrade_delay}\",\n" +
            "        \"&bPrice: &d{upgrade_price}\",\n" +
            "        \"{upgrade_purchased}\"\n" +
            "      ],\n" +
            "      \"itemFlags\": [\n" +
            "        \"HIDE_ENCHANTS\"\n" +
            "      ],\n" +
            "      \"unbreakable\": false\n" +
            "    }\n" +
            "  }", ArenaData.GSON);
    //</editor-fold>

    public LegacyExtensionConverter(File extensionsDirectory) {
        this.extensionsDirectory = extensionsDirectory;
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
        if (!extensionsDirectory.exists()) return; // There are no files to convert
        Arrays.stream(extensionsDirectory.listFiles()).forEach(this::convert);
    }

    private void convert(File file) {
        if (file.isFile()) {
            if (!FilenameUtils.getExtension(file.getName()).equals("json"))
                return; // Ignored file
            DirectConfiguration d = DirectConfiguration.of(JsonFile.of(file));
            StringJoiner changed = new StringJoiner(" / ").setEmptyValue("");
            if (!d.contains("runCommandsForWinners")) { // File is already in the new form
                Map<Integer, Map<SenderType, List<String>>> commands = new HashMap<>();
                commands.put(1, MapBuilder.of(new HashMap<SenderType, List<String>>())
                        .put(SenderType.PLAYER, d.get("runCommandsByWinner", ReflectiveTypes.LIST_STRING_TYPE))
                        .put(SenderType.CONSOLE, d.get("runCommandsByConsoleForWinner", ReflectiveTypes.LIST_STRING_TYPE))
                        .build());
                d.remove("runCommandsByWinner");
                d.remove("runCommandsByConsoleForWinner");
                d.set("runCommandsForWinners", commands);
                changed.add("Added rewards for other winners");
            }
            if (!d.contains("scoreboard")) {
                ScoreboardHolder sb = new ScoreboardHolder();
                d.set("scoreboard", MapBuilder.of(new LinkedHashMap<>())
                        .put(ArenaStage.WAITING, sb)
                        .put(ArenaStage.COUNTDOWN, sb)
                        .put(ArenaStage.ACTIVE, sb).build());
                changed.add("Added scoreboards");
            }
            if (!d.contains("doubleJump")) {
                d.set("doubleJump", new DataHolder());
                changed.add("Added double jumps");
            }
            if (!d.contains("giveDroppedItems")) {
                d.set("giveDroppedItems", true);
                changed.add("Added giveDroppedItems option");
            }
            if (d.contains("scoreboard") && d.getMap("scoreboard").containsKey("enabled")) { // old scoreboard format
                ScoreboardHolder holder = d.get("scoreboard", ScoreboardHolder.class, ArenaData.GSON);
                ScoreboardHolder sb = new ScoreboardHolder();
                d.set("scoreboard", MapBuilder.of(new LinkedHashMap<>()).putIfAbsent(ArenaStage.ACTIVE, holder)
                        .putIfAbsent(ArenaStage.WAITING, sb).putIfAbsent(ArenaStage.COUNTDOWN, sb).build());
                changed.add("Add a new scoreboard for each arena stage");
            }
            if (d.getFile().getFile().getName().startsWith("bow_spleef") && !d.contains("tripleArrows")) {
                d.set("tripleArrows", new Settings());
                changed.add("Add triple arrows");
            }
            String ch = changed.toString().trim();
            if (!ch.isEmpty()) {
                d.save(Throwable::printStackTrace, ArenaData.GSON);
                SpleefX.logger().info("[LegacyExtensionConverter] Successfully converted old extension " + file.getName() + " to the newer format. (Changes: " + ch.trim() + ")");
            }
        } else { // it is a directory not a file, so convert recursively
            File[] files = file.listFiles();
            if (files == null) return;
            for (File old : files) {
                convert(old);
            }
        }
    }
}