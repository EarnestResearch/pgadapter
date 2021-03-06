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

package com.google.cloud.spanner.pgadapter.metadata;

import com.google.cloud.spanner.pgadapter.Server;
import com.google.cloud.spanner.pgadapter.utils.Credentials;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Metadata extractor for CLI.
 */
public class OptionsMetadata {

  private static final String OPTION_SERVER_PORT = "s";
  private static final String OPTION_PROJECT_ID = "p";
  private static final String OPTION_INSTANCE_ID = "i";
  private static final String OPTION_DATABASE_NAME = "d";
  private static final String OPTION_CREDENTIALS_FILE = "c";
  private static final String OPTION_TEXT_FORMAT = "f";
  private static final String OPTION_BINARY_FORMAT = "b";
  private static final String OPTION_AUTHENTICATE = "a";
  private static final String OPTION_PSQL_MODE = "q";
  private static final String OPTION_COMMAND_METADATA_FILE = "j";
  private static final String OPTION_QUERY_REWRITES_FILE = "r";
  private static final String OPTION_BIGQUERY_MODE = "x";
  private static final String COMMAND_METADATA_FILE_DEFAULT = "metadata/command_metadata.json";
  private static final String CLI_ARGS =
      "gcpga -p <project> -i <instance> -d <database> -c <credentials_file>";
  private static final String OPTION_HELP = "h";
  private static final String DEFAULT_PORT = "5432";
  private static final String EMPTY_COMMAND_JSON = "{\"commands\":[]}";
  private static final int MIN_PORT = 1, MAX_PORT = 65535;

  private final String connectionURL;
  private final int proxyPort;
  private final TextFormat textFormat;
  private final boolean binaryFormat;
  private final boolean authenticate;
  private final boolean psqlMode;
  private final JSONObject commandMetadataJSON;
  private final List<QueryRewritesMetadata> queryRewritesJSON;

  public OptionsMetadata(String[] args) {
    CommandLine commandLine = buildOptions(args);
    this.connectionURL = buildConnectionURL(commandLine);
    this.proxyPort = buildProxyPort(commandLine);
    this.textFormat = buildTextFormat(commandLine);
    this.binaryFormat = commandLine.hasOption(OPTION_BINARY_FORMAT);
    this.authenticate = commandLine.hasOption(OPTION_AUTHENTICATE);
    this.psqlMode = commandLine.hasOption(OPTION_PSQL_MODE);
    this.commandMetadataJSON = buildCommandMetadataJSON(commandLine);
    this.queryRewritesJSON = buildQueryRewritesJSON(commandLine);
  }

  public OptionsMetadata(String connectionURL,
      int proxyPort,
      TextFormat textFormat,
      boolean forceBinary,
      boolean authenticate,
      boolean psqlMode,
      JSONObject commandMetadata,
      List<QueryRewritesMetadata> queryRewrites) {
    this.connectionURL = connectionURL;
    this.proxyPort = proxyPort;
    this.textFormat = textFormat;
    this.binaryFormat = forceBinary;
    this.authenticate = authenticate;
    this.psqlMode = psqlMode;
    this.commandMetadataJSON = commandMetadata;
    this.queryRewritesJSON = queryRewrites;
  }

  public static String getDefaultCommandMetadataFilePath() {
    return COMMAND_METADATA_FILE_DEFAULT;
  }

  /**
   * Takes the text format option result and parses it accordingly to fit the TextFormat data type.
   *
   * @param commandLine The parsed options for CLI
   * @return The specified text format from the user input, or default POSTGRESQL if none.
   */
  private TextFormat buildTextFormat(CommandLine commandLine) {
    return TextFormat.valueOf(
        commandLine.getOptionValue(OPTION_TEXT_FORMAT,
            TextFormat.POSTGRESQL.toString())
    );
  }

  /**
   * Takes the proxy port option result and parses it accordingly to fit port specs.
   *
   * @param commandLine The parsed options for CLI
   * @return The designated port if any, otherwise the default port.
   */
  private int buildProxyPort(CommandLine commandLine) {
    int port = Integer.parseInt(
        commandLine.getOptionValue(
            OPTION_SERVER_PORT,
            DEFAULT_PORT));
    if (port < MIN_PORT || port > MAX_PORT) {
      throw new IllegalArgumentException("Port must be between " + MIN_PORT + " and " + MAX_PORT);
    }
    return port;
  }

  /**
   * Get credential file path from either command line or application default. If neither throw
   * error.
   *
   * @param commandLine The parsed options for CLI
   * @return The absolute path of the credentials file.
   */
  private String buildCredentialsFile(CommandLine commandLine) {
    if (!commandLine.hasOption(OPTION_CREDENTIALS_FILE)) {
      String credentialsPath = Credentials.getApplicationDefaultCredentialsFilePath();
      if (credentialsPath == null) {
        throw new IllegalArgumentException(
            "User must specify a valid credential file, "
            + "or have application default credentials set-up.");
      }
      return credentialsPath;
    }
    return commandLine.getOptionValue(OPTION_CREDENTIALS_FILE);
  }

