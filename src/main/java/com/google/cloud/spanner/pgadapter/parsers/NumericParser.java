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

package com.google.cloud.spanner.pgadapter.parsers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Translate from wire protocol to numeric.
 */
public class NumericParser extends Parser<BigDecimal> {

  public NumericParser(ResultSet item, int position) throws SQLException {
    this.item = item.getBigDecimal(position);
  }

  public NumericParser(Object item) {
    this.item = (BigDecimal) item;
  }

  public NumericParser(byte[] item) {
    this.item = new BigDecimal(new String(item));
  }

  @Override
  public BigDecimal getItem() {
    return this.item;
  }

  @Override
  protected String stringParse() {
    return this.item.toString();
  }

}
