package me.PantherYTac;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ClaimBlockManager {
    private final ClaimPlugin plugin;
    private final Map<String, ItemStack> blockItems = new HashMap<>();
    private final NamespacedKey key;

    public ClaimBlockManager(ClaimPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "claim_size");
        loadBlocks();
    }

    public void reload() {
        loadBlocks();
    }

    private void loadBlocks() {
        blockItems.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("claim-types");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String matName = section.getString(id + ".material");
            if (matName == null) continue; // No block item defined for this type

            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("Invalid material for claim block " + id + ": " + matName);
                continue;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    section.getString(id + ".block-name", id + " Claim Block")));
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList(id + ".block-lore")) {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }

            // Add economy price if available
            ClaimManager.SizePreset preset = plugin.getManager().getPreset(id);
            if (preset != null && preset.price > 0 && plugin.getConfig().getBoolean("features.economy", true)) {
                lore.add(org.bukkit.ChatColor.GOLD + "Price: " + preset.price);
            }

            meta.setLore(lore);

            // Tag special item with size ID
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id.toUpperCase(Locale.ROOT));
            item.setItemMeta(meta);

            blockItems.put(id.toUpperCase(Locale.ROOT), item);
        }
    }

    public ItemStack getClaimBlockItem(String sizeId) {
        ItemStack base = blockItems.get(sizeId.toUpperCase(Locale.ROOT));
        if (base == null) return null;
        return base.clone();
    }

    public String getSizeIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String pdcSize = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (pdcSize != null && !pdcSize.isEmpty()) {
            return pdcSize.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    public boolean isClaimBlock(ItemStack item) {
        return getSizeIdFromItem(item) != null;
    }
}
