package me.PantherYTac;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClaimEnterLeaveListener implements Listener {
    private final ClaimPlugin plugin;
    private final ClaimManager manager;

    public ClaimEnterLeaveListener(ClaimPlugin plugin, ClaimManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String resolveOwnerNames(Claim claim) {
        if (claim.getOwners().isEmpty()) return "Unknown";
        return claim.getOwners().stream()
                .map(id -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                    return op.getName() != null ? op.getName() : "Unknown";
                })
                .collect(Collectors.joining(", "));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return; // prevent NPE

        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        Optional<Claim> to = manager.getClaimAt(e.getTo());
        Optional<Claim> from = manager.getClaimAt(e.getFrom());

        if (from.isPresent() && (to.isEmpty() || !from.get().getId().equals(to.get().getId()))) {
            // leaving
            Claim claim = from.get();
            String ownerNames = resolveOwnerNames(claim);
            String msg = plugin.getConfig().getString("messages.leave", "&7You have left the claim of %owner%")
                    .replace("&", "§")
                    .replace("%owner%", ownerNames);
            p.sendMessage(msg);
        }

        if (to.isPresent() && (from.isEmpty() || !from.get().getId().equals(to.get().getId()))) {
            // entering
            Claim claim = to.get();

            if (plugin.getConfig().getBoolean("features.analytics", true) && claim.getFlag("analytics")) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
                String timestamp = sdf.format(new java.util.Date());
                claim.addVisitorLog("§7[" + timestamp + "] §f" + p.getName());
            }

            String msg = claim.getWelcomeMessage();
            if (plugin.getConfig().getBoolean("features.welcome_messages", true) && !msg.isEmpty()) {
                String formatted = plugin.getConfig().getString("messages.welcome", "&6[Claim] &f%message%")
                        .replace("&", "§")
                        .replace("%message%", msg);
                p.sendMessage(formatted);
            } else {
                String ownerNames = resolveOwnerNames(claim);
                String defaultEnter = plugin.getConfig().getString("messages.enter", "&7You have entered the claim of %owner%")
                        .replace("&", "§")
                        .replace("%owner%", ownerNames);
                p.sendMessage(defaultEnter);
            }
        }
    }
}
