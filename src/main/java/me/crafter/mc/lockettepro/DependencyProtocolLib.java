package me.crafter.mc.lockettepro;

import de.blablubbabc.insigns.ProtocolUtils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class DependencyProtocolLib {

    public static void setUpProtocolLib(Plugin plugin) {
        if (Config.protocollib) {
            addTileEntityDataListener(plugin);
            addMapChunkListener(plugin);
        }
    }

    public static void cleanUpProtocolLib(Plugin plugin) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                ProtocolLibrary.getProtocolManager().removePacketListeners(plugin);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addTileEntityDataListener(Plugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW, PacketType.Play.Server.TILE_ENTITY_DATA) {
            //PacketPlayOutTileEntityData -> ClientboundBlockEntityDataPacket
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                NbtCompound signData = ProtocolUtils.Packet.TileEntityData.getTileEntityData(packet);

                if (signData == null || !ProtocolUtils.TileEntity.Sign.isTileEntitySignData(signData)) {
                    return;
                }

                String[] rawLines = ProtocolUtils.TileEntity.Sign.getText(signData);

                if (onSignSend(rawLines)) {
                    PacketContainer outgoingPacket = event.getPacket().shallowClone();

                    NbtCompound outgoingSignData = replaceSignData(signData, rawLines);

                    ProtocolUtils.Packet.TileEntityData.setTileEntityData(outgoingPacket, outgoingSignData);

                    event.setPacket(outgoingPacket);
                }
            }
        });
    }

    public static void addMapChunkListener(Plugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.LOW, PacketType.Play.Server.MAP_CHUNK) {
            //PacketPlayOutMapChunk - > ClientboundLevelChunkPacket
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                // Only replace the outgoing packet if needed:
                PacketContainer outgoingPacket = null;
                List<InternalStructure> outgoingTileEntitiesInfo = null;

                List<InternalStructure> tileEntitiesInfo = ProtocolUtils.Packet.MapChunk.getTileEntitiesInfo(packet);
                for (int index = 0, size = tileEntitiesInfo.size(); index < size; index++) {
                    InternalStructure tileEntityInfo = tileEntitiesInfo.get(index);
                    NbtCompound tileEntityData = ProtocolUtils.Packet.MapChunk.TileEntityInfo.getNbt(tileEntityInfo);
                    if (tileEntityData == null) {
                        continue;
                    }

                    // Check if the tile entity is a sign:
                    if (!ProtocolUtils.TileEntity.Sign.isTileEntitySignData(tileEntityData)) {
                        continue;
                    }

                    String[] rawLines = ProtocolUtils.TileEntity.Sign.getText(tileEntityData);

                    if (onSignSend(rawLines)) {
                        if (outgoingPacket == null) {
                            outgoingPacket = packet.shallowClone();
                            outgoingTileEntitiesInfo = new ArrayList<>(tileEntitiesInfo);
                        }

                        NbtCompound outgoingSignData = replaceSignData(tileEntityData, rawLines);

                        InternalStructure newTileEntityInfo = ProtocolUtils.Packet.MapChunk.TileEntityInfo.cloneWithNewNbt(tileEntityInfo, outgoingSignData);
                        outgoingTileEntitiesInfo.set(index, newTileEntityInfo);
                    }
                }

                if (outgoingPacket != null) {
                    ProtocolUtils.Packet.MapChunk.setTileEntitiesInfo(outgoingPacket, outgoingTileEntitiesInfo);

                    event.setPacket(outgoingPacket);
                }
            }
        });
    }

    private static NbtCompound replaceSignData(NbtCompound previousSignData, String[] newSignText) {
        NbtCompound newSignData = NbtFactory.ofCompound(previousSignData.getName());

        // Copy the previous tile entity data (shallow copy):
        for (String key : previousSignData.getKeys()) {
            newSignData.put(key, previousSignData.getValue(key));
        }

        // Replace the sign text:
        ProtocolUtils.TileEntity.Sign.setText(newSignData, newSignText);

        return newSignData;
    }

    public static boolean onSignSend(String[] rawLines) {
        if (LocketteProAPI.isLockStringOrAdditionalString(Utils.getSignLineFromUnknown(rawLines[0]))) {
            // Private line
            String line1 = Utils.getSignLineFromUnknown(rawLines[0]);
            if (LocketteProAPI.isLineExpired(line1)) {
                rawLines[0] = "{\"text\":\"" + Config.getLockExpireString() + "\"}";
            } else {
                rawLines[0] = "{\"text\":\"" + Utils.StripSharpSign(line1) + "\"}";
            }
            // Other line
            for (int i = 1; i < 4; i++) {
                String line = Utils.getSignLineFromUnknown(rawLines[i]);
                if (Utils.isUsernameUuidLine(line)) {
                    rawLines[i] = "{\"text\":\"" + Utils.getUsernameFromLine(line) + "\"}";
                }
            }
            return true;
        }
        return false;
    }
}
