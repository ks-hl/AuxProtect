package dev.heliosares.auxprotect.database;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.InvSerialization.PlayerInventoryRecord;

public class InvDiffManager {
	private final SQLManager sql;
	private final IAuxProtect plugin;
	private long blobid;

	public InvDiffManager(SQLManager sql, IAuxProtect plugin) {
		this.sql = sql;
		this.plugin = plugin;
	}

	public void init(Connection connection) throws SQLException {
		try (PreparedStatement stmt = connection
				.prepareStatement("SELECT MAX(blobid) FROM " + Table.AUXPROTECT_INVDIFFBLOB)) {
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					blobid = rs.getLong(1);
				}
			}
		}
	}

	private static class BlobCache {
		long lastused;
		final long blobid;
		final byte[] ablob;
		final int hash;

		BlobCache(long blobid, byte[] ablob) {
			this.blobid = blobid;
			this.ablob = ablob;
			this.lastused = System.currentTimeMillis();
			hash = Arrays.hashCode(ablob);
		}
	}

	private final List<BlobCache> cache = new ArrayList<>();
	private long lastpurged = System.currentTimeMillis();

	public void logInvDiff(UUID uuid, int slot, int qty, ItemStack item) throws SQLException {
		byte[] blob = null;
		final long time = System.currentTimeMillis();
		Integer damage = null;
		if (qty != 0 && item != null) {
			if (item.getItemMeta() != null && item.getItemMeta()instanceof Damageable meta) {
				damage = meta.getDamage();
				meta.setDamage(0);
				item.setItemMeta(meta);
			}
			try {
				blob = InvSerialization.toByteArraySingle(item);
			} catch (IOException e) {
				plugin.print(e);
				return;
			}
		}
		long blobid = getBlobId(blob);
		String stmt = "INSERT INTO " + Table.AUXPROTECT_INVDIFF.toString()
				+ " (time, uid, slot, qty, blobid, damage) VALUES (?,?,?,?,?,?)";

		Connection connection = sql.getConnection();
		try (PreparedStatement statement = connection.prepareStatement(stmt)) {
			statement.setLong(1, time);
			int uid = sql.getUIDFromUUID("$" + uuid.toString(), false);
			statement.setInt(2, uid);
			statement.setInt(3, slot);
			if (qty >= 0) {
				statement.setInt(4, qty);
			}
			if (blobid >= 0) {
				statement.setLong(5, blobid);
			}
			if (damage != null) {
				statement.setInt(6, damage);
			}
			statement.execute();
		} finally {
			sql.returnConnection(connection);
		}
	}

	private long getBlobId(final byte[] blob) throws SQLException {
		if (blob == null) {
			return -1;
		}
		long cachedid = -1;
		boolean purge = System.currentTimeMillis() - lastpurged > 10 * 60 * 1000L;
		Iterator<BlobCache> it = cache.iterator();
		int hash = Arrays.hashCode(blob);
		for (BlobCache other = null; it.hasNext();) {
			other = it.next();
			// TODO needs tested, should be fine
			out: if (other.hash == hash && blob.length == other.ablob.length) {
				for (int i = 0; i < blob.length; i++) {
					if (blob[i] != other.ablob[i]) {
						break out; // This iteration isn't really necessary, just a check
					}
				}
				cachedid = other.blobid;
				if (!purge)
					break;
			}
			if (System.currentTimeMillis() - other.lastused > 600000L) {
				it.remove();
			}
		}
		if (purge) {
			lastpurged = System.currentTimeMillis();
		}

		if (cachedid > 0) {
			return cachedid;
		}

		cachedid = findOrInsertBlob(blob);
		if (cachedid > 0) {
			cache.add(new BlobCache(cachedid, blob));
		}
		return cachedid;
	}

	private long findOrInsertBlob(byte[] blob) throws SQLException {

		String stmt = "SELECT blobid FROM " + Table.AUXPROTECT_INVDIFFBLOB.toString() + " WHERE ablob=?";
		// synchronized (sql.connection) {
		Connection connection = sql.getConnection();
		try (PreparedStatement statement = connection.prepareStatement(stmt)) {
			if (sql.isMySQL()) {
				Blob sqlblob = connection.createBlob();
				sqlblob.setBytes(1, blob);
				statement.setBlob(1, sqlblob);
			} else {
				statement.setBytes(1, blob);
			}
			try (ResultSet rs = statement.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1);
				}
			}
		} finally {
			sql.returnConnection(connection);
		}
		connection = sql.getConnection();
		// }
		stmt = "INSERT INTO " + Table.AUXPROTECT_INVDIFFBLOB.toString() + " (blobid, ablob) VALUES (?,?)";
		// synchronized (sql.connection) {
		try (PreparedStatement statement = connection.prepareStatement(stmt)) {
			long blobid = ++this.blobid;
			statement.setLong(1, blobid);
			if (sql.isMySQL()) {
				Blob sqlblob = connection.createBlob();
				sqlblob.setBytes(1, blob);
				statement.setBlob(2, sqlblob);
			} else {
				statement.setBytes(2, blob);
			}
			statement.execute();
			return blobid;
		} finally {
			sql.returnConnection(connection);
		}
		// }
	}

	public static record DiffInventoryRecord(long basetime, int numdiff, PlayerInventoryRecord inventory) {
	};

	public DiffInventoryRecord getContentsAt(int uid, final long time)
			throws SQLException, IOException, ClassNotFoundException {

		PlayerInventoryRecord inv = null;
		long after = 0;

		// synchronized (sql.connection) {
		Connection connection = sql.getConnection();
		try (PreparedStatement statement = connection.prepareStatement("SELECT time,`blob`" + //
				"\nFROM " + Table.AUXPROTECT_INVBLOB.toString() + //
				"\nWHERE time=(" + "SELECT time FROM " + Table.AUXPROTECT_INVENTORY.toString()
				+ " WHERE uid=? AND action_id=? AND time<=? ORDER BY time DESC LIMIT 1);")) {
			statement.setInt(1, uid);
			statement.setInt(2, EntryAction.INVENTORY.id);
			statement.setLong(3, time);
			try (ResultSet rs = statement.executeQuery()) {
				if (rs.next()) {
					after = rs.getLong("time");

					byte[] blob = null;
					if (sql.isMySQL()) {
						try (InputStream in = rs.getBlob("blob").getBinaryStream()) {
							blob = in.readAllBytes();
						}
					} else {
						blob = rs.getBytes("blob");
					}
					if (blob != null) {
						inv = InvSerialization.toPlayerInventory(blob);
					}
				}
			}
		} finally {
			sql.returnConnection(connection);
		}
		if (inv == null) {
			return null;
		}
		// }
		List<ItemStack> output = playerInvToList(inv, true);
		// synchronized (sql.connection) {
		int numdiff = 0;
		connection = sql.getConnection();
		try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM "
				+ Table.AUXPROTECT_INVDIFF.toString() + " LEFT JOIN " + Table.AUXPROTECT_INVDIFFBLOB.toString() + " ON "
				+ Table.AUXPROTECT_INVDIFF.toString() + ".blobid=" + Table.AUXPROTECT_INVDIFFBLOB.toString()
				+ ".blobid where uid=? AND time BETWEEN ? AND ? ORDER BY time ASC")) {
			statement.setInt(1, uid);
			statement.setLong(2, after);
			statement.setLong(3, time);
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					int slot = rs.getInt("slot");
					byte[] blob = null;
					if (sql.isMySQL()) {
						try (InputStream in = rs.getBlob("ablob").getBinaryStream()) {
							blob = in.readAllBytes();
						} catch (Exception ignored) {
						}
					} else {
						blob = rs.getBytes("ablob");
					}

					int qty = rs.getInt("qty");
					ItemStack item;
					if (qty == 0 && !rs.wasNull()) {
						item = null;
					} else {
						if (blob == null) {
							item = output.get(slot);
						} else {
							item = InvSerialization.toItemStack(blob);
						}
						if (item != null) {
							if (qty > 0) {
								item.setAmount(qty);
								plugin.debug("setting slot " + slot + " to " + qty);
							}
							if (item.getItemMeta() != null && item.getItemMeta()instanceof Damageable meta) {
								int damage = rs.getInt("damage");
								if (!rs.wasNull()) {
									meta.setDamage(damage);
									item.setItemMeta(meta);
								}
							}
						}
					}
					output.set(slot, item);
					numdiff++;
				}
			}
		} finally {
			sql.returnConnection(connection);
		}
		return new DiffInventoryRecord(after, numdiff, listToPlayerInv(output, inv.exp()));
	}

	public static PlayerInventoryRecord listToPlayerInv(List<ItemStack> contents, int exp) {
		ItemStack[] storage = new ItemStack[36];
		ItemStack[] armor = new ItemStack[4];
		ItemStack[] extra = new ItemStack[1];
		ItemStack[] ender = new ItemStack[27];
		for (int i = 0; i < contents.size(); i++) {
			ItemStack item = contents.get(i);
			if (i < 27) {
				storage[i + 9] = item;
			} else if (i < 36) {
				storage[i - 27] = item;
			} else if (i < 40) {
				armor[4 - i + 35] = item;
			} else if (i < 41) {
				extra[i - 40] = item;
			} else if (i < 68) {
				ender[i - 41] = item;
			} else
				break;
		}
		return new PlayerInventoryRecord(storage, armor, extra, ender, exp);
	}

	public static List<ItemStack> playerInvToList(PlayerInventoryRecord inv, boolean addender) {
		if (inv == null) {
			return null;
		}
		List<ItemStack> output = new ArrayList<>();
		for (int i = 9; i < inv.storage().length; i++) {
			output.add(inv.storage()[i]);
		}
		for (int i = 0; i < 9; i++) {
			output.add(inv.storage()[i]);
		}
		for (int i = inv.armor().length - 1; i >= 0; i--) {
			output.add(inv.armor()[i]);
		}
		for (int i = 0; i < inv.extra().length; i++) {
			output.add(inv.extra()[i]);
		}
		if (addender) {
			for (ItemStack item : inv.ender()) {
				output.add(item);
			}
		}
		return output;
	}
}
