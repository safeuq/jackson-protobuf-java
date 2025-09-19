package io.github.safeuq.proto.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.safeuq.proto.entities.Name;
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
  public void testUnknownPropertyFail() throws JsonProcessingException {
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
  public void testUnknownPropertyPass() throws JsonProcessingException {
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
}
