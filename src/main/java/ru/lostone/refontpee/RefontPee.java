package ru.lostone.refontpee;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Sound;
import org.bukkit.Particle.DustOptions;
import org.bukkit.util.Vector;

public final class RefontPee extends JavaPlugin implements Listener, CommandExecutor {
    private static final double STEP = 0.35;
    private static final int STEPS = 6;
    private static final double CROUCH_HEIGHT = 0.9;
    private static final long DURATION_MS = 4500L;
    private static final long SOUND_INTERVAL_MS = 650L;

    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSoundAt = new ConcurrentHashMap<>();
    private BukkitTask task;
    private DustOptions[] gradientDust;

    @Override
    public void onEnable() {
        Bukkit.createBlockData(Material.YELLOW_WOOL);
        gradientDust = buildGradientDust();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("pee") != null) {
            getCommand("pee").setExecutor(this);
        }

        task = Bukkit.getScheduler().runTaskTimer(this, this::tickPee, 1L, 3L);
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        activeUntil.clear();
        lastSoundAt.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        activeUntil.remove(id);
        lastSoundAt.remove(id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        UUID id = player.getUniqueId();
        boolean nowActive;
        if (activeUntil.containsKey(id)) {
            activeUntil.remove(id);
            lastSoundAt.remove(id);
            nowActive = false;
        } else {
            activeUntil.put(id, System.currentTimeMillis() + DURATION_MS);
            nowActive = true;
        }

        player.sendMessage(nowActive ? gradient("Режим писания: ВКЛ") : gradient("Режим писания: ВЫКЛ"));
        return true;
    }

    private void tickPee() {
        if (activeUntil.isEmpty()) {
            return;
        }

        Iterator<UUID> iterator = activeUntil.keySet().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Long until = activeUntil.get(id);
            if (until == null || System.currentTimeMillis() > until) {
                iterator.remove();
                lastSoundAt.remove(id);
                continue;
            }

            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                lastSoundAt.remove(id);
                continue;
            }

            Location base = player.getLocation().add(0.0, CROUCH_HEIGHT, 0.0);
            Vector dir = base.getDirection().normalize();
            World world = base.getWorld();
            if (world == null) {
                continue;
            }

            for (int i = 1; i <= STEPS; i++) {
                DustOptions dust = gradientDust[i - 1];
                Location point = base.clone().add(dir.clone().multiply(i * STEP));
                world.spawnParticle(
                        Particle.REDSTONE,
                        point,
                        1,
                        0.02,
                        0.02,
                        0.02,
                        0.0,
                        dust
                );
            }

            long now = System.currentTimeMillis();
            Long last = lastSoundAt.get(id);
            if (last == null || now - last >= SOUND_INTERVAL_MS) {
                lastSoundAt.put(id, now);
                world.playSound(base, Sound.ENTITY_PLAYER_SPLASH, 0.35f, 1.2f);
                world.playSound(base, Sound.BLOCK_WATER_AMBIENT, 0.2f, 0.9f);
            }
        }
    }

    private String gradient(String text) {
        int[] start = {255, 236, 120};
        int[] end = {255, 153, 40};
        StringBuilder out = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float t = len == 1 ? 0.0f : (float) i / (len - 1);
            int r = (int) (start[0] + (end[0] - start[0]) * t);
            int g = (int) (start[1] + (end[1] - start[1]) * t);
            int b = (int) (start[2] + (end[2] - start[2]) * t);
            out.append(hexColor(r, g, b)).append(text.charAt(i));
        }
        return out.toString();
    }

    private String hexColor(int r, int g, int b) {
        String hex = String.format("%02X%02X%02X", r, g, b);
        StringBuilder out = new StringBuilder("§x");
        for (int i = 0; i < hex.length(); i++) {
            out.append('§').append(hex.charAt(i));
        }
        return out.toString();
    }

    private DustOptions[] buildGradientDust() {
        DustOptions[] dust = new DustOptions[STEPS];
        int r1 = 255;
        int g1 = 236;
        int b1 = 120;
        int r2 = 255;
        int g2 = 185;
        int b2 = 40;
        for (int i = 0; i < STEPS; i++) {
            float t = STEPS == 1 ? 0.0f : (float) i / (STEPS - 1);
            int r = (int) (r1 + (r2 - r1) * t);
            int g = (int) (g1 + (g2 - g1) * t);
            int b = (int) (b1 + (b2 - b1) * t);
            dust[i] = new DustOptions(Color.fromRGB(r, g, b), 1.1f);
        }
        return dust;
    }
}