  /**
   * Takes user inputs and builds a JDBC connection string from them.
   *
   * @param commandLine The parsed options for CLI
   * @return The parsed JDBC connection string.
   */
  private String buildConnectionURL(CommandLine commandLine) {
    if(commandLine.hasOption(OPTION_BIGQUERY_MODE)) {
      return String.format("jdbc:bigquery://https://www.googleaps.com/bigquery/v2:443;"
            + "ProjectId=%s;"
            + "DefaultDataset=%s;"
            + "OAuthType=3",
        commandLine.getOptionValue(OPTION_PROJECT_ID),
        commandLine.getOptionValue(OPTION_DATABASE_NAME));
    } else {
      // Note that Credentials here is the credentials file, not the actual credentials
      return String.format("jdbc:cloudspanner:/"
            + "projects/%s/"
            + "instances/%s/"
            + "databases/%s"
            + ";credentials=%s",
         commandLine.getOptionValue(OPTION_PROJECT_ID),
         commandLine.getOptionValue(OPTION_INSTANCE_ID),
         commandLine.getOptionValue(OPTION_DATABASE_NAME),
         buildCredentialsFile(commandLine));
    }
  }

  /**
   * Takes the content of the specified (or default) command file and parses it into JSON format. If
   * finding the file fails in any-way, print an error and keep going with an empty spec. This is
   * done as commands are only required for PSQL mode and we wouldn't want it to affect other
   * important functionalities.
   *
   * @param commandLine The parsed options for CLI
   * @return The JSON object corresponding to the string contained within the specified (or default)
   * command file.
   */
  private JSONObject buildCommandMetadataJSON(CommandLine commandLine) {
    if(commandLine.hasOption(OPTION_COMMAND_METADATA_FILE) &&
        !commandLine.hasOption(OPTION_PSQL_MODE)) {
      throw new IllegalArgumentException(
          "PSQL Mode must be toggled (-q) to specify command metadata file (-j).");
    }

    File commandMetadataFile = new File(commandLine.getOptionValue(
        OPTION_COMMAND_METADATA_FILE,
        COMMAND_METADATA_FILE_DEFAULT));
    
    return loadJSON(Optional.of(commandMetadataFile), "command metadata")
            .orElseGet(() -> parseEmptyJson(EMPTY_COMMAND_JSON));
  }

  /**
   * Takes the content of the specified (or default) query rewrites file and parses it into JSON format. If
   * finding the file fails in any-way, print an error and keep going with an empty spec.
   *
   * @param commandLine The parsed options for CLI
   * @return The decoded rewrites specified within the specified file
   */
  private List<QueryRewritesMetadata> buildQueryRewritesJSON(CommandLine commandLine) {
    Optional<String> filePath = Optional.ofNullable(commandLine.getOptionValue(OPTION_QUERY_REWRITES_FILE));
    
    return loadJSON(filePath.map(x->new File(x)), "query rewrites").map(
      x -> QueryRewritesMetadata.fromJSON(x)
    ).orElse(Collections.emptyList());
  }

  /**
   * General load JSON method for loading configs
   * @param fileOpt
   * @param description
   * @return the JSONObject representation of the config
   */
  private Optional<JSONObject> loadJSON(Optional<File> fileOpt, String description) {
    JSONParser parser = new JSONParser();
    return fileOpt.flatMap(file -> {
      try {
        return Optional.of((JSONObject) parser
                .parse(new String(Files.readAllBytes(file.toPath()))));
      } catch (IOException e) {
        System.err.println(String
                .format("Specified %s file %s not found! Ignoring commands metadata file.",
                        description, file));
        return Optional.empty();
      } catch (org.json.simple.parser.ParseException e) {
        throw new IllegalArgumentException("Unable to process provided JSON file:", e);
      }
    });
  }

  /**
   * Load the empty JSON or throw runtime error
   * @param emptyJSON
   * @return the empty JSON
   */
  private JSONObject parseEmptyJson(String emptyJSON) {
    try {
      return (JSONObject) new JSONParser().parse(emptyJSON);
    } catch (org.json.simple.parser.ParseException ex) {
      throw new IllegalArgumentException(
              "Something went wrong! Processing empty JSON file failed!", ex);
    }
  }

