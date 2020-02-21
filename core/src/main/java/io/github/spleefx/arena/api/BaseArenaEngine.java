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

import io.github.spleefx.SpleefX;
import io.github.spleefx.arena.ArenaPlayer;
import io.github.spleefx.arena.ArenaPlayer.ArenaPlayerState;
import io.github.spleefx.arena.ArenaStage;
import io.github.spleefx.arena.api.GameTask.Phase;
import io.github.spleefx.data.PlayerStatistic;
import io.github.spleefx.extension.GameEvent;
import io.github.spleefx.extension.ability.DoubleJumpHandler.DataHolder;
import io.github.spleefx.extension.ability.DoubleJumpHandler.DoubleJumpItems;
import io.github.spleefx.extension.ability.GameAbility;
import io.github.spleefx.message.MessageKey;
import io.github.spleefx.perk.GamePerk;
import io.github.spleefx.scoreboard.ScoreboardHolder;
import io.github.spleefx.sign.SignManager;
import io.github.spleefx.team.GameTeam;
import io.github.spleefx.team.TeamColor;
import io.github.spleefx.util.code.MapBuilder;
import io.github.spleefx.util.game.BukkitTaskUtils;
import io.github.spleefx.util.game.Chat;
import io.github.spleefx.util.game.Metas;
import io.github.spleefx.util.game.PlayerContext;
import io.github.spleefx.util.plugin.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.github.spleefx.compatibility.CompatibilityHandler.getProtocol;
import static io.github.spleefx.util.plugin.PluginSettings.*;

public abstract class BaseArenaEngine<R extends GameArena> implements ArenaEngine {

    protected Map<UUID, EnumMap<GameAbility, Integer>> abilityCount = new HashMap<>();

    protected Map<UUID, Map<GamePerk, Integer>> perksCount = new HashMap<>();

    /**
     * Represents all tasks that are ran when the game is over
     */
    private List<GameTask> endTasks = new ArrayList<>();

    /**
     * A predicate for validating whether an arena is fully setup or not.
     * <p>
     * If {@link Predicate#test(Object)} returns true this means that the arena requires setup
     */
    private static final Predicate<GameArena> SETUP_VALIDATION = arena -> {
        if (arena.getArenaType() != ArenaType.FREE_FOR_ALL)
            return arena.getTeams().size() < 2 || !arena.getSpawnPoints().keySet().containsAll(arena.getTeams());
        else
            return !FFAManager.IS_READY.test(arena);
    };

    private List<ArenaPlayer> dead = new LinkedList<>();

    /**
     * The arena that is subject to processing
     */
    protected R arena;

    /**
     * A list of all players in the arena
     */
    private Map<ArenaPlayer, GameTeam> playerTeams = new HashMap<>();

    /**
     * Represents all alive players
     */
    private List<Player> alive = new CopyOnWriteArrayList<>();

    /**
     * Time left to start
     */
    public int countdown = PluginSettings.COUNTDOWN_ON_ENOUGH_PLAYERS.get();

    /**
     * The task that controls the cooldown
     */
    private BukkitTask countdownTask;

    /**
     * The task that controls the game
     */
    private BukkitTask gameTask;

    /**
     * The task that controls the game time
     */
    private BukkitTask timerTask;

    /**
     * The time left till the game ends
     */
    public int timeLeft;

    /**
     * The arena sign manager
     */
    private SignManager signManager;

    /**
     * Creates an engine for the specified arena
     *
     * @param arena Arena to create for
     */
    public BaseArenaEngine(R arena) {
        this.arena = arena;
        signManager = new SignManager(arena);
        timeLeft = arena.getGameTime() * 60;
    }

    /**
     * Returns the current game stage
     *
     * @return The current game stage
     */
    @Override
    public ArenaStage getArenaStage() {
        if (!arena.isEnabled() || !arena.getExtension().isEnabled())
            return arena.stage = ArenaStage.DISABLED;
        if (SETUP_VALIDATION.test(arena))
            return arena.stage = ArenaStage.NEEDS_SETUP;
        return arena.stage == null || arena.stage == ArenaStage.NEEDS_SETUP ? (arena.stage = ArenaStage.WAITING) : arena.stage;
    }

