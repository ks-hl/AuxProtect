package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.AuxProtectAPI;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.Language.L;
import dev.heliosares.auxprotect.database.*;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.exceptions.LookupException;
import dev.heliosares.auxprotect.exceptions.ParseException;
import dev.heliosares.auxprotect.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class Parameters {

    // ----------------------------------------------
    // ------------------- FIELDS -------------------
    // ----------------------------------------------
    public final long time_created = System.currentTimeMillis();
    private final IAuxProtect plugin;
    private final List<Long> exactTime = new ArrayList<>();
    private final List<String> uids = new ArrayList<>();
    private final List<String> users = new ArrayList<>();
    // action
    private final List<Integer> actions = new ArrayList<>();
    private final List<String> datas = new ArrayList<>();
    // radius
    private final HashMap<Integer, Boolean> radius = new HashMap<>();
    private final List<String> worlds = new ArrayList<>();
    // flags
    private final List<Flag> flags = new ArrayList<>();
    // ratings
    private final List<Short> ratings = new ArrayList<>();
    // user
    boolean negateUser;
    // target
    boolean negateTarget;
    // data
    boolean negateData;
    // world
    boolean negateWorld;
    // time
    private long after;
    private long before = Long.MAX_VALUE;
    private List<String> targets = new ArrayList<>();
    // table
    private Table table;
    private Location location;

    // ----------------------------------------------------
    // ------------------- CONSTRUCTORS -------------------
    // ----------------------------------------------------

    private Parameters() {
        plugin = AuxProtectAPI.getInstance();
    }

    public Parameters(Table table) {
        this();
        this.table = table;
    }

    // -----------------------------------------------
    // ---------------- COMMAND BASED ----------------
    // -----------------------------------------------

    /**
     * This method is used by the lookup command to parse commands. This may be used
     * by an API by manually creating a String[] args
     *
     * @param sender The player sending the command. Used for permission checks. Null to bypass
     * @param args   Arguments of the command.
     */
    public static Parameters parse(@Nullable SenderAdapter sender, String[] args)
            throws ParseException, LookupException {
        IAuxProtect plugin = AuxProtectAPI.getInstance();
        Parameters parameters = new Parameters();
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
                    if (sender != null && !flag.hasPermission(sender)) {
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
                count++;
                switch (token) {
                    case "user" -> {
                        parameters.user(param);
                        continue;
                    }
                    case "target" -> {
                        targetstr = param;
                        continue;
                    }
                    case "data" -> {
                        datastr = param;
                        continue;
                    }
                    case "action" -> {
                        parameters.action(sender, param);
                        continue;
                    }
                    case "before" -> {
                        parameters.time(param, true);
                        continue;
                    }
                    case "after" -> {
                        parameters.time(param, false);
                        continue;
                    }
                    case "time" -> {
                        parameters.time(param);
                        continue;
                    }
                    case "radius" -> {
                        if (sender == null)
                            throw new ParseException(L.NOTPLAYERERROR);
                        if (sender.getPlatform() != PlatformType.SPIGOT)
                            throw new ParseException(L.INVALID_PARAMETER, line);
                        if (sender.getSender() instanceof org.bukkit.entity.Player player) {
                            parameters.radius(player.getLocation(), param);
                        } else {
                            throw new ParseException(L.NOTPLAYERERROR);
                        }
                        continue;
                    }
                    case "world" -> {
                        parameters.world(param);
                        continue;
                    }
                    case "rating" -> {
                        for (String str : param.split(",")) {
                            try {
                                parameters.ratings.add(Short.parseShort(str));
                            } catch (NumberFormatException e) {
                                throw new ParseException(L.INVALID_PARAMETER, line);
                            }
                        }
                        continue;
                    }
                    case "db" -> {
                        if (!APPermission.ADMIN.hasPermission(sender)) {
                            throw new ParseException(L.NO_PERMISSION);
                        }
                        try {
                            parameters.table = Table.valueOf(param.toUpperCase());
                        } catch (Exception e) {
                            throw new ParseException(L.INVALID_PARAMETER, line);
                        }
                        continue;
                    }
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
        if (parameters.hasFlag(Flag.PLAYBACK)) {
            if (plugin.getPlatform() != PlatformType.SPIGOT || sender == null)
                throw new ParseException(L.INVALID_PARAMETER, "#playback");
            parameters.actions.clear();
            parameters.actions.add(EntryAction.TP.id);
            parameters.actions.add(EntryAction.TP.idPos);
            parameters.actions.add(EntryAction.POS.id);
            parameters.worlds.clear();
            parameters.worlds.add(((org.bukkit.entity.Player) sender.getSender()).getWorld().getName());
        }
        if (parameters.flags.contains(Flag.MONEY)) {
            parameters.actions.clear();
            parameters.actions.add(EntryAction.MONEY.id);
            parameters.actions.add(EntryAction.MONEY.idPos);
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
            parameters.data(datastr);
        }

        parameters.target(targetstr);

        plugin.debug("After:" + parameters.after + " Before:" + parameters.before);
        return parameters;
    }

    private static String replaceAlias(String base, String alias, String fullName) {
        if (base.equalsIgnoreCase(alias)) {
            return fullName;
        }
        return base;
    }

    private static List<String> split(final String str) {
        StringBuilder build = new StringBuilder();
        boolean escape = false;
        List<String> values = new ArrayList<>();
        for (char current : str.toCharArray()) {
            if (current == '\\') {
                escape = true;
                continue;
            }
            if (!escape && current == ',') {
                values.add(build.toString());
                build = new StringBuilder();
                continue;
            }
            if (escape && current != ',') {
                build.append('\\');
            }
            build.append(current);

            if (escape) {
                escape = false;
            }
        }
        if (escape) {
            build.append('\\');
        }
        if (build.length() > 0) {
            values.add(build.toString());
        }
        return values;
    }

    /**
     * Sets the time. Equivalent to time:<param>
     *
     * @param param May be a range, a single time, or an exact time
     */
    public Parameters time(String param) throws ParseException {
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
        return this;
    }

    public Parameters before(String time) throws ParseException {
        time(time, true);
        return this;
    }

    public Parameters after(String time) throws ParseException {
        time(time, false);
        return this;
    }

    /**
     * Sets the user of the lookup. Equivalent to user:<param>
     *
     * @param param The user, or null to clear.
     * @throws LookupException The user is not found
     */
    public Parameters user(@Nullable String param) throws LookupException {
        if (param == null) {
            users.clear();
            uids.clear();
            return this;
        }
        //noinspection AssignmentUsedAsCondition
        if (negateUser = param.startsWith("!")) {
            param = param.substring(1);
        }
        for (String user : param.split(",")) {
            int uid;
            int altuid;
            try {
                uid = plugin.getSqlManager().getUserManager().getUIDFromUsername(user, false);
                altuid = plugin.getSqlManager().getUserManager().getUIDFromUUID(user, false);
            } catch (BusyException e) {
                throw new LookupException(L.DATABASE_BUSY);
            } catch (SQLException e) {
                throw new LookupException(L.ERROR);
            }

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
        return this;
    }

    /**
     * Sets the action. Equivalent to action:<param>
     *
     * @param sender Only used for permission checks.
     * @param param  The action
     */
    public Parameters action(@Nullable SenderAdapter sender, String param) throws ParseException {
        if (param.startsWith("!")) {
            throw new ParseException(Language.L.COMMAND__LOOKUP__ACTION_NEGATE);
        }
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
        return this;
    }

    /**
     * Sets the target of the lookup. Equivalent to target:<target>
     * <p>
     * <<<<<<< Updated upstream
     *
     * @param param Null will clear
     * @throws IllegalStateException if the table is null
     */
    public Parameters target(@Nullable String param) throws LookupException {
        if (param == null) {
            targets.clear();
            return this;
        }
        if (table == null) {
            throw new IllegalStateException("action or table must be set before target");
        }
        //noinspection AssignmentUsedAsCondition
        if (negateTarget = param.startsWith("!")) {
            param = param.substring(1);
        }
        if (table.hasStringTarget()) {
            targets.addAll(split(param));
        } else {
            for (String target : param.split(",")) {
                int uid;
                int altuid;
                try {
                    uid = plugin.getSqlManager().getUserManager().getUIDFromUsername(target, false);
                    altuid = plugin.getSqlManager().getUserManager().getUIDFromUUID(target, false);
                } catch (BusyException e) {
                    throw new LookupException(L.DATABASE_BUSY);
                } catch (SQLException e) {
                    throw new LookupException(L.ERROR);
                }
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
        return this;
    }

    /**
     * Sets the data. Equivalent to data:<param>
     */
    public void data(String param) throws ParseException {
        if (table == null) {
            throw new IllegalStateException("action or table must be set before target");
        }
        if (!table.hasData()) {
            throw new ParseException(Language.L.COMMAND__LOOKUP__NODATA);
        }
        //noinspection AssignmentUsedAsCondition
        if (negateData = param.startsWith("!")) {
            param = param.substring(1);
        }
        datas.addAll(split(param));
    }

    // -------------------------------------------------
    // ------------------- API BASED -------------------
    // -------------------------------------------------

    public void radius(Location location, String param) throws ParseException {
        this.location = location;
        for (String str : param.split(",")) {
            try {
                boolean negate = str.startsWith("!");
                if (negate) {
                    str = str.substring(1);
                }
                radius.put(Integer.parseInt(str), negate);
            } catch (Exception e) {
                throw new ParseException(Language.L.INVALID_PARAMETER, param);
            }
        }
    }

    public void world(String param) throws ParseException {
        //noinspection AssignmentUsedAsCondition
        if (negateWorld = param.startsWith("!")) {
            param = param.substring(1);
        }
        for (String str : param.split(",")) {
            World world = Bukkit.getWorld(str);
            if (world == null) {
                throw new ParseException(Language.L.COMMAND__LOOKUP__UNKNOWN_WORLD, str);
            }
            worlds.add(world.getName());
        }
    }

    public Parameters time(long start, long stop) {
        after(Math.min(start, stop));
        before(Math.max(start, stop));

        return this;
    }

    public Parameters before(long time) {
        this.before = time;
        return this;
    }

    public Parameters after(long time) {
        this.after = time;
        return this;
    }

    /**
     * Adds the specified UUID to the list of users
     *
     * @param uuid   The UUID to be added
     * @param negate Whether to negate
     * @throws LookupException If the user is not found
     */
    public Parameters user(UUID uuid, boolean negate) throws LookupException {
        this.negateUser = negate;
        int uid;
        try {
            uid = plugin.getSqlManager().getUserManager().getUIDFromUUID("$" + uuid.toString(), false);
        } catch (SQLException e) {
            throw new LookupException(L.DATABASE_BUSY);
        }

        if (uid > 0) {
            uids.add(Integer.toString(uid));
        } else {
            throw new LookupException(Language.L.LOOKUP_PLAYERNOTFOUND, uuid);
        }
        users.add(uuid.toString());
        return this;
    }

    /**
     * Adds actions to this parameter instance
     *
     * @param sender Will be used for individual action permission checks. Null will
     *               bypass checks
     * @param action The actions to be added
     * @param state  -1 for negative, 0 for either, 1 for positive
     */
    public Parameters addAction(@Nullable SenderAdapter sender, EntryAction action, int state) throws ParseException {
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
        return this;
    }

    /**
     * Adds the specified UUID to the list of users
     *
     * @param uuid   The UUID to be added
     * @param negate Whether to negate
     * @throws LookupException If the user is not found
     */
    public Parameters target(UUID uuid, boolean negate) throws LookupException {
        this.negateTarget = negate;
        int uid;
        try {
            uid = plugin.getSqlManager().getUserManager().getUIDFromUUID("$" + uuid.toString(), false);
        } catch (BusyException e) {
            throw new LookupException(L.DATABASE_BUSY);
        } catch (SQLException e) {
            throw new LookupException(L.ERROR);
        }

        if (uid > 0) {
            targets.add(Integer.toString(uid));
        } else {
            throw new LookupException(Language.L.LOOKUP_PLAYERNOTFOUND, uuid);
        }
        return this;
    }

    /**
     * Sets the flags. Does not affect any other flags.
     *
     * @param flag the flag to add
     */
    public Parameters flag(Flag flag) {
        flags.add(flag);
        return this;
    }

    public Parameters addExactTime(long exactTime) {
        this.exactTime.add(exactTime);
        return this;
    }

    public Parameters addRadius(int radius, boolean negate) {
        this.radius.put(radius, negate);
        return this;
    }

    public Parameters addWorld(String world) {
        this.worlds.add(world);
        return this;
    }

    /**
     * Clears the flags
     */
    public Parameters resetFlags() {
        this.flags.clear();
        return this;
    }

    public Parameters addRating(short rating) {
        this.ratings.add(rating);
        return this;
    }

    // -----------------------------------------------
    // ------------------- GETTERS -------------------
    // -----------------------------------------------

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

    public Parameters setLocation(Location location) {
        this.location = location;
        return this;
    }

    public boolean isNegateWorld() {
        return negateWorld;
    }

    public Parameters setNegateWorld(boolean negateWorld) {
        this.negateWorld = negateWorld;
        return this;
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

    public boolean hasFlag(Flag flag) {
        return flags.contains(flag);
    }

    // -----------------------------------------------
    // ------------------- PRIVATE -------------------
    // -----------------------------------------------

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
            StringBuilder stmt = new StringBuilder("(");

            boolean first = true;

            if (table.hasStringTarget()) {
                for (String target : targets) {
                    if (first) {
                        first = false;
                    } else {
                        stmt.append(" OR ");
                    }
                    stmt.append("target LIKE ?");
                    out.add(target.replaceAll("-", "[- ]").replaceAll("\\*", "%"));
                }
            } else {
                stmt.append("target_id IN ");
                stmt.append(toGroup(targets));
            }

            stmts.add((negateTarget ? "NOT " : "") + stmt + ")");
        }
        if (!exactTime.isEmpty()) {
            StringBuilder stmt = new StringBuilder();
            for (Long exact : exactTime) {
                if (stmt.length() > 0) {
                    stmt.append(" OR ");
                }
                stmt.append("time = ").append(exact);
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
            StringBuilder stmt = new StringBuilder("(");

            boolean first = true;

            for (String data : datas) {
                if (first) {
                    first = false;
                } else {
                    stmt.append(" OR ");
                }
                stmt.append("data LIKE ?");
                out.add(data.replaceAll("-", "[- ]").replaceAll("\\*", "%"));
            }
            stmts.add((negateData ? "NOT " : "") + stmt + ")");
        }

        if (table.hasLocation()) {
            if (!radius.isEmpty() && location != null) {
                radius.forEach((r, n) -> {
                    String between = " BETWEEN ";
                    assert location.getWorld() != null;
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
                StringBuilder stmt = new StringBuilder("world_id" + (negateWorld ? " NOT" : "") + " IN (");
                boolean first = true;
                for (String w : worlds) {
                    if (first) {
                        first = false;
                    } else {
                        stmt.append(",");
                    }
                    stmt.append(sql.getWID(w));
                }
                stmts.add(stmt + ")");
            }
        }

        StringBuilder outsql = new StringBuilder();
        for (String stmt : stmts) {
            if (stmt == null || stmt.length() == 0) {
                continue;
            }
            if (outsql.length() > 0) {
                outsql.append(" AND ");
            }
            outsql.append("(").append(stmt).append(")");
        }
        String[] output = new String[out.size() + 1];
        output[0] = outsql.toString();
        for (int i = 0; i < out.size(); i++) {
            output[i + 1] = out.get(i);
        }
        return output;
    }

    public boolean matches(DbEntry entry) throws SQLException {
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
        boolean any = false;
        for (String data : datas) {
            StringBuilder node = new StringBuilder();
            for (String part : data.split("[*\\-]")) {
                if (node.length() > 0) {
                    node.append(".*");
                }
                node.append(Pattern.quote(part));
            }
            //noinspection AssignmentUsedAsCondition
            if (any = data.matches(node.toString())) {
                break;
            }
        }
        if (!any) {
            return false;
        }
        if (!radius.isEmpty() && location != null) {
            if (radius.entrySet().stream().anyMatch((e) -> e.getValue() == distance(entry) > e.getKey())) {
                return false;
            }
        }
        if (!worlds.isEmpty()) {
            return worlds.contains(entry.world) != negateWorld;
        }

        return true;
    }

    private Parameters time(String param, boolean before) throws ParseException {
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
        return this;
    }

    private int distance(DbEntry entry) {
        if (location == null) {
            return -1;
        }
        assert location.getWorld() != null;
        if (!entry.world.equals(location.getWorld().getName())) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.max(Math.abs(entry.x - location.getBlockX()), Math.abs(entry.y - location.getBlockY())),
                Math.abs(entry.z - location.getBlockZ()));
    }

    private String toGroup(List<?> list) {
        StringBuilder stmt = new StringBuilder("(");
        boolean first = true;
        for (Object id : list) {
            if (first) {
                first = false;
            } else {
                stmt.append(",");
            }
            stmt.append(id);
        }
        return stmt + ")";
    }

    public enum Flag {
        COUNT(null), COUNT_ONLY(null), PT(APPermission.LOOKUP_PLAYTIME), XRAY(APPermission.LOOKUP_XRAY), BW(null),
        MONEY(APPermission.LOOKUP_MONEY), ACTIVITY(APPermission.LOOKUP_ACTIVITY), PLAYBACK(APPermission.LOOKUP_PLAYBACK), INCREMENTAL_POS(APPermission.LOOKUP_PLAYBACK),
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

}
