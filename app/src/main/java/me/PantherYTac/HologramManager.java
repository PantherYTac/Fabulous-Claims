package me.PantherYTac;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.*;

public class HologramManager {
    private final ClaimPlugin plugin;
    private final ClaimManager manager;
    private final Map<UUID, List<ArmorStand>> activeHolograms = new HashMap<>();

    public HologramManager(ClaimPlugin plugin, ClaimManager manager) {
        this.plugin = plugin;
        this.manager = manager;

        // Schedule periodic refresh task (every 5 seconds)
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllHolograms, 100L, 100L);
    }

    public void updateAllHolograms() {
        if (!plugin.getConfig().getBoolean("features.holographic_banners", true)) {
            removeAllHolograms();
            return;
        }

        Set<UUID> currentClaims = new HashSet<>();

        for (Claim claim : manager.getClaims()) {
            currentClaims.add(claim.getId());

            // Check if hologram flag is enabled for this claim
            if (!claim.getFlag("hologram")) {
                removeHologram(claim.getId());
                continue;
            }

            World world = Bukkit.getWorld(claim.getWorldName());
            if (world == null || !world.isChunkLoaded(claim.getCenterX() >> 4, claim.getCenterZ() >> 4)) {
                removeHologram(claim.getId());
                continue;
            }

            spawnOrUpdateHologram(claim, world);
        }

        // Clean up holograms for claims that no longer exist
        Iterator<Map.Entry<UUID, List<ArmorStand>>> iterator = activeHolograms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<ArmorStand>> entry = iterator.next();
            if (!currentClaims.contains(entry.getKey())) {
                for (ArmorStand stand : entry.getValue()) {
                    if (stand != null && stand.isValid()) stand.remove();
                }
                iterator.remove();
            }
        }
    }

    private void spawnOrUpdateHologram(Claim claim, World world) {
        Location baseLoc = new Location(world, claim.getCenterX() + 0.5, claim.getCenterY() + 2.2, claim.getCenterZ() + 0.5);

        String ownerName = "Unknown";
        if (!claim.getOwners().isEmpty()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(claim.getOwners().iterator().next());
            if (op.getName() != null) ownerName = op.getName();
        }

        String titleLine = "§6§l❖ §e§l" + (claim.getName().isEmpty() ? "Claim (" + claim.getSizeId() + ")" : claim.getName()) + " §6§l❖";
        String ownerLine = "§7Owner: §f" + ownerName;
        String welcomeLine = claim.getWelcomeMessage().isEmpty()
                ? "§b" + claim.getSizeX() + "x" + claim.getSizeY() + "x" + claim.getSizeZ() + " Protected Area"
                : "§a\"" + claim.getWelcomeMessage() + "\"";

        List<String> lines = Arrays.asList(titleLine, ownerLine, welcomeLine);

        List<ArmorStand> stands = activeHolograms.get(claim.getId());
        if (stands == null || stands.isEmpty() || stands.stream().anyMatch(s -> s == null || !s.isValid())) {
            removeHologram(claim.getId());
            stands = new ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                Location lineLoc = baseLoc.clone().add(0, (lines.size() - 1 - i) * 0.28, 0);
                ArmorStand stand = (ArmorStand) world.spawnEntity(lineLoc, EntityType.ARMOR_STAND);
                stand.setGravity(false);
                stand.setCanPickupItems(false);
                stand.setCustomNameVisible(true);
                stand.setCustomName(lines.get(i));
                stand.setVisible(false);
                stand.setSmall(true);
                stand.setMarker(true);
                stands.add(stand);
            }
            activeHolograms.put(claim.getId(), stands);
        } else {
            // Update existing lines
            for (int i = 0; i < lines.size() && i < stands.size(); i++) {
                ArmorStand stand = stands.get(i);
                if (stand != null && stand.isValid()) {
                    stand.setCustomName(lines.get(i));
                }
            }
        }
    }

    public void removeHologram(UUID claimId) {
        List<ArmorStand> stands = activeHolograms.remove(claimId);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
    }

    public void removeAllHolograms() {
        for (List<ArmorStand> stands : activeHolograms.values()) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
        activeHolograms.clear();
    }
}
