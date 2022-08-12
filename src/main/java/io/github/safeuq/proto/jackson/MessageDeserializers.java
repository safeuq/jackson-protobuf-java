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

package io.github.safeuq.proto.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.protobuf.Internal;
import com.google.protobuf.Message;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;

public class MessageDeserializers extends Deserializers.Base {
  private final TypeRegistry registry;
  private final JsonFormat.TypeRegistry oldRegistry;

  public MessageDeserializers(TypeRegistry registry, JsonFormat.TypeRegistry oldRegistry) {
    this.registry = registry;
    this.oldRegistry = oldRegistry;
  }

  @Override
  @SuppressWarnings("unchecked")
  public JsonDeserializer<?> findBeanDeserializer(
      JavaType type, DeserializationConfig config, BeanDescription beanDesc)
      throws JsonMappingException {
    Class<?> rawClass = type.getRawClass();
    if (rawClass != Message.class && Message.class.isAssignableFrom(rawClass)) {
      return new DeserializerImpl((Class<? extends Message>) rawClass);
    }
    return super.findBeanDeserializer(type, config, beanDesc);
  }

  private class DeserializerImpl extends StdDeserializer<Message> {
    private DeserializerImpl(Class<? extends Message> messageClass) {
      super(messageClass);
    }

    @Override
    public Message deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      Message.Builder builder = Internal.getDefaultInstance(handledType()).newBuilderForType();
      JacksonFormat.Parser protoJsonParser = JacksonFormat.parser();
      if (registry != null) {
        protoJsonParser = protoJsonParser.usingTypeRegistry(registry);
      }
      if (oldRegistry != null) {
        protoJsonParser = protoJsonParser.usingTypeRegistry(oldRegistry);
      }
      protoJsonParser.merge(parser.readValueAsTree(), builder);
      return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Message> handledType() {
      return (Class<? extends Message>) super.handledType();
    }
  }
}
