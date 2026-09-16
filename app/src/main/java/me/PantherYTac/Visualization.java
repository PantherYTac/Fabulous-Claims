package me.PantherYTac;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Visualization implements Listener {
    private static final Set<UUID> enabled = new HashSet<>();
    private static final Map<UUID, String> themes = new HashMap<>();
    private static BukkitRunnable task;

    public static void init(ClaimPlugin plugin) {
        enabled.clear();
        themes.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getManager().isVisualizationEnabled(p.getUniqueId())) {
                enabled.add(p.getUniqueId());
            }
        }
        if (!enabled.isEmpty()) {
            start();
        }
    }

    public static void setTheme(UUID uuid, String theme) {
        themes.put(uuid, theme.toUpperCase(Locale.ROOT));
    }

    public static String getTheme(UUID uuid) {
        return themes.getOrDefault(uuid, "DEFAULT");
    }

    public static void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        enabled.clear();
        themes.clear();
    }

    public static void toggle(Player p) {
        UUID uuid = p.getUniqueId();
        boolean newState;
        if (enabled.contains(uuid)) {
            enabled.remove(uuid);
            newState = false;
            p.sendMessage("§eClaim visualization disabled.");
            stopIfNone();
        } else {
            enabled.add(uuid);
            newState = true;
            p.sendMessage("§aClaim visualization enabled (" + getTheme(uuid) + " theme).");
            start();
        }
        ClaimPlugin.getInstance().getManager().setVisualizationEnabled(uuid, newState);
    }

    private static void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : new HashSet<>(enabled)) {
                    Player player = ClaimPlugin.getInstance().getServer().getPlayer(id);
                    if (player == null) {
                        enabled.remove(id);
                        continue;
                    }
                    var cm = ClaimPlugin.getInstance().getManager();
                    Location pLoc = player.getLocation();
                    if (pLoc.getWorld() == null) continue;

                    for (Claim c : cm.getClaims()) {
                        if (c.getWorldName().equals(pLoc.getWorld().getName())) {
                            double distSq = Math.pow(c.getCenterX() - pLoc.getX(), 2)
                                    + Math.pow(c.getCenterZ() - pLoc.getZ(), 2);
                            if (distSq <= 10000.0) { // 100 * 100 blocks
                                render(player, c);
                            }
                        }
                    }
                }
            }
        };
        task.runTaskTimer(ClaimPlugin.getInstance(), 0L, 20L);
    }

    private static void stopIfNone() {
        if (enabled.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean isEnabled = ClaimPlugin.getInstance().getManager().isVisualizationEnabled(p.getUniqueId());
        if (isEnabled) {
            enabled.add(p.getUniqueId());
            start();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        enabled.remove(e.getPlayer().getUniqueId());
        themes.remove(e.getPlayer().getUniqueId());
        stopIfNone();
    }

    private static Particle getParticleForTheme(String theme) {
        switch (theme.toUpperCase(Locale.ROOT)) {
            case "CYAN":
                try { return Particle.valueOf("SOUL_FIRE_FLAME"); } catch (Exception e) { return Particle.FLAME; }
            case "ENCHANTMENT":
                try { return Particle.valueOf("ENCHANTMENT_TABLE"); } catch (Exception e) { return Particle.CRIT; }
            case "HEART":
                return Particle.HEART;
            case "PORTAL":
                return Particle.PORTAL;
            case "DEFAULT":
            default:
                return getHappyVillagerParticle();
        }
    }

    private static Particle getHappyVillagerParticle() {
        try {
            return Particle.valueOf("HAPPY_VILLAGER");
        } catch (IllegalArgumentException e) {
            try {
                return Particle.valueOf("VILLAGER_HAPPY");
            } catch (IllegalArgumentException ex) {
                return Particle.HEART;
            }
        }
    }

    private static void render(Player p, Claim c) {
        Particle part = getParticleForTheme(getTheme(p.getUniqueId()));
        int halfX = c.getSizeX() / 2, halfZ = c.getSizeZ() / 2;
        int cx = c.getCenterX(), cz = c.getCenterZ();
        int minY = c.getCenterY() - c.getSizeY() / 2;
        int maxY = c.getCenterY() + c.getSizeY() / 2;

        // 1. Render 4 vertical corner lines (from minY to maxY)
        int[] xs = {cx - halfX, cx + halfX};
        int[] zs = {cz - halfZ, cz + halfZ};
        for (int x : xs) {
            for (int z : zs) {
                for (double y = minY; y <= maxY; y += 2.0) {
                    p.spawnParticle(part, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }

        // 2. Render horizontal grid at player's height (clamped to claim bounds)
        int playerY = p.getLocation().getBlockY();
        double targetY = playerY + 0.5;
        if (targetY < minY) targetY = minY + 0.5;
        if (targetY > maxY) targetY = maxY - 0.5;

        // Draw boundaries at targetY
        for (double x = cx - halfX; x <= cx + halfX; x += 1.0) {
            p.spawnParticle(part, x + 0.5, targetY, cz - halfZ + 0.5, 1, 0, 0, 0, 0);
            p.spawnParticle(part, x + 0.5, targetY, cz + halfZ + 0.5, 1, 0, 0, 0, 0);
        }
        for (double z = cz - halfZ; z <= cz + halfZ; z += 1.0) {
            p.spawnParticle(part, cx - halfX + 0.5, targetY, z + 0.5, 1, 0, 0, 0, 0);
            p.spawnParticle(part, cx + halfX + 0.5, targetY, z + 0.5, 1, 0, 0, 0, 0);
        }
    }
}
