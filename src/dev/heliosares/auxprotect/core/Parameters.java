package dev.heliosares.auxprotect.core;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.LookupManager;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.Table;
import dev.heliosares.auxprotect.database.XrayEntry;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class Parameters {
	public final long time_created = System.currentTimeMillis();

	private long after;
	private long before = Long.MAX_VALUE;
	private long exactTime = -1;

	boolean negateUser;
	private List<String> uids = new ArrayList<>();
	private List<String> users = new ArrayList<>();

	private List<Integer> actions = new ArrayList<>();

	boolean negateTarget;
	private List<String> targets = new ArrayList<>();

	boolean negateData;
	private List<String> datas = new ArrayList<>();

	private Table table;

	boolean negateRadius;
	private int radius = -1;
	private Location location;

	boolean negateWorld;
	private List<String> worlds = new ArrayList<>();

	private List<Flag> flags = new ArrayList<>();

	private List<Short> ratings = new ArrayList<>();

	public static enum Flag {
		COUNT, COUNT_ONLY, PT, XRAY, BW, MONEY, ACTIVITY, RETENTION, HIDE_COORDS;
	}

	public static Parameters parse(IAuxProtect plugin, MySender sender, String[] args)
			throws ParseException, LookupManager.LookupException {
		Parameters parameters = new Parameters();
		SQLManager sql = plugin.getSqlManager();
		if (!sender.isBungee() && sender.getSender() instanceof org.bukkit.entity.Player) {
			parameters.location = ((org.bukkit.entity.Player) sender.getSender()).getLocation();
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
					boolean permission = true;
					if (flag == Flag.ACTIVITY) {
						if (!APPermission.LOOKUP_ACTIVITY.hasPermission(sender)) {
							permission = false;
						}
					} else if (flag == Flag.PT) {
						if (!APPermission.LOOKUP_PLAYTIME.hasPermission(sender)) {
							permission = false;
						}
					} else if (flag == Flag.MONEY) {
						if (!APPermission.LOOKUP_MONEY.hasPermission(sender)) {
							permission = false;
						}
					} else if (flag == Flag.RETENTION) {
						if (!APPermission.LOOKUP_RETENTION.hasPermission(sender)) {
							permission = false;
						}
					}
					if (!permission) {
						throw new ParseException(plugin.translate("no-permission-flag"));
					}
					parameters.flags.add(flag);
					continue;
				}
			}
			String[] split = line.split(":");

			String token = split[0];

			if (split.length == 2) {
				String param = split[1];
				boolean negate = false;
				if (param.startsWith("!")) {
					negate = true;
					param = param.substring(1);
				}
				count++;
				switch (token) {
				case "u":
				case "user":
					parameters.negateUser = negate;
					for (String user : param.split(",")) {
						int uid = sql.getUIDFromUsername(user);
						int altuid = sql.getUIDFromUUID(user);
						boolean good = false;
						if (uid > 0) {
							parameters.uids.add(Integer.toString(uid));
							good = true;
						}
						if (altuid > 0) {
							parameters.uids.add(Integer.toString(altuid));
							good = true;
						}
						if (!good) {
							throw new LookupManager.LookupException(LookupManager.LookupExceptionType.PLAYER_NOT_FOUND,
									String.format(plugin.translate("lookup-playernotfound"), user));
						}
						parameters.users.add(user);
					}
					continue;
				case "target":
					parameters.negateTarget = negate;
					targetstr = param;
					continue;
				case "data":
					parameters.negateData = negate;
					datastr = param;
					continue;
				case "a":
				case "action":
					for (String actionStr : param.split(",")) {
						int state = 0;
						boolean pos = actionStr.startsWith("+");
						if (pos || actionStr.startsWith("-")) {
							state = pos ? 1 : -1;
							actionStr = actionStr.substring(1);
						}
						EntryAction action = EntryAction.getAction(actionStr);
						if (action == null) {
							throw new ParseException(String.format(plugin.translate("lookup-unknownaction"), param));
						}
						APPermission actionNode = APPermission.LOOKUP_ACTION.dot(action.toString().toLowerCase());
						if (!actionNode.hasPermission(sender)) {
							throw new ParseException(String.format(plugin.translate("lookup-action-perm") + " (%s)",
									param, actionNode.node));
						}
						if (parameters.table != null && parameters.table != action.getTable()) {
							throw new ParseException(plugin.translate("lookup-incompatible-tables"));
						}
						parameters.table = action.getTable();
						if (action.hasDual) {
							if (state != -1) {
								parameters.actions.add(action.idPos);
							}
							if (state != 1) {
								parameters.actions.add(action.id);
							}
						} else {
							parameters.actions.add(action.id);
						}
					}
					continue;
				case "before":
				case "after":
				case "t":
				case "time":
					boolean plusminus = param.contains("+-");
					boolean minus = param.contains("-");
					if (minus) { // || plusminus unnecessary because they both have '-'
						String[] range = param.split("\\+?-");
						if (range.length != 2) {
							throw new ParseException(String.format(plugin.translate("lookup-invalid-parameter"), line));
						}

						long time1;
						long time2;
						try {
							time1 = TimeUtil.stringToMillis(range[0]);
							time2 = TimeUtil.stringToMillis(range[1]);
						} catch (NumberFormatException e) {
							throw new ParseException(String.format(plugin.translate("lookup-invalid-parameter"), line));
						}

						if (!range[0].endsWith("e")) {
							time1 = System.currentTimeMillis() - time1;
						}
						if (plusminus) {
							parameters.after = time1 - time2;
							parameters.before = time1 + time2;
						} else {
							if (!range[1].endsWith("e")) {
								time2 = System.currentTimeMillis() - time2;
							}
							parameters.after = Math.min(time1, time2);
							parameters.before = Math.max(time1, time2);
						}
					} else if (token.equalsIgnoreCase("time") && param.endsWith("e")) {
						parameters.exactTime = Long.parseLong(param.substring(0, param.length() - 1));
					} else {
						long time;
						try {
							time = TimeUtil.stringToMillis(param);
							if (time < 0) {
								throw new ParseException(
										String.format(plugin.translate("lookup-invalid-parameter"), line));
							}
						} catch (NumberFormatException e) {
							throw new ParseException(String.format(plugin.translate("lookup-invalid-parameter"), line));
						}

						if (!param.endsWith("e")) {
							time = System.currentTimeMillis() - time;
						}

						if (token.equalsIgnoreCase("time") || token.equalsIgnoreCase("after")) {
							parameters.after = time;
						} else {
							parameters.before = time;
						}
					}
					continue;
				case "r":
				case "radius":
					parameters.negateRadius = negate;
					try {
						parameters.radius = Integer.parseInt(param);
					} catch (NumberFormatException e) {
						throw new ParseException(String.format(plugin.translate("lookup-invalid-parameter"), line));
					}
					continue;
				case "w":
				case "world":
					parameters.negateWorld = negate;
					for (String str : param.split(",")) {
						World world = Bukkit.getWorld(str);
						if (world == null) {
							throw new ParseException(String.format(plugin.translate("lookup-unknown-world"), str));
						}
						parameters.worlds.add(world.getName());
					}
					continue;
				case "rating":
					for (String str : param.split(",")) {
						try {
							parameters.ratings.add(Short.parseShort(str));
						} catch (NumberFormatException e) {
							throw new ParseException(String.format(plugin.translate("lookup-invalid-parameter"), line));
						}
					}
					continue;
				case "db":
					if (!APPermission.ADMIN.hasPermission(sender)) {
						throw new ParseException(plugin.translate("no-permission"));
					}
					try {
						parameters.table = Table.valueOf(param.toUpperCase());
					} catch (Exception e) {
						throw new ParseException(String.format(plugin.translate("lookup-invalid-parameter"), line));
					}
					continue;
				}
			}
			throw new ParseException(String.format(plugin.translate("lookup-invalid-parameter"), line));

		}
		if (count < 1) {
			throw new ParseException(plugin.translate("lookup-invalid-notenough"));
		}
		if (parameters.actions.size() == 0) {
			for (EntryAction action : EntryAction.values()) {
				if (action.getTable() == Table.AUXPROTECT_MAIN
						&& !APPermission.LOOKUP_ACTION.dot(action.toString().toLowerCase()).hasPermission(sender)) {
					throw new ParseException(plugin.translate("lookup-action-none"));
				}
			}
		}
		if (parameters.flags.contains(Flag.BW)) {
			parameters.uids.addAll(parameters.targets);
			parameters.targets = parameters.uids;
		}
		if (parameters.flags.contains(Flag.ACTIVITY) || parameters.flags.contains(Flag.PT)) {
			if (parameters.users.size() > 1) {
				throw new ParseException(plugin.translate("lookup-playtime-toomanyusers"));
			} else if (parameters.uids.size() == 0) {
				throw new ParseException(plugin.translate("lookup-playtime-nouser"));
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
						throw new ParseException(plugin.translate("lookup-rating-wrong"));
					}
				}
			} else {
				parameters.actions.add(EntryAction.VEIN.id);
			}

		}
		if (parameters.table == null) {
			parameters.table = Table.AUXPROTECT_MAIN;
		}
		if (datastr != null) {
			if (!parameters.table.hasData()) {
				throw new ParseException(plugin.translate("lookup-nodata"));
			}
			for (String data : split(datastr, true)) {
				parameters.datas.add(data);
			}
		}

		if (targetstr != null) {
			if (parameters.table.hasStringTarget()) {
				for (String target : split(targetstr, true)) {
					parameters.targets.add(target);
				}
			} else {
				for (String target : targetstr.split(",")) {
					int uid = sql.getUIDFromUsername(target);
					int altuid = sql.getUIDFromUUID(target);
					boolean good = false;
					if (uid > 0) {
						parameters.targets.add(Integer.toString(uid));
						good = true;
					}
					if (altuid > 0) {
						parameters.targets.add(Integer.toString(altuid));
						good = true;
					}
					if (!good) {
						throw new LookupManager.LookupException(LookupManager.LookupExceptionType.PLAYER_NOT_FOUND,
								String.format(plugin.translate("lookup-playernotfound"), target));
					}
				}
			}
		}
		plugin.debug("After:" + parameters.after + " Before:" + parameters.before);
		return parameters;
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
		if (exactTime > -1 && entry.getTime() != entry.getTime()) {
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
			// TODO
		}
		if (radius > -1 && location != null) {
			boolean contains = Math.abs(entry.x - location.getBlockX()) > radius
					|| Math.abs(entry.y - location.getBlockY()) > radius
					|| Math.abs(entry.z - location.getBlockZ()) > radius;
			if (contains == negateRadius) {
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

	public String[] toSQL(IAuxProtect plugin) throws LookupManager.LookupException {
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
		if (exactTime > -1) {
			stmts.add("time = " + exactTime);
		}
		if (after > 0) {
			stmts.add("time > " + after);
		}
		if (before < Long.MAX_VALUE) {
			stmts.add("time < " + before);
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
		plugin.debug("r:" + radius + " l:" + location + " has:" + table.hasLocation());
		if (radius > -1 && location != null && table.hasLocation()) {
			String worldstmt = "world_id = " + sql.getWID(location.getWorld().getName());
			String between = (negateRadius ? " NOT" : "") + " BETWEEN ";
			String coordstmt = "(x" + between + (location.getBlockX() - radius) + " AND "
					+ (location.getBlockX() + radius) + " AND y" + between + (location.getBlockY() - radius) + " AND "
					+ (location.getBlockY() + radius) + " AND z" + between + (location.getBlockZ() - radius) + " AND "
					+ (location.getBlockZ() + radius) + ")";
			if (negateRadius) {
				stmts.add("(NOT " + worldstmt + " OR " + coordstmt + ")");
			} else {
				stmts.add(worldstmt);
				stmts.add(coordstmt);
			}
		}
		if (!worlds.isEmpty() && table.hasLocation()) {
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

		String outsql = "";
		for (String stmt : stmts) {
			if (outsql.length() > 0) {
				outsql += " AND ";
			}
			outsql += stmt;
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

	public static class ParseException extends Exception {
		private static final long serialVersionUID = -3371728414735680055L;

		public ParseException(String msg) {
			super(msg);
		}

	}

	public long getAfter() {
		return after;
	}

	public long getBefore() {
		return before;
	}

	public long getExactTime() {
		return exactTime;
	}

	public boolean isNegateUser() {
		return negateUser;
	}

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

	public boolean isNegateRadius() {
		return negateRadius;
	}

	public int getRadius() {
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

	public void setExactTime(long exactTime) {
		this.exactTime = exactTime;
	}

	public void setNegateUser(boolean negateUser) {
		this.negateUser = negateUser;
	}

	public void setUsers(List<String> users) {
		this.uids = users;
	}

	public void setActions(List<Integer> actions) {
		this.actions = actions;
	}

	public void setNegateTarget(boolean negateTarget) {
		this.negateTarget = negateTarget;
	}

	public void setTargets(List<String> targets) {
		this.targets = targets;
	}

	public void setNegateData(boolean negateData) {
		this.negateData = negateData;
	}

	public void setDatas(List<String> datas) {
		this.datas = datas;
	}

	public void setTable(Table table) {
		this.table = table;
	}

	public void setNegateRadius(boolean negateRadius) {
		this.negateRadius = negateRadius;
	}

	public void setRadius(int radius) {
		this.radius = radius;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public void setNegateWorld(boolean negateWorld) {
		this.negateWorld = negateWorld;
	}

	public void setWorld(List<String> world) {
		this.worlds = world;
	}

	public void setFlags(List<Flag> flags) {
		this.flags = flags;
	}

	public void setRatings(List<Short> ratings) {
		this.ratings = ratings;
	}

	public boolean hasFlag(Flag flag) {
		return flags.contains(flag);
	}

}
