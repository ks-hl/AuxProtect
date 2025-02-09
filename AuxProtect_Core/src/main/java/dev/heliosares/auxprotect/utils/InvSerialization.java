package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class InvSerialization {

    public static byte[] toByteArray(ItemStack... array) throws IOException {
        if (array == null || array.length == 0) {
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(byteArrayOutputStream)) {

            if (array.length > 1) stream.writeInt(array.length);

            for (ItemStack itemStack : array) {
                stream.writeObject(itemStack);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static byte[] toByteArraySingle(ItemStack item) throws IOException {
        if (item == null) {
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(byteArrayOutputStream)) {
            stream.writeObject(item);
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static ItemStack[] toItemStackArray(byte[] bytes) throws ClassNotFoundException, IOException {
        if (bytes == null) {
            return null;
        }
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(byteArrayInputStream)) {
            int size = 1;
            try {
                size = stream.readInt();
            } catch (Exception ignored) {
                //Allows reading a single item as an array
            }
            if (size < 1) return new ItemStack[0];
            ItemStack[] arrayOfItemStack = new ItemStack[size];

            for (int i = 0; i < arrayOfItemStack.length; i++) {
                arrayOfItemStack[i] = (ItemStack) stream.readObject();
            }

            return arrayOfItemStack;
        }
    }

    /**
     * Equivalent to {@link InvSerialization#toItemStackArray(byte[])}[0]
     *
     * @param bytes blob
     * @return The itemstack or the first if it's an array
     * @throws ClassNotFoundException Not an itemstack
     * @throws IOException            Malformed blob
     */
    public static ItemStack toItemStack(byte[] bytes) throws ClassNotFoundException, IOException {
        return toItemStackArray(bytes)[0];
    }

    public static boolean isCustom(ItemStack i) {
        if (i.getType() == Material.FILLED_MAP || i.getType() == Material.WRITTEN_BOOK
                || i.getType() == Material.WRITABLE_BOOK) {
            return true;
        }
        if (i.hasItemMeta()) return true;
        return !i.getEnchantments().isEmpty();
    }


    public static Inventory toInventory(byte[] bytes, InventoryHolder holder, String title)
            throws ClassNotFoundException, IOException {
        ItemStack[] contents = toItemStackArray(bytes);
        return toInventory(holder, title, contents);
    }

    public static Inventory toInventory(InventoryHolder holder, String title, ItemStack... contents) {
        if (contents == null) {
            return null;
        }
        int size = (int) Math.ceil(contents.length / 9.0) * 9;
        if (size < 9) size = 9;
        Inventory inventory = Bukkit.getServer().createInventory(holder, size, title);
        inventory.setContents(contents);
        return inventory;
    }

    public static byte[] playerToByteArray(Player player) throws IOException {
        if (player == null) {
            return null;
        }
        return playerToByteArray(new PlayerInventoryRecord(player.getInventory().getStorageContents(),
                player.getInventory().getArmorContents(), player.getInventory().getExtraContents(),
                player.getEnderChest().getContents(), Experience.getTotalExp(player)), player.getName());
    }

    public static byte[] playerToByteArray(PlayerInventoryRecord record, String playerName) throws IOException {
        if (record == null) {
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(byteArrayOutputStream)) {

            stream.writeInt(record.storage().length);
            for (int i = 0; i < record.storage().length; i++) {
                ItemStack item = record.storage()[i];
                write(stream, item, playerName, "main inventory", i);
            }

            stream.writeInt(record.armor().length);
            for (int i = 0; i < record.armor().length; i++) {
                ItemStack item = record.armor()[i];
                write(stream, item, playerName, "armor", i);
            }

            stream.writeInt(record.extra().length);
            for (int i = 0; i < record.extra().length; i++) {
                ItemStack item = record.extra()[i];
                write(stream, item, playerName, "offhand/extra", i);
            }

            stream.writeInt(record.ender().length);
            for (int i = 0; i < record.ender().length; i++) {
                ItemStack item = record.ender()[i];
                write(stream, item, playerName, "ender chest", i);
            }

            stream.writeInt(record.exp());
            stream.flush(); // This little boi took me at least 2 hours to figure out...

            return byteArrayOutputStream.toByteArray();
        }
    }

    private static final Map<String, Long> spamMap = new HashMap<>();

    private static void write(BukkitObjectOutputStream stream, ItemStack item, String playerName, String inventoryName, int index) throws IOException {
        try {
            stream.writeObject(item);
        } catch (Throwable e) {
            String msg = "Failed to serialize item in " + inventoryName + ", slot " + index + " of " + playerName + ". This is not an issue with AuxProtect, it is Bukkit failing to serialize the item correctly. The item will be logged as an empty slot. (" + e.getClass().getName() + ": " + e.getMessage() + ")";
            spamMap.values().removeIf(l -> System.currentTimeMillis() - l > 60000L);
            if (!spamMap.containsKey(msg)) {
                spamMap.put(msg, System.currentTimeMillis());
                AuxProtectAPI.warning(msg);
            }
            stream.writeObject(null);
        }
    }

    public static PlayerInventoryRecord toPlayerInventory(byte[] bytes) throws IOException, ClassNotFoundException {
        if (bytes == null) return null;
        ItemStack[][] contents = new ItemStack[4][];
        int exp;

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(byteArrayInputStream)) {
            for (int i = 0; i < contents.length; i++) {
                int size = stream.readInt();
                contents[i] = new ItemStack[size];
                for (int i1 = 0; i1 < size; i1++) {
                    contents[i][i1] = (ItemStack) stream.readObject();
                }
            }
            exp = stream.readInt();
        }
        return new PlayerInventoryRecord(contents[0], contents[1], contents[2], contents[3], exp);
    }

    public record PlayerInventoryRecord(ItemStack[] storage, ItemStack[] armor, ItemStack[] extra,
                                        ItemStack[] ender, int exp) {
    }
}
