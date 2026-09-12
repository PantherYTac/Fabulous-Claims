package me.PantherYTac;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ClaimCommand implements CommandExecutor, TabCompleter {
    private final ClaimPlugin plugin;
    private final ClaimManager manager;
    private final ClaimGUI gui;
    private final ClaimBlockManager blockManager;

    public ClaimCommand(ClaimPlugin plugin, ClaimManager manager, ClaimGUI gui, ClaimBlockManager blockManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.blockManager = blockManager;
    }

    private boolean feature(String path, boolean def) {
        return plugin.getConfig().getBoolean("features." + path, def);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player p = (sender instanceof Player) ? (Player) sender : null;


        if (args.length == 0) {
            // Color-coded help output with all features
            String prefix = plugin.getPrefix(); // pulls from config
            sender.sendMessage(prefix + "§eAvailable commands:");

            if (sender.hasPermission("fabulousclaims.create")) {
                sender.sendMessage("§2/claim create <sizeId> §7- Create a claim at your location.");
            }

            sender.sendMessage("§3/claim add <player> §7- Trust a player in your claim.");
            sender.sendMessage("§5/claim remove <player> §7- Untrust a player in your claim.");
            sender.sendMessage("§f/claim transfer <player> §7- Transfer ownership of this claim.");
            sender.sendMessage("§8/claim list §7- Show all your claims.");
            sender.sendMessage("§4/claim delete §7- Delete the claim you are inside.");

            if (sender.hasPermission("fabulousclaims.giveblock")) {
                sender.sendMessage("§6/claim giveblock <sizeId> §7- Get a claim block item.");
            }

            if (feature("gui_enabled", true)) {
                sender.sendMessage("§3/claim gui §7- Open the claim management GUI.");
            }

            if (feature("visualization", true) && sender.hasPermission("fabulousclaims.visualize")) {
                sender.sendMessage("§5/claim visualize §7- Toggle particle visualization of claim boundaries.");
            }

            if (feature("custom_names", true)) {
                sender.sendMessage("§2/claim name <text> §7- Set a custom name for your claim.");
            }

            if (feature("welcome_messages", true)) {
                sender.sendMessage("§3/claim welcome <text> §7- Set a welcome message for your claim.");
            }

            if (feature("flags", true) && sender.hasPermission("fabulousclaims.flags")) {
                sender.sendMessage("§f/claim flag <key> <on|off> §7- Toggle claim flags.");
            }

            if (feature("upgrades", true) && sender.hasPermission("fabulousclaims.upgrade")) {
                sender.sendMessage("§6/claim upgrade <sizeId> §7- Upgrade your claim to a larger preset.");
            }

            if (feature("claim_bank", true)) {
                sender.sendMessage("§8/claim bank §7- Access shared claim bank.");
            }

            if (feature("sub_leasing", true)) {
                sender.sendMessage("§5/claim rent §7- Access sub-leasing / rental console.");
            }

            if (feature("particle_themes", true)) {
                sender.sendMessage("§3/claim theme <name> §7- Change boundary particle theme.");
            }

            if (feature("analytics", true)) {
                sender.sendMessage("§2/claim analytics §7- View visitor and incident logs.");
            }

            if (sender.hasPermission("fabulousclaims.admin")) {
                sender.sendMessage("§4/claim admin <inspect|delete|transfer|move|reload> §7- Admin tools.");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "bank": {
                if (p == null) { plugin.sendPrefixed(sender, "§cOnly players can manage claim bank."); return true; }
                if (!feature("claim_bank", true)) { plugin.sendPrefixed(p, "§cClaim bank is disabled by server config."); return true; }
                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) { plugin.sendPrefixed(p, "§cStand inside your claim to open claim bank."); return true; }
                List<Claim> list = manager.getClaimsOf(p.getUniqueId());
                gui.openBankMenu(p, claimOpt.get(), list.indexOf(claimOpt.get()));
                return true;
            }
            case "rent": {
                if (p == null) { plugin.sendPrefixed(sender, "§cOnly players can manage claim rent."); return true; }
                if (!feature("sub_leasing", true)) { plugin.sendPrefixed(p, "§cSub-leasing is disabled by server config."); return true; }
                Optional<Claim> claimOpt = manager.getClaimAt(p.getLocation());
                if (claimOpt.isEmpty()) { plugin.sendPrefixed(p, "§cStand inside a claim to view rent options."); return true; }
                List<Claim> list = manager.getClaimsOf(p.getUniqueId());
                int idx = list.contains(claimOpt.get()) ? list.indexOf(claimOpt.get()) : 0;
                gui.openRentMenu(p, claimOpt.get(), idx);
                return true;
            }
            case "theme": {
                if (p == null) { plugin.sendPrefixed(sender, "§cOnly players can change particle themes."); return true; }
                if (!feature("particle_themes", true)) { plugin.sendPrefixed(p, "§cParticle themes are disabled by server config."); return true; }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim theme <DEFAULT|CYAN|ENCHANTMENT|HEART|PORTAL>");
                    return true;
                }
                String themeName = args[1].toUpperCase(Locale.ROOT);
                Visualization.setTheme(p.getUniqueId(), themeName);
                plugin.sendPrefixed(p, "§aParticle theme set to: " + themeName);
                return true;
            }
            case "analytics": {
                if (p == null) { plugin.sendPrefixed(sender, "§cOnly players can view analytics."); return true; }
                if (!feature("analytics", true)) { plugin.sendPrefixed(p, "§cAnalytics is disabled by server config."); return true; }
                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) { plugin.sendPrefixed(p, "§cStand inside your claim to view analytics."); return true; }
                List<Claim> list = manager.getClaimsOf(p.getUniqueId());
                gui.openAnalyticsMenu(p, claimOpt.get(), list.indexOf(claimOpt.get()));
                return true;
            }

            case "create": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can create claims.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim create <sizeId>");
                    return true;
                }

                // Check if claims are enabled in this world
                if (!plugin.isClaimsEnabled(p.getWorld())) {
                    plugin.sendPrefixed(p, "§cClaims are disabled in this world.");
                    return true;
                }
                if (!sender.hasPermission("fabulousclaims.create")) {
                    plugin.sendPrefixed(sender, "§cYou are not allowed to use /claim create.");
                    return true;
                }

                String sizeId = args[1].toUpperCase(Locale.ROOT);
                ClaimManager.SizePreset preset = manager.getPreset(sizeId);

                if (preset == null) {
                    plugin.sendPrefixed(p, "§cUnknown size preset.");
                    return true;
                }
                if (!manager.isClaimBlockAllowed(p, sizeId)) {
                    plugin.sendPrefixed(p, "§cYou are not allowed to create " + sizeId + " claims.");
                    return true;
                }
                if (!manager.canCreateClaim(p)) {
                    plugin.sendPrefixed(p, "§cYou have reached your claim limit.");
                    return true;
                }
                if (manager.areaOverlaps(p.getLocation(), preset)) {
                    plugin.sendPrefixed(p, "§cThis area overlaps another claim.");
                    return true;
                }
                if (!manager.canAfford(p, preset)) {
                    plugin.sendPrefixed(p, "§cYou cannot afford this claim.");
                    return true;
                }
                if (!manager.charge(p, preset.price)) {
                    plugin.sendPrefixed(p, "§cPayment failed.");
                    return true;
                }

                manager.createClaim(p, p.getLocation(), preset, false);
                plugin.sendPrefixed(p, "§aClaim created at your location.");
                return true;
            }
            case "giveblock": {
                if (!sender.hasPermission("fabulousclaims.giveblock")) {
                    plugin.sendPrefixed(sender, "§cOnly admins can use /claim giveblock.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(sender, "§eUsage: /claim giveblock <sizeId> [player] [amount]");
                    return true;
                }

                String sizeId = args[1].toUpperCase(Locale.ROOT);
                ItemStack block = blockManager.getClaimBlockItem(sizeId);
                if (block == null) {
                    plugin.sendPrefixed(sender, "§cNo claim block configured for " + sizeId);
                    return true;
                }

                // Default target is the sender if they are a player
                Player targetPlayer = null;
                if (args.length >= 3) {
                    targetPlayer = Bukkit.getPlayerExact(args[2]);
                    if (targetPlayer == null) {
                        plugin.sendPrefixed(sender, "§cPlayer '" + args[2] + "' not found.");
                        return true;
                    }
                } else if (sender instanceof Player) {
                    targetPlayer = (Player) sender;
                } else {
                    plugin.sendPrefixed(sender, "§cConsole must specify a player.");
                    return true;
                }

                // Default amount is 1
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                        if (amount <= 0) amount = 1;
                    } catch (NumberFormatException ex) {
                        plugin.sendPrefixed(sender, "§cInvalid amount, using 1.");
                        amount = 1;
                    }
                }

                ItemStack toGive = block.clone();
                toGive.setAmount(amount);
                targetPlayer.getInventory().addItem(toGive);

                if (targetPlayer.equals(sender)) {
                    plugin.sendPrefixed(sender, "§aYou received " + amount + " " + block.getItemMeta().getDisplayName());
                } else {
                    plugin.sendPrefixed(sender, "§aGave " + amount + " " + block.getItemMeta().getDisplayName() + " to " + targetPlayer.getName());
                    plugin.sendPrefixed(targetPlayer, "§aYou received " + amount + " " + block.getItemMeta().getDisplayName() + " from " + sender.getName());
                }
                return true;
            }



            case "delete": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can delete claims.");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cYou are not inside your claim.");
                    return true;
                }
                Claim claim = claimOpt.get();

                // Prevent command deletion of block‑placed claims unless admin
                if (claim.isCreatedByBlock() && !p.hasPermission("fabulousclaims.admin") && !feature("claim_block_cleanup", false)) {
                    plugin.sendPrefixed(p, "§cThis claim was created by placing a claim block. Break the block to delete it.");
                    return true;
                }

                manager.deleteClaim(claim);
                plugin.sendPrefixed(p, "§aClaim deleted.");
                return true;
            }

            case "add": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can manage trusted players.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim add <player>");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cStand inside your claim to add trusted players.");
                    return true;
                }
                Claim claim = claimOpt.get();

                int limit = manager.getTrustLimit(p); // read from config based on group
                if (limit >= 0 && claim.getTrusted().size() >= limit) {
                    plugin.sendPrefixed(p, "§cYou have reached your trusted player limit ("
                            + claim.getTrusted().size() + "/" + limit + ").");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                boolean added = manager.addTrusted(claim, target);

                if (added) {
                    plugin.sendPrefixed(p, "§aTrusted " + target.getName() + " ("
                            + claim.getTrusted().size() + "/" + (limit < 0 ? "∞" : limit) + ")");
                } else {
                    plugin.sendPrefixed(p, "§eAlready trusted or invalid.");
                }
                return true;
            }

            case "remove": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can manage trusted players.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim remove <player>");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cStand inside your claim to remove trusted players.");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                boolean removed = manager.removeTrusted(claimOpt.get(), target);

                if (removed) {
                    plugin.sendPrefixed(p, "§aUntrusted " + target.getName());
                } else {
                    plugin.sendPrefixed(p, "§eThey were not trusted.");
                }
                return true;
            }
 
            case "transfer": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can transfer ownership of claims.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim transfer <player>");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cStand inside your claim to transfer ownership.");
                    return true;
                }

                Claim claim = claimOpt.get();
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    plugin.sendPrefixed(p, "§cPlayer '" + args[1] + "' must be online to transfer ownership.");
                    return true;
                }

                if (target.getUniqueId().equals(p.getUniqueId())) {
                    plugin.sendPrefixed(p, "§cYou cannot transfer ownership to yourself.");
                    return true;
                }

                if (claim.isOwner(target.getUniqueId())) {
                    plugin.sendPrefixed(p, "§c" + target.getName() + " is already an owner of this claim.");
                    return true;
                }

                if (!manager.canCreateClaim(target)) {
                    plugin.sendPrefixed(p, "§cTransfer failed: " + target.getName() + " has reached their claim limit.");
                    return true;
                }

                // Perform the transfer
                manager.addOwner(claim, target);
                manager.removeOwner(claim, p);

                plugin.sendPrefixed(p, "§aSuccessfully transferred ownership of this claim to " + target.getName() + ".");
                plugin.sendPrefixed(target, "§aYou have been transferred ownership of a claim by " + p.getName() + ".");
                return true;
            }

            case "gui": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can open the claims GUI.");
                    return true;
                }
                if (!feature("gui_enabled", true)) {
                    plugin.sendPrefixed(p, "§eGUI is disabled by server config.");
                    return true;
                }

                // Just call with 2 arguments, since the method builds its own title
                gui.openClaimsList(p, manager.getClaimsOf(p.getUniqueId()));

                return true;
            }



            case "list": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can view their claims.");
                    return true;
                }

                List<Claim> list = manager.getClaimsOf(p.getUniqueId());
                plugin.sendPrefixed(p, "§eYou own " + list.size() + " claim(s).");

                for (int i = 0; i < list.size(); i++) {
                    Claim c = list.get(i);
                    String name = c.getName().isEmpty() ? "(unnamed)" : c.getName();
                    plugin.sendPrefixed(p, "§7#" + i + " §f" + name + " §8[" + c.getSizeId() + "] §7" +
                            c.getWorldName() + " @ " + c.getCenterX() + "," + c.getCenterY() + "," + c.getCenterZ());
                }
                return true;
            }

            case "visualize": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can toggle visualization.");
                    return true;
                }
                if (!feature("visualization", true)) {
                    plugin.sendPrefixed(p, "§eVisualization is disabled by server config.");
                    return true;
                }
                if (!p.hasPermission("fabulousclaims.visualize")) {
                    plugin.sendPrefixed(p, "§cYou lack permission: fabulousclaims.visualize");
                    return true;
                }

                Visualization.toggle(p);
                plugin.sendPrefixed(p, "§aVisualization toggled."); // optional feedback
                return true;
            }

            case "name": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can rename claims.");
                    return true;
                }
                if (!feature("custom_names", true)) {
                    plugin.sendPrefixed(p, "§eCustom names are disabled by server config.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim name <text>");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cStand inside your claim to rename it.");
                    return true;
                }

                Claim c = claimOpt.get();
                c.setName(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                manager.save();

                plugin.sendPrefixed(p, "§aClaim renamed.");
                return true;
            }

            case "welcome": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can change welcome messages.");
                    return true;
                }
                if (!feature("welcome_messages", true)) {
                    plugin.sendPrefixed(p, "§eWelcome messages are disabled by server config.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim welcome <text>");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cStand inside your claim to set a welcome message.");
                    return true;
                }

                Claim c = claimOpt.get();
                c.setWelcomeMessage(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                manager.save();

                plugin.sendPrefixed(p, "§aWelcome message updated.");
                return true;
            }

            case "flag": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can manage claim flags.");
                    return true;
                }
                if (!feature("flags", true)) {
                    plugin.sendPrefixed(p, "§eClaim flags are disabled by server config.");
                    return true;
                }
                if (!p.hasPermission("fabulousclaims.flags")) {
                    plugin.sendPrefixed(p, "§cYou lack permission: fabulousclaims.flags");
                    return true;
                }
                if (args.length < 3) {
                    plugin.sendPrefixed(p, "§eUsage: /claim flag <key> <on|off>");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cStand inside your claim to set flags.");
                    return true;
                }

                String key = args[1].toLowerCase(Locale.ROOT);
                boolean value = args[2].equalsIgnoreCase("on");
                Claim c = claimOpt.get();
                c.setFlag(key, value);
                manager.save();

                String message = plugin.getConfig()
                        .getString("messages.flag_set", "§aFlag %flag% set to %value%")
                        .replace("%flag%", key)
                        .replace("%value%", value ? "on" : "off");

                plugin.sendPrefixed(p, message);
                return true;
            }

            case "upgrade": {
                if (p == null) {
                    plugin.sendPrefixed(sender, "§cOnly players can upgrade claims.");
                    return true;
                }
                if (!p.hasPermission("fabulousclaims.upgrade")) {
                    plugin.sendPrefixed(p, "§cYou lack permission: fabulousclaims.upgrade");
                    return true;
                }
                if (!feature("upgrades", true)) {
                    plugin.sendPrefixed(p, "§eClaim upgrades are disabled by server config.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim upgrade <sizeId>");
                    return true;
                }

                Optional<Claim> claimOpt = manager.getOwnedClaimAt(p.getUniqueId(), p.getLocation());
                if (claimOpt.isEmpty()) {
                    plugin.sendPrefixed(p, "§cStand inside your claim to upgrade it.");
                    return true;
                }

                Claim c = claimOpt.get();
                String sizeId = args[1].toUpperCase(Locale.ROOT);
                ClaimManager.SizePreset preset = manager.getPreset(sizeId);

                if (preset == null) {
                    plugin.sendPrefixed(p, "§cUnknown size preset.");
                    return true;
                }

                if (!manager.upgradeClaim(c, preset, p)) {
                    String failMsg = ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.upgrade_fail", "&cUpgrade failed: overlap or insufficient funds")
                    );
                    plugin.sendPrefixed(p, failMsg);
                    return true;
                }

                String successMsg = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.upgrade_success", "&aClaim upgraded to %size%")
                                .replace("%size%", preset.label)
                );
                plugin.sendPrefixed(p, successMsg);
                return true;
            }

            case "admin": {
                if (p == null) {
                    if (args.length >= 2 && "reload".equalsIgnoreCase(args[1])) {
                        plugin.reloadConfig();
                        manager.load();
                        plugin.getBlockManager().reload();
                        plugin.sendPrefixed(sender, "§aFabulousClaims configuration reloaded.");
                        return true;
                    }
                    plugin.sendPrefixed(sender, "§cOnly players can run this admin command.");
                    return true;
                }

                if (!p.hasPermission("fabulousclaims.admin")) {
                    plugin.sendPrefixed(p, "§cYou need fabulousclaims.admin.");
                    return true;
                }
                if (args.length < 2) {
                    plugin.sendPrefixed(p, "§eUsage: /claim admin <inspect|delete|transfer|move|reload>");
                    return true;
                }

                String action = args[1].toLowerCase(Locale.ROOT);
                switch (action) {
                    case "inspect": {
                        if (p == null) {
                            plugin.sendPrefixed(sender, "§cOnly players can create claims.");
                            return true;
                        }
                        Optional<Claim> claimOpt = manager.getClaimAt(p.getLocation());
                        if (claimOpt.isEmpty()) {
                            plugin.sendPrefixed(p, "§eNo claim here.");
                            return true;
                        }
                        Claim c = claimOpt.get();

                        String ownerNames = c.getOwners().stream()
                                .map(id -> {
                                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                    return op.getName() != null ? op.getName() : "Unknown";
                                })
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Unknown");

                        String trustedNames = c.getTrusted().stream()
                                .map(id -> {
                                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                    return op.getName() != null ? op.getName() : "Unknown";
                                })
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("(none)");

                        String flagsFormatted = c.getFlags().entrySet().stream()
                                .map(entry -> {
                                    String valueColored = entry.getValue() ? "§a■" : "§c■";
                                    return entry.getKey() + ": " + valueColored;
                                })
                                .reduce((a, b) -> a + "  " + b)
                                .orElse("(none)");

                        plugin.sendPrefixed(p, "§6Owner(s): §f" + ownerNames);
                        plugin.sendPrefixed(p, "§6Size: §f" + c.getSizeId() + " (" + c.getSizeX() + "x" + c.getSizeY() + "x" + c.getSizeZ() + ")");
                        plugin.sendPrefixed(p, "§6Trusted: §f" + trustedNames);
                        plugin.sendPrefixed(p, "§6Flags: §f" + flagsFormatted);
                        plugin.sendPrefixed(p, "§6Welcome: §f" + (c.getWelcomeMessage().isEmpty() ? "(none)" : c.getWelcomeMessage()));
                        plugin.sendPrefixed(p, "§6Name: §f" + (c.getName().isEmpty() ? "(unnamed)" : c.getName()));
                        return true;
                    }

                    case "delete": {
                        if (p == null) {
                            plugin.sendPrefixed(sender, "§cOnly players can create claims.");
                            return true;
                        }
                        Optional<Claim> claimOpt = manager.getClaimAt(p.getLocation());
                        if (claimOpt.isEmpty()) {
                            plugin.sendPrefixed(p, "§eNo claim here.");
                            return true;
                        }

                        Claim c = claimOpt.get();
                        String ownerNames = c.getOwners().stream()
                                .map(id -> {
                                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                    return op.getName() != null ? op.getName() : "Unknown";
                                })
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Unknown");

                        String claimName = c.getName().isEmpty() ? "(unnamed)" : c.getName();
                        manager.deleteClaim(c);

                        plugin.sendPrefixed(p, "§cClaim '" + claimName + "' owned by " + ownerNames + " was force-deleted.");
                        return true;
                    }

                    case "transfer": {
                        if (p == null) {
                            plugin.sendPrefixed(sender, "§cOnly players can create claims.");
                            return true;
                        }
                        if (args.length < 3) {
                            plugin.sendPrefixed(p, "§eUsage: /claim admin transfer <player>");
                            return true;
                        }
                        Optional<Claim> claimOpt = manager.getClaimAt(p.getLocation());
                        if (claimOpt.isEmpty()) {
                            plugin.sendPrefixed(p, "§eNo claim here.");
                            return true;
                        }

                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                        Claim c = claimOpt.get();

                        String oldOwners = c.getOwners().stream()
                                .map(id -> {
                                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                    return op.getName() != null ? op.getName() : "Unknown";
                                })
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Unknown");

                        for (UUID u : new HashSet<>(c.getOwners())) {
                            manager.removeOwner(c, Bukkit.getOfflinePlayer(u));
                        }

                        manager.addOwner(c, target);

                        String newOwners = c.getOwners().stream()
                                .map(id -> {
                                    OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                                    return op.getName() != null ? op.getName() : "Unknown";
                                })
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Unknown");

                        plugin.sendPrefixed(p, "§aClaim transferred. Old owner(s): " + oldOwners + " §7→ New owner(s): " + newOwners);
                        return true;
                    }

                    case "move": {
                        if (p == null) {
                            plugin.sendPrefixed(sender, "§cOnly players can create claims.");
                            return true;
                        }
                        plugin.sendPrefixed(p, "§eMove not implemented. Consider delete + recreate at target.");
                        return true;
                    }

                    case "reload": {
                        plugin.reloadConfig();
                        manager.load();
                        plugin.getBlockManager().reload();
                        plugin.sendPrefixed(sender, "§aFabulousClaims configuration reloaded.");
                        return true;
                    }

                    default:
                        plugin.sendPrefixed(p, "§eUsage: /claim admin <inspect|delete|transfer|move|reload>");
                        return true;
                }
            }

            default:
                plugin.sendPrefixed(p,  "§eUse /claim for help.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("add","remove","transfer","list","delete"));

            if (sender.hasPermission("fabulousclaims.create")) {
                base.add("create");
            }

            if (sender.hasPermission("fabulousclaims.giveblock")) {
                base.add("giveblock");
            }

            if (feature("gui_enabled", true)) {
                base.add("gui");
            }

            if (feature("visualization", true) && sender.hasPermission("fabulousclaims.visualize")) {
                base.add("visualize");
            }
            
            if (feature("custom_names", true)) {
                base.add("name");
            }

            if (feature("welcome_messages", true)) {
                base.add("welcome");
            }

            if (feature("flags", true) && sender.hasPermission("fabulousclaims.flags")) {
                base.add("flag");
            }

            if (feature("upgrades", true) && sender.hasPermission("fabulousclaims.upgrade")) {
                base.add("upgrade");
            }

            if (feature("claim_bank", true)) {
                base.add("bank");
            }

            if (feature("sub_leasing", true)) {
                base.add("rent");
            }

            if (feature("particle_themes", true) && sender.hasPermission("fabulousclaims.visualize")) {
                base.add("theme");
            }

            if (feature("analytics", true)) {
                base.add("analytics");
            }

            if (sender.hasPermission("fabulousclaims.admin")) {
                base.add("admin");
            }

            return base;
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create":
                    if (sender.hasPermission("fabulousclaims.create")) {
                        return new ArrayList<>(manager.getSizePresets().keySet());
                    } else {
                        return Collections.emptyList();
                    }
                case "giveblock":
                    if (sender.hasPermission("fabulousclaims.giveblock")) {
                        return new ArrayList<>(manager.getSizePresets().keySet());
                    } else {
                        return Collections.emptyList();
                    }
                case "upgrade":
                    if (feature("upgrades", true) && sender.hasPermission("fabulousclaims.upgrade")) {
                        return new ArrayList<>(manager.getSizePresets().keySet());
                    } else {
                        return Collections.emptyList();
                    }
                case "flag":
                    if (feature("flags", true) && sender.hasPermission("fabulousclaims.flags")) {
                        return Arrays.asList("mobspawning", "firespread", "tnt", "hologram", "forrent", "claimbank", "analytics");
                    } else {
                        return Collections.emptyList();
                    }
                case "theme":
                    if (feature("particle_themes", true) && sender.hasPermission("fabulousclaims.visualize")) {
                        return Arrays.asList("DEFAULT", "CYAN", "ENCHANTMENT", "HEART", "PORTAL");
                    } else {
                        return Collections.emptyList();
                    }
                case "admin":
                    if (sender.hasPermission("fabulousclaims.admin")) {
                        return Arrays.asList("inspect","delete","transfer","move","reload");
                    } else {
                        return Collections.emptyList();
                    }
                case "add":
                case "remove":
                case "transfer": {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (sender instanceof Player && online.getUniqueId().equals(((Player) sender).getUniqueId())) {
                            continue;
                        }
                        names.add(online.getName());
                    }
                    return names;
                }
            }
        }

        // Suggest player names for giveblock
        if (args.length == 3 && "giveblock".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }

        // Suggest amounts for giveblock
        if (args.length == 4 && "giveblock".equalsIgnoreCase(args[0])) {
            return Arrays.asList("1","5","10","32","64");
        }

        if (args.length == 3 && "flag".equalsIgnoreCase(args[0])) {
            return Arrays.asList("on","off");
        }

        // Suggest player names for /claim admin transfer <player>
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "transfer".equalsIgnoreCase(args[1])
                && sender.hasPermission("fabulousclaims.admin")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }

        return Collections.emptyList();
    }
}
