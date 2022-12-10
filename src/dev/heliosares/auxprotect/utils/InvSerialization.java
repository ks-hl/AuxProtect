package dev.heliosares.auxprotect.utils;

import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class InvSerialization {

    @Deprecated
    public static final String ITEM_SEPARATOR = ",ITEM,";

    public static byte[] toByteArray(ItemStack... array) throws IOException {
        if (array == null || array.length == 0) {
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(byteArrayOutputStream)) {

            stream.writeInt(array.length);

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

    public static Inventory toInventory(byte[] bytes, InventoryHolder holder, String title)
            throws ClassNotFoundException, IOException {
        ItemStack[] contents = toItemStackArray(bytes);
        if (contents == null) {
            return null;
        }
        int size = (int) Math.ceil(contents.length / 9.0) * 9;
        Inventory inventory = Bukkit.getServer().createInventory(holder, size, title);
        inventory.setContents(contents);
        return inventory;
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
            }
            if (size < 1) {
                AuxProtectSpigot.getInstance().warning("Empty BLOB");
                return new ItemStack[0];
            }
            ItemStack[] arrayOfItemStack = new ItemStack[size];

            for (int i = 0; i < arrayOfItemStack.length; i++) {
                arrayOfItemStack[i] = (ItemStack) stream.readObject();
            }

            return arrayOfItemStack;
        }
    }

    public static ItemStack toItemStack(byte[] bytes) throws ClassNotFoundException, IOException {
        if (bytes == null) {
            return null;
        }
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(byteArrayInputStream)) {
            return (ItemStack) stream.readObject();
        }
    }

    public static boolean isCustom(ItemStack i) {
        if (i.getType() == Material.FILLED_MAP || i.getType() == Material.WRITTEN_BOOK
                || i.getType() == Material.WRITABLE_BOOK) {
            return true;
        }
        if (i.hasItemMeta()) {
            return true;
        }
        return i.getEnchantments().size() > 0;
    }

    public static byte[] playerToByteArray(Player player) throws IOException {
        if (player == null) {
            return null;
        }
        return playerToByteArray(new PlayerInventoryRecord(player.getInventory().getStorageContents(),
                player.getInventory().getArmorContents(), player.getInventory().getExtraContents(),
                player.getEnderChest().getContents(), Experience.getTotalExp(player)));
    }

    public static byte[] playerToByteArray(PlayerInventoryRecord record) throws IOException {
        if (record == null) {
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(byteArrayOutputStream)) {

            stream.writeInt(record.storage().length);
            for (ItemStack item : record.storage()) {
                stream.writeObject(item);
            }

            stream.writeInt(record.armor().length);
            for (ItemStack item : record.armor()) {
                stream.writeObject(item);
            }

            stream.writeInt(record.extra().length);
            for (ItemStack item : record.extra()) {
                stream.writeObject(item);
            }

            stream.writeInt(record.ender().length);
            for (ItemStack item : record.ender()) {
                stream.writeObject(item);
            }

            stream.writeInt(record.exp());
            stream.flush(); // This little boi took me at least 2 hours to figure out...

            return byteArrayOutputStream.toByteArray();
        }
    }

    public static void debug(byte[] bytes) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        System.out.println("Debug Byte[] Dump:");
        try (BukkitObjectInputStream stream = new BukkitObjectInputStream(byteArrayInputStream)) {
            boolean keep = true;
            while (keep) {
                keep = false;
                try {
                    System.out.println(stream.readInt());
                    keep = true;
                } catch (Exception ignored) {
                }
                try {
                    Object o = stream.readObject();
                    String out = "null";
                    if (o != null) {
                        out = o.toString();
                        if (out.length() > 50) {
                            out = out.substring(0, 50);
                        }
                    }
                    System.out.println(out);
                    keep = true;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        System.out.println("EOF");
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

    @Deprecated
    public static PlayerInventoryRecord toPlayer(String base64) throws IOException, ClassNotFoundException {
        String[] parts = base64.split(",");
        ItemStack[][] contents = new ItemStack[4][];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = InvSerialization.toItemStackArray(parts[i]);
        }

        return new PlayerInventoryRecord(contents[0], contents[1], contents[2], contents[3],
                Integer.parseInt(parts[4]));
    }

    public static String toBase64(byte[] bytes) {
        return Base64Coder.encodeLines(bytes);
    }

    @Deprecated
    public static Inventory toInventory(String base64, InventoryHolder holder, String title)
            throws IOException, ClassNotFoundException {
        return toInventory(Base64Coder.decodeLines(base64), holder, title);
    }

    @Deprecated
    public static ItemStack[] toItemStackArray(String base64) throws IOException, ClassNotFoundException {
        return toItemStackArray(Base64Coder.decodeLines(base64));
    }

    public record PlayerInventoryRecord(ItemStack[] storage, ItemStack[] armor, ItemStack[] extra,
                                        ItemStack[] ender, int exp) {
    }
}