    /**
     * Updates the game stage and all the subordinate signs
     *
     * @param stage New stage to set
     */
    @Override
    public void setArenaStage(ArenaStage stage) {
        arena.stage = stage;
        getSignManager().update();
    }

    /**
     * Selects a team randomly as long as it's not full
     *
     * @return The randomly selected team
     */
    @Override
    public GameTeam selectTeam() {
        if (arena.getArenaType() == ArenaType.FREE_FOR_ALL) return arena.getGameTeams().get(0); // The "FFA" team
        GameTeam team = arena.getGameTeams().get(ThreadLocalRandom.current().nextInt(arena.getGameTeams().size()));
        if (team.getMembers().size() == arena.getMembersPerTeam()) return selectTeam();
        return team;
    }

    /**
     * Joins the player to the arena
     *
     * @param p Player to join
     */
    @Override
    public boolean join(ArenaPlayer p) {
        Player player = p.getPlayer();
        if (!arena.isEnabled() || !arena.getExtension().isEnabled()) {
            MessageKey.ARENA_DISABLED.send(player, arena, null, null, player, null, null,
                    -1, arena.getExtension());
            return false;
        }
        if (containsItems(player.getInventory()) && (boolean) ARENA_REQUIRE_EMPTY_INV.get()) {
            MessageKey.MUST_HAVE_EMPTY_INV.send(player, arena, null, null, player, null, null,
                    -1, arena.getExtension());
            return false;
        }
        if (isFull()) {
            MessageKey.ARENA_FULL.send(player, arena, null, null, player, null, null,
                    -1, arena.getExtension());
            return false;
        }
        switch (arena.getEngine().getArenaStage()) {
            case DISABLED:
                MessageKey.ARENA_DISABLED.send(player, arena, null, null, player, null, null,
                        -1, arena.getExtension());
                return false;
            case ACTIVE:
                MessageKey.ARENA_ALREADY_ACTIVE.send(player, arena, null, null, player, null, null,
                        -1, arena.getExtension());
                return false;
            case REGENERATING:
                MessageKey.ARENA_REGENERATING.send(player, arena, null, null, player, null, null,
                        -1, arena.getExtension());
                return false;
            case NEEDS_SETUP:
                MessageKey.ARENA_NEEDS_SETUP.send(player, arena, null, null, player, null, null,
                        -1, arena.getExtension());
                return false;
        }
        if (playerTeams.containsKey(p)) return false;
        GameTeam team = selectTeam();
        team.getMembers().add(player);
        playerTeams.put(p, team);
        prepare(p, team);
        if (arena.getArenaType() == ArenaType.TEAMS)
            playerTeams.forEach((pl, r) -> MessageKey.PLAYER_JOINED_T.send(pl.getPlayer(), arena, team.getColor(), null, player, null,
                    null, -1, arena.getExtension()));
        else
            playerTeams.forEach((pl, r) -> MessageKey.PLAYER_JOINED_FFA.send(pl.getPlayer(), arena, team.getColor(), null, player, null,
                    null, -1, arena.getExtension()));
        getSignManager().update();
        if (playerTeams.size() >= arena.getMinimum())
            countdown();
        abilityCount.put(player.getUniqueId(), (EnumMap<GameAbility, Integer>)
                MapBuilder.of(new EnumMap<GameAbility, Integer>(GameAbility.class))
                        .put(GameAbility.DOUBLE_JUMP, arena.getExtension().getDoubleJumpSettings().getDefaultAmount()).build());
        return true;
    }

    private static boolean containsItems(Inventory inventory) {
        //noinspection ConstantConditions like bro you're wrong
        return Arrays.stream(inventory.getContents()).anyMatch(Objects::nonNull);
    }

    /**
     * Invoked when the player quits
     *
     * @param p Player to quit
     */
    @Override
    public void quit(ArenaPlayer p) {
        load(p);
        Player player = p.getPlayer();
        GameTeam team = playerTeams.remove(p);
        //playerCount.set(playerCount.get() - 1);
        if (team != null) {
            team.getAlive().remove(player);
            team.getMembers().remove(player);
        }
        alive.remove(player);
        if (playerTeams.size() < arena.getMinimum()) {
            try {
                countdownTask.cancel();
            } catch (NullPointerException ignored) { // Already cancelled
            }
            countdown = PluginSettings.COUNTDOWN_ON_ENOUGH_PLAYERS.get();
            setArenaStage(ArenaStage.WAITING);
            broadcast(MessageKey.NOT_ENOUGH_PLAYERS);
            playerTeams.keySet().forEach(ap -> ap.getPlayer().setLevel(0));
        }
    }

