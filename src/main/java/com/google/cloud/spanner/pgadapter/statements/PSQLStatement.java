// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.spanner.pgadapter.statements;

import com.google.cloud.spanner.pgadapter.ConnectionHandler;
import com.google.cloud.spanner.pgadapter.commands.Command;

import java.sql.Connection;
import java.sql.SQLException;
import org.json.simple.JSONObject;

/**
 * Meant to be utilized when running as a proxy for PSQL. Not meant for production runs. Generally
 * here, all statements will take some penalty by running matchers to determine whether they belong
 * to a specific meta-command. If matches, translates the command into something Spanner can
 * handle.
 */
public class PSQLStatement extends IntermediateStatement {

  public PSQLStatement(String sql, ConnectionHandler connectionHandler) throws SQLException {
    super(sql, connectionHandler, x->translateSQL(x, connectionHandler));
  }

  @Override
  public void execute() {
    super.execute();
  }

  /**
   * Translate a Postgres Specific command into something Spanner can handle. Currently, this is
   * only concerned with PSQL specific meta-commands.
   *
   * @param sql The SQL statement to be translated.
   * @return The translated SQL statement if it matches any {@link Command} statement. Otherwise
   * gives out the original Statement.
   */
  private static String translateSQL(String sql, ConnectionHandler connectionHandler) {
    Connection connection = connectionHandler.getJdbcConnection();
    JSONObject commandMetadataJSON = connectionHandler.getServer().getOptions().getCommandMetadataJSON();
    for (Command currentCommand : Command
        .getCommands(sql, connection, commandMetadataJSON)) {
      if (currentCommand.is()) {
        return currentCommand.translate();
      }
    }
    return sql;
  }
}
