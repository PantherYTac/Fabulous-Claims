package me.PantherYTac;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Optional;

public class ClaimMobProtectionListener implements Listener {
    private final ClaimManager manager;

    public ClaimMobProtectionListener(ClaimManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (!ClaimPlugin.getInstance().getConfig().getBoolean("features.flags", true)) return;
        Optional<Claim> claim = manager.getClaimAt(e.getLocation());
        if (claim.isPresent() && !claim.get().getFlag("mobspawning")) {
            e.setCancelled(true);
        }
    }
}