    /**
     * Invoked when the player loses
     *
     * @param player Player to lose
     * @param team   The player's team
     */
    @Override
    public void lose(ArenaPlayer player, GameTeam team) {
        Player p = player.getPlayer();
        dead.add(ArenaPlayer.adapt(p));
        SpleefX.getPlugin().getDataProvider().add(PlayerStatistic.LOSSES, p, arena.getExtension(), 1);
        load(player);
        playerTeams.get(player).getAlive().remove(player.getPlayer());
        team.getAlive().remove(player.getPlayer());
        alive.remove(player.getPlayer());
        if (arena.getArenaType() == ArenaType.TEAMS)
            playerTeams.keySet().forEach((pz) -> MessageKey.PLAYER_LOST_T.send(pz.getPlayer(), arena, team.getColor(), null, player.getPlayer(), null, null, -1, arena.getExtension()));
        else
            playerTeams.keySet().forEach((pz) -> MessageKey.PLAYER_LOST_FFA.send(pz.getPlayer(), arena, team.getColor(), null, player.getPlayer(), null, null, -1, arena.getExtension()));
        arena.getExtension().getGameTitles().get(GameEvent.LOSE).display(player.getPlayer());
    }

    /**
     * Invoked when the player wins
     *
     * @param p    Player to win
     * @param team The player's team
     */
    @Override
    public void win(ArenaPlayer p, GameTeam team) {
        Player player = p.getPlayer();
        SpleefX.getPlugin().getDataProvider().add(PlayerStatistic.WINS, player, arena.getExtension(), 1);
        gameTask.cancel();
        timerTask.cancel();
        timeLeft = arena.getGameTime() * 60;
        load(p);
        getPlayerTeams().keySet().forEach(pl -> {
            Chat.sendUnprefixed(pl.getPlayer(), arena.getExtension().getChatPrefix() + team.getColor().getChatColor() + player.getName() + " &ahas won!");
            arena.getExtension().getGameTitles().get(GameEvent.WIN).display(pl.getPlayer(), player.getName());
        });
        dead.add(ArenaPlayer.adapt(player)); // to give the winner rewards
        Collections.reverse(dead);
        arena.getExtension().getRunCommandsForWinners().forEach((index, commandsToRun) -> {
            try {
                ArenaPlayer died = dead.get(index - 1);
                if (died != null)
                    commandsToRun.forEach((sender, commands) -> commands.forEach(c -> sender.run(died.getPlayer(), c)));
            } catch (IndexOutOfBoundsException ignored) { // Theres a reward for the 3rd place but there is no 3rd player, etc.
            }
        });
        end();
    }

    /**
     * Invoked when the game result is draw
     */
    @Override
    public void draw() {
        gameTask.cancel();
        timerTask.cancel();
        timeLeft = arena.getGameTime() * 60;
        getPlayerTeams().keySet().forEach(p -> {
            load(p);
            arena.getExtension().getGameTitles().get(GameEvent.DRAW).display(p.getPlayer());
            SpleefX.getPlugin().getDataProvider().add(PlayerStatistic.DRAWS, p.getPlayer(), arena.getExtension(), 1);
        });
        end();
    }

    /**
     * Prepares the player to the game, by adding effects
     *
     * @param p    Player to prepare
     * @param team The player's team
     */
    @Override
    public void prepare(ArenaPlayer p, GameTeam team) {
        save(p);
        Player player = p.getPlayer();
        ArenaPlayer.adapt(player).setState(ArenaPlayerState.WAITING).setCurrentArena(arena);
        player.setGameMode(arena.getExtension().getWaitingMode());
        if (team.getColor() != TeamColor.FFA)
            player.teleport(arena.getLobby() == null ? arena.getSpawnPoints().get(team.getColor()) : arena.getLobby());
        else if (arena.getLobby() == null) player.teleport(arena.getFFAManager().getSpawnpoint(arena, player));
        else {
            player.teleport(arena.getLobby());
            arena.getFFAManager().getSpawnpoint(arena, player);
        }
        player.getInventory().setItem(arena.getExtension().getQuitItemSlot(), arena.getExtension().getQuitItem().factory().create());
        displayScoreboard(p);
    }

