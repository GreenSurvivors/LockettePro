package de.greensurvivors.greenlocker.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.greensurvivors.greenlocker.GreenLocker;
import de.greensurvivors.greenlocker.config.MessageManager;
import de.greensurvivors.greenlocker.impl.signdata.SignLock;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class MiscUtils {
    private static final Pattern usernamePattern = Pattern.compile("^.?[a-zA-Z0-9_]{3,16}$");
    private static final Set<UUID> notified = new HashSet<>();

    // Helper functions
    public static Block putPrivateSignOn(Block newsign, BlockFace blockface, Material material, Player player) {
        Material blockType = Material.getMaterial(material.name().replace("_SIGN", "_WALL_SIGN"));
        if (blockType != null && Tag.WALL_SIGNS.isTagged(blockType)) {
            newsign.setType(blockType);
        } else {
            newsign.setType(Material.OAK_WALL_SIGN);
        }
        BlockData data = newsign.getBlockData();
        if (data instanceof Directional) {
            ((Directional) data).setFacing(blockface);
            newsign.setBlockData(data, true);
        }
        Sign sign = (Sign) newsign.getState();
        sign.update();
        if (newsign.getType() == Material.DARK_OAK_WALL_SIGN) {
            sign.getSide(Side.FRONT).setColor(DyeColor.WHITE);
        }
        sign.getSide(Side.FRONT).line(0, GreenLocker.getPlugin().getMessageManager().getLang(MessageManager.LangPath.PRIVATE_SIGN));
        sign.getSide(Side.FRONT).line(1, Component.text(player.getName()));
        sign.setWaxed(true);
        sign.update();

        SignLock.addPlayer(sign, true, player);
        return newsign;
    }

    public static void removeASign(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.getInventory().getItemInMainHand().getAmount() == 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
        }
    }

    public static boolean shouldNotify(Player player) {
        if (notified.contains(player.getUniqueId())) {
            return false;
        } else {
            notified.add(player.getUniqueId());
            return true;
        }
    }

    public static boolean getAccess(Block block) { // Requires hasValidCache()
        List<MetadataValue> metadatas = block.getMetadata("locked");
        return metadatas.get(0).asBoolean();
    }

    public static boolean isUserName(String text) {
        return usernamePattern.matcher(text).matches();
    }

    // Warning: don't use this in a sync way
    public static String getUuidByUsernameFromMojang(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            final String responsestring = getJsonFromURL(url);
            JsonObject json = JsonParser.parseString(responsestring).getAsJsonObject();
            String rawuuid = json.get("id").getAsString();
            return rawuuid.substring(0, 8) + "-" + rawuuid.substring(8, 12) + "-" + rawuuid.substring(12, 16) + "-" + rawuuid.substring(16, 20) + "-" + rawuuid.substring(20);
        } catch (Exception ignored) {
        }
        return null;
    }

    // Warning: don't use this in a sync way
    public static String getUsernameByUUIDFromMojang(UUID uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString());
            final String responsestring = getJsonFromURL(url);
            JsonObject json = JsonParser.parseString(responsestring).getAsJsonObject();
            return json.get("name").getAsString();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static @NotNull String getJsonFromURL(@NotNull URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        return response.toString();
    }

    public static @NotNull List<String> getNamesFromUUIDStrSet(final @NotNull Set<String> stringUUIDs) {
        List<String> players = new ArrayList<>(stringUUIDs.size());

        for (String uuidStr : stringUUIDs) {
            try {
                players.add(Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).getName());
            } catch (IllegalArgumentException e) {
                GreenLocker.getPlugin().getLogger().log(Level.WARNING, "couldn't get UUID from String \"" + uuidStr + "\"", e);
            }
        }

        return players;
    }
}
