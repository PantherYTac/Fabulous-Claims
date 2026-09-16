package me.PantherYTac;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class ClaimProtectionListener implements Listener {
    private final ClaimManager manager;
    private final ClaimBlockManager blockManager;

    public ClaimProtectionListener(ClaimManager manager, ClaimBlockManager blockManager) {
        this.manager = manager;
        this.blockManager = blockManager;
    }

    private boolean isAllowed(Player p, Claim claim) {
        if (p.hasPermission("fabulousclaims.admin")) return true;
        if (claim.isOwner(p.getUniqueId())) return true;
        if (claim.getTrusted().contains(p.getUniqueId())) return true;
        if (claim.isRented() && p.getUniqueId().equals(claim.getRenterUuid())) return true;
        return false;
    }

    private void logIncident(Claim claim, Player p, String action) {
        if (ClaimPlugin.getInstance().getConfig().getBoolean("features.analytics", true) && claim.getFlag("analytics")) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
            String timestamp = sdf.format(new java.util.Date());
            claim.addIncidentLog("§7[" + timestamp + "] §c" + p.getName() + " §7(" + action + ")");
        }
    }

    private boolean isFeatureActive(String path) {
        return ClaimPlugin.getInstance().getConfig().getBoolean("features." + path, true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        ItemStack item = (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND)
                ? p.getInventory().getItemInOffHand()
                : e.getItemInHand();

        String sizeId = blockManager.getSizeIdFromItem(item);

        if (sizeId != null) {
            // Placing a claim block
            ClaimManager.SizePreset preset = manager.getPreset(sizeId);
            if (preset == null) return;

            Location loc = e.getBlockPlaced().getLocation();

            // Check if claims are enabled in this world
            if (!ClaimPlugin.getInstance().isClaimsEnabled(loc.getWorld())) {
                ClaimPlugin.getInstance().sendPrefixed(p, "§cClaims are disabled in this world.");
                e.setCancelled(true);
                return;
            }

            if (!manager.isClaimBlockAllowed(p, sizeId)) {
                ClaimPlugin.getInstance().sendPrefixed(p, "§cYou are not allowed to use " + sizeId + " claim blocks.");
                e.setCancelled(true);
                return;
            }

            if (!manager.canCreateClaim(p)) {
                ClaimPlugin.getInstance().sendPrefixed(p, "§cYou have reached your claim limit (" + manager.getClaimsOf(p.getUniqueId()).size() + "/" + manager.getMaxClaimsFor(p) + ").");
                e.setCancelled(true);
                return;
            }

            if (manager.areaOverlaps(loc, preset)) {
                ClaimPlugin.getInstance().sendPrefixed(p, "§cThis area overlaps another claim.");
                e.setCancelled(true);
                return;
            }

            if (!manager.canAfford(p, preset)) {
                ClaimPlugin.getInstance().sendPrefixed(p, "§cYou cannot afford this claim.");
                e.setCancelled(true);
                return;
            }
            if (!manager.charge(p, preset.price)) {
                ClaimPlugin.getInstance().sendPrefixed(p, "§cPayment failed.");
                e.setCancelled(true);
                return;
            }

            manager.createClaim(p, loc, preset, true);

            // Check if claim block cleanup is enabled
            if (isFeatureActive("claim_block_cleanup")) {
                e.getBlockPlaced().setType(Material.AIR);
            }

            String displayName = (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                    ? item.getItemMeta().getDisplayName()
                    : preset.label + " Claim Block";
            ClaimPlugin.getInstance().sendPrefixed(p, "§aClaim created using " + displayName + "!");
        } else {
            // Placing a normal block inside a claim
            Location loc = e.getBlockPlaced().getLocation();
            Optional<Claim> claimOpt = manager.getClaimAt(loc);
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                if (!isAllowed(p, claim)) {
                    p.sendMessage("§cYou do not have permission to build in this claim.");
                    logIncident(claim, p, "Block Place");
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getBlock().getLocation();
        Optional<Claim> claimOpt = manager.getClaimAt(loc);
        if (claimOpt.isEmpty()) return;

        Claim claim = claimOpt.get();
        boolean isAdmin = p.hasPermission("fabulousclaims.admin");
        boolean isOwner = claim.isOwner(p.getUniqueId());
        boolean isTrusted = claim.getTrusted().contains(p.getUniqueId()) || isOwner;

        // If it's a claim-block created claim, check if they are breaking the actual anchor block
        if (claim.isCreatedByBlock()) {
            Location anchorLoc = new Location(
                    p.getWorld(),
                    claim.getCenterX(),
                    claim.getCenterY(),
                    claim.getCenterZ()
            );
            if (loc.getBlockX() == anchorLoc.getBlockX()
                    && loc.getBlockY() == anchorLoc.getBlockY()
                    && loc.getBlockZ() == anchorLoc.getBlockZ()) {
                // Breaking the anchor block
                if (isOwner || isAdmin) {
                    manager.deleteClaim(claim);
                    p.sendMessage("§cClaim deleted.");
                    e.setDropItems(false);
                    ItemStack blockItem = blockManager.getClaimBlockItem(claim.getSizeId());
                    if (blockItem != null) {
                        e.getBlock().setType(Material.AIR);
                        p.getWorld().dropItemNaturally(loc, blockItem);
                    }
                } else {
                    p.sendMessage("§cOnly the owner or an admin can break the claim block.");
                    e.setCancelled(true);
                }
                return;
            }
        }

        // Breaking a regular block inside the claim
        if (!isAllowed(p, claim)) {
            p.sendMessage("§cYou do not have permission to build in this claim.");
            logIncident(claim, p, "Block Break");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Location loc = e.getClickedBlock().getLocation();
        Optional<Claim> claimOpt = manager.getClaimAt(loc);
        if (claimOpt.isEmpty()) return;

        Claim claim = claimOpt.get();
        Player p = e.getPlayer();

        if (isAllowed(p, claim)) return;

        org.bukkit.block.Block block = e.getClickedBlock();
        boolean isProtected = false;

        if (block.getState() instanceof org.bukkit.block.Container) {
            isProtected = true;
        } else {
            Material type = block.getType();
            String name = type.name();
            if (name.contains("DOOR") || name.contains("GATE") || name.contains("TRAPDOOR")
                    || name.contains("BUTTON") || name.contains("LEVER") || name.contains("PLATE")) {
                isProtected = true;
            } else if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL && name.contains("FARMLAND")) {
                isProtected = true;
            }
        }

        if (isProtected) {
            p.sendMessage("§cThis container or interaction is locked by the claim.");
            logIncident(claim, p, "Interact (" + block.getType().name() + ")");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (!ClaimPlugin.getInstance().getConfig().getBoolean("features.flags", true)) return;
        Optional<Claim> claim = manager.getClaimAt(e.getBlock().getLocation());
        if (claim.isPresent() && !claim.get().getFlag("firespread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent e) {
        if (!ClaimPlugin.getInstance().getConfig().getBoolean("features.flags", true)) return;
        Optional<Claim> claim = manager.getClaimAt(e.getBlock().getLocation());
        if (claim.isPresent() && !claim.get().getFlag("firespread")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent e) {
        if (!ClaimPlugin.getInstance().getConfig().getBoolean("features.flags", true)) return;
        e.blockList().removeIf(block -> {
            Optional<Claim> claimOpt = manager.getClaimAt(block.getLocation());
            if (claimOpt.isPresent()) {
                return !claimOpt.get().getFlag("tnt");
            }
            return false;
        });
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent e) {
        if (!ClaimPlugin.getInstance().getConfig().getBoolean("features.flags", true)) return;
        e.blockList().removeIf(block -> {
            Optional<Claim> claimOpt = manager.getClaimAt(block.getLocation());
            if (claimOpt.isPresent()) {
                return !claimOpt.get().getFlag("tnt");
            }
            return false;
        });
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getBlock().getLocation();
        Optional<Claim> claimOpt = manager.getClaimAt(loc);
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();
            boolean isAdmin = p.hasPermission("fabulousclaims.admin");
            boolean isOwner = claim.isOwner(p.getUniqueId());
            boolean isTrusted = claim.getTrusted().contains(p.getUniqueId()) || isOwner;

            if (!isTrusted && !isAdmin) {
                p.sendMessage("§cYou do not have permission to place liquids here.");
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getBlock().getLocation();
        Optional<Claim> claimOpt = manager.getClaimAt(loc);
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();
            boolean isAdmin = p.hasPermission("fabulousclaims.admin");
            boolean isOwner = claim.isOwner(p.getUniqueId());
            boolean isTrusted = claim.getTrusted().contains(p.getUniqueId()) || isOwner;

            if (!isTrusted && !isAdmin) {
                p.sendMessage("§cYou do not have permission to collect liquids here.");
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getRightClicked().getLocation();
        Optional<Claim> claimOpt = manager.getClaimAt(loc);
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();
            boolean isAdmin = p.hasPermission("fabulousclaims.admin");
            boolean isOwner = claim.isOwner(p.getUniqueId());
            boolean isTrusted = claim.getTrusted().contains(p.getUniqueId()) || isOwner;

            if (!isTrusted && !isAdmin) {
                p.sendMessage("§cYou cannot modify armor stands here.");
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player) {
            Player p = (Player) e.getRemover();
            Location loc = e.getEntity().getLocation();
            Optional<Claim> claimOpt = manager.getClaimAt(loc);
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                boolean isAdmin = p.hasPermission("fabulousclaims.admin");
                boolean isOwner = claim.isOwner(p.getUniqueId());
                boolean isTrusted = claim.getTrusted().contains(p.getUniqueId()) || isOwner;

                if (!isTrusted && !isAdmin) {
                    p.sendMessage("§cYou cannot break items here.");
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        org.bukkit.entity.Entity entity = e.getRightClicked();
        Location loc = entity.getLocation();
        Optional<Claim> claimOpt = manager.getClaimAt(loc);
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();
            boolean isAdmin = p.hasPermission("fabulousclaims.admin");
            boolean isOwner = claim.isOwner(p.getUniqueId());
            boolean isTrusted = claim.getTrusted().contains(p.getUniqueId()) || isOwner;

            if (!isTrusted && !isAdmin) {
                boolean protect = entity instanceof org.bukkit.entity.ItemFrame
                        || entity instanceof org.bukkit.entity.ArmorStand
                        || entity instanceof org.bukkit.entity.Animals
                        || entity instanceof org.bukkit.entity.Villager;
                if (protect) {
                    p.sendMessage("§cYou cannot interact with entities here.");
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        Player attacker = null;
        if (e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) e.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker != null) {
            Location loc = e.getEntity().getLocation();
            Optional<Claim> claimOpt = manager.getClaimAt(loc);
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                boolean isAdmin = attacker.hasPermission("fabulousclaims.admin");
                boolean isOwner = claim.isOwner(attacker.getUniqueId());
                boolean isTrusted = claim.getTrusted().contains(attacker.getUniqueId()) || isOwner;

                if (!isTrusted && !isAdmin) {
                    boolean isMonster = e.getEntity() instanceof org.bukkit.entity.Monster
                            || e.getEntity() instanceof org.bukkit.entity.Slime
                            || e.getEntity() instanceof org.bukkit.entity.Ghast
                            || e.getEntity() instanceof org.bukkit.entity.Phantom;
                    if (!isMonster) {
                        attacker.sendMessage("§cYou cannot harm entities inside this claim.");
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Location pistonLoc = e.getBlock().getLocation();
        Optional<Claim> pistonClaim = manager.getClaimAt(pistonLoc);
        UUID pistonClaimId = pistonClaim.map(Claim::getId).orElse(null);

        org.bukkit.block.BlockFace dir = e.getDirection();

        for (org.bukkit.block.Block block : e.getBlocks()) {
            Location blockLoc = block.getLocation();
            Optional<Claim> blockClaim = manager.getClaimAt(blockLoc);
            UUID blockClaimId = blockClaim.map(Claim::getId).orElse(null);

            if (blockClaimId != null && !blockClaimId.equals(pistonClaimId)) {
                e.setCancelled(true);
                return;
            }

            Location destLoc = blockLoc.clone().add(dir.getModX(), dir.getModY(), dir.getModZ());
            Optional<Claim> destClaim = manager.getClaimAt(destLoc);
            UUID destClaimId = destClaim.map(Claim::getId).orElse(null);

            if (destClaimId != null && !destClaimId.equals(pistonClaimId)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Location pistonLoc = e.getBlock().getLocation();
        Optional<Claim> pistonClaim = manager.getClaimAt(pistonLoc);
        UUID pistonClaimId = pistonClaim.map(Claim::getId).orElse(null);

        for (org.bukkit.block.Block block : e.getBlocks()) {
            Location blockLoc = block.getLocation();
            Optional<Claim> blockClaim = manager.getClaimAt(blockLoc);
            UUID blockClaimId = blockClaim.map(Claim::getId).orElse(null);

            if (blockClaimId != null && !blockClaimId.equals(pistonClaimId)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