    /**
     * Prepares the player to the game, by teleporting, adding items, etc
     *
     * @param arenaPlayer Player to prepare
     * @param team        The player's team
     */
    @Override
    public void prepareForGame(ArenaPlayer arenaPlayer, GameTeam team) {
        Player player = arenaPlayer.getPlayer();
        team.getAlive().add(player);

        arenaPlayer.setState(ArenaPlayerState.IN_GAME)
                .setCurrentArena(arena);

        player.setGameMode(arena.getExtension().getInGameMode());
        arena.getExtension().getGivePotionEffects().forEach(player::addPotionEffect);
        if (arena.getArenaType() == ArenaType.FREE_FOR_ALL)
            player.teleport(arena.getFFAManager().get(arena.getFFAManager().getIndex(player)));
        else
            player.teleport(arena.getSpawnPoints().get(team.getColor()));
        player.getInventory().clear();
        arena.getExtension().getItemsToAdd().forEach((slot, item) -> player.getInventory().setItem(slot, item.factory().create()));
        SpleefX.getPlugin().getDataProvider().add(PlayerStatistic.GAMES_PLAYED, player, arena.getExtension(), 1);
        DataHolder doubleJumpSettings = arena.getExtension().getDoubleJumpSettings();
        if (!doubleJumpSettings.isEnabled()) return;
        if (doubleJumpSettings.getDefaultAmount() > 0) {
            player.setAllowFlight(true);
            addDoubleJumpItems(arenaPlayer, true);
        }
        arenaPlayer.getStats().getPerks().forEach((perk, count) -> {
            if (perk.consumeFrom(arenaPlayer)) {
                perksCount.computeIfAbsent(player.getUniqueId(), (k) -> new HashMap<>()).put(perk, perk.getPurchaseSettings().getIngameAmount());
                if (perk.canUse(arena.getExtension()))
                    perk.giveToPlayer(arenaPlayer);
            }
        });
        displayScoreboard(arenaPlayer);
    }

    public void addDoubleJumpItems(ArenaPlayer player, boolean newState) {
        DataHolder doubleJumpSettings = arena.getExtension().getDoubleJumpSettings();
        if (!doubleJumpSettings.isEnabled()) return;
        DoubleJumpItems items = doubleJumpSettings.getDoubleJumpItems();
        if (items.isEnabled() && doubleJumpSettings.getDefaultAmount() > 0)
            player.getPlayer().getInventory().setItem(items.getSlot(), newState ? items.getAvailable().factory().create() : items.getUnavailable().factory().create());
    }

    /**
     * Runs the countdown
     */
    @Override
    public void countdown() {
        if (countdownTask != null && !BukkitTaskUtils.isCancelled(countdownTask)) return;
        setArenaStage(ArenaStage.COUNTDOWN);
        if (ARENA_REGENERATE_BEFORE_COUNTDOWN.get())
            SpleefX.getPlugin().getArenaManager().regenerateArena(arena.getKey());

        playerTeams.forEach((p, team) -> MessageKey.GAME_STARTING.send(p.getPlayer(), arena, team.getColor(), null, p.getPlayer(), null, null, countdown, arena.getExtension()));

        Map<String, String> numbersToDisplay = TITLE_ON_COUNTDOWN_NUMBERS.get();
        countdownTask = Bukkit.getScheduler().runTaskTimer(SpleefX.getPlugin(), () -> {
            countdown--;
            playerTeams.forEach((p, value) -> {
                displayScoreboard(p);
                if (DISPLAY_COUNTDOWN_ON_EXP_BAR.get())
                    p.getPlayer().setLevel(countdown);
                String title = numbersToDisplay.get(Integer.toString(countdown));
                if (title != null)
                    if (TITLE_ON_COUNTDOWN_ENABLED.get()) {
                        getProtocol().displayTitle(p.getPlayer(), title, TITLE_ON_COUNTDOWN_SUBTITLE.get(), TITLE_ON_COUNTDOWN_FADE_IN.get(), TITLE_ON_COUNTDOWN_DISPLAY.get(), TITLE_ON_COUNTDOWN_FADE_OUT.get());
                        MessageKey.GAME_COUNTDOWN.send(p.getPlayer(), arena, value.getColor(), null, p.getPlayer(), null, countdown + "", countdown, arena.getExtension());
                    }
                List<Integer> when = PLAY_SOUND_ON_EACH_BROADCAST_WHEN.get();
                if (when.contains(countdown))
                    p.getPlayer().playSound(p.getPlayer().getLocation(), (Sound) PLAY_SOUND_ON_EACH_BROADCAST_SOUND.get(), 1, 1);
            });

            if (countdown == 0) {
                countdownTask.cancel();
                countdown = PluginSettings.COUNTDOWN_ON_ENOUGH_PLAYERS.get();
                start();
            }
        }, 20, 20);
    }

