package dev.heliosares.auxprotect.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import dev.heliosares.auxprotect.AuxProtectAPI;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.exceptions.ParseException;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class Parameters {
	public final long time_created = System.currentTimeMillis();
	private final IAuxProtect plugin;

	private long after;
	private long before = Long.MAX_VALUE;
	private List<Long> exactTime = new ArrayList<>();

	boolean negateUser;
	private List<String> uids = new ArrayList<>();
	private List<String> users = new ArrayList<>();

	private List<Integer> actions = new ArrayList<>();

	boolean negateTarget;
	private List<String> targets = new ArrayList<>();

	boolean negateData;
	private List<String> datas = new ArrayList<>();

	private Table table;

	private HashMap<Integer, Boolean> radius = new HashMap<>();
	private Location location;

	boolean negateWorld;
	private List<String> worlds = new ArrayList<>();

	private List<Flag> flags = new ArrayList<>();

	private List<Short> ratings = new ArrayList<>();

	public static enum Flag {
		COUNT(null), COUNT_ONLY(null), PT(APPermission.LOOKUP_PLAYTIME), XRAY(APPermission.LOOKUP_XRAY), BW(null),
		MONEY(APPermission.LOOKUP_MONEY), ACTIVITY(APPermission.LOOKUP_ACTIVITY),
		RETENTION(APPermission.LOOKUP_RETENTION), HIDE_COORDS(null);

		private final APPermission perm;

		Flag(APPermission perm) {
			this.perm = perm;
		}

		public boolean hasPermission(SenderAdapter sender) {
			if (perm == null) {
				return true;
			}
			return perm.hasPermission(sender);
		}
	}

	private Parameters() {
		plugin = AuxProtectAPI.getInstance();
	}

	public Parameters(Table table) {
		this();
		this.table = table;
	}

	/**
	 * This method is used by the lookup command to parse commands. This may be used
	 * by an API by manually creating a String[] args
	 * 
	 * @param plugin
	 * @param sender
	 * @param args
	 * @return
	 * @throws ParseException
	 * @throws LookupException
	 */
	public static Parameters parse(@Nonnull SenderAdapter sender, String[] args)
			throws ParseException, LookupException {
		IAuxProtect plugin = AuxProtectAPI.getInstance();
		Parameters parameters = new Parameters();
		if (sender.getPlatform() == PlatformType.SPIGOT
				&& sender.getSender() instanceof org.bukkit.entity.Player player) {
			parameters.location = player.getLocation();
		}
		int count = 0;
		String datastr = null;
		String targetstr = null;
		for (int i = 1; i < args.length; i++) {
			String line = args[i];
			if (line.startsWith("#")) {
				Flag flag = null;
				try {
					flag = Flag.valueOf(line.toUpperCase().substring(1).replaceAll("-", "_"));
				} catch (Exception ignored) {
				}
				if (flag != null) {
					if (!flag.hasPermission(sender)) {
						throw new ParseException(Language.L.NO_PERMISSION_FLAG);
					}
					parameters.flags.add(flag);
					continue;
				}
			}
			String[] split = line.split(":");

			String token = split[0].toLowerCase();
			token = replaceAlias(token, "a", "action");
			token = replaceAlias(token, "t", "time");
			token = replaceAlias(token, "u", "user");
			token = replaceAlias(token, "r", "radius");
			token = replaceAlias(token, "w", "world");
			token = replaceAlias(token, "a", "action");

			if (split.length == 2) {
				String param = split[1];
				boolean negate = false;
				if (param.startsWith("!")) {
					if (token.equals("action")) {
						throw new LookupException(Language.L.COMMAND__LOOKUP__ACTION_NEGATE);
					} else if (!token.equals("radius")) {
						negate = true;
						param = param.substring(1);
					}
				}
				count++;
				switch (token) {
				case "user":
					parameters.user(param, negate);
					continue;
				case "target":
					parameters.negateTarget = negate;
					targetstr = param;
					continue;
				case "data":
					parameters.negateData = negate;
					datastr = param;
					continue;
				case "action":
					parameters.action(sender, param);
					continue;
				case "before":
					parameters.time(param, true);
					continue;
				case "after":
					parameters.time(param, false);
					continue;
				case "time":
					parameters.time(param);
					continue;
				case "radius":
					for (String str : param.split(",")) {
						try {
							negate = str.startsWith("!");
							if (negate) {
								str = str.substring(1);
							}
							parameters.radius.put(Integer.parseInt(str), negate);
						} catch (Exception e) {
							throw new ParseException(Language.L.INVALID_PARAMETER, line);
						}
					}
					continue;
				case "world":
					parameters.negateWorld = negate;
					for (String str : param.split(",")) {
						World world = Bukkit.getWorld(str);
						if (world == null) {
							throw new ParseException(Language.L.COMMAND__LOOKUP__UNKNOWN_WORLD, str);
						}
						parameters.worlds.add(world.getName());
					}
					continue;
				case "rating":
					for (String str : param.split(",")) {
						try {
							parameters.ratings.add(Short.parseShort(str));
						} catch (NumberFormatException e) {
							throw new ParseException(Language.L.INVALID_PARAMETER, line);
						}
					}
					continue;
				case "db":
					if (!APPermission.ADMIN.hasPermission(sender)) {
						throw new ParseException(Language.L.NO_PERMISSION);
					}
					try {
						parameters.table = Table.valueOf(param.toUpperCase());
					} catch (Exception e) {
						throw new ParseException(Language.L.INVALID_PARAMETER, line);
					}
					continue;
				}
			}
			throw new ParseException(Language.L.INVALID_PARAMETER, line);

		}
		if (count < 1) {
			throw new ParseException(Language.L.INVALID_NOTENOUGH);
		}
		if (parameters.actions.size() == 0 || parameters.table == null) {
			for (EntryAction action : EntryAction.values()) {
				if (action.getTable() == Table.AUXPROTECT_MAIN
						&& !APPermission.LOOKUP_ACTION.dot(action.toString().toLowerCase()).hasPermission(sender)) {
					throw new ParseException(Language.L.COMMAND__LOOKUP__ACTION_NONE);
				}
			}
			parameters.table = Table.AUXPROTECT_MAIN;
		}
		if (parameters.flags.contains(Flag.BW)) {
			parameters.uids.addAll(parameters.targets);
			parameters.targets = parameters.uids;
		}
		if (parameters.flags.contains(Flag.ACTIVITY) || parameters.flags.contains(Flag.PT)) {
			if (parameters.users.size() > 1) {
				throw new ParseException(Language.L.LOOKUP_PLAYTIME_TOOMANYUSERS);
			} else if (parameters.uids.size() == 0) {
				throw new ParseException(Language.L.LOOKUP_PLAYTIME_NOUSER);
			}
		}
		if (parameters.flags.contains(Flag.PT)) {
			parameters.actions.clear();
			parameters.actions.add(EntryAction.SESSION.id);
			parameters.actions.add(EntryAction.SESSION.idPos);
		}
		if (parameters.flags.contains(Flag.ACTIVITY)) {
			parameters.actions.clear();
			parameters.actions.add(EntryAction.ACTIVITY.id);
			parameters.actions.add(EntryAction.ACTIVITY.idPos);
		}
		if (parameters.ratings.size() > 0) {
			if (!parameters.actions.isEmpty()) {
				for (int id : parameters.actions) {
					if (id != EntryAction.VEIN.id) {
						throw new ParseException(Language.L.COMMAND__LOOKUP__RATING_WRONG);
					}
				}
			} else {
				parameters.actions.add(EntryAction.VEIN.id);
			}

		}
		if (datastr != null) {
			if (!parameters.table.hasData()) {
				throw new ParseException(Language.L.COMMAND__LOOKUP__NODATA);
			}
			for (String data : split(datastr, true)) {
				parameters.datas.add(data);
			}
		}

		parameters.target(targetstr, parameters.negateTarget);

		plugin.debug("After:" + parameters.after + " Before:" + parameters.before);
		return parameters;
	}

	private static String replaceAlias(String base, String alias, String fullName) {
		if (base.equalsIgnoreCase(alias)) {
			return fullName;
		}
		return base;
	}

	/**
	 * Sets the user of the lookup
	 * 
	 * @param param
	 * @param negate
	 * @throws LookupException
	 */
	public void user(String param, boolean negate) throws LookupException {
		if (param == null) {
			users.clear();
			uids.clear();
			return;
		}
		negateUser = negate;
		for (String user : param.split(",")) {
			int uid = plugin.getSqlManager().getUIDFromUsername(user);
			int altuid = plugin.getSqlManager().getUIDFromUUID(user);
			boolean good = false;
			if (uid > 0) {
				uids.add(Integer.toString(uid));
				good = true;
			}
			if (altuid > 0) {
				uids.add(Integer.toString(altuid));
				good = true;
			}
			if (!good) {
				throw new LookupException(Language.L.LOOKUP_PLAYERNOTFOUND, user);
			}
			users.add(user);
		}
	}

	/**
	 * Adds the specified UUID to the list of users
	 * 
	 * @param uuid   The UUID to be added
	 * @param negate Whether to negate
	 * @throws LookupException If the user is not found
	 */
	public void user(UUID uuid, boolean negate) throws LookupException {
		this.negateUser = negate;
		int uid = plugin.getSqlManager().getUIDFromUUID("$" + uuid.toString());
		if (uid > 0) {
			uids.add(Integer.toString(uid));
		} else {
			throw new LookupException(Language.L.LOOKUP_PLAYERNOTFOUND, uuid);
		}
		users.add(uuid.toString());
	}

	/**
	 * Adds the specified UUID to the list of users
	 * 
	 * @param uuid   The UUID to be added
	 * @param negate Whether to negate
	 * @throws LookupException If the user is not found
	 */
	public void target(UUID uuid, boolean negate) throws LookupException {
		this.negateTarget = negate;
		int uid = plugin.getSqlManager().getUIDFromUUID("$" + uuid.toString());
		if (uid > 0) {
			targets.add(Integer.toString(uid));
		} else {
			throw new LookupException(Language.L.LOOKUP_PLAYERNOTFOUND, uuid);
		}
	}

	/**
	 * Used to set the target of the lookup. Action/Table must be set before calling
	 * this method.
	 * 
	 * @param param  Null will clear
	 * @param negate
	 * @throws LookupException
	 * @throws IllegalStateException if the table is null
	 */
	public void target(@Nullable String param, boolean negate) throws LookupException {
		if (param == null) {
			targets.clear();
			return;
		}
		if (table == null) {
			throw new IllegalStateException("action or table must be set before target");
		}
		if (table.hasStringTarget()) {
			for (String target : split(param, true)) {
				targets.add(target);
			}
		} else {
			for (String target : param.split(",")) {
				int uid = plugin.getSqlManager().getUIDFromUsername(target);
				int altuid = plugin.getSqlManager().getUIDFromUUID(target);
				boolean good = false;
				if (uid > 0) {
					targets.add(Integer.toString(uid));
					good = true;
				}
				if (altuid > 0) {
					targets.add(Integer.toString(altuid));
					good = true;
				}
				if (!good) {
					throw new LookupException(Language.L.LOOKUP_PLAYERNOTFOUND, target);
				}
			}
		}
	}

	/**
	 * Sets the actions of this parameter instance
	 * 
	 * @param sender Will be used for individual action permission checks. Null will
	 *               bypass checks
	 * @param param  CSV of actions
	 * @throws ParseException
	 */
	public void action(@Nullable SenderAdapter sender, String param) throws ParseException {
		for (String actionStr : param.split(",")) {
			int state = 0;
			boolean pos = actionStr.startsWith("+");
			if (pos || actionStr.startsWith("-")) {
				state = pos ? 1 : -1;
				actionStr = actionStr.substring(1);
			}
			EntryAction action = EntryAction.getAction(actionStr);
			if (action == null) {
				throw new ParseException(Language.L.LOOKUP_UNKNOWNACTION, param);
			}
			addAction(sender, action, state);
		}
	}

	/**
	 * 
	 * Adds actions to this parameter instance
	 * 
	 * @param sender Will be used for individual action permission checks. Null will
	 *               bypass checks
	 * @param action The actions to be added
	 * @param int    state -1 for negative, 0 for either, 1 for positive
	 * @throws ParseException
	 */
	public void addAction(@Nullable SenderAdapter sender, EntryAction action, int state) throws ParseException {
		if (!action.isEnabled()) {
			throw new ParseException(Language.L.ACTION_DISABLED);
		}

		if (sender != null && !action.hasPermission(sender)) {
			throw new ParseException(Language.L.COMMAND__LOOKUP__ACTION_PERM, action.getNode());
		}
		if (table != null && table != action.getTable()) {
			throw new ParseException(Language.L.COMMAND__LOOKUP__INCOMPATIBLE_TABLES);
		}
		table = action.getTable();
		if (action.hasDual) {
			if (state != -1) {
				actions.add(action.idPos);
			}
			if (state != 1) {
				actions.add(action.id);
			}
		} else {
			actions.add(action.id);
		}
	}

	/**
	 * Used to parse time:
	 * 
	 * @param param May be a range, a single time, or an exact time
	 * @throws ParseException
	 */
	public void time(String param) throws ParseException {
		param = param.replace("ms", "f");
		boolean plusminus = param.contains("+-");
		boolean minus = param.contains("-");
		if (minus) { // || plusminus unnecessary because they both have '-'
			String[] range = param.split("\\+?-");
			if (range.length != 2) {
				throw new ParseException(Language.L.INVALID_PARAMETER, param);
			}

			long time1;
			long time2;
			try {
				time1 = TimeUtil.stringToMillis(range[0]);
				time2 = TimeUtil.stringToMillis(range[1]);
			} catch (NumberFormatException e) {
				throw new ParseException(Language.L.INVALID_PARAMETER, param);
			}

			if (!range[0].endsWith("e")) {
				time1 = System.currentTimeMillis() - time1;
			}
			if (plusminus) {
				after = time1 - time2;
				before = time1 + time2;
			} else {
				if (!range[1].endsWith("e")) {
					time2 = System.currentTimeMillis() - time2;
				}
				after = Math.min(time1, time2);
				before = Math.max(time1, time2);
			}
		} else if (param.endsWith("e")) {
			exactTime.add(Long.parseLong(param.substring(0, param.length() - 1)));
		} else {
			long time;
			try {
				time = TimeUtil.stringToMillis(param);
				if (time < 0) {
					throw new ParseException(Language.L.INVALID_PARAMETER, param);
				}
			} catch (NumberFormatException e) {
				throw new ParseException(Language.L.INVALID_PARAMETER, param);
			}

			if (!param.endsWith("e")) {
				time = System.currentTimeMillis() - time;
			}
			after = time;
		}
	}

	public void time(long start, long stop) {
		after(Math.min(start, stop));
		before(Math.max(start, stop));
	}

	private void time(String param, boolean before) throws ParseException {
		try {
			long time = TimeUtil.stringToMillis(param);
			if (time < 0) {
				throw new ParseException(Language.L.INVALID_PARAMETER, param);
			}
			if (!param.endsWith("e")) {
				time = System.currentTimeMillis() - time;
			}
			if (before) {
				this.before = time;
			} else {
				this.after = time;
			}
		} catch (NumberFormatException e) {
			throw new ParseException(Language.L.INVALID_PARAMETER, param);
		}
	}

	public void before(long time) {
		this.before = time;
	}

	public void before(String time) throws ParseException {
		time(time, true);
	}

	public void after(long time) {
		this.after = time;
	}

	public void after(String time) throws ParseException {
		time(time, false);
	}

	public boolean matches(DbEntry entry) {
		if (!uids.isEmpty()) {
			boolean contains = false;
			for (String user : uids) {
				if (user.equalsIgnoreCase(entry.getUserUUID())) {
					contains = true;
					break;
				}
			}
			if (contains == negateUser) {
				return false;
			}
		}
		if (!targets.isEmpty()) {
			boolean contains = false;
			for (String target : targets) {
				if (target.equalsIgnoreCase(entry.getTargetUUID())) {
					contains = true;
					break;
				}
			}
			if (contains == negateTarget) {
				return false;
			}
		}
		if (!exactTime.isEmpty() && !exactTime.contains(entry.getTime())) {
			return false;
		}
		if (entry.getTime() < after || entry.getTime() > before) {
			return false;
		}
		if (!actions.isEmpty() && !actions.contains(entry.getAction().getId(entry.getState()))) {
			return false;
		}
		if (!ratings.isEmpty() && entry instanceof XrayEntry && !ratings.contains(((XrayEntry) entry).getRating())) {
			return false;
		}
		if (datas != null) {
			boolean any = false;
			for (String data : datas) {
				String node = "";
				for (String part : data.split("[\\*\\-]")) {
					if (node.length() > 0) {
						node += ".*";
					}
					node += Pattern.quote(part);
				}
				if (any = data.matches(node)) {
					break;
				}
			}
			if (!any) {
				return false;
			}
		}
		if (!radius.isEmpty() && location != null) {
			if (radius.entrySet().stream().anyMatch((e) -> e.getValue() == distance(entry) > e.getKey())) {
				return false;
			}
		}
		if (!worlds.isEmpty()) {
			if (worlds.contains(entry.world) == negateWorld) {
				return false;
			}
		}

		return true;
	}

	private int distance(DbEntry entry) {
		if (location == null) {
			return -1;
		}
		if (!entry.world.equals(location.getWorld().getName())) {
			return Integer.MAX_VALUE;
		}
		return Math.max(Math.max(Math.abs(entry.x - location.getBlockX()), Math.abs(entry.y - location.getBlockY())),
				Math.abs(entry.z - location.getBlockZ()));
	}

	public String[] toSQL(IAuxProtect plugin) {
		if (table == null) {
			throw new IllegalStateException();
		}
		SQLManager sql = plugin.getSqlManager();
		List<String> stmts = new ArrayList<>();
		List<String> out = new ArrayList<>();

		if (!uids.isEmpty()) {
			String stmt = "uid " + (negateUser ? "NOT " : "") + "IN ";
			stmt += toGroup(uids);
			stmts.add(stmt);
		}
		if (!targets.isEmpty()) {
			String stmt = "(";

			boolean first = true;

			if (table.hasStringTarget()) {
				for (String target : targets) {
					if (first) {
						first = false;
					} else {
						stmt += " OR ";
					}
					stmt += "target LIKE ?";
					out.add(target.replaceAll("-", "[- ]").replaceAll("\\*", "%"));
				}
			} else {
				stmt += "target_id IN ";
				stmt += toGroup(targets);
			}

			stmts.add((negateTarget ? "NOT " : "") + stmt + ")");
		}
		if (!exactTime.isEmpty()) {
			String stmt = "";
			for (Long exact : exactTime) {
				if (stmt.length() > 0) {
					stmt += " OR ";
				}
				stmt += "time = " + exact;
			}
			stmts.add("(" + stmt + ")");
		}
		if (after > 0) {
			stmts.add("time >= " + after);
		}
		if (before < Long.MAX_VALUE) {
			stmts.add("time <= " + before);
		}
		if (!actions.isEmpty() && table.hasActionId()) {
			stmts.add("action_id IN " + toGroup(actions));
		}
		if (!ratings.isEmpty() && table == Table.AUXPROTECT_XRAY) {
			stmts.add("rating IN " + toGroup(ratings));
		}
		if (!datas.isEmpty() && table.hasData()) {
			String stmt = "(";

			boolean first = true;

			for (String data : datas) {
				if (first) {
					first = false;
				} else {
					stmt += " OR ";
				}
				stmt += "data LIKE ?";
				out.add(data.replaceAll("-", "[- ]").replaceAll("\\*", "%"));
			}
			stmts.add((negateData ? "NOT " : "") + stmt + ")");
		}

		if (table.hasLocation()) {
			if (!radius.isEmpty() && location != null) {
				radius.forEach((r, n) -> {
					String between = " BETWEEN ";
					String coordstmt = "x" + between + (location.getBlockX() - r) + " AND " + (location.getBlockX() + r)
							+ " AND y" + between + (location.getBlockY() - r) + " AND " + (location.getBlockY() + r)
							+ " AND z" + between + (location.getBlockZ() - r) + " AND " + (location.getBlockZ() + r)
							+ " AND world_id=" + sql.getWID(location.getWorld().getName());
					if (n) {
						coordstmt = "NOT (" + coordstmt + ")";
					}
					stmts.add(coordstmt);
				});
			}
			if (!worlds.isEmpty()) {
				String stmt = "world_id" + (negateWorld ? " NOT" : "") + " IN (";
				boolean first = true;
				for (String w : worlds) {
					if (first) {
						first = false;
					} else {
						stmt += ",";
					}
					stmt += sql.getWID(w);
				}
				stmts.add(stmt + ")");
			}
		}

		String outsql = "";
		for (String stmt : stmts) {
			if (stmt == null || stmt.length() == 0) {
				continue;
			}
			if (outsql.length() > 0) {
				outsql += " AND ";
			}
			outsql += "(" + stmt + ")";
		}
		String[] output = new String[out.size() + 1];
		output[0] = outsql;
		for (int i = 0; i < out.size(); i++) {
			output[i + 1] = out.get(i);
		}
		return output;
	}

	private static List<String> split(final String str, final boolean allowEscape) {
		String build = "";
		boolean escape = false;
		List<String> values = new ArrayList<>();
		for (char current : str.toCharArray()) {
			if (allowEscape && current == '\\') {
				escape = true;
				continue;
			}
			if (!escape && current == ',') {
				values.add(build);
				build = "";
				continue;
			}
			if (escape && current != ',') {
				build += '\\';
			}
			build += current;

			if (escape) {
				escape = false;
			}
		}
		if (escape) {
			build += '\\';
		}
		if (build.length() > 0) {
			values.add(build);
		}
		return values;
	}

	private String toGroup(List<?> list) {
		String stmt = "(";
		boolean first = true;
		for (Object id : list) {
			if (first) {
				first = false;
			} else {
				stmt += ",";
			}
			stmt += id;
		}
		return stmt + ")";
	}

	public long getAfter() {
		return after;
	}

	public long getBefore() {
		return before;
	}

	public List<Long> getExactTime() {
		return exactTime;
	}

	public boolean isNegateUser() {
		return negateUser;
	}

	/**
	 * This is only used in a select few places. Parameters#getUIDS matters more
	 * 
	 * @return the list of users
	 */
	public List<String> getUsers() {
		return users;
	}

	public List<String> getUIDs() {
		return uids;
	}

	public List<Integer> getActions() {
		return actions;
	}

	public boolean isNegateTarget() {
		return negateTarget;
	}

	public List<String> getTargets() {
		return targets;
	}

	public boolean isNegateData() {
		return negateData;
	}

	public List<String> getDatas() {
		return datas;
	}

	public Table getTable() {
		return table;
	}

	public HashMap<Integer, Boolean> getRadius() {
		return radius;
	}

	public Location getLocation() {
		return location;
	}

	public boolean isNegateWorld() {
		return negateWorld;
	}

	public List<String> getWorld() {
		return worlds;
	}

	public List<Flag> getFlags() {
		return flags;
	}

	public List<Short> getRatings() {
		return ratings;
	}

	public void setAfter(long after) {
		this.after = after;
	}

	public void setBefore(long before) {
		this.before = before;
	}

	public void addExactTime(long exactTime) {
		this.exactTime.add(exactTime);
	}

	public void setNegateUser(boolean negateUser) {
		this.negateUser = negateUser;
	}

	public void addAction(int action) {
		this.actions.add(action);
	}

	public void setNegateTarget(boolean negateTarget) {
		this.negateTarget = negateTarget;
	}

	public void addTarget(String target) {
		this.targets.add(target);
	}

	public void setNegateData(boolean negateData) {
		this.negateData = negateData;
	}

	public void addData(String data) {
		this.datas.add(data);
	}

	public void setTable(Table table) {
		this.table = table;
	}

	public void addRadius(int radius, boolean negate) {
		this.radius.put(radius, negate);
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public void setNegateWorld(boolean negateWorld) {
		this.negateWorld = negateWorld;
	}

	public void addWorld(String world) {
		this.worlds.add(world);
	}

	/**
	 * Clears the flags
	 */
	public void resetFlags() {
		this.flags.clear();
	}

	/**
	 * Sets or unsets the flags. Does not affect any other flags.
	 * 
	 * @param flags
	 */
	public void setFlags(boolean state, Flag... flags) {
		for (Flag flag : flags) {
			if (state) {
				this.flags.add(flag);
			} else {
				this.flags.remove(flag);
			}
		}
	}

	public void addRating(short rating) {
		this.ratings.add(rating);
	}

	public boolean hasFlag(Flag flag) {
		return flags.contains(flag);
	}

}
