package me.PantherYTac;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ClaimManager {
    private final ClaimPlugin plugin;
    private final Map<UUID, Claim> claimsById = new HashMap<>();
    private final Map<UUID, List<Claim>> claimsByOwner = new HashMap<>();
    private final Map<String, Set<Claim>> claimsByChunk = new HashMap<>();

    private final File claimsFolder;
    private final File playersFolder;
    private final File legacyStorageFile;

    public static class SizePreset {
        public final String id;
        public final String label;
        public final int x, y, z;
        public final double price;
        public SizePreset(String id, String label, int x, int y, int z, double price) {
            this.id = id; this.label = label; this.x = x; this.y = y; this.z = z; this.price = price;
        }
    }

    private final Map<String, SizePreset> sizePresets = new LinkedHashMap<>();

    public ClaimManager(ClaimPlugin plugin) {
        this.plugin = plugin;
        this.claimsFolder = new File(plugin.getDataFolder(), "claims");
        this.playersFolder = new File(plugin.getDataFolder(), "players");
        if (!claimsFolder.exists()) claimsFolder.mkdirs();
        if (!playersFolder.exists()) playersFolder.mkdirs();

        String legacyPath = plugin.getConfig().getString("storage.file", "claims.yml");
        this.legacyStorageFile = new File(plugin.getDataFolder(), legacyPath);

        // Load claim types (presets)
        ConfigurationSection typesSection = plugin.getConfig().getConfigurationSection("claim-types");
        if (typesSection != null) {
            for (String id : typesSection.getKeys(false)) {
                ConfigurationSection sub = typesSection.getConfigurationSection(id);
                if (sub == null) continue;
                String label = sub.getString("label", id);
                int x = sub.getInt("x", 64);
                int y = sub.getInt("y", 64);
                int z = sub.getInt("z", 64);
                double price = sub.getDouble("price", 0.0);
                sizePresets.put(id.toUpperCase(Locale.ROOT), new SizePreset(id, label, x, y, z, price));
            }
        }
    }

    private int toInt(Object o, int def) { return o instanceof Number ? ((Number) o).intValue() : def; }
    private double toDouble(Object o, double def) { return o instanceof Number ? ((Number) o).doubleValue() : def; }

    private List<String> getChunkKeys(Claim claim) {
        List<String> keys = new ArrayList<>();
        int halfX = claim.getSizeX() / 2;
        int halfZ = claim.getSizeZ() / 2;
        int minX = claim.getCenterX() - halfX;
        int maxX = claim.getCenterX() + halfX;
        int minZ = claim.getCenterZ() - halfZ;
        int maxZ = claim.getCenterZ() + halfZ;

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                keys.add(claim.getWorldName() + ":" + cx + ":" + cz);
            }
        }
        return keys;
    }

    private void addClaimToSpatialIndex(Claim claim) {
        for (String key : getChunkKeys(claim)) {
            claimsByChunk.computeIfAbsent(key, k -> new HashSet<>()).add(claim);
        }
    }

    private void removeClaimFromSpatialIndex(Claim claim) {
        for (String key : getChunkKeys(claim)) {
            Set<Claim> set = claimsByChunk.get(key);
            if (set != null) {
                set.remove(claim);
                if (set.isEmpty()) {
                    claimsByChunk.remove(key);
                }
            }
        }
    }

    private void migrateLegacyStorage() {
        if (!legacyStorageFile.exists()) return;

        plugin.getLogger().info("Found legacy storage file. Starting migration to folder-based storage...");
        FileConfiguration legacyConfig = YamlConfiguration.loadConfiguration(legacyStorageFile);
        ConfigurationSection root = legacyConfig.getConfigurationSection("claims");
        if (root != null) {
            int count = 0;
            for (String idStr : root.getKeys(false)) {
                ConfigurationSection cs = root.getConfigurationSection(idStr);
                if (cs == null) continue;
                try {
                    File file = new File(claimsFolder, idStr + ".yml");
                    FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    for (String key : cs.getKeys(true)) {
                        cfg.set(key, cs.get(key));
                    }
                    cfg.save(file);
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to migrate claim " + idStr + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Successfully migrated " + count + " claims to folder storage.");
        }

        // Rename or delete old storage file to complete migration
        File backup = new File(plugin.getDataFolder(), "claims.yml.bak");
        if (legacyStorageFile.renameTo(backup)) {
            plugin.getLogger().info("Renamed legacy claims.yml to claims.yml.bak");
        } else {
            legacyStorageFile.delete();
            plugin.getLogger().info("Deleted legacy claims.yml");
        }
    }

    public void load() {
        claimsById.clear();
        claimsByOwner.clear();
        claimsByChunk.clear();

        migrateLegacyStorage();

        File[] files = claimsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            String idStr = name.substring(0, name.length() - 4);
            FileConfiguration cs = YamlConfiguration.loadConfiguration(file);
            try {
                UUID id = UUID.fromString(idStr);
                String world = Objects.requireNonNull(cs.getString("world"));
                int cx = cs.getInt("center.x"), cy = cs.getInt("center.y"), cz = cs.getInt("center.z");
                int sx = cs.getInt("size.x"), sy = cs.getInt("size.y"), sz = cs.getInt("size.z");
                String sizeId = cs.getString("sizeId", "MEDIUM");
                boolean createdByBlock = cs.getBoolean("createdByBlock", true);

                Location worldLoc = new Location(Bukkit.getWorld(world), cx, cy, cz);
                List<String> ownersS = cs.getStringList("owners");
                UUID primaryOwner = ownersS.isEmpty()
                        ? UUID.fromString(Objects.requireNonNull(cs.getString("owner")))
                        : UUID.fromString(ownersS.get(0));

                Claim claim = new Claim(id, primaryOwner, worldLoc, sx, sy, sz, sizeId, createdByBlock);

                for (String u : ownersS) claim.getOwners().add(UUID.fromString(u));
                for (String u : cs.getStringList("trusted")) claim.getTrusted().add(UUID.fromString(u));

                claim.setName(cs.getString("name", ""));
                claim.setWelcomeMessage(cs.getString("welcomeMessage", ""));
                claim.setBankBalance(cs.getDouble("bankBalance", 0.0));
                claim.setRentPricePerDay(cs.getDouble("rentPricePerDay", 0.0));
                String rUuidStr = cs.getString("renterUuid", "");
                if (rUuidStr != null && !rUuidStr.isEmpty()) {
                    claim.setRenterUuid(UUID.fromString(rUuidStr));
                }
                claim.setRentExpireTime(cs.getLong("rentExpireTime", 0L));
                for (String log : cs.getStringList("visitorLogs")) claim.addVisitorLog(log);
                for (String log : cs.getStringList("incidentLogs")) claim.addIncidentLog(log);

                ConfigurationSection flagsSec = cs.getConfigurationSection("flags");
                if (flagsSec != null) {
                    for (String key : flagsSec.getKeys(false)) {
                        claim.setFlag(key, flagsSec.getBoolean(key, false));
                    }
                } else {
                    ConfigurationSection defFlags = plugin.getConfig().getConfigurationSection("defaults.flags");
                    if (defFlags != null) {
                        for (String key : defFlags.getKeys(false)) {
                            claim.setFlag(key, defFlags.getBoolean(key, false));
                        }
                    }
                }

                claimsById.put(id, claim);
                for (UUID ownerId : claim.getOwners()) {
                    claimsByOwner.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(claim);
                }
                addClaimToSpatialIndex(claim);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load claim file " + name + ": " + e.getMessage());
            }
        }
    }

    public void saveClaim(Claim claim) {
        if (claim == null) return;
        File file = new File(claimsFolder, claim.getId().toString() + ".yml");
        FileConfiguration cfg = new YamlConfiguration();

        cfg.set("world", claim.getWorldName());
        cfg.set("center.x", claim.getCenterX());
        cfg.set("center.y", claim.getCenterY());
        cfg.set("center.z", claim.getCenterZ());
        cfg.set("size.x", claim.getSizeX());
        cfg.set("size.y", claim.getSizeY());
        cfg.set("size.z", claim.getSizeZ());
        cfg.set("sizeId", claim.getSizeId());
        cfg.set("createdByBlock", claim.isCreatedByBlock());
        cfg.set("name", claim.getName());
        cfg.set("welcomeMessage", claim.getWelcomeMessage());
        cfg.set("bankBalance", claim.getBankBalance());
        cfg.set("rentPricePerDay", claim.getRentPricePerDay());
        cfg.set("renterUuid", claim.getRenterUuid() != null ? claim.getRenterUuid().toString() : "");
        cfg.set("rentExpireTime", claim.getRentExpireTime());
        cfg.set("visitorLogs", claim.getVisitorLogs());
        cfg.set("incidentLogs", claim.getIncidentLogs());

        List<String> ownersL = new ArrayList<>();
        for (UUID u : claim.getOwners()) ownersL.add(u.toString());
        cfg.set("owners", ownersL);

        List<String> trustedL = new ArrayList<>();
        for (UUID u : claim.getTrusted()) trustedL.add(u.toString());
        cfg.set("trusted", trustedL);

        Map<String, Object> flagsMap = new HashMap<>(claim.getFlags());
        cfg.set("flags", flagsMap);

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save claim file " + file.getName() + ": " + e.getMessage());
        }
    }

    public void save() {
        for (Claim claim : claimsById.values()) {
            saveClaim(claim);
        }
    }

    // Player metadata storage methods
    public boolean isVisualizationEnabled(UUID playerId) {
        File file = new File(playersFolder, playerId.toString() + ".yml");
        if (!file.exists()) return false;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        return cfg.getBoolean("visualization_enabled", false);
    }

    public void setVisualizationEnabled(UUID playerId, boolean enabled) {
        File file = new File(playersFolder, playerId.toString() + ".yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("visualization_enabled", enabled);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player profile for " + playerId + ": " + e.getMessage());
        }
    }

    public Map<String, SizePreset> getSizePresets() {
        return Collections.unmodifiableMap(sizePresets);
    }

    public SizePreset getPreset(String id) {
        if (id == null) return null;
        return sizePresets.get(id.toUpperCase(Locale.ROOT));
    }

    public SizePreset getDefaultPreset() {
        String defId = plugin.getConfig().getString("default_size_id", "MEDIUM");
        return getPreset(defId);
    }

    public List<Claim> getClaimsOf(UUID owner) {
        return claimsByOwner.getOrDefault(owner, Collections.emptyList());
    }

    public Collection<Claim> getClaims() {
        return Collections.unmodifiableCollection(claimsById.values());
    }

    public Optional<Claim> getClaimAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return Optional.empty();
        String key = loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
        Set<Claim> set = claimsByChunk.get(key);
        if (set == null) return Optional.empty();
        for (Claim claim : set) {
            if (claim.isInside(loc)) return Optional.of(claim);
        }
        return Optional.empty();
    }

    public Optional<Claim> getOwnedClaimAt(UUID owner, Location loc) {
        for (Claim c : getClaimsOf(owner)) {
            if (c.isInside(loc)) return Optional.of(c);
        }
        return Optional.empty();
    }

    public Optional<Claim> getClaimByIndex(UUID owner, int index) {
        List<Claim> list = getClaimsOf(owner);
        if (index < 0 || index >= list.size()) return Optional.empty();
        return Optional.of(list.get(index));
    }

    public boolean areaOverlaps(Location center, SizePreset preset) {
        Claim candidate = new Claim(UUID.randomUUID(), UUID.randomUUID(), center,
                preset.x, preset.y, preset.z, preset.id, false);
        for (Claim existing : claimsById.values()) {
            if (candidate.overlaps(existing)) return true;
        }
        return false;
    }

    public Claim createClaim(Player owner, Location center, SizePreset preset, boolean createdByBlock) {
        UUID id = UUID.randomUUID();
        int height = (this.plugin.feature("vertical_claim", false)) ? center.getWorld().getMaxHeight() : preset.y;

        Claim claim = new Claim(id, owner.getUniqueId(), center,
                preset.x, height, preset.z, preset.id, createdByBlock);

        // seed flags from config defaults
        ConfigurationSection defFlags = plugin.getConfig().getConfigurationSection("defaults.flags");
        if (defFlags != null) {
            for (String key : defFlags.getKeys(false)) {
                claim.setFlag(key, defFlags.getBoolean(key, false));
            }
        }

        claimsById.put(id, claim);
        claimsByOwner.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(claim);
        addClaimToSpatialIndex(claim);
        saveClaim(claim);
        return claim;
    }

    public Claim createClaim(Player owner, Location center, SizePreset preset) {
        return createClaim(owner, center, preset, false);
    }

    public boolean deleteClaim(Claim claim) {
        if (claim == null) return false;
        claimsById.remove(claim.getId());
        for (UUID ownerId : claim.getOwners()) {
            List<Claim> list = claimsByOwner.get(ownerId);
            if (list != null) list.removeIf(c -> c.getId().equals(claim.getId()));
        }
        removeClaimFromSpatialIndex(claim);

        File file = new File(claimsFolder, claim.getId().toString() + ".yml");
        if (file.exists()) {
            file.delete();
        }
        return true;
    }

    public boolean addTrusted(Claim claim, OfflinePlayer target) {
        if (claim == null || target == null || target.getUniqueId() == null) return false;
        boolean added = claim.getTrusted().add(target.getUniqueId());
        if (added) saveClaim(claim);
        return added;
    }

    public boolean removeTrusted(Claim claim, OfflinePlayer target) {
        if (claim == null || target == null || target.getUniqueId() == null) return false;
        boolean removed = claim.getTrusted().remove(target.getUniqueId());
        if (removed) saveClaim(claim);
        return removed;
    }

    public boolean addOwner(Claim claim, OfflinePlayer target) {
        if (claim == null || target == null || target.getUniqueId() == null) return false;
        boolean added = claim.getOwners().add(target.getUniqueId());
        if (added) {
            claimsByOwner.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>()).add(claim);
            saveClaim(claim);
        }
        return added;
    }

    public boolean removeOwner(Claim claim, OfflinePlayer target) {
        if (claim == null || target == null || target.getUniqueId() == null) return false;
        boolean removed = claim.getOwners().remove(target.getUniqueId());
        if (removed) {
            List<Claim> list = claimsByOwner.get(target.getUniqueId());
            if (list != null) list.removeIf(c -> c.getId().equals(claim.getId()));
            saveClaim(claim);
        }
        return removed;
    }

    public boolean isClaimBlockAllowed(Player player, String sizeId) {
        if (player.hasPermission("fabulousclaims.admin")) return true;

        List<String> defaultAllowed = plugin.getConfig().getStringList("limits.default-allowed-blocks");
        if (defaultAllowed.isEmpty()) {
            defaultAllowed = Collections.singletonList("SMALL");
        }

        boolean allowed = false;
        for (String size : defaultAllowed) {
            if (size.equalsIgnoreCase(sizeId)) {
                allowed = true;
                break;
            }
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-permissions");
        if (section != null) {
            for (String node : section.getKeys(false)) {
                if (player.hasPermission(node)) {
                    List<String> list = section.getStringList(node + ".allowed-blocks");
                    for (String size : list) {
                        if (size.equalsIgnoreCase(sizeId)) {
                            allowed = true;
                            break;
                        }
                    }
                }
            }
        }
        return allowed;
    }

    public int getMaxClaimsFor(Player player) {
        if (player.hasPermission("fabulousclaims.admin")) return 9999;

        int max = plugin.getConfig().getInt("limits.default", 2);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-permissions");
        if (section != null) {
            for (String node : section.getKeys(false)) {
                if (player.hasPermission(node)) {
                    int val = section.getInt(node + ".max-claims", max);
                    if (val > max) max = val;
                }
            }
        }

        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("limits.groups");
        LuckPerms lp = getLuckPerms();
        String primaryGroup = null;
        if (lp != null) {
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) primaryGroup = user.getPrimaryGroup();
        }

        if (groups != null) {
            if (primaryGroup != null && groups.contains(primaryGroup)) {
                int val = groups.getInt(primaryGroup, max);
                if (val > max) max = val;
            }
            for (String key : groups.getKeys(false)) {
                if (player.hasPermission("group." + key)) {
                    int val = groups.getInt(key, max);
                    if (val > max) max = val;
                }
            }
        }
        return max;
    }

    public boolean canCreateClaim(Player player) {
        if (player.hasPermission("fabulousclaims.admin")) return true;
        int max = getMaxClaimsFor(player);
        int current = getClaimsOf(player.getUniqueId()).size();
        return current < max;
    }

    public boolean isEconomyEnabled() {
        return plugin.getConfig().getBoolean("features.economy", false);
    }

    public boolean canAfford(Player player, SizePreset preset) {
        if (!isEconomyEnabled()) return true;
        Economy eco = getEconomy();
        if (eco == null) return true;
        return eco.getBalance(player) >= preset.price;
    }

    public boolean charge(Player player, double amount) {
        if (!isEconomyEnabled()) return true;
        Economy eco = getEconomy();
        if (eco == null) return true;
        return eco.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (!isEconomyEnabled()) return true;
        Economy eco = getEconomy();
        if (eco == null) return true;
        return eco.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean upgradeEnabled() {
        return plugin.getConfig().getBoolean("features.upgrades", true);
    }

    public boolean upgradeClaim(Claim claim, SizePreset newPreset, Player actor) {
        if (claim == null || newPreset == null) return false;
        if (!upgradeEnabled()) return false;
        if (!isClaimBlockAllowed(actor, newPreset.id)) return false;
        Location center = new Location(Bukkit.getWorld(claim.getWorldName()), claim.getCenterX(), claim.getCenterY(), claim.getCenterZ());
        if (areaOverlaps(center, newPreset)) return false;
        if (!canAfford(actor, newPreset)) return false;
        if (!charge(actor, newPreset.price)) return false;

        removeClaimFromSpatialIndex(claim);
        claim.setSize(newPreset);
        addClaimToSpatialIndex(claim);
        saveClaim(claim);
        return true;
    }

    private String getGroup(Player player) {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            var user = api.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getPrimaryGroup();
            }
        }
        return "default";
    }

    public boolean canAddTrusted(Player player, Claim claim) {
        String group = getGroup(player);
        int limit = plugin.getConfig().getInt("trust-limits." + group, plugin.getConfig().getInt("trust-limits.default", -1));

        if (limit < 0) return true;
        return claim.getTrusted().size() < limit;
    }

    public int getTrustLimit(Player player) {
        String group = getGroup(player);
        return plugin.getConfig().getInt("trust-limits." + group,
                plugin.getConfig().getInt("trust-limits.default", -1));
    }

    private LuckPerms getLuckPerms() {
        try {
            var reg = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            return reg != null ? reg.getProvider() : null;
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    private Economy getEconomy() {
        try {
            var reg = Bukkit.getServicesManager().getRegistration(Economy.class);
            return reg != null ? reg.getProvider() : null;
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }
}
