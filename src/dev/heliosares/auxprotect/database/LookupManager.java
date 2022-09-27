package dev.heliosares.auxprotect.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Parameters;

public class LookupManager {
	public enum LookupExceptionType {
		SYNTAX, PLAYER_NOT_FOUND, ACTION_NEGATE, UNKNOWN_ACTION, ACTION_INCOMPATIBLE, UNKNOWN_WORLD, UNKNOWN_TABLE,
		GENERAL, TOO_MANY
	}

	public static class LookupException extends Exception {
		private static final long serialVersionUID = -8329753973868577238L;

		public final LookupExceptionType error;

		public LookupException(LookupExceptionType error, String errorMessage) {
			super(errorMessage);
			this.error = error;
		}

		@Override
		public String toString() {
			return error.toString() + ": " + super.getMessage();
		}
	}

	private final SQLManager sql;
	private final IAuxProtect plugin;

	public LookupManager(SQLManager sql, IAuxProtect plugin) {
		this.sql = sql;
		this.plugin = plugin;
	}

	public ArrayList<DbEntry> lookup(Parameters params) throws LookupManager.LookupException {
		String[] sqlstmts = params.toSQL(plugin);

		ArrayList<String> writeparams = new ArrayList<>();
		for (int i = 1; i < sqlstmts.length; i++) {
			writeparams.add(sqlstmts[i]);
		}
		String stmt = "SELECT * FROM " + params.getTable().name();
		if (sqlstmts[0].length() > 1) {
			stmt += "\nWHERE " + sqlstmts[0];
		}
		stmt += "\nORDER BY time DESC\nLIMIT " + (SQLManager.MAX_LOOKUP_SIZE + 1) + ";";
		return sql.lookup(params.getTable(), stmt, writeparams);
	}

	public int count(Parameters params) throws LookupManager.LookupException {
		String[] sqlstmts = params.toSQL(plugin);

		ArrayList<String> writeparams = new ArrayList<>();
		for (int i = 1; i < sqlstmts.length; i++) {
			writeparams.add(sqlstmts[i]);
		}
		String stmt = "SELECT COUNT() FROM " + params.getTable().name();
		if (sqlstmts[0].length() > 1) {
			stmt += "\nWHERE " + sqlstmts[0];
		}
		plugin.debug(stmt);
		Connection connection = sql.getConnection();
		synchronized (connection) {
			try (PreparedStatement statement = connection.prepareStatement(stmt)) {
				for (int i = 1; i < sqlstmts.length; i++) {
					statement.setString(i, sqlstmts[i]);
				}
				try (ResultSet rs = statement.executeQuery()) {
					if (rs.next()) {
						return rs.getInt(1);
					}
				}
			} catch (SQLException e) {
				plugin.print(e);
				throw new LookupManager.LookupException(LookupManager.LookupExceptionType.GENERAL,
						plugin.translate("lookup-error"));
			}
		}
		return -1;
	}
}
