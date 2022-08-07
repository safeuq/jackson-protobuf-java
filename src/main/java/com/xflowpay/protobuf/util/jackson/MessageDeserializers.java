package com.xflowpay.protobuf.util.jackson;

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
import java.io.IOException;

public class MessageDeserializers extends Deserializers.Base {
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

  private static class DeserializerImpl extends StdDeserializer<Message> {
    private DeserializerImpl(Class<? extends Message> messageClass) {
      super(messageClass);
    }

    @Override
    public Message deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      Message.Builder builder = Internal.getDefaultInstance(handledType()).newBuilderForType();
      JacksonFormat.parser().merge(parser.readValueAsTree(), builder);
      return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Message> handledType() {
      return (Class<? extends Message>) super.handledType();
    }
  }
}