  /**
   * Simple setup for command line option parsing.
   *
   * @param args user's CLI args
   * @return The parsed command line options.
   */
  private CommandLine buildOptions(String[] args) {
    Options options = new Options();
    options.addOption(OPTION_SERVER_PORT, "server-port", true,
        "This proxy's port number (Default 5432).");
    options.addRequiredOption(OPTION_PROJECT_ID, "project", true,
        "The id of the GCP project wherein lives the Spanner database.");
    options.addRequiredOption(OPTION_INSTANCE_ID, "instance", true,
        "The id of the Spanner instance within the GCP project.");
    options.addRequiredOption(OPTION_DATABASE_NAME, "database", true,
        "The name of the Spanner database within the GCP project.");
    options.addOption(OPTION_CREDENTIALS_FILE, "credentials-file", true,
        "The full path of the file location wherein lives the GCP credentials."
            + "If not specified, will try to read application default credentials.");
    options.addOption(OPTION_TEXT_FORMAT, "format", true,
        "The TextFormat that should be used as the format for the server"
            + " (default is POSTGRESQL).");
    options.addOption(OPTION_AUTHENTICATE, "authenticate", false,
        "Whether you wish the proxy to perform an authentication step."
    );
    options.addOption(OPTION_PSQL_MODE, "psql-mode", false,
        "This option turns on PSQL mode. This mode allows better compatibility to PSQL, "
            + "with an added performance cost. This mode should not be used for production, and we "
            + "do not guarantee its functionality beyond the basics."
    );
    options.addOption(OPTION_COMMAND_METADATA_FILE, "options-metadata", true,
        "The full path of the file containing the metadata specifications for psql-mode's "
            + "dynamic matcher. Each item in this matcher will create a runtime-generated command "
            + "which will translate incoming commands into whatever back-end SQL is desired.");
    options.addOption(OPTION_QUERY_REWRITES_FILE, "query-rewrites-metadata", true,
            "The full path of the file containing query rewrite instructions.");
    options.addOption(OPTION_BIGQUERY_MODE, "bigquery", false, "BigQuery connection mode.");
    options.addOption(OPTION_HELP, "help", false,
        "Print help."
    );
    options.addOption(OPTION_BINARY_FORMAT, "force-binary-format", false,
        "Force the server to send data back in binary PostgreSQL format when no specific "
            + "format has been requested. The PostgreSQL wire protocol specifies that the server "
            + "should send data in text format in those cases. This setting overrides this default "
            + "and should be used with caution, for example for testing purposes, as clients might "
            + "not accept this behavior. This setting only affects query results in extended query "
            + "mode. Queries in simple query mode will always return results in text format. If "
            + "you do not know what extended query mode and simple query mode is, then you should "
            + "probably not be using this setting.");
    CommandLineParser parser = new DefaultParser();
    HelpFormatter help = new HelpFormatter();
    try {
      CommandLine commandLine = parser.parse(options, args);
      if (commandLine.hasOption(OPTION_HELP)) {
        help.printHelp(CLI_ARGS, options);
        System.exit(0);
      }
      return commandLine;
    } catch (ParseException e) {
      help.printHelp(CLI_ARGS, options);
      throw new IllegalArgumentException(e.getMessage());
    }
  }

  public boolean isBinaryFormat() {
    return this.binaryFormat;
  }

  public JSONObject getCommandMetadataJSON() {
    return this.commandMetadataJSON;
  }

  public List<QueryRewritesMetadata> getQueryRewritesJSON() {
    return this.queryRewritesJSON;
  }

  public String getConnectionURL() {
    return this.connectionURL;
  }

  public int getProxyPort() {
    return this.proxyPort;
  }

  public TextFormat getTextFormat() {
    return this.textFormat;
  }

  public boolean shouldAuthenticate() {
    return this.authenticate;
  }

  public boolean isPSQLMode() {
    return this.psqlMode;
  }

  /**
   * The PostgreSQL wire protocol can send data in both binary and text format. When using text
   * format, the {@link Server} will normally send output back to the client using a format
   * understood by PostgreSQL clients. If you are using the server with a text-only client that does
   * not try to interpret the data that is returned by the server, such as for example psql, then it
   * is advisable to use Cloud Spanner formatting. The server will then return all data in a format
   * understood by Cloud Spanner.
   *
   * The default format used by the server is {@link TextFormat}.
   */
  public enum TextFormat {
    /**
     * The default format. Data is returned to the client in a format that PostgreSQL clients should
     * be able to understand and stringParse. Use this format if you are using the {@link Server}
     * with a client that tries to interpret the data that is returned by the server, such as for
     * example the PostgreSQL JDBC driver.
     */
    POSTGRESQL,
    /**
     * Data is returned to the client in Cloud Spanner format. Use this format if you are using the
     * server with a text-only client, such as psql, that does not try to interpret and stringParse
     * the data that is returned.
     */
    SPANNER
  }
}
