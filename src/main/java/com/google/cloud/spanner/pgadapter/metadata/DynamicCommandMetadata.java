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

import java.util.ArrayList;
import java.util.List;
import static com.google.cloud.spanner.pgadapter.metadata.JSONUtils.*;
import org.json.simple.JSONObject;

/**
 * DynamicCommandMetadata is a simple POJO for extracting commands which are user-definable and
 * generated at run-time from a user-defined JSON. This class concerns with the population of those
 * JSON objects onto more accessible formats for easier internal handling.
 */
public class DynamicCommandMetadata {

  private static final String COMMANDS_KEY = "commands";
  private static final String INPUT_KEY = "input_pattern";
  private static final String OUTPUT_KEY = "output_pattern";
  private static final String MATCHER_KEY = "matcher_array";

  private final String inputPattern;
  private final String outputPattern;
  private final List<String> matcherOrder;

  private DynamicCommandMetadata(JSONObject commandJSON) {
    this.inputPattern = getJSONString(commandJSON, INPUT_KEY);
    this.outputPattern = getJSONString(commandJSON, OUTPUT_KEY);
    this.matcherOrder = new ArrayList<>();
    for (Object arrayObject : getJSONArray(commandJSON, MATCHER_KEY)) {
      String argumentNumber = (String) arrayObject;
      this.matcherOrder.add(argumentNumber);
    }
  }

  /**
   * Takes a JSON object and returns a list of metadata objects holding the desired information.
   *
   * @param jsonObject Input JSON object in the format {"commands": [{"input_pattern": "",
   * "output_pattern": "", "matcher_array": [number1, ...]}, ...]}
   * @return A list of constructed metadata objects in the format understood by DynamicCommands
   */
  public static List<DynamicCommandMetadata> fromJSON(JSONObject jsonObject) {
    List<DynamicCommandMetadata> resultList = new ArrayList<>();
    for (Object currentJSONObject : getJSONArray(jsonObject, COMMANDS_KEY)) {
      resultList.add(new DynamicCommandMetadata((JSONObject) currentJSONObject));
    }
    return resultList;
  }

  public String getInputPattern() {
    return this.inputPattern;
  }

  public String getOutputPattern() {
    return this.outputPattern;
  }

  public List<String> getMatcherOrder() {
    return this.matcherOrder;
  }
}
