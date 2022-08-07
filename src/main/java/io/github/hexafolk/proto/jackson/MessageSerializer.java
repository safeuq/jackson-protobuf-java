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

package io.github.hexafolk.proto.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;

/**
 * @author Safeuq Mohamed
 */
public class MessageSerializer extends StdSerializer<MessageOrBuilder> {
  private final boolean treatDefaultFieldsAsIs;
  private final boolean preservingProtoFieldNames;
  private final TypeRegistry registry;
  private final JsonFormat.TypeRegistry oldRegistry;

  public MessageSerializer(
      boolean treatDefaultFieldsAsIs,
      boolean preservingProtoFieldNames,
      TypeRegistry registry,
      JsonFormat.TypeRegistry oldRegistry) {
    super(MessageOrBuilder.class);
    this.treatDefaultFieldsAsIs = treatDefaultFieldsAsIs;
    this.preservingProtoFieldNames = preservingProtoFieldNames;
    this.registry = registry;
    this.oldRegistry = oldRegistry;
  }

  @Override
  public void serialize(
      MessageOrBuilder message, JsonGenerator generator, SerializerProvider provider)
      throws IOException {
    try {
      JacksonFormat.PrinterBuilder printerBuilder = JacksonFormat.printerBuilder();
      JsonInclude.Value propertyInclusion =
          provider.getDefaultPropertyInclusion(message.getClass());
      boolean includingDefaultValueFields = false;
      boolean omitNullFields = true;
      switch (propertyInclusion.getValueInclusion()) {
        case ALWAYS:
          includingDefaultValueFields = true;
          omitNullFields = false;
          break;
        case NON_NULL:
        case NON_ABSENT:
          includingDefaultValueFields = true;
          break;
        case USE_DEFAULTS:
          includingDefaultValueFields = treatDefaultFieldsAsIs;
          break;
      }
      if (preservingProtoFieldNames) {
        printerBuilder.preservingProtoFieldNames();
      }
      if (includingDefaultValueFields) {
        printerBuilder.includingDefaultValueFields();
      }
      if (!treatDefaultFieldsAsIs) {
        printerBuilder.treatDefaultFieldsAsNull();
      }
      if (omitNullFields) {
        printerBuilder.omitNullFields();
      }
      if (registry != null) {
        printerBuilder.usingTypeRegistry(registry);
      }
      if (oldRegistry != null) {
        printerBuilder.usingTypeRegistry(oldRegistry);
      }
      printerBuilder.build().appendTo(message, generator);
    } catch (InvalidProtocolBufferException exception) {
      throw new JsonMappingException(null, exception.getMessage(), exception);
    }
  }
}
