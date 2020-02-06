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
package io.github.spleefx.arena.api;

import com.google.gson.annotations.Expose;
import io.github.spleefx.arena.ArenaPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A class for managing arenas with {@link ArenaType#FREE_FOR_ALL}
 */
public class FFAManager {

    /**
     * A predicate for testing whether
     */
    public static final Predicate<GameArena> IS_READY = arena -> (arena.getMaxPlayerCount() >= 2) && (arena.getFFAManager().spawnpoints.size() >= arena.getMaximum());

    /**
     * Map for spawnpoints
     */
    @Expose
    private Map<Integer, Location> spawnpoints = new HashMap<>();

    /**
     * Represents all the players
     */
    private Map<ArenaPlayer, Integer> players = new HashMap<>();

    /**
     * Registers a spawnpoint
     *
     * @param index    Index of the spawnpoint
     * @param location Location of the spawnpoint
     */
    public void registerSpawnpoint(int index, Location location) {
        spawnpoints.put(index, location);
    }

    /**
     * Returns the spawnpoint of the first free slot
     *
     * @return The spawnpoint
     */
    public Location getSpawnpoint(GameArena arena, Player player) {
        int empty = spawnpoints.keySet().stream().filter(index -> index <= arena.getMaximum() && !players.containsValue(index)).findFirst().orElse(-1);
        if (empty == -1) return null; // The arena is full
        players.put(ArenaPlayer.adapt(player), empty);
        return spawnpoints.get(empty);
    }

    public Location get(int index) {
        return spawnpoints.get(index);
    }

    public int getIndex(Player player) {
        return players.get(ArenaPlayer.adapt(player));
    }

    public void remove(Player player) {
        players.remove(ArenaPlayer.adapt(player));
    }

}
