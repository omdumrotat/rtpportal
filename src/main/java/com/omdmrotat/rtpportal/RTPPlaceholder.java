package com.omdmrotat.rtpportal;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class RTPPlaceholder extends PlaceholderExpansion {

    private final RTPPortal plugin;

    public RTPPlaceholder(RTPPortal plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rtpportal";
    }

    @Override
    public @NotNull String getAuthor() {
        return "omdmrotat";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("timer")) {
            return String.valueOf(plugin.getTeleportTimer().get());
        }
        return null;
    }
}