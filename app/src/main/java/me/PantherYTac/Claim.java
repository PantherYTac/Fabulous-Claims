package me.PantherYTac;

import org.bukkit.Location;

import java.util.*;

public class Claim {
    private final UUID id;
    private final Set<UUID> owners = new HashSet<>();
    private final String worldName;
    private final int centerX, centerY, centerZ;

    private int sizeX, sizeY, sizeZ;
    private String sizeId;

    private final boolean createdByBlock;
    private final Set<UUID> trusted = new HashSet<>();

    private String name = "";
    private String welcomeMessage = "";
    private final Map<String, Boolean> flags = new HashMap<>();

    // Unique feature fields
    private double bankBalance = 0.0;
    private double rentPricePerDay = 0.0;
    private UUID renterUuid = null;
    private long rentExpireTime = 0L;
    private final List<String> visitorLogs = new ArrayList<>();
    private final List<String> incidentLogs = new ArrayList<>();

    public Claim(UUID id, UUID owner, Location center,
                 int sizeX, int sizeY, int sizeZ,
                 String sizeId, boolean createdByBlock) {
        this.id = id;
        this.owners.add(owner);
        this.worldName = Objects.requireNonNull(center.getWorld()).getName();
        this.centerX = center.getBlockX();
        this.centerY = center.getBlockY();
        this.centerZ = center.getBlockZ();
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.sizeId = sizeId;
        this.createdByBlock = createdByBlock;
    }

    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int halfX = sizeX / 2, halfY = sizeY / 2, halfZ = sizeZ / 2;
        return Math.abs(loc.getBlockX() - centerX) <= halfX
                && Math.abs(loc.getBlockY() - centerY) <= halfY
                && Math.abs(loc.getBlockZ() - centerZ) <= halfZ;
    }

    public boolean overlaps(Claim other) {
        if (other == null) return false;
        if (!this.worldName.equals(other.worldName)) return false;

        int thisMinX = centerX - sizeX / 2, thisMaxX = centerX + sizeX / 2;
        int thisMinY = centerY - sizeY / 2, thisMaxY = centerY + sizeY / 2;
        int thisMinZ = centerZ - sizeZ / 2, thisMaxZ = centerZ + sizeZ / 2;

        int otherMinX = other.centerX - other.sizeX / 2, otherMaxX = other.centerX + other.sizeX / 2;
        int otherMinY = other.centerY - other.sizeY / 2, otherMaxY = other.centerY + other.sizeY / 2;
        int otherMinZ = other.centerZ - other.sizeZ / 2, otherMaxZ = other.centerZ + other.sizeZ / 2;

        boolean xOverlap = thisMinX <= otherMaxX && thisMaxX >= otherMinX;
        boolean yOverlap = thisMinY <= otherMaxY && thisMaxY >= otherMinY;
        boolean zOverlap = thisMinZ <= otherMaxZ && thisMaxZ >= otherMinZ;

        return xOverlap && yOverlap && zOverlap;
    }

    // Getters
    public UUID getId() { return id; }
    public Set<UUID> getOwners() { return owners; }
    public String getWorldName() { return worldName; }
    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }
    public int getCenterZ() { return centerZ; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public String getSizeId() { return sizeId; }
    public Set<UUID> getTrusted() { return trusted; }
    public boolean isCreatedByBlock() { return createdByBlock; }

    // Ownership & Renting helpers
    public boolean isOwner(UUID uuid) { return owners.contains(uuid); }

    public double getBankBalance() { return bankBalance; }
    public void setBankBalance(double balance) { this.bankBalance = Math.max(0.0, balance); }

    public double getRentPricePerDay() { return rentPricePerDay; }
    public void setRentPricePerDay(double price) { this.rentPricePerDay = Math.max(0.0, price); }

    public UUID getRenterUuid() { return renterUuid; }
    public void setRenterUuid(UUID renterUuid) { this.renterUuid = renterUuid; }

    public long getRentExpireTime() { return rentExpireTime; }
    public void setRentExpireTime(long rentExpireTime) { this.rentExpireTime = rentExpireTime; }

    public boolean isRented() {
        return renterUuid != null && System.currentTimeMillis() < rentExpireTime;
    }

    public List<String> getVisitorLogs() { return visitorLogs; }
    public void addVisitorLog(String log) {
        if (visitorLogs.size() >= 10) visitorLogs.remove(0);
        visitorLogs.add(log);
    }

    public List<String> getIncidentLogs() { return incidentLogs; }
    public void addIncidentLog(String log) {
        if (incidentLogs.size() >= 10) incidentLogs.remove(0);
        incidentLogs.add(log);
    }

    // Naming + welcome
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name; }

    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String msg) { this.welcomeMessage = msg == null ? "" : msg; }

    // Flags
    public Map<String, Boolean> getFlags() { return flags; }
    public boolean getFlag(String key) { return flags.getOrDefault(key.toLowerCase(Locale.ROOT), false); }
    public void setFlag(String key, boolean value) { flags.put(key.toLowerCase(Locale.ROOT), value); }

    // Size update (upgrade)
    public void setSize(ClaimManager.SizePreset preset) {
        this.sizeX = preset.x;
        this.sizeY = preset.y;
        this.sizeZ = preset.z;
        this.sizeId = preset.id;
    }
}
