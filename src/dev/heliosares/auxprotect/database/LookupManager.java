package dev.heliosares.auxprotect.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Parameters;
import dev.heliosares.auxprotect.exceptions.LookupException;

public class LookupManager {
	private final SQLManager sql;
	private final IAuxProtect plugin;

	public LookupManager(SQLManager sql, IAuxProtect plugin) {
		this.sql = sql;
		this.plugin = plugin;
	}

	public ArrayList<DbEntry> lookup(Parameters... params) throws LookupException {
		ArrayList<DbEntry> out = null;
		for (Parameters param : params) {
			String[] sqlstmts = param.toSQL(plugin);

			ArrayList<String> writeparams = new ArrayList<>();
			for (int i = 1; i < sqlstmts.length; i++) {
				writeparams.add(sqlstmts[i]);
			}
			String stmt = "SELECT * FROM " + param.getTable().toString();
			if (sqlstmts[0].length() > 1) {
				stmt += "\nWHERE " + sqlstmts[0];
			}
			stmt += "\nORDER BY time DESC\nLIMIT " + (SQLManager.MAX_LOOKUP_SIZE + 1) + ";";
			ArrayList<DbEntry> results = sql.lookup(param.getTable(), stmt, writeparams);
			if (out == null) {
				out = results;
			} else {
				out.addAll(results);
			}
		}
		return out;
	}

	public int count(Parameters... params) throws LookupException {
		int count = 0;
		Connection connection = null;
		try {
			connection = sql.getConnection();

			for (Parameters param : params) {
				String[] sqlstmts = param.toSQL(plugin);

				ArrayList<String> writeparams = new ArrayList<>();
				for (int i = 1; i < sqlstmts.length; i++) {
					writeparams.add(sqlstmts[i]);
				}
				String stmt = sql.getCountStmt(param.getTable().toString());
				if (sqlstmts[0].length() > 1) {
					stmt += "\nWHERE " + sqlstmts[0];
				}
				plugin.debug(stmt);
				try (PreparedStatement statement = connection.prepareStatement(stmt)) {
					for (int i = 1; i < sqlstmts.length; i++) {
						statement.setString(i, sqlstmts[i]);
					}
					try (ResultSet rs = statement.executeQuery()) {
						if (rs.next()) {
							count += rs.getInt(1);
						}
					}
				}
			}
		} catch (SQLException e1) {
			plugin.print(e1);
			throw new LookupException(Language.L.ERROR);
		} finally {
			sql.returnConnection(connection);
		}
		return count;
	}
}
