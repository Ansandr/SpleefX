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
package io.github.spleefx.listeners;

import io.github.spleefx.arena.ArenaPlayer;
import io.github.spleefx.arena.api.GameArena;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for {@link PlayerJoinEvent} and {@link PlayerQuitEvent}
 */
public class ConnectionListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ArenaPlayer.adapt(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        ArenaPlayer player = ArenaPlayer.adapt(event.getPlayer());
        GameArena arena = player.getCurrentArena();
        if (arena == null || arena.getEngine() == null || arena.getEngine().getPlayerTeams() == null || arena.getEngine().getPlayerTeams().isEmpty())
            return;
        switch (player.getState()) {
            case WAITING:
            case SPECTATING:
                arena.getEngine().quit(player, true);
                break;
            case IN_GAME:
                arena.getEngine().lose(player, arena.getEngine().getPlayerTeams().get(player), true);
                break;
        }
        ArenaPlayer.remove(event.getPlayer());
    }
}
