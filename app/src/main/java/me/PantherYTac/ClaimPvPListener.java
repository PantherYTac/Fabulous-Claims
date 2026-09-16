package me.PantherYTac;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;

public class ClaimPvPListener implements Listener {
    private final ClaimPlugin plugin;
    private final ClaimManager manager;

    public ClaimPvPListener(ClaimPlugin plugin, ClaimManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        // Only handle player vs player combat
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;

        Player victim = (Player) e.getEntity();
        Player attacker = (Player) e.getDamager();

        // Check config toggle
        boolean allowPvP = plugin.getConfig().getBoolean("features.pvp_in_claims", true);

        if (!allowPvP) {
            Optional<Claim> claim = manager.getClaimAt(victim.getLocation());
            if (claim.isPresent()) {
                e.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "PvP is disabled inside claims.");
            }
        }
    }
}