    /**
     * Starts the game
     */
    @Override
    public void start() {
        setArenaStage(ArenaStage.ACTIVE);
        playerTeams.forEach((arenaPlayer, team) -> {
            alive.add(arenaPlayer.getPlayer());
            prepareForGame(arenaPlayer, team);
        });
        timeLeft = arena.getGameTime() * 60;
        loop();
    }

    /**
     * Runs the game loop
     */
    @Override
    public void loop() {
        if (getArenaStage() != ArenaStage.ACTIVE) return;
        Map<String, String> numbers = TIME_OUT_WARN.get();
        try {
            timerTask = Bukkit.getScheduler().runTaskTimer(SpleefX.getPlugin(), () -> {
                timeLeft--;
                String m = numbers.get(Integer.toString(timeLeft));
                playerTeams.forEach((p, team) -> {
                    if (m != null)
                        MessageKey.GAME_TIMEOUT.send(p.getPlayer(), arena, team.getColor(), null, p.getPlayer(), null, m, timeLeft, arena.getExtension());
                });
            }, 20, 20);

            gameTask = Bukkit.getScheduler().runTaskTimer(SpleefX.getPlugin(), () -> {
                if (timeLeft == 0)
                    draw();
                if (arena.getArenaType() == ArenaType.FREE_FOR_ALL) {
                    GameTeam ffa = arena.getGameTeams().get(0);
                    alive.stream().filter(player -> player.getLocation().getY() <= arena.getDeathLevel()).forEach(player -> lose(ArenaPlayer.adapt(player), ffa));
                    if (alive.size() == 1) {
                        ArenaPlayer p = ArenaPlayer.adapt(alive.get(0));
                        win(p, playerTeams.get(p));
                        return;
                    }
                } else {
                    arena.getGameTeams().forEach(team -> team.getAlive().forEach((player) -> {
                        if (player.getLocation().getY() <= arena.getDeathLevel()) lose(ArenaPlayer.adapt(player), team);
                        if (team.getAlive().size() == 0)
                            playerTeams.keySet().forEach(p -> MessageKey.TEAM_ELIMINATED.send(p.getPlayer(), arena, team.getColor(), null, null, null, null, -1, arena.getExtension()));
                    }));
                    if (alive.size() == 1) {
                        Player p = alive.get(0);
                        ArenaPlayer player = ArenaPlayer.adapt(p);
                        win(player, playerTeams.get(player));
                        return;
                    }
                }
                if (alive.size() == 0) draw();
            }, ((Integer) ARENA_UPDATE_INTERVAL.get()).longValue(), ((Integer) ARENA_UPDATE_INTERVAL.get()).longValue());
        } catch (ConcurrentModificationException | NullPointerException ignored) {
        }
    }

    /**
     * Ends the game
     */
    @Override
    public void end() {
        endTasks.stream().filter(task -> task.getPhase() == Phase.BEFORE).forEach(GameTask::run);
        setArenaStage(ArenaStage.WAITING);
        playerTeams.clear();
        //playerCount.set(0);
        alive.clear();
        dead.clear();
        abilityCount.clear();
        arena.getGameTeams().forEach(team -> {
            team.getMembers().clear();
            team.getAlive().clear();
        });
        endTasks.stream().filter(task -> task.getPhase() == Phase.AFTER).forEach(GameTask::run);
        regenerate();
    }

