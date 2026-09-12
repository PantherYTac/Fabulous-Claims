package me.PantherYTac;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class ClaimGUI implements Listener {
    private final ClaimPlugin plugin;
    private final ClaimManager manager;

    public ClaimGUI(ClaimPlugin plugin, ClaimManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean feature(String path, boolean def) {
        return plugin.getConfig().getBoolean("features." + path, def);
    }

    private ItemStack createGlass(Material material, String name) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openClaimsList(Player p, List<Claim> claims) {
        if (!feature("gui_enabled", true)) {
            plugin.sendPrefixed(p, "§eGUI is disabled by server config.");
            return;
        }

        Inventory inv = Bukkit.createInventory(p, 54, "§8§l❖ §6§lYour Claims §8§l❖");

        // Fill borders with dark glass
        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack topBorder = createGlass(Material.CYAN_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, topBorder);
            inv.setItem(i + 45, border);
        }
        for (int i = 9; i < 45; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }

        // Close button at slot 49
        inv.setItem(49, createItem(Material.BARRIER, "§c§l✖ Close Menu", List.of("§7Click to exit.")));

        // Slot positions inside border: row 2 to 5 (slots 10-16, 19-25, 28-34, 37-43)
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < claims.size() && i < slots.length; i++) {
            Claim c = claims.get(i);
            ItemStack item = new ItemStack(Material.LODESTONE);
            ItemMeta meta = item.getItemMeta();

            String name = c.getName().isEmpty() ? "§eClaim #" + i : "§e" + c.getName();
            meta.setDisplayName("§6§l" + name + " §7(§b" + c.getSizeId() + "§7)");

            List<String> lore = new ArrayList<>();
            lore.add("§8────────────────────────");
            lore.add("§7📍 World: §f" + c.getWorldName());
            lore.add("§7📍 Coordinates: §fX: " + c.getCenterX() + " §7| §fY: " + c.getCenterY() + " §7| §fZ: " + c.getCenterZ());
            lore.add("§7📐 Dimensions: §f" + c.getSizeX() + "x" + c.getSizeY() + "x" + c.getSizeZ());
            lore.add("§7💰 Bank: §a$" + c.getBankBalance());
            lore.add("§7👥 Trusted Players: §a" + c.getTrusted().size());
            if (!c.getWelcomeMessage().isEmpty()) {
                lore.add("§7💬 Welcome: §f\"" + c.getWelcomeMessage() + "§f\"");
            }
            lore.add("§8────────────────────────");
            lore.add("§e▶ Click to manage this claim");

            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        p.openInventory(inv);
        plugin.sendPrefixed(p, "§eOpened claim management menu.");
    }

    public void openClaimManage(Player p, Claim claim, int index) {
        if (!feature("gui_enabled", true)) {
            p.sendMessage("§eGUI is disabled by server config.");
            return;
        }

        Inventory inv = Bukkit.createInventory(p, 45, "§8§l❖ §6§lManage Claim #" + index + " §8§l❖");

        // Fill background
        ItemStack bg = createGlass(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(i + 36, border);
        }
        for (int i = 9; i < 36; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }

        // Info Book (Slot 10)
        String cName = claim.getName().isEmpty() ? "Unnamed Claim" : claim.getName();
        inv.setItem(10, createItem(Material.BOOK, "§6§l" + cName, List.of(
                "§8────────────────────────",
                "§7Preset: §b" + claim.getSizeId() + " §7(" + claim.getSizeX() + "x" + claim.getSizeY() + "x" + claim.getSizeZ() + ")",
                "§7Location: §f" + claim.getWorldName() + " @ " + claim.getCenterX() + "," + claim.getCenterY() + "," + claim.getCenterZ(),
                "§7Bank Balance: §a$" + claim.getBankBalance(),
                "§7Rent Status: " + (claim.isRented() ? "§aRented" : "§cNot Rented"),
                "§8────────────────────────"
        )));

        // Add Trusted (Slot 12)
        inv.setItem(12, createItem(Material.PAPER, "§a§l➕ Trust a Player", List.of(
                "§7Click to grant build permission",
                "§7to a friend in this claim.",
                "",
                "§e▶ Click and type name in chat"
        )));

        // Rename (Slot 14)
        if (feature("custom_names", true)) {
            inv.setItem(14, createItem(Material.NAME_TAG, "§e§l🏷 Rename Claim", List.of(
                    "§7Current: §f" + cName,
                    "",
                    "§e▶ Click to set new name in chat"
            )));
        }

        // Welcome Message (Slot 16)
        if (feature("welcome_messages", true)) {
            String wel = claim.getWelcomeMessage().isEmpty() ? "(none)" : claim.getWelcomeMessage();
            inv.setItem(16, createItem(Material.OAK_SIGN, "§b§l💬 Welcome Message", List.of(
                    "§7Current: §f" + wel,
                    "",
                    "§e▶ Click to update message in chat"
            )));
        }

        // Holographic Banner Toggle (Slot 18)
        if (feature("holographic_banners", true)) {
            boolean holoOn = claim.getFlag("hologram");
            inv.setItem(18, createItem(Material.BEACON, "§e§l✨ Hologram Banner", List.of(
                    "§7Status: " + (holoOn ? "§a§l[ENABLED]" : "§c§l[DISABLED]"),
                    "§7Spawns a 3D hologram above",
                    "§7the claim anchor block.",
                    "",
                    "§e▶ Click to toggle hologram"
            )));
        }

        // Flags (Slot 20)
        if (feature("flags", true)) {
            inv.setItem(20, createItem(Material.LEVER, "§e§l⚙ Claim Flags", List.of(
                    "§7Toggle mob spawning, fire spread,",
                    "§7TNT explosions and protection flags.",
                    "",
                    "§e▶ Click to open flags menu"
            )));
        }

        // Teleport (Slot 22)
        inv.setItem(22, createItem(Material.ENDER_PEARL, "§d§l🚀 Teleport to Claim", List.of(
                "§7Instantly teleport to the center",
                "§7of this claim.",
                "",
                "§e▶ Click to teleport"
        )));

        // Upgrade (Slot 24)
        if (feature("upgrades", true)) {
            inv.setItem(24, createItem(Material.EMERALD, "§a§l▲ Upgrade Claim Size", List.of(
                    "§7Expand boundary area to a",
                    "§7larger preset tier.",
                    "",
                    "§e▶ Click to view upgrade options"
            )));
        }

        // Shared Claim Bank (Slot 26)
        if (feature("claim_bank", true)) {
            inv.setItem(26, createItem(Material.GOLD_INGOT, "§6§l💰 Shared Claim Bank", List.of(
                    "§7Balance: §a$" + claim.getBankBalance(),
                    "§7Deposit or withdraw funds for",
                    "§7claim upgrades & maintenance.",
                    "",
                    "§e▶ Click to open bank console"
            )));
        }

        // Particle Visualization & Themes (Slot 28)
        if (feature("visualization", true)) {
            inv.setItem(28, createItem(Material.GLOWSTONE_DUST, "§b§l✨ Visualizer & Themes", List.of(
                    "§7Current Theme: §f" + Visualization.getTheme(p.getUniqueId()),
                    "§7Highlights claim borders with",
                    "§7glowing particles.",
                    "",
                    "§e▶ Click to toggle or change theme"
            )));
        }

        // Sub-Leasing / Rent Console (Slot 30)
        if (feature("sub_leasing", true)) {
            inv.setItem(30, createItem(Material.OAK_DOOR, "§3§l🏠 Sub-Leasing Console", List.of(
                    "§7Price: §a$" + claim.getRentPricePerDay() + "/day",
                    "§7Status: " + (claim.isRented() ? "§aRented" : (claim.getFlag("forrent") ? "§eAvailable for Rent" : "§cNot Listed")),
                    "",
                    "§e▶ Click to open renting console"
            )));
        }

        // Analytics Console (Slot 32)
        if (feature("analytics", true)) {
            inv.setItem(32, createItem(Material.BOOKSHELF, "§d§l📊 Visitor & Incident Logs", List.of(
                    "§7Visitors: §f" + claim.getVisitorLogs().size() + " logged",
                    "§7Incidents: §c" + claim.getIncidentLogs().size() + " logged",
                    "",
                    "§e▶ Click to view claim analytics"
            )));
        }

        // Delete Claim (Slot 34)
        inv.setItem(34, createItem(Material.RED_STAINED_GLASS_PANE, "§c§l🗑 Delete Claim", List.of(
                "§7Permanently delete this claim.",
                "§cCannot be undone!",
                "",
                "§c▶ Click to delete"
        )));

        // Back to List (Slot 40)
        inv.setItem(40, createItem(Material.ARROW, "§e§l« Back to Claims", List.of("§7Return to your claims list.")));

        p.openInventory(inv);
    }

    public void openBankMenu(Player p, Claim claim, int index) {
        Inventory inv = Bukkit.createInventory(p, 27, "§8§l❖ §6§lClaim Bank #" + index + " §8§l❖");

        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        // Bank Info (Slot 10)
        inv.setItem(10, createItem(Material.GOLD_BLOCK, "§6§lClaim Balance: §a$" + claim.getBankBalance(), List.of(
                "§7Pooled funds can be used",
                "§7for claim upgrades."
        )));

        // Deposit $100 (Slot 12)
        inv.setItem(12, createItem(Material.GREEN_CONCRETE, "§a§lDeposit $100", List.of("§7Deposit $100 from your balance into claim bank.")));

        // Deposit $1000 (Slot 13)
        inv.setItem(13, createItem(Material.LIME_CONCRETE, "§a§lDeposit $1,000", List.of("§7Deposit $1,000 from your balance into claim bank.")));

        // Withdraw $100 (Slot 15 - Owners only)
        if (claim.isOwner(p.getUniqueId()) || p.hasPermission("fabulousclaims.admin")) {
            inv.setItem(15, createItem(Material.RED_CONCRETE, "§c§lWithdraw $100", List.of("§7Withdraw $100 from claim bank into your balance.")));
        }

        // Back button (Slot 22)
        inv.setItem(22, createItem(Material.ARROW, "§e§l« Back to Management", List.of("§7Return to claim #" + index)));

        p.openInventory(inv);
    }

    public void openRentMenu(Player p, Claim claim, int index) {
        Inventory inv = Bukkit.createInventory(p, 27, "§8§l❖ §3§lSub-Leasing #" + index + " §8§l❖");

        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        // Rent Status (Slot 10)
        String statusStr = claim.isRented()
                ? "§aRented by " + (Bukkit.getOfflinePlayer(claim.getRenterUuid()).getName())
                : (claim.getFlag("forrent") ? "§eListed for Rent" : "§cNot Listed");
        inv.setItem(10, createItem(Material.OAK_DOOR, "§3§lRent Status", List.of(
                "§7Price: §a$" + claim.getRentPricePerDay() + "/day",
                "§7Status: " + statusStr
        )));

        // Set Rent Price (Slot 12 - Owner only)
        if (claim.isOwner(p.getUniqueId()) || p.hasPermission("fabulousclaims.admin")) {
            inv.setItem(12, createItem(Material.NAME_TAG, "§e§lSet Daily Rent Price", List.of("§7Click and type price in chat.")));
            boolean forRent = claim.getFlag("forrent");
            inv.setItem(14, createItem(forRent ? Material.LIME_WOOL : Material.RED_WOOL,
                    forRent ? "§a§lListed for Rent" : "§c§lNot Listed",
                    List.of("§e▶ Click to toggle rental availability")));
        }

        // Rent for 1 Day (Slot 16 - non-owner)
        if (!claim.isOwner(p.getUniqueId()) && claim.getFlag("forrent") && !claim.isRented()) {
            inv.setItem(16, createItem(Material.EMERALD, "§a§lRent Claim for 1 Day", List.of("§7Cost: §a$" + claim.getRentPricePerDay(), "§e▶ Click to rent")));
        }

        // Back button (Slot 22)
        inv.setItem(22, createItem(Material.ARROW, "§e§l« Back to Management", List.of("§7Return to claim #" + index)));

        p.openInventory(inv);
    }

    public void openThemesMenu(Player p, Claim claim, int index) {
        Inventory inv = Bukkit.createInventory(p, 27, "§8§l❖ §b§lParticle Themes §8§l❖");

        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        String currentTheme = Visualization.getTheme(p.getUniqueId());

        inv.setItem(10, createItem(Material.EMERALD, "§a§lDEFAULT (Villager)", List.of(currentTheme.equals("DEFAULT") ? "§a§l[SELECTED]" : "§eClick to select")));
        inv.setItem(12, createItem(Material.SOUL_TORCH, "§b§lCYAN (Soul Flame)", List.of(currentTheme.equals("CYAN") ? "§a§l[SELECTED]" : "§eClick to select")));
        inv.setItem(14, createItem(Material.ENCHANTING_TABLE, "§d§lENCHANTMENT (Glyphs)", List.of(currentTheme.equals("ENCHANTMENT") ? "§a§l[SELECTED]" : "§eClick to select")));
        inv.setItem(15, createItem(Material.POPPY, "§c§lHEART (Hearts)", List.of(currentTheme.equals("HEART") ? "§a§l[SELECTED]" : "§eClick to select")));
        inv.setItem(16, createItem(Material.OBSIDIAN, "§5§lPORTAL (Purple)", List.of(currentTheme.equals("PORTAL") ? "§a§l[SELECTED]" : "§eClick to select")));

        // Back button (Slot 22)
        inv.setItem(22, createItem(Material.ARROW, "§e§l« Back to Management", List.of("§7Return to claim #" + index)));

        p.openInventory(inv);
    }

    public void openAnalyticsMenu(Player p, Claim claim, int index) {
        Inventory inv = Bukkit.createInventory(p, 36, "§8§l❖ §d§lAnalytics for Claim #" + index + " §8§l❖");

        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 27; i < 36; i++) inv.setItem(i, border);

        // Visitors (Slot 10-13)
        List<String> visitors = claim.getVisitorLogs();
        List<String> vLore = new ArrayList<>();
        if (visitors.isEmpty()) {
            vLore.add("§7No visitors logged yet.");
        } else {
            for (String log : visitors) vLore.add(log);
        }
        inv.setItem(11, createItem(Material.BOOK, "§a§lRecent Visitors (" + visitors.size() + ")", vLore));

        // Incidents (Slot 15-16)
        List<String> incidents = claim.getIncidentLogs();
        List<String> iLore = new ArrayList<>();
        if (incidents.isEmpty()) {
            iLore.add("§aNo griefing incidents logged!");
        } else {
            for (String log : incidents) iLore.add(log);
        }
        inv.setItem(15, createItem(Material.WRITABLE_BOOK, "§c§lBlocked Incidents (" + incidents.size() + ")", iLore));

        // Analytics flag toggle (Slot 22 - Owner only)
        if (claim.isOwner(p.getUniqueId()) || p.hasPermission("fabulousclaims.admin")) {
            boolean on = claim.getFlag("analytics");
            inv.setItem(22, createItem(on ? Material.LIME_WOOL : Material.RED_WOOL,
                    on ? "§a§lAnalytics Enabled" : "§c§lAnalytics Disabled",
                    List.of("§e▶ Click to toggle logging")));
        }

        // Back button (Slot 31)
        inv.setItem(31, createItem(Material.ARROW, "§e§l« Back to Management", List.of("§7Return to claim #" + index)));

        p.openInventory(inv);
    }

    private void openFlagsMenu(Player p, Claim claim, int index) {
        Inventory inv = Bukkit.createInventory(p, 27, "§8§l❖ §e§lFlags for Claim #" + index + " §8§l❖");

        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        Map<String, Boolean> flags = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        flags.putAll(claim.getFlags());

        int slot = 10;
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (slot > 16) break;
            boolean on = entry.getValue();
            Material woolMat = on ? Material.LIME_WOOL : Material.RED_WOOL;
            String stateStr = on ? "§a§l[ENABLED]" : "§c§l[DISABLED]";

            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + stateStr);
            lore.add("");
            lore.add("§e▶ Click to toggle this flag");

            inv.setItem(slot++, createItem(woolMat, "§f§l" + entry.getKey().toUpperCase(Locale.ROOT), lore));
        }

        // Back button (Slot 22)
        inv.setItem(22, createItem(Material.ARROW, "§e§l« Back to Management", List.of("§7Return to claim #" + index)));

        p.openInventory(inv);
    }

    private void openUpgradeMenu(Player p, Claim claim, int index) {
        Inventory inv = Bukkit.createInventory(p, 27, "§8§l❖ §a§lUpgrade Claim #" + index + " §8§l❖");

        ItemStack border = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        int slot = 10;
        for (ClaimManager.SizePreset preset : manager.getSizePresets().values()) {
            if (slot > 16) break;
            boolean larger = preset.x >= claim.getSizeX() && preset.y >= claim.getSizeY() && preset.z >= claim.getSizeZ();
            if (!larger) continue;

            List<String> lore = new ArrayList<>();
            lore.add("§8────────────────────────");
            lore.add("§7Dimensions: §f" + preset.x + "x" + preset.y + "x" + preset.z);
            lore.add("§7Cost: §a$" + preset.price);
            lore.add("§8────────────────────────");
            lore.add("§e▶ Click to upgrade to this tier");

            inv.setItem(slot++, createItem(Material.EMERALD, "§a§l" + preset.label + " Tier §7(" + preset.id + ")", lore));
        }

        // Back button (Slot 22)
        inv.setItem(22, createItem(Material.ARROW, "§e§l« Back to Management", List.of("§7Return to claim #" + index)));

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String rawTitle = e.getView().getTitle();
        if (rawTitle == null) return;
        String title = ChatColor.stripColor(rawTitle);

        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        // Claims list
        if (title.contains("Your Claims")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.BARRIER) {
                p.closeInventory();
                return;
            }

            if (clicked.getType() == Material.LODESTONE) {
                ItemMeta meta = clicked.getItemMeta();
                if (meta != null && meta.getDisplayName() != null) {
                    String dName = ChatColor.stripColor(meta.getDisplayName());
                    List<Claim> list = manager.getClaimsOf(p.getUniqueId());
                    for (int i = 0; i < list.size(); i++) {
                        Claim c = list.get(i);
                        String expectedName = c.getName().isEmpty() ? "Claim #" + i : c.getName();
                        if (dName.contains(expectedName)) {
                            openClaimManage(p, c, i);
                            return;
                        }
                    }
                }
            }
            return;
        }

        // Claim manage
        if (title.contains("Manage Claim #")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int idx = 0;
            int claimIndexPos = title.indexOf("Manage Claim #");
            if (claimIndexPos >= 0) {
                String numberPart = title.substring(claimIndexPos + 14).trim();
                numberPart = numberPart.replaceAll("[^0-9]", "");
                try { idx = Integer.parseInt(numberPart); } catch (NumberFormatException ignored) {}
            }

            List<Claim> list = manager.getClaimsOf(p.getUniqueId());
            if (idx < 0 || idx >= list.size()) {
                p.closeInventory();
                plugin.sendPrefixed(p, "§cClaim index out of range.");
                return;
            }
            Claim claim = list.get(idx);
            Material type = clicked.getType();

            // Back button
            if (type == Material.ARROW) {
                openClaimsList(p, list);
                return;
            }

            // Hologram Toggle via BEACON
            if (type == Material.BEACON && feature("holographic_banners", true)) {
                if (!claim.isOwner(p.getUniqueId()) && !p.hasPermission("fabulousclaims.admin")) {
                    plugin.sendPrefixed(p, "§cOnly the claim owner can toggle holograms.");
                    return;
                }
                boolean cur = claim.getFlag("hologram");
                claim.setFlag("hologram", !cur);
                manager.save();
                plugin.sendPrefixed(p, "§aHologram banner set to: " + (!cur ? "§aENABLED" : "§cDISABLED"));
                openClaimManage(p, claim, idx);
                return;
            }

            // Shared Bank via GOLD_INGOT
            if (type == Material.GOLD_INGOT && feature("claim_bank", true)) {
                openBankMenu(p, claim, idx);
                return;
            }

            // Sub-leasing Console via OAK_DOOR
            if (type == Material.OAK_DOOR && feature("sub_leasing", true)) {
                openRentMenu(p, claim, idx);
                return;
            }

            // Particle Themes & Visualizer via GLOWSTONE_DUST
            if (type == Material.GLOWSTONE_DUST && feature("visualization", true)) {
                openThemesMenu(p, claim, idx);
                return;
            }

            // Analytics via BOOKSHELF
            if (type == Material.BOOKSHELF && feature("analytics", true)) {
                openAnalyticsMenu(p, claim, idx);
                return;
            }

            // Remove trusted by clicking player head
            if (type == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                if (meta != null && meta.getOwningPlayer() != null) {
                    OfflinePlayer op = meta.getOwningPlayer();
                    boolean removed = manager.removeTrusted(claim, op);
                    plugin.sendPrefixed(p, removed ? "§aRemoved " + op.getName() + " from trusted." : "§eThey were not trusted.");
                    openClaimManage(p, claim, idx);
                }
                return;
            }

            // Add trusted via PAPER
            if (type == Material.PAPER) {
                p.closeInventory();
                plugin.sendPrefixed(p, "§eType the player name to trust in chat:");
                final Claim finalClaim = claim;
                final int finalIdx = idx;
                final Player finalPlayer = p;

                Bukkit.getPluginManager().registerEvents(new OneShotChatListener(finalPlayer.getUniqueId(), name -> {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                    boolean added = manager.addTrusted(finalClaim, target);
                    plugin.sendPrefixed(finalPlayer, added ? "§aAdded " + target.getName() + " to trusted." : "§eThey are already trusted or invalid.");
                    openClaimManage(finalPlayer, finalClaim, finalIdx);
                }), plugin);
                return;
            }

            // Rename via NAME_TAG
            if (type == Material.NAME_TAG && feature("custom_names", true)) {
                p.closeInventory();
                plugin.sendPrefixed(p, "§eType the new claim name in chat:");
                final Claim finalClaim = claim;
                final int finalIdx = idx;
                final Player finalPlayer = p;

                Bukkit.getPluginManager().registerEvents(new OneShotChatListener(finalPlayer.getUniqueId(), name -> {
                    finalClaim.setName(name);
                    manager.save();
                    plugin.sendPrefixed(finalPlayer, "§aClaim renamed to: " + name);
                    openClaimManage(finalPlayer, finalClaim, finalIdx);
                }), plugin);
                return;
            }

            // Welcome message via OAK_SIGN
            if (type == Material.OAK_SIGN && feature("welcome_messages", true)) {
                p.closeInventory();
                plugin.sendPrefixed(p, "§eType the welcome message in chat:");
                final Claim finalClaim = claim;
                final int finalIdx = idx;
                final Player finalPlayer = p;

                Bukkit.getPluginManager().registerEvents(new OneShotChatListener(finalPlayer.getUniqueId(), msg -> {
                    finalClaim.setWelcomeMessage(msg);
                    manager.save();
                    plugin.sendPrefixed(finalPlayer, "§aWelcome message updated.");
                    openClaimManage(finalPlayer, finalClaim, finalIdx);
                }), plugin);
                return;
            }

            // Flags via LEVER
            if (type == Material.LEVER && feature("flags", true)) {
                openFlagsMenu(p, claim, idx);
                return;
            }

            // Teleport via ENDER_PEARL
            if (type == Material.ENDER_PEARL) {
                org.bukkit.World w = Bukkit.getWorld(claim.getWorldName());
                if (w == null) {
                    plugin.sendPrefixed(p, "§cWorld '" + claim.getWorldName() + "' is not loaded.");
                    return;
                }
                Location loc = new Location(w, claim.getCenterX() + 0.5, claim.getCenterY() + 1, claim.getCenterZ() + 0.5);
                p.teleport(loc);
                plugin.sendPrefixed(p, "§aTeleported to your claim center!");
                p.closeInventory();
                return;
            }

            // Upgrade via EMERALD
            if (type == Material.EMERALD && feature("upgrades", true)) {
                openUpgradeMenu(p, claim, idx);
                return;
            }

            // Delete via RED_STAINED_GLASS_PANE
            if (type == Material.RED_STAINED_GLASS_PANE) {
                if (claim.isCreatedByBlock() && !p.hasPermission("fabulousclaims.admin")) {
                    plugin.sendPrefixed(p, "§cThis claim was created by placing a claim block. Break the block to delete it.");
                    return;
                }
                manager.deleteClaim(claim);
                p.closeInventory();
                plugin.sendPrefixed(p, "§aDeleted claim #" + idx + ".");
                openClaimsList(p, manager.getClaimsOf(p.getUniqueId()));
                return;
            }
        }

        // Bank Menu
        if (title.contains("Claim Bank #")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int idx = getClaimIndexFromTitle(title, "Claim Bank #");
            List<Claim> list = manager.getClaimsOf(p.getUniqueId());
            if (idx < 0 || idx >= list.size()) { p.closeInventory(); return; }
            Claim claim = list.get(idx);

            if (clicked.getType() == Material.ARROW) { openClaimManage(p, claim, idx); return; }

            if (clicked.getType() == Material.GREEN_CONCRETE) {
                if (manager.charge(p, 100.0)) {
                    claim.setBankBalance(claim.getBankBalance() + 100.0);
                    manager.saveClaim(claim);
                    plugin.sendPrefixed(p, "§aDeposited $100 into claim bank.");
                    openBankMenu(p, claim, idx);
                } else {
                    plugin.sendPrefixed(p, "§cInsufficient funds.");
                }
            } else if (clicked.getType() == Material.LIME_CONCRETE) {
                if (manager.charge(p, 1000.0)) {
                    claim.setBankBalance(claim.getBankBalance() + 1000.0);
                    manager.saveClaim(claim);
                    plugin.sendPrefixed(p, "§aDeposited $1,000 into claim bank.");
                    openBankMenu(p, claim, idx);
                } else {
                    plugin.sendPrefixed(p, "§cInsufficient funds.");
                }
            } else if (clicked.getType() == Material.RED_CONCRETE) {
                if (!claim.isOwner(p.getUniqueId()) && !p.hasPermission("fabulousclaims.admin")) {
                    plugin.sendPrefixed(p, "§cOnly claim owners can withdraw bank funds.");
                    return;
                }
                if (claim.getBankBalance() >= 100.0) {
                    claim.setBankBalance(claim.getBankBalance() - 100.0);
                    manager.deposit(p, 100.0);
                    manager.saveClaim(claim);
                    plugin.sendPrefixed(p, "§aWithdrew $100 from claim bank.");
                    openBankMenu(p, claim, idx);
                } else {
                    plugin.sendPrefixed(p, "§cInsufficient bank balance.");
                }
            }
            return;
        }

        // Sub-Leasing Menu
        if (title.contains("Sub-Leasing #")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int idx = getClaimIndexFromTitle(title, "Sub-Leasing #");
            List<Claim> list = manager.getClaimsOf(p.getUniqueId());
            if (idx < 0 || idx >= list.size()) { p.closeInventory(); return; }
            Claim claim = list.get(idx);

            if (clicked.getType() == Material.ARROW) { openClaimManage(p, claim, idx); return; }

            if (clicked.getType() == Material.NAME_TAG && (claim.isOwner(p.getUniqueId()) || p.hasPermission("fabulousclaims.admin"))) {
                p.closeInventory();
                plugin.sendPrefixed(p, "§eType daily rent price in chat:");
                Bukkit.getPluginManager().registerEvents(new OneShotChatListener(p.getUniqueId(), input -> {
                    try {
                        double price = Double.parseDouble(input);
                        claim.setRentPricePerDay(price);
                        manager.saveClaim(claim);
                        plugin.sendPrefixed(p, "§aDaily rent price set to $" + price);
                    } catch (NumberFormatException ex) {
                        plugin.sendPrefixed(p, "§cInvalid price.");
                    }
                    openRentMenu(p, claim, idx);
                }), plugin);
                return;
            }

            if ((clicked.getType() == Material.LIME_WOOL || clicked.getType() == Material.RED_WOOL) && (claim.isOwner(p.getUniqueId()) || p.hasPermission("fabulousclaims.admin"))) {
                boolean cur = claim.getFlag("forrent");
                claim.setFlag("forrent", !cur);
                manager.saveClaim(claim);
                plugin.sendPrefixed(p, "§aRental availability set to: " + (!cur ? "§aENABLED" : "§cDISABLED"));
                openRentMenu(p, claim, idx);
                return;
            }
            return;
        }

        // Particle Themes Menu
        if (title.contains("Particle Themes")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.ARROW) { p.closeInventory(); return; }

            Material mat = clicked.getType();
            String chosen = "DEFAULT";
            if (mat == Material.SOUL_TORCH) chosen = "CYAN";
            else if (mat == Material.ENCHANTING_TABLE) chosen = "ENCHANTMENT";
            else if (mat == Material.POPPY) chosen = "HEART";
            else if (mat == Material.OBSIDIAN) chosen = "PORTAL";

            Visualization.setTheme(p.getUniqueId(), chosen);
            plugin.sendPrefixed(p, "§aParticle theme set to: " + chosen);
            p.closeInventory();
            return;
        }

        // Analytics Menu
        if (title.contains("Analytics for Claim #")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int idx = getClaimIndexFromTitle(title, "Analytics for Claim #");
            List<Claim> list = manager.getClaimsOf(p.getUniqueId());
            if (idx < 0 || idx >= list.size()) { p.closeInventory(); return; }
            Claim claim = list.get(idx);

            if (clicked.getType() == Material.ARROW) { openClaimManage(p, claim, idx); return; }

            if ((clicked.getType() == Material.LIME_WOOL || clicked.getType() == Material.RED_WOOL) && (claim.isOwner(p.getUniqueId()) || p.hasPermission("fabulousclaims.admin"))) {
                boolean cur = claim.getFlag("analytics");
                claim.setFlag("analytics", !cur);
                manager.saveClaim(claim);
                plugin.sendPrefixed(p, "§aAnalytics logging set to: " + (!cur ? "§aENABLED" : "§cDISABLED"));
                openAnalyticsMenu(p, claim, idx);
            }
            return;
        }

        // Flags menu
        if (title.contains("Flags for Claim #")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int idx = getClaimIndexFromTitle(title, "Flags for Claim #");
            List<Claim> list = manager.getClaimsOf(p.getUniqueId());
            if (idx < 0 || idx >= list.size()) {
                p.closeInventory();
                plugin.sendPrefixed(p, "§cClaim index out of range.");
                return;
            }
            Claim claim = list.get(idx);

            if (clicked.getType() == Material.ARROW) {
                openClaimManage(p, claim, idx);
                return;
            }

            if (clicked.getType() == Material.LIME_WOOL || clicked.getType() == Material.RED_WOOL) {
                ItemMeta meta = clicked.getItemMeta();
                if (meta != null && meta.getDisplayName() != null) {
                    String flagName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT);
                    boolean current = claim.getFlag(flagName);
                    claim.setFlag(flagName, !current);
                    manager.save();
                    String msg = plugin.getConfig().getString("messages.flag_set", "§aFlag %flag% set to %value%")
                            .replace("%flag%", flagName)
                            .replace("%value%", (!current) ? "on" : "off");
                    plugin.sendPrefixed(p, msg);
                    openFlagsMenu(p, claim, idx);
                }
            }
            return;
        }

        // Upgrade menu
        if (title.contains("Upgrade Claim #")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int idx = getClaimIndexFromTitle(title, "Upgrade Claim #");
            List<Claim> list = manager.getClaimsOf(p.getUniqueId());
            if (idx < 0 || idx >= list.size()) {
                p.closeInventory();
                plugin.sendPrefixed(p, "§cClaim index out of range.");
                return;
            }
            Claim claim = list.get(idx);

            if (clicked.getType() == Material.ARROW) {
                openClaimManage(p, claim, idx);
                return;
            }

            if (clicked.getType() == Material.EMERALD) {
                String presetId = null;
                ItemMeta meta = clicked.getItemMeta();
                if (meta != null && meta.getDisplayName() != null) {
                    String stripped = ChatColor.stripColor(meta.getDisplayName());
                    int open = stripped.indexOf('(');
                    int close = stripped.indexOf(')');
                    if (open >= 0 && close > open) {
                        presetId = stripped.substring(open + 1, close).trim();
                    }
                }

                if (presetId == null) {
                    plugin.sendPrefixed(p, "§cInvalid preset selection.");
                    return;
                }

                ClaimManager.SizePreset preset = manager.getPreset(presetId);
                if (preset == null) {
                    plugin.sendPrefixed(p, "§cUnknown size preset.");
                    return;
                }

                if (!p.hasPermission("fabulousclaims.upgrade")) {
                    plugin.sendPrefixed(p, "§cYou lack permission: fabulousclaims.upgrade");
                    return;
                }

                if (!manager.upgradeClaim(claim, preset, p)) {
                    plugin.sendPrefixed(p, plugin.getConfig().getString("messages.upgrade_fail", "§cUpgrade failed: overlap or insufficient funds"));
                    return;
                }

                plugin.sendPrefixed(p, plugin.getConfig().getString("messages.upgrade_success", "§aClaim upgraded to %size%").replace("%size%", preset.label));
                openClaimManage(p, claim, idx);
            }
        }
    }

    private int getClaimIndexFromTitle(String title, String prefix) {
        int idx = 0;
        int pos = title.indexOf(prefix);
        if (pos >= 0) {
            String numberPart = title.substring(pos + prefix.length()).trim();
            numberPart = numberPart.replaceAll("[^0-9]", "");
            try { idx = Integer.parseInt(numberPart); } catch (NumberFormatException ignored) {}
        }
        return idx;
    }

    private static class OneShotChatListener implements Listener {
        private final UUID playerId;
        private final java.util.function.Consumer<String> callback;
        private final org.bukkit.scheduler.BukkitTask timeoutTask;

        public OneShotChatListener(UUID playerId, java.util.function.Consumer<String> callback) {
            this.playerId = playerId;
            this.callback = callback;
            this.timeoutTask = Bukkit.getScheduler().runTaskLater(ClaimPlugin.getInstance(), () -> {
                org.bukkit.event.HandlerList.unregisterAll(this);
                Player p = Bukkit.getPlayer(playerId);
                if (p != null) {
                    ClaimPlugin.getInstance().sendPrefixed(p, "§cClaim chat input timed out.");
                }
            }, 600L); // 30 seconds
        }

        @EventHandler
        public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
            if (!e.getPlayer().getUniqueId().equals(playerId)) return;
            e.setCancelled(true);
            String msg = e.getMessage().trim();
            org.bukkit.event.HandlerList.unregisterAll(this);
            timeoutTask.cancel();
            Bukkit.getScheduler().runTask(ClaimPlugin.getInstance(), () -> callback.accept(msg));
        }

        @EventHandler
        public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
            if (e.getPlayer().getUniqueId().equals(playerId)) {
                org.bukkit.event.HandlerList.unregisterAll(this);
                timeoutTask.cancel();
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        String rawTitle = e.getView().getTitle();
        if (rawTitle == null) return;
        String title = ChatColor.stripColor(rawTitle);

        if (title.contains("Your Claims")
                || title.contains("Manage Claim #")
                || title.contains("Flags for Claim #")
                || title.contains("Upgrade Claim #")
                || title.contains("Claim Bank #")
                || title.contains("Sub-Leasing #")
                || title.contains("Particle Themes")
                || title.contains("Analytics for Claim #")) {
            e.setCancelled(true);
        }
    }
}
