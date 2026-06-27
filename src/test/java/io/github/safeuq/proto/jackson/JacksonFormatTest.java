package io.github.safeuq.proto.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.safeuq.proto.entities.MapMessage;
import io.github.safeuq.proto.entities.Name;
import io.github.safeuq.proto.entities.NameJava;
import org.checkerframework.checker.units.qual.N;
import org.junit.jupiter.api.Test;

public class JacksonFormatTest {
  @Test
  public void testParsePass() throws JsonProcessingException {
    JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    Name name =
        jsonMapper.readValue(
            "{\n" + "  \"first_name\":" + " \"John\",\n" + "  \"last_name\": \"Doe\"\n" + "}",
            Name.class);

    assertEquals(name, Name.newBuilder().setFirstName("John").setLastName("Doe").build());
  }

  @Test
  public void testParsePassCamel() throws JsonProcessingException {
    JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    Name name =
        jsonMapper.readValue(
            "{\n" + "  \"firstName\":" + " \"John\",\n" + "  \"lastName\": \"Doe\"\n" + "}",
            Name.class);

    assertEquals(name, Name.newBuilder().setFirstName("John").setLastName("Doe").build());
  }

  @Test
  public void testParseUnknownPropertyFail() throws JsonProcessingException {
    JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    assertThrows(
        JsonMappingException.class,
        () ->
            jsonMapper.readValue(
                "{\n"
                    + "  \"first_name\":"
                    + " \"John\",\n"
                    + "  \"last_name\": \"Doe\",\n"
                    + "  \"blah\": \"Blah\"\n"
                    + "}",
                Name.class));
  }

  @Test
  public void testParseUnknownPropertyPass() throws JsonProcessingException {
    JsonMapper jsonMapper =
        JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndAddModules()
            .build();

    Name name =
        jsonMapper.readValue(
            "{\n"
                + "  \"first_name\":"
                + " \"John\",\n"
                + "  \"last_name\": \"Doe\",\n"
                + "  \"blah\": \"Blah\"\n"
                + "}",
            Name.class);

    assertEquals(name, Name.newBuilder().setFirstName("John").setLastName("Doe").build());
  }

  @Test
  public void testConvertValue() {
    JsonMapper jsonMapper =
        JsonMapper.builder()
            .addModule(JavaProtoModule.builder().preservingProtoFieldNames().build())
            .build();

    Name name = Name.newBuilder().setFirstName("John").setLastName("Doe").build();
    NameJava nameJava = new NameJava("John", null, "Doe", null, null);

    NameJava parsed = jsonMapper.convertValue(name, NameJava.class);
    assertEquals(nameJava, parsed);

    Name parsedName = jsonMapper.convertValue(nameJava, Name.class);
    assertEquals(name, parsedName);
  }

  @Test
  public void testPrintMap() throws JsonProcessingException {
    JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    MapMessage mapMessage = MapMessage.newBuilder().putValues("hello", "world")
        .putNames("name", Name.newBuilder().setFirstName("John").setLastName("Doe").build())
        .build();
    System.out.println(jsonMapper.writeValueAsString(mapMessage));
  }
}