    /**
     * Forcibly ends the game (used when the server is shutting down)
     */
    @Override
    public void forceEnd() {
        playerTeams.keySet().forEach(p -> {
            load(p);
            MessageKey.SERVER_STOPPED.send(p.getPlayer(), arena, null, null, p.getPlayer(), null, null, -1, arena.getExtension());
        });
        end();
    }

    /**
     * Regenerates the arena
     */
    @Override
    public void regenerate() {
        setArenaStage(ArenaStage.REGENERATING);
        SpleefX.getPlugin().getArenaManager().regenerateArena(arena.getKey());
        arena.stage = ArenaStage.WAITING;
        getSignManager().update();
    }

    /**
     * Saves the player data before they enter the arena, such as the inventory and location
     *
     * @param player Player to save for
     */
    @Override
    public void save(ArenaPlayer player) {
        Metas.set(player.getPlayer(), "spleefx.data", new FixedMetadataValue(SpleefX.getPlugin(), new PlayerContext(player.getPlayer())));
    }

    /**
     * Loads the saved data into the player
     *
     * @param p Player to load for
     */
    @Override
    public void load(ArenaPlayer p) {
        Player player = p.getPlayer();
        PlayerContext context = null;
        if (player.hasMetadata("spleefx.data")) {
            context = Metas.get(player, "spleefx.data");
            if (context != null)
                context.load(player);
            player.removeMetadata("spleefx.data", SpleefX.getPlugin());
        }
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()); // remove scoreboard
        ArenaPlayer.adapt(player).setCurrentArena(null).setState(ArenaPlayerState.NOT_INGAME);
        if (arena.getArenaType() == ArenaType.FREE_FOR_ALL) {
            arena.getFFAManager().remove(player);
        }
        abilityCount.remove(player.getUniqueId());
        if (context != null)
            player.setAllowFlight(context.allowFlight);
        player.setFallDistance(-500);
    }

    /**
     * Displays the game scoreboard for the player
     *
     * @param p Player to display for
     */
    @Override
    public void displayScoreboard(ArenaPlayer p) {
        if (p == null) return;
        Player player = p.getPlayer();
        ScoreboardHolder scoreboard = arena.getExtension().getScoreboard().get(arena.stage);
        if (scoreboard == null || !scoreboard.isEnabled()) return;
        scoreboard.scoreboard(ArenaPlayer.adapt(player), getScoreboardMap(player), arena)
                .ifPresent(s -> {
                    s.build();
                    player.setScoreboard(s.getScoreboard());
                });
    }

    /**
     * Returns the scoreboard placeholders map
     *
     * @param player Player to retrieve necessary information from
     * @return The placeholders map
     */
    protected Map<String, Supplier<String>> getScoreboardMap(Player player) {
        Map<String, Supplier<String>> map = new HashMap<>();
        map.put("{double_jumps}", () -> Integer.toString(abilityCount.get(player.getUniqueId()).get(GameAbility.DOUBLE_JUMP)));
        return map;
    }

    /**
     * Sends a message to all the players
     *
     * @param key Message key to broadcast
     */
    @Override
    public void broadcast(MessageKey key) {
        playerTeams.forEach((player, team) -> key.send(player.getPlayer(), arena, team.getColor(), null, null, null, null, -1, arena.getExtension()));
    }

    /**
     * Returns the signs manager, which updates all signs accordingly
     *
     * @return The signs manager
     */
    @Override
    public SignManager getSignManager() {
        return signManager;
    }

    /**
     * Returns a map of all arena players assigned to their teams
     *
     * @return ^
     */
    @Override
    public Map<ArenaPlayer, GameTeam> getPlayerTeams() {
        return playerTeams;
    }

    /**
     * Returns whether is this arena full or not
     *
     * @return ^
     */
    @Override
    public boolean isFull() {
        return playerTeams.size() == arena.getMaximum();
    }

    /**
     * Registers a task that is ran when the game ends
     *
     * @param task Task to add
     */
    public void registerEndTask(GameTask task) {
        endTasks.add(task);
    }

    /**
     * Returns the ability count of each player
     *
     * @return The ability count map
     */
    @Override
    public Map<UUID, EnumMap<GameAbility, Integer>> getAbilityCount() {
        return abilityCount;
    }

}