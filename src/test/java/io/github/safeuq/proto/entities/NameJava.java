// Copyright (c) 2022 - Safeuq Mohamed, mohamedsafeuq@outlook.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.github.safeuq.proto.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.StringJoiner;

public class NameJava {
  @JsonProperty("first_name")
  private final String firstName;

  @JsonProperty("middle_name")
  private final String middleName;

  @JsonProperty("last_name")
  private final String lastName;

  @JsonProperty("full_name")
  private final String fullName;

  @JsonProperty("nickname")
  private final String nickname;

  @JsonCreator
  public NameJava(
      @JsonProperty("first_name") String firstName,
      @JsonProperty("middle_name") String middleName,
      @JsonProperty("last_name") String lastName,
      @JsonProperty("full_name") String fullName,
      @JsonProperty("nickname") String nickname) {
    this.firstName = firstName;
    this.middleName = middleName;
    this.lastName = lastName;
    this.fullName = fullName;
    this.nickname = nickname;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NameJava)) return false;
    NameJava nameJava = (NameJava) o;
    return Objects.equals(firstName, nameJava.firstName)
        && Objects.equals(middleName, nameJava.middleName)
        && Objects.equals(lastName, nameJava.lastName)
        && Objects.equals(fullName, nameJava.fullName)
        && Objects.equals(nickname, nameJava.nickname);
  }

  @Override
  public int hashCode() {
    return Objects.hash(firstName, middleName, lastName, fullName, nickname);
  }

  @Override
  public String toString() {
    StringJoiner result = new StringJoiner(", ", "{", "}");
    if (this.firstName != null) {
      result.add("\"first_name\": " + "\"" + this.firstName + "\"");
    }
    if (this.middleName != null) {
      result.add("\"middle_name\": " + "\"" + this.middleName + "\"");
    }
    if (this.lastName != null) {
      result.add("\"last_name\": " + "\"" + this.lastName + "\"");
    }
    if (this.fullName != null) {
      result.add("\"full_name\": " + "\"" + this.fullName + "\"");
    }
    if (this.nickname != null) {
      result.add("\"nickname\": " + "\"" + this.nickname + "\"");
    }
    return result.toString();
  }
}
