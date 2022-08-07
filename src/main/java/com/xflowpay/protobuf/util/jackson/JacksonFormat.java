// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
// https://developers.google.com/protocol-buffers/
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.xflowpay.protobuf.util.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.FieldMaskUtil;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.Timestamps;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class to convert protobuf messages to/from the <a href=
 * "https://developers.google.com/protocol-buffers/docs/proto3#json">Proto3 JSON format.</a> Only
 * proto3 features are supported. Proto2 only features such as extensions and unknown fields are
 * discarded in the conversion. That is, when converting proto2 messages to JSON format, extensions
 * and unknown fields are treated as if they do not exist. This applies to proto2 messages embedded
 * in proto3 messages as well.
 */
public class JacksonFormat {
  private static final Logger logger = Logger.getLogger(JacksonFormat.class.getName());

  private JacksonFormat() {}

  /** Creates a {@link Printer} with default configurations. */
  public static PrinterBuilder printerBuilder() {
    return new PrinterBuilder();
  }

  /** A Printer converts a protobuf message to the proto3 JSON format. */
  public static class Printer {
    private final TypeRegistry registry;
    private final JsonFormat.TypeRegistry oldRegistry;
    // NOTE: There are 3 states for these *defaultValueFields variables:
    // 1) Default - alwaysOutput is false & including is empty set. Fields only output if they are
    //    set to non-default values.
    // 2) No-args includingDefaultValueFields() called - alwaysOutput is true & including is
    //    irrelevant (but set to empty set). All fields are output regardless of their values.
    // 3) includingDefaultValueFields(Set<FieldDescriptor>) called - alwaysOutput is false &
    //    including is set to the specified set. Fields in that set are always output & fields not
    //    in that set are only output if set to non-default values.
    private final boolean alwaysOutputDefaultValueFields;
    private final Set<FieldDescriptor> includingDefaultValueFields;
    private final boolean preservingProtoFieldNames;
    private final boolean printingEnumsAsInts;
    private final boolean sortingMapKeys;
    private final boolean treatDefaultFieldsAsNull;
    private final boolean omitNullFields;

    private Printer(
        TypeRegistry registry,
        JsonFormat.TypeRegistry oldRegistry,
        boolean alwaysOutputDefaultValueFields,
        Set<FieldDescriptor> includingDefaultValueFields,
        boolean preservingProtoFieldNames,
        boolean printingEnumsAsInts,
        boolean sortingMapKeys,
        boolean treatDefaultFieldsAsNull,
        boolean omitNullFields) {
      this.registry = registry;
      this.oldRegistry = oldRegistry;
      this.alwaysOutputDefaultValueFields = alwaysOutputDefaultValueFields;
      this.includingDefaultValueFields = includingDefaultValueFields;
      this.preservingProtoFieldNames = preservingProtoFieldNames;
      this.printingEnumsAsInts = printingEnumsAsInts;
      this.sortingMapKeys = sortingMapKeys;
      this.treatDefaultFieldsAsNull = treatDefaultFieldsAsNull;
      this.omitNullFields = omitNullFields;
    }

    /**
     * Converts a protobuf message to the proto3 JSON format.
     *
     * @throws InvalidProtocolBufferException if the message contains Any types that can't be
     *     resolved
     * @throws IOException if writing to the output fails
     */
    public void appendTo(MessageOrBuilder message, JsonGenerator generator) throws IOException {
      new PrinterImpl(this, generator).print(message);
    }
  }

  /** Builder for {@link Printer} */
  public static class PrinterBuilder {
    private TypeRegistry registry;
    private JsonFormat.TypeRegistry oldRegistry;
    // NOTE: There are 3 states for these *defaultValueFields variables:
    // 1) Default - alwaysOutput is false & including is empty set. Fields only output if they are
    //    set to non-default values.
    // 2) No-args includingDefaultValueFields() called - alwaysOutput is true & including is
    //    irrelevant (but set to empty set). All fields are output regardless of their values.
    // 3) includingDefaultValueFields(Set<FieldDescriptor>) called - alwaysOutput is false &
    //    including is set to the specified set. Fields in that set are always output & fields not
    //    in that set are only output if set to non-default values.
    private boolean alwaysOutputDefaultValueFields;
    private Set<FieldDescriptor> includingDefaultValueFields;
    private boolean preservingProtoFieldNames;
    private boolean printingEnumsAsInts;
    private boolean sortingMapKeys;
    private boolean treatDefaultFieldsAsNull;
    private boolean omitNullFields;

    private PrinterBuilder() {
      this.registry = TypeRegistry.getEmptyTypeRegistry();
      this.oldRegistry = JsonFormat.TypeRegistry.getEmptyTypeRegistry();
      this.alwaysOutputDefaultValueFields = false;
      this.includingDefaultValueFields = Collections.emptySet();
      this.preservingProtoFieldNames = false;
      this.printingEnumsAsInts = false;
      this.sortingMapKeys = false;
      this.treatDefaultFieldsAsNull = false;
      this.omitNullFields = false;
    }

    /**
     * Creates a new {@link Printer} using the given registry. The new Printer clones all other
     * configurations from the current {@link Printer}.
     *
     * @throws IllegalArgumentException if a registry is already set
     */
    public PrinterBuilder usingTypeRegistry(JsonFormat.TypeRegistry oldRegistry) {
      if (this.oldRegistry != JsonFormat.TypeRegistry.getEmptyTypeRegistry()
          || this.registry != TypeRegistry.getEmptyTypeRegistry()) {
        throw new IllegalArgumentException("Only one registry is allowed.");
      }
      this.registry = TypeRegistry.getEmptyTypeRegistry();
      this.oldRegistry = oldRegistry;
      return this;
    }

    /**
     * Sets the given registry for the {@link PrinterImpl}.
     *
     * @throws IllegalArgumentException if a registry is already set
     */
    public PrinterBuilder usingTypeRegistry(TypeRegistry registry) {
      if (this.oldRegistry != JsonFormat.TypeRegistry.getEmptyTypeRegistry()
          || this.registry != TypeRegistry.getEmptyTypeRegistry()) {
        throw new IllegalArgumentException("Only one registry is allowed.");
      }
      this.registry = registry;
      return this;
    }

    /**
     * Modifier that will also print fields set to their defaults. Empty repeated fields and map
     * fields will be printed as well.
     */
    public PrinterBuilder includingDefaultValueFields() {
      checkUnsetIncludingDefaultValueFields();
      alwaysOutputDefaultValueFields = true;
      includingDefaultValueFields = Collections.emptySet();
      return this;
    }

    /** Modifier that prints enum field values as integers instead of as string. */
    public PrinterBuilder printingEnumsAsInts() {
      checkUnsetPrintingEnumsAsInts();
      printingEnumsAsInts = true;
      return this;
    }

    private void checkUnsetPrintingEnumsAsInts() {
      if (printingEnumsAsInts) {
        throw new IllegalStateException("JsonFormat printingEnumsAsInts has already been set.");
      }
    }

    /**
     * Modifier that will also print default-valued fields if their FieldDescriptors are found in
     * the supplied set. Empty repeated fields and map fields will be printed as well, if they
     * match. Call includingDefaultValueFields() with no args to unconditionally output all fields.
     */
    public PrinterBuilder includingDefaultValueFields(Set<FieldDescriptor> fieldsToAlwaysOutput) {
      Preconditions.checkArgument(
          null != fieldsToAlwaysOutput && !fieldsToAlwaysOutput.isEmpty(),
          "Non-empty Set must be supplied for includingDefaultValueFields.");

      checkUnsetIncludingDefaultValueFields();
      alwaysOutputDefaultValueFields = false;
      includingDefaultValueFields =
          Collections.unmodifiableSet(new HashSet<>(fieldsToAlwaysOutput));
      return this;
    }

    private void checkUnsetIncludingDefaultValueFields() {
      if (alwaysOutputDefaultValueFields || !includingDefaultValueFields.isEmpty()) {
        throw new IllegalStateException(
            "JsonFormat includingDefaultValueFields has already been set.");
      }
    }

    /**
     * Modifier that is configured to use the original proto field names as defined in the .proto
     * file rather than converting them to lowerCamelCase.
     */
    public PrinterBuilder preservingProtoFieldNames() {
      preservingProtoFieldNames = true;
      return this;
    }

    /**
     * Modifier that will sort the map keys in the JSON output.
     *
     * <p>Use of this modifier is discouraged. The generated JSON messages are equivalent with and
     * without this option set, but there are some corner use cases that demand a stable output,
     * while order of map keys is otherwise arbitrary.
     *
     * <p>The generated order is not well-defined and should not be depended on, but it's stable.
     */
    public PrinterBuilder sortingMapKeys() {
      sortingMapKeys = true;
      return this;
    }

    public PrinterBuilder treatDefaultFieldsAsNull() {
      treatDefaultFieldsAsNull = true;
      return this;
    }

    public PrinterBuilder omitNullFields() {
      omitNullFields = true;
      return this;
    }

    public Printer build() {
      return new Printer(
          registry,
          oldRegistry,
          alwaysOutputDefaultValueFields,
          includingDefaultValueFields,
          preservingProtoFieldNames,
          printingEnumsAsInts,
          sortingMapKeys,
          treatDefaultFieldsAsNull,
          omitNullFields);
    }
  }

  /** Creates a {@link Parser} with default configuration. */
  public static Parser parser() {
    return new Parser(
        TypeRegistry.getEmptyTypeRegistry(),
        JsonFormat.TypeRegistry.getEmptyTypeRegistry(),
        false,
        Parser.DEFAULT_RECURSION_LIMIT);
  }

  /** A Parser parses the proto3 JSON format into a protobuf message. */
  public static class Parser {
    private final TypeRegistry registry;
    private final JsonFormat.TypeRegistry oldRegistry;
    private final boolean ignoringUnknownFields;
    private final int recursionLimit;

    // The default parsing recursion limit is aligned with the proto binary parser.
    private static final int DEFAULT_RECURSION_LIMIT = 100;

    private Parser(
        TypeRegistry registry,
        JsonFormat.TypeRegistry oldRegistry,
        boolean ignoreUnknownFields,
        int recursionLimit) {
      this.registry = registry;
      this.oldRegistry = oldRegistry;
      this.ignoringUnknownFields = ignoreUnknownFields;
      this.recursionLimit = recursionLimit;
    }

    /**
     * Creates a new {@link Parser} using the given registry. The new Parser clones all other
     * configurations from this Parser.
     *
     * @throws IllegalArgumentException if a registry is already set
     */
    public Parser usingTypeRegistry(JsonFormat.TypeRegistry oldRegistry) {
      if (this.oldRegistry != JsonFormat.TypeRegistry.getEmptyTypeRegistry()
          || this.registry != TypeRegistry.getEmptyTypeRegistry()) {
        throw new IllegalArgumentException("Only one registry is allowed.");
      }
      return new Parser(
          TypeRegistry.getEmptyTypeRegistry(), oldRegistry, ignoringUnknownFields, recursionLimit);
    }

    /**
     * Creates a new {@link Parser} using the given registry. The new Parser clones all other
     * configurations from this Parser.
     *
     * @throws IllegalArgumentException if a registry is already set
     */
    public Parser usingTypeRegistry(TypeRegistry registry) {
      if (this.oldRegistry != JsonFormat.TypeRegistry.getEmptyTypeRegistry()
          || this.registry != TypeRegistry.getEmptyTypeRegistry()) {
        throw new IllegalArgumentException("Only one registry is allowed.");
      }
      return new Parser(registry, oldRegistry, ignoringUnknownFields, recursionLimit);
    }

    /**
     * Creates a new {@link Parser} configured to not throw an exception when an unknown field is
     * encountered. The new Parser clones all other configurations from this Parser.
     */
    public Parser ignoringUnknownFields() {
      return new Parser(this.registry, oldRegistry, true, recursionLimit);
    }

    /**
     * Parses from the proto3 JSON format into a protobuf message.
     *
     * @throws InvalidProtocolBufferException if the input is not valid JSON proto3 format or there
     *     are unknown fields in the input.
     */
    public void merge(TreeNode treeNode, Message.Builder builder)
        throws InvalidProtocolBufferException {
      new ParserTreeImpl(registry, oldRegistry, ignoringUnknownFields, recursionLimit)
          .merge(treeNode, builder);
    }

    // For testing only.
    Parser usingRecursionLimit(int recursionLimit) {
      return new Parser(registry, oldRegistry, ignoringUnknownFields, recursionLimit);
    }
  }

  /**
   * An interface for JSON formatting that can be used in combination with the
   * omittingInsignificantWhitespace() method
   */
  interface TextGenerator {
    void indent();

    void outdent();

    void print(final CharSequence text) throws IOException;
  }

  /** Format the JSON without indentation */
  private static final class CompactTextGenerator implements TextGenerator {
    private final Appendable output;

    private CompactTextGenerator(final Appendable output) {
      this.output = output;
    }

    /** ignored by compact printer */
    @Override
    public void indent() {}

    /** ignored by compact printer */
    @Override
    public void outdent() {}

    /** Print text to the output stream. */
    @Override
    public void print(final CharSequence text) throws IOException {
      output.append(text);
    }
  }

  /** A Printer converts protobuf messages to the proto3 JSON format. */
  private static final class PrinterImpl {
    private final TypeRegistry registry;
    private final JsonFormat.TypeRegistry oldRegistry;
    private final boolean alwaysOutputDefaultValueFields;
    private final Set<FieldDescriptor> includingDefaultValueFields;
    private final boolean preservingProtoFieldNames;
    private final boolean printingEnumsAsInts;
    private final boolean sortingMapKeys;
    private final boolean treatDefaultFieldsAsNull;
    private final boolean omitNullFields;
    private final JsonGenerator generator;

    PrinterImpl(@Nonnull Printer printer, JsonGenerator jsonGenerator) {
      this.registry = printer.registry;
      this.oldRegistry = printer.oldRegistry;
      this.alwaysOutputDefaultValueFields = printer.alwaysOutputDefaultValueFields;
      this.includingDefaultValueFields = printer.includingDefaultValueFields;
      this.preservingProtoFieldNames = printer.preservingProtoFieldNames;
      this.printingEnumsAsInts = printer.printingEnumsAsInts;
      this.sortingMapKeys = printer.sortingMapKeys;
      this.treatDefaultFieldsAsNull = printer.treatDefaultFieldsAsNull;
      this.omitNullFields = printer.omitNullFields;
      this.generator = jsonGenerator;
    }

    void print(MessageOrBuilder message) throws IOException {
      WellKnownTypePrinter specialPrinter =
          wellKnownTypePrinters.get(message.getDescriptorForType().getFullName());
      if (specialPrinter != null) {
        specialPrinter.print(this, message);
        return;
      }
      print(message, null);
    }

    private interface WellKnownTypePrinter {
      void print(PrinterImpl printer, MessageOrBuilder message) throws IOException;
    }

    private static final Map<String, WellKnownTypePrinter> wellKnownTypePrinters =
        buildWellKnownTypePrinters();

    private static Map<String, WellKnownTypePrinter> buildWellKnownTypePrinters() {
      Map<String, WellKnownTypePrinter> printers = new HashMap<String, WellKnownTypePrinter>();
      // Special-case Any.
      printers.put(
          Any.getDescriptor().getFullName(),
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printAny(message);
            }
          });
      // Special-case wrapper types.
      WellKnownTypePrinter wrappersPrinter =
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printWrapper(message);
            }
          };
      printers.put(BoolValue.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(Int32Value.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(UInt32Value.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(Int64Value.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(UInt64Value.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(StringValue.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(BytesValue.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(FloatValue.getDescriptor().getFullName(), wrappersPrinter);
      printers.put(DoubleValue.getDescriptor().getFullName(), wrappersPrinter);
      // Special-case Timestamp.
      printers.put(
          Timestamp.getDescriptor().getFullName(),
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printTimestamp(message);
            }
          });
      // Special-case Duration.
      printers.put(
          Duration.getDescriptor().getFullName(),
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printDuration(message);
            }
          });
      // Special-case FieldMask.
      printers.put(
          FieldMask.getDescriptor().getFullName(),
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printFieldMask(message);
            }
          });
      // Special-case Struct.
      printers.put(
          Struct.getDescriptor().getFullName(),
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printStruct(message);
            }
          });
      // Special-case Value.
      printers.put(
          Value.getDescriptor().getFullName(),
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printValue(message);
            }
          });
      // Special-case ListValue.
      printers.put(
          ListValue.getDescriptor().getFullName(),
          new WellKnownTypePrinter() {
            @Override
            public void print(PrinterImpl printer, MessageOrBuilder message) throws IOException {
              printer.printListValue(message);
            }
          });
      return printers;
    }

    /** Prints google.protobuf.Any */
    private void printAny(MessageOrBuilder message) throws IOException {
      if (Any.getDefaultInstance().equals(message)) {
        generator.writeStartObject();
        generator.writeEndObject();
        return;
      }
      Descriptor descriptor = message.getDescriptorForType();
      FieldDescriptor typeUrlField = descriptor.findFieldByName("type_url");
      FieldDescriptor valueField = descriptor.findFieldByName("value");
      // Validates type of the message. Note that we can't just cast the message
      // to com.google.protobuf.Any because it might be a DynamicMessage.
      if (typeUrlField == null
          || valueField == null
          || typeUrlField.getType() != FieldDescriptor.Type.STRING
          || valueField.getType() != FieldDescriptor.Type.BYTES) {
        throw new InvalidProtocolBufferException("Invalid Any type.");
      }
      String typeUrl = (String) message.getField(typeUrlField);
      Descriptor type = registry.getDescriptorForTypeUrl(typeUrl);
      if (type == null) {
        type = getDescriptorForTypeUrl(oldRegistry, typeUrl);
        if (type == null) {
          throw new InvalidProtocolBufferException("Cannot find type for url: " + typeUrl);
        }
      }
      ByteString content = (ByteString) message.getField(valueField);
      Message contentMessage =
          DynamicMessage.getDefaultInstance(type).getParserForType().parseFrom(content);
      WellKnownTypePrinter printer = wellKnownTypePrinters.get(getTypeName(typeUrl));
      if (printer != null) {
        // If the type is one of the well-known types, we use a special
        // formatting.
        generator.writeStartObject();
        generator.writeFieldName("@type");
        generator.writePOJO(typeUrl);

        generator.writeObjectFieldStart("value");
        printer.print(this, contentMessage);
        generator.writeEndObject();

        generator.writeEndObject();
      } else {
        // Print the content message instead (with a "@type" field added).
        print(contentMessage, typeUrl);
      }
    }

    /** Prints wrapper types (e.g., google.protobuf.Int32Value) */
    private void printWrapper(MessageOrBuilder message) throws IOException {
      Descriptor descriptor = message.getDescriptorForType();
      FieldDescriptor valueField = descriptor.findFieldByName("value");
      if (valueField == null) {
        throw new InvalidProtocolBufferException("Invalid Wrapper type.");
      }
      // When formatting wrapper types, we just print its value field instead of
      // the whole message.
      printSingleFieldValue(valueField, message.getField(valueField));
    }

    private ByteString toByteString(MessageOrBuilder message) {
      if (message instanceof Message) {
        return ((Message) message).toByteString();
      } else {
        return ((Message.Builder) message).build().toByteString();
      }
    }

    /** Prints google.protobuf.Timestamp */
    private void printTimestamp(MessageOrBuilder message) throws IOException {
      Timestamp value = Timestamp.parseFrom(toByteString(message));
      generator.writeString(Timestamps.toString(value));
    }

    /** Prints google.protobuf.Duration */
    private void printDuration(MessageOrBuilder message) throws IOException {
      Duration value = Duration.parseFrom(toByteString(message));
      generator.writeString(Durations.toString(value));
    }

    /** Prints google.protobuf.FieldMask */
    private void printFieldMask(MessageOrBuilder message) throws IOException {
      FieldMask value = FieldMask.parseFrom(toByteString(message));
      generator.writeString(FieldMaskUtil.toJsonString(value));
    }

    /** Prints google.protobuf.Struct */
    private void printStruct(MessageOrBuilder message) throws IOException {
      Descriptor descriptor = message.getDescriptorForType();
      FieldDescriptor field = descriptor.findFieldByName("fields");
      if (field == null) {
        throw new InvalidProtocolBufferException("Invalid Struct type.");
      }
      // Struct is formatted as a map object.
      printMapFieldValue(field, message.getField(field));
    }

    /** Prints google.protobuf.Value */
    private void printValue(MessageOrBuilder message) throws IOException {
      // For a Value message, only the value of the field is formatted.
      Map<FieldDescriptor, Object> fields = message.getAllFields();
      if (fields.isEmpty()) {
        // No value set.
        generator.writeNull();
        return;
      }
      // A Value message can only have at most one field set (it only contains
      // an oneof).
      if (fields.size() != 1) {
        throw new InvalidProtocolBufferException("Invalid Value type.");
      }
      for (Map.Entry<FieldDescriptor, Object> entry : fields.entrySet()) {
        printSingleFieldValue(entry.getKey(), entry.getValue());
      }
    }

    /** Prints google.protobuf.ListValue */
    private void printListValue(MessageOrBuilder message) throws IOException {
      Descriptor descriptor = message.getDescriptorForType();
      FieldDescriptor field = descriptor.findFieldByName("values");
      if (field == null) {
        throw new InvalidProtocolBufferException("Invalid ListValue type.");
      }
      printRepeatedFieldValue(field, message.getField(field));
    }

    /** Prints a regular message with an optional type URL. */
    private void print(MessageOrBuilder message, @Nullable String typeUrl) throws IOException {
      generator.writeStartObject();

      if (typeUrl != null) {
        generator.writeFieldName("@type");
        generator.writePOJO(typeUrl);
      }
      Map<FieldDescriptor, Object> fieldsToPrint = null;
      if (alwaysOutputDefaultValueFields || !includingDefaultValueFields.isEmpty()) {
        fieldsToPrint = new TreeMap<FieldDescriptor, Object>(message.getAllFields());
        for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
          if (field.isOptional()) {
            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE
                && !message.hasField(field)) {
              // Always skip empty optional message fields. If not we will recurse indefinitely if
              // a message has itself as a sub-field.
              continue;
            }
            OneofDescriptor oneof = field.getContainingOneof();
            if (oneof != null && !message.hasField(field)) {
              // Skip all oneof fields except the one that is actually set
              continue;
            }
          }
          if (!fieldsToPrint.containsKey(field)
              && (alwaysOutputDefaultValueFields || includingDefaultValueFields.contains(field))) {
            fieldsToPrint.put(field, message.getField(field));
          }
        }
      } else {
        fieldsToPrint = message.getAllFields();
      }
      for (Map.Entry<FieldDescriptor, Object> field : fieldsToPrint.entrySet()) {
        printField(message, field.getKey(), field.getValue());
      }
      generator.writeEndObject();
    }

    private void printField(MessageOrBuilder message, FieldDescriptor field, Object value)
        throws IOException {
      if (field.isMapField()) {
        printFieldName(field);
        printMapFieldValue(field, value);
      } else if (field.isRepeated()) {
        printFieldName(field);
        printRepeatedFieldValue(field, value);
      } else if (!message.hasField(field) && treatDefaultFieldsAsNull) {
        if (!omitNullFields) {
          printFieldName(field);
          printNullValue();
        }
      } else {
        printFieldName(field);
        printSingleFieldValue(field, value);
      }
    }

    private void printFieldName(FieldDescriptor field) throws IOException {
      if (preservingProtoFieldNames) {
        generator.writeFieldName(field.getName());
      } else {
        generator.writeFieldName(field.getJsonName());
      }
    }

    @SuppressWarnings("rawtypes")
    private void printRepeatedFieldValue(FieldDescriptor field, Object value) throws IOException {
      generator.writeStartArray();
      for (Object element : (List) value) {
        printSingleFieldValue(field, element);
      }
      generator.writeEndArray();
    }

    private void printMapFieldValue(FieldDescriptor field, Object value) throws IOException {
      Descriptor type = field.getMessageType();
      FieldDescriptor keyField = type.findFieldByName("key");
      FieldDescriptor valueField = type.findFieldByName("value");
      if (keyField == null || valueField == null) {
        throw new InvalidProtocolBufferException("Invalid map field.");
      }
      generator.writeStartObject();

      @SuppressWarnings("unchecked") // Object guaranteed to be a List for a map field.
      Collection<Object> elements = (List<Object>) value;
      if (sortingMapKeys && !elements.isEmpty()) {
        Comparator<Object> cmp = null;
        if (keyField.getType() == FieldDescriptor.Type.STRING) {
          cmp =
              new Comparator<Object>() {
                @Override
                public int compare(final Object o1, final Object o2) {
                  ByteString s1 = ByteString.copyFromUtf8((String) o1);
                  ByteString s2 = ByteString.copyFromUtf8((String) o2);
                  return ByteString.unsignedLexicographicalComparator().compare(s1, s2);
                }
              };
        }
        TreeMap<Object, Object> tm = new TreeMap<>(cmp);
        for (Object element : elements) {
          Message entry = (Message) element;
          Object entryKey = entry.getField(keyField);
          tm.put(entryKey, element);
        }
        elements = tm.values();
      }

      for (Object element : elements) {
        Message entry = (Message) element;
        Object entryKey = entry.getField(keyField);
        Object entryValue = entry.getField(valueField);
        // Key fields are always double-quoted.
        printSingleFieldValue(keyField, entryKey, true);
        printSingleFieldValue(valueField, entryValue);
      }
      generator.writeEndObject();
    }

    private void printSingleFieldValue(FieldDescriptor field, Object value) throws IOException {
      printSingleFieldValue(field, value, false);
    }

    private void printNullValue() throws IOException {
      generator.writeNull();
    }

    /**
     * Prints a field's value in the proto3 JSON format.
     *
     * @param alwaysWithQuotes whether to always add double-quotes to primitive types
     */
    private void printSingleFieldValue(
        final FieldDescriptor field, final Object value, boolean alwaysWithQuotes)
        throws IOException {
      switch (field.getType()) {
        case INT32:
        case SINT32:
        case SFIXED32:
          if (alwaysWithQuotes) {
            generator.writeString(((Integer) value).toString());
          } else {
            generator.writeNumber(((Integer) value));
          }
          break;

        case INT64:
        case SINT64:
        case SFIXED64:
          generator.writeString(((Long) value).toString());
          break;

        case BOOL:
          Boolean booleanValue = ((Boolean) value);
          if (alwaysWithQuotes) {
            generator.writeString(booleanValue.toString());
          } else {
            generator.writeBoolean(booleanValue);
          }
          break;

        case FLOAT:
          Float floatValue = (Float) value;
          if (floatValue.isNaN()) {
            generator.writeString("NaN");
          } else if (floatValue.isInfinite()) {
            if (floatValue < 0) {
              generator.writeString("\"-Infinity\"");
            } else {
              generator.writeString("\"Infinity\"");
            }
          } else {
            if (alwaysWithQuotes) {
              generator.writeString(floatValue.toString());
            } else {
              generator.writeNumber(floatValue);
            }
          }
          break;

        case DOUBLE:
          Double doubleValue = (Double) value;
          if (doubleValue.isNaN()) {
            generator.writeString("NaN");
          } else if (doubleValue.isInfinite()) {
            if (doubleValue < 0) {
              generator.writeString("\"-Infinity\"");
            } else {
              generator.writeString("\"Infinity\"");
            }
          } else {
            if (alwaysWithQuotes) {
              generator.writeString(doubleValue.toString());
            } else {
              generator.writeNumber(doubleValue);
            }
          }
          break;

        case UINT32:
        case FIXED32:
          String unsignedString = unsignedToString((Integer) value);
          if (alwaysWithQuotes) {
            generator.writeString(unsignedString);
          } else {
            generator.writeNumber(unsignedString);
          }
          break;

        case UINT64:
        case FIXED64:
          generator.writeString(unsignedToString((Long) value));
          break;

        case STRING:
          generator.writePOJO(value);
          break;

        case BYTES:
          generator.writeString(BaseEncoding.base64().encode(((ByteString) value).toByteArray()));
          break;

        case ENUM:
          // Special-case google.protobuf.NullValue (it's an Enum).
          if (field.getEnumType().getFullName().equals("google.protobuf.NullValue")) {
            // No matter what value it contains, we always print it as "null".

            if (alwaysWithQuotes) {
              generator.writeString("null");
            } else {
              generator.writeNull();
            }
          } else {
            if (printingEnumsAsInts || ((EnumValueDescriptor) value).getIndex() == -1) {
              generator.writeNumber(String.valueOf(((EnumValueDescriptor) value).getNumber()));
            } else {
              generator.writeString(((EnumValueDescriptor) value).getName());
            }
          }
          break;

        case MESSAGE:
        case GROUP:
          print((Message) value);
          break;
      }
    }
  }

  /** Convert an unsigned 32-bit integer to a string. */
  private static String unsignedToString(final int value) {
    if (value >= 0) {
      return Integer.toString(value);
    } else {
      return Long.toString(value & 0x00000000FFFFFFFFL);
    }
  }

  /** Convert an unsigned 64-bit integer to a string. */
  private static String unsignedToString(final long value) {
    if (value >= 0) {
      return Long.toString(value);
    } else {
      // Pull off the most-significant bit so that BigInteger doesn't think
      // the number is negative, then set it again using setBit().
      return BigInteger.valueOf(value & Long.MAX_VALUE).setBit(Long.SIZE - 1).toString();
    }
  }

  private static Descriptor getDescriptorForTypeUrl(
      JsonFormat.TypeRegistry oldRegistry, String typeUrl) throws InvalidProtocolBufferException {
    return oldRegistry.find(getTypeName(typeUrl));
  }

  private static String getTypeName(String typeUrl) throws InvalidProtocolBufferException {
    String[] parts = typeUrl.split("/");
    if (parts.length == 1) {
      throw new InvalidProtocolBufferException("Invalid type url found: " + typeUrl);
    }
    return parts[parts.length - 1];
  }

  public static class ParserTreeImpl {
    private final TypeRegistry registry;
    private final JsonFormat.TypeRegistry oldRegistry;
    private final boolean ignoringUnknownFields;
    private final int recursionLimit;
    private int currentDepth;

    ParserTreeImpl(
        TypeRegistry registry,
        JsonFormat.TypeRegistry oldRegistry,
        boolean ignoreUnknownFields,
        int recursionLimit) {
      this.registry = registry;
      this.oldRegistry = oldRegistry;
      this.ignoringUnknownFields = ignoreUnknownFields;
      this.recursionLimit = recursionLimit;
      this.currentDepth = 0;
    }

    private interface WellKnownTypeParser {
      void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
          throws InvalidProtocolBufferException;
    }

    private static final Map<String, WellKnownTypeParser> wellKnownTypeParsers =
        buildWellKnownTypeParsers();

    private static Map<String, WellKnownTypeParser> buildWellKnownTypeParsers() {
      Map<String, WellKnownTypeParser> parsers = new HashMap<String, WellKnownTypeParser>();
      // Special-case Any.
      parsers.put(
          Any.getDescriptor().getFullName(),
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeAny(json, builder);
            }
          });
      // Special-case wrapper types.
      WellKnownTypeParser wrappersPrinter =
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeWrapper(json, builder);
            }
          };
      parsers.put(BoolValue.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(Int32Value.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(UInt32Value.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(Int64Value.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(UInt64Value.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(StringValue.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(BytesValue.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(FloatValue.getDescriptor().getFullName(), wrappersPrinter);
      parsers.put(DoubleValue.getDescriptor().getFullName(), wrappersPrinter);
      // Special-case Timestamp.
      parsers.put(
          Timestamp.getDescriptor().getFullName(),
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeTimestamp(json, builder);
            }
          });
      // Special-case Duration.
      parsers.put(
          Duration.getDescriptor().getFullName(),
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeDuration(json, builder);
            }
          });
      // Special-case FieldMask.
      parsers.put(
          FieldMask.getDescriptor().getFullName(),
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeFieldMask(json, builder);
            }
          });
      // Special-case Struct.
      parsers.put(
          Struct.getDescriptor().getFullName(),
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeStruct(json, builder);
            }
          });
      // Special-case ListValue.
      parsers.put(
          ListValue.getDescriptor().getFullName(),
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeListValue(json, builder);
            }
          });
      // Special-case Value.
      parsers.put(
          Value.getDescriptor().getFullName(),
          new WellKnownTypeParser() {
            @Override
            public void merge(ParserTreeImpl parser, TreeNode json, Message.Builder builder)
                throws InvalidProtocolBufferException {
              parser.mergeValue(json, builder);
            }
          });
      return parsers;
    }

    public void merge(TreeNode treeNode, Message.Builder builder)
        throws InvalidProtocolBufferException {
      WellKnownTypeParser specialParser =
          wellKnownTypeParsers.get(builder.getDescriptorForType().getFullName());
      if (specialParser != null) {
        specialParser.merge(this, treeNode, builder);
        return;
      }
      mergeMessage(treeNode, builder, false);
    }

    // Maps from camel-case field names to FieldDescriptor.
    private final Map<Descriptor, Map<String, FieldDescriptor>> fieldNameMaps =
        new HashMap<Descriptor, Map<String, FieldDescriptor>>();

    private Map<String, FieldDescriptor> getFieldNameMap(Descriptor descriptor) {
      if (!fieldNameMaps.containsKey(descriptor)) {
        Map<String, FieldDescriptor> fieldNameMap = new HashMap<String, FieldDescriptor>();
        for (FieldDescriptor field : descriptor.getFields()) {
          fieldNameMap.put(field.getName(), field);
          fieldNameMap.put(field.getJsonName(), field);
        }
        fieldNameMaps.put(descriptor, fieldNameMap);
        return fieldNameMap;
      }
      return fieldNameMaps.get(descriptor);
    }

    private void mergeMessage(TreeNode json, Message.Builder builder, boolean skipTypeUrl)
        throws InvalidProtocolBufferException {
      if (!(json instanceof ObjectNode)) {
        throw new InvalidProtocolBufferException("Expect message object but got: " + json);
      }
      ObjectNode object = (ObjectNode) json;
      Map<String, FieldDescriptor> fieldNameMap = getFieldNameMap(builder.getDescriptorForType());

      var iterator = object.fields();
      while (iterator.hasNext()) {
        Map.Entry<String, JsonNode> entry = iterator.next();
        String key = entry.getKey();
        JsonNode value = entry.getValue();
        if (skipTypeUrl && key.equals("@type")) {
          continue;
        }
        FieldDescriptor field = fieldNameMap.get(key);
        if (field == null) {
          if (ignoringUnknownFields) {
            continue;
          }
          throw new InvalidProtocolBufferException(
              "Cannot find field: "
                  + key
                  + " in message "
                  + builder.getDescriptorForType().getFullName());
        }
        mergeField(field, value, builder);
      }
    }

    private void mergeAny(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      Descriptor descriptor = builder.getDescriptorForType();
      FieldDescriptor typeUrlField = descriptor.findFieldByName("type_url");
      FieldDescriptor valueField = descriptor.findFieldByName("value");
      // Validates type of the message. Note that we can't just cast the message
      // to com.google.protobuf.Any because it might be a DynamicMessage.
      if (typeUrlField == null
          || valueField == null
          || typeUrlField.getType() != FieldDescriptor.Type.STRING
          || valueField.getType() != FieldDescriptor.Type.BYTES) {
        throw new InvalidProtocolBufferException("Invalid Any type.");
      }

      if (!(json instanceof ObjectNode)) {
        throw new InvalidProtocolBufferException("Expect message object but got: " + json);
      }
      ObjectNode object = (ObjectNode) json;
      if (object.isEmpty()) {
        return; // builder never modified, so it will end up building the default instance of Any
      }
      JsonNode typeUrlElement = object.get("@type");
      if (typeUrlElement == null) {
        throw new InvalidProtocolBufferException("Missing type url when parsing: " + json);
      }
      String typeUrl = typeUrlElement.asText();
      Descriptor contentType = registry.getDescriptorForTypeUrl(typeUrl);
      if (contentType == null) {
        contentType = getDescriptorForTypeUrl(oldRegistry, typeUrl);
        if (contentType == null) {
          throw new InvalidProtocolBufferException("Cannot resolve type: " + typeUrl);
        }
      }
      builder.setField(typeUrlField, typeUrl);
      Message.Builder contentBuilder =
          DynamicMessage.getDefaultInstance(contentType).newBuilderForType();
      WellKnownTypeParser specialParser = wellKnownTypeParsers.get(contentType.getFullName());
      if (specialParser != null) {
        JsonNode value = object.get("value");
        if (value != null) {
          specialParser.merge(this, value, contentBuilder);
        }
      } else {
        mergeMessage(json, contentBuilder, true);
      }
      builder.setField(valueField, contentBuilder.build().toByteString());
    }

    private void mergeFieldMask(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      FieldMask value = FieldMaskUtil.fromJsonString(((JsonNode) json).asText());
      builder.mergeFrom(value.toByteString());
    }

    private void mergeTimestamp(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      try {
        Timestamp value = Timestamps.parse(((JsonNode) json).asText());
        builder.mergeFrom(value.toByteString());
      } catch (ParseException | UnsupportedOperationException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Failed to parse timestamp: " + json);
        ex.initCause(e);
        throw ex;
      }
    }

    private void mergeDuration(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      try {
        Duration value = Durations.parse(((JsonNode) json).asText());
        builder.mergeFrom(value.toByteString());
      } catch (ParseException | UnsupportedOperationException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Failed to parse duration: " + json);
        ex.initCause(e);
        throw ex;
      }
    }

    private void mergeStruct(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      Descriptor descriptor = builder.getDescriptorForType();
      FieldDescriptor field = descriptor.findFieldByName("fields");
      if (field == null) {
        throw new InvalidProtocolBufferException("Invalid Struct type.");
      }
      mergeMapField(field, json, builder);
    }

    private void mergeListValue(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      Descriptor descriptor = builder.getDescriptorForType();
      FieldDescriptor field = descriptor.findFieldByName("values");
      if (field == null) {
        throw new InvalidProtocolBufferException("Invalid ListValue type.");
      }
      mergeRepeatedField(field, json, builder);
    }

    private void mergeValue(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      Descriptor type = builder.getDescriptorForType();
      if (json instanceof ValueNode) {
        ValueNode primitive = (ValueNode) json;
        if (primitive.isBoolean()) {
          builder.setField(type.findFieldByName("bool_value"), primitive.asBoolean());
        } else if (primitive.isNumber()) {
          builder.setField(type.findFieldByName("number_value"), primitive.asDouble());
        } else if (primitive.isNull()) {
          builder.setField(
              type.findFieldByName("null_value"), NullValue.NULL_VALUE.getValueDescriptor());
        } else {
          builder.setField(type.findFieldByName("string_value"), primitive.asText());
        }
      } else if (json instanceof ObjectNode) {
        FieldDescriptor field = type.findFieldByName("struct_value");
        Message.Builder structBuilder = builder.newBuilderForField(field);
        merge(json, structBuilder);
        builder.setField(field, structBuilder.build());
      } else if (json instanceof ArrayNode) {
        FieldDescriptor field = type.findFieldByName("list_value");
        Message.Builder listBuilder = builder.newBuilderForField(field);
        merge(json, listBuilder);
        builder.setField(field, listBuilder.build());
      } else {
        throw new IllegalStateException("Unexpected json data: " + json);
      }
    }

    private void mergeWrapper(TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      Descriptor type = builder.getDescriptorForType();
      FieldDescriptor field = type.findFieldByName("value");
      if (field == null) {
        throw new InvalidProtocolBufferException("Invalid wrapper type: " + type.getFullName());
      }
      builder.setField(field, parseFieldValue(field, json, builder));
    }

    private void mergeField(FieldDescriptor field, TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      if (field.isRepeated()) {
        if (builder.getRepeatedFieldCount(field) > 0) {
          throw new InvalidProtocolBufferException(
              "Field " + field.getFullName() + " has already been set.");
        }
      } else {
        if (builder.hasField(field)) {
          throw new InvalidProtocolBufferException(
              "Field " + field.getFullName() + " has already been set.");
        }
      }
      if (field.isRepeated() && json instanceof NullNode) {
        // We allow "null" as value for all field types and treat it as if the
        // field is not present.
        return;
      }
      if (field.isMapField()) {
        mergeMapField(field, json, builder);
      } else if (field.isRepeated()) {
        mergeRepeatedField(field, json, builder);
      } else if (field.getContainingOneof() != null) {
        mergeOneofField(field, json, builder);
      } else {
        Object value = parseFieldValue(field, json, builder);
        if (value != null) {
          // A field interpreted as "null" is means it's treated as absent.
          builder.setField(field, value);
        }
      }
    }

    private void mergeMapField(FieldDescriptor field, TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      if (!(json instanceof ObjectNode)) {
        throw new InvalidProtocolBufferException("Expect a map object but found: " + json);
      }
      Descriptor type = field.getMessageType();
      FieldDescriptor keyField = type.findFieldByName("key");
      FieldDescriptor valueField = type.findFieldByName("value");
      if (keyField == null || valueField == null) {
        throw new InvalidProtocolBufferException("Invalid map field: " + field.getFullName());
      }
      ObjectNode object = (ObjectNode) json;
      Iterator<Map.Entry<String, JsonNode>> iterator = object.fields();
      while (iterator.hasNext()) {
        Map.Entry<String, JsonNode> entry = iterator.next();
        Message.Builder entryBuilder = builder.newBuilderForField(field);
        Object key = parseFieldValue(keyField, TextNode.valueOf(entry.getKey()), entryBuilder);
        Object value = parseFieldValue(valueField, entry.getValue(), entryBuilder);
        if (value == null) {
          if (ignoringUnknownFields && valueField.getType() == Type.ENUM) {
            continue;
          } else {
            throw new InvalidProtocolBufferException("Map value cannot be null.");
          }
        }
        entryBuilder.setField(keyField, key);
        entryBuilder.setField(valueField, value);
        builder.addRepeatedField(field, entryBuilder.build());
      }
    }

    private void mergeOneofField(FieldDescriptor field, TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      Object value = parseFieldValue(field, json, builder);
      if (value == null) {
        // A field interpreted as "null" is means it's treated as absent.
        return;
      }
      if (builder.getOneofFieldDescriptor(field.getContainingOneof()) != null) {
        throw new InvalidProtocolBufferException(
            "Cannot set field "
                + field.getFullName()
                + " because another field "
                + builder.getOneofFieldDescriptor(field.getContainingOneof()).getFullName()
                + " belonging to the same oneof has already been set ");
      }
      builder.setField(field, value);
    }

    private void mergeRepeatedField(FieldDescriptor field, TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      if (!(json instanceof ArrayNode)) {
        throw new InvalidProtocolBufferException(
            "Expected an array for " + field.getName() + " but found " + json);
      }
      ArrayNode array = (ArrayNode) json;
      for (int i = 0; i < array.size(); ++i) {
        Object value = parseFieldValue(field, array.get(i), builder);
        if (value == null) {
          if (ignoringUnknownFields && field.getType() == Type.ENUM) {
            continue;
          } else {
            throw new InvalidProtocolBufferException(
                "Repeated field elements cannot be null in field: " + field.getFullName());
          }
        }
        builder.addRepeatedField(field, value);
      }
    }

    private int parseInt32(JsonNode json) throws InvalidProtocolBufferException {
      try {
        return Integer.parseInt(json.asText());
      } catch (RuntimeException e) {
        // Fall through.
      }
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      try {
        BigDecimal value = new BigDecimal(json.asText());
        return value.intValueExact();
      } catch (RuntimeException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Not an int32 value: " + json);
        ex.initCause(e);
        throw ex;
      }
    }

    private long parseInt64(JsonNode json) throws InvalidProtocolBufferException {
      try {
        return Long.parseLong(json.asText());
      } catch (RuntimeException e) {
        // Fall through.
      }
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      try {
        BigDecimal value = new BigDecimal(json.asText());
        return value.longValueExact();
      } catch (RuntimeException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Not an int64 value: " + json);
        ex.initCause(e);
        throw ex;
      }
    }

    private int parseUint32(JsonNode json) throws InvalidProtocolBufferException {
      try {
        long result = Long.parseLong(json.asText());
        if (result < 0 || result > 0xFFFFFFFFL) {
          throw new InvalidProtocolBufferException("Out of range uint32 value: " + json);
        }
        return (int) result;
      } catch (RuntimeException e) {
        // Fall through.
      }
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      try {
        BigDecimal decimalValue = new BigDecimal(json.asText());
        BigInteger value = decimalValue.toBigIntegerExact();
        if (value.signum() < 0 || value.compareTo(new BigInteger("FFFFFFFF", 16)) > 0) {
          throw new InvalidProtocolBufferException("Out of range uint32 value: " + json);
        }
        return value.intValue();
      } catch (RuntimeException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Not an uint32 value: " + json);
        ex.initCause(e);
        throw ex;
      }
    }

    private static final BigInteger MAX_UINT64 = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    private long parseUint64(JsonNode json) throws InvalidProtocolBufferException {
      try {
        BigDecimal decimalValue = new BigDecimal(json.asText());
        BigInteger value = decimalValue.toBigIntegerExact();
        if (value.compareTo(BigInteger.ZERO) < 0 || value.compareTo(MAX_UINT64) > 0) {
          throw new InvalidProtocolBufferException("Out of range uint64 value: " + json);
        }
        return value.longValue();
      } catch (RuntimeException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Not an uint64 value: " + json);
        ex.initCause(e);
        throw ex;
      }
    }

    private boolean parseBool(JsonNode json) throws InvalidProtocolBufferException {
      if (json.asText().equals("true")) {
        return true;
      }
      if (json.asText().equals("false")) {
        return false;
      }
      throw new InvalidProtocolBufferException("Invalid bool value: " + json);
    }

    private static final double EPSILON = 1e-6;

    private float parseFloat(JsonNode json) throws InvalidProtocolBufferException {
      if (json.asText().equals("NaN")) {
        return Float.NaN;
      } else if (json.asText().equals("Infinity")) {
        return Float.POSITIVE_INFINITY;
      } else if (json.asText().equals("-Infinity")) {
        return Float.NEGATIVE_INFINITY;
      }
      try {
        // We don't use Float.parseFloat() here because that function simply
        // accepts all double values. Here we parse the value into a Double
        // and do explicit range check on it.
        double value = Double.parseDouble(json.asText());
        // When a float value is printed, the printed value might be a little
        // larger or smaller due to precision loss. Here we need to add a bit
        // of tolerance when checking whether the float value is in range.
        if (value > Float.MAX_VALUE * (1.0 + EPSILON)
            || value < -Float.MAX_VALUE * (1.0 + EPSILON)) {
          throw new InvalidProtocolBufferException("Out of range float value: " + json);
        }
        return (float) value;
      } catch (RuntimeException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Not a float value: " + json);
        ex.initCause(e);
        throw e;
      }
    }

    private static final BigDecimal MORE_THAN_ONE = new BigDecimal(String.valueOf(1.0 + EPSILON));
    // When a float value is printed, the printed value might be a little
    // larger or smaller due to precision loss. Here we need to add a bit
    // of tolerance when checking whether the float value is in range.
    private static final BigDecimal MAX_DOUBLE =
        new BigDecimal(String.valueOf(Double.MAX_VALUE)).multiply(MORE_THAN_ONE);
    private static final BigDecimal MIN_DOUBLE =
        new BigDecimal(String.valueOf(-Double.MAX_VALUE)).multiply(MORE_THAN_ONE);

    private double parseDouble(JsonNode json) throws InvalidProtocolBufferException {
      if (json.asText().equals("NaN")) {
        return Double.NaN;
      } else if (json.asText().equals("Infinity")) {
        return Double.POSITIVE_INFINITY;
      } else if (json.asText().equals("-Infinity")) {
        return Double.NEGATIVE_INFINITY;
      }
      try {
        // We don't use Double.parseDouble() here because that function simply
        // accepts all values. Here we parse the value into a BigDecimal and do
        // explicit range check on it.
        BigDecimal value = new BigDecimal(json.asText());
        if (value.compareTo(MAX_DOUBLE) > 0 || value.compareTo(MIN_DOUBLE) < 0) {
          throw new InvalidProtocolBufferException("Out of range double value: " + json);
        }
        return value.doubleValue();
      } catch (RuntimeException e) {
        InvalidProtocolBufferException ex =
            new InvalidProtocolBufferException("Not a double value: " + json);
        ex.initCause(e);
        throw ex;
      }
    }

    private String parseString(JsonNode json) {
      return json.asText();
    }

    private ByteString parseBytes(JsonNode json) {
      try {
        return ByteString.copyFrom(BaseEncoding.base64().decode(json.asText()));
      } catch (IllegalArgumentException e) {
        return ByteString.copyFrom(BaseEncoding.base64Url().decode(json.asText()));
      }
    }

    @Nullable
    private EnumValueDescriptor parseEnum(EnumDescriptor enumDescriptor, JsonNode json)
        throws InvalidProtocolBufferException {
      String value = json.asText();
      EnumValueDescriptor result = enumDescriptor.findValueByName(value);
      if (result == null) {
        // Try to interpret the value as a number.
        try {
          int numericValue = parseInt32(json);
          if (enumDescriptor.getFile().getSyntax() == FileDescriptor.Syntax.PROTO3) {
            result = enumDescriptor.findValueByNumberCreatingIfUnknown(numericValue);
          } else {
            result = enumDescriptor.findValueByNumber(numericValue);
          }
        } catch (InvalidProtocolBufferException e) {
          // Fall through. This exception is about invalid int32 value we get from parseInt32() but
          // that's not the exception we want the user to see. Since result == null, we will throw
          // an exception later.
        }

        // todo(elharo): if we are ignoring unknown fields, shouldn't we still
        // throw InvalidProtocolBufferException for a non-numeric value here?
        if (result == null && !ignoringUnknownFields) {
          throw new InvalidProtocolBufferException(
              "Invalid enum value: " + value + " for enum type: " + enumDescriptor.getFullName());
        }
      }
      return result;
    }

    @Nullable
    private Object parseFieldValue(FieldDescriptor field, TreeNode json, Message.Builder builder)
        throws InvalidProtocolBufferException {
      if (json instanceof NullNode) {
        if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE
            && field.getMessageType().getFullName().equals(Value.getDescriptor().getFullName())) {
          // For every other type, "null" means absence, but for the special
          // Value message, it means the "null_value" field has been set.
          Value value = Value.newBuilder().setNullValueValue(0).build();
          return builder.newBuilderForField(field).mergeFrom(value.toByteString()).build();
        } else if (field.getJavaType() == FieldDescriptor.JavaType.ENUM
            && field.getEnumType().getFullName().equals(NullValue.getDescriptor().getFullName())) {
          // If the type of the field is a NullValue, then the value should be explicitly set.
          return field.getEnumType().findValueByNumber(0);
        }
        return null;
      } else if (json instanceof ObjectNode) {
        if (field.getType() != FieldDescriptor.Type.MESSAGE
            && field.getType() != FieldDescriptor.Type.GROUP) {
          // If the field type is primitive, but the json type is JsonObject rather than
          // JsonElement, throw a type mismatch error.
          throw new InvalidProtocolBufferException(
              String.format("Invalid value: %s for expected type: %s", json, field.getType()));
        }
      }
      switch (field.getType()) {
        case INT32:
        case SINT32:
        case SFIXED32:
          return parseInt32((JsonNode) json);

        case INT64:
        case SINT64:
        case SFIXED64:
          return parseInt64((JsonNode) json);

        case BOOL:
          return parseBool((JsonNode) json);

        case FLOAT:
          return parseFloat((JsonNode) json);

        case DOUBLE:
          return parseDouble((JsonNode) json);

        case UINT32:
        case FIXED32:
          return parseUint32((JsonNode) json);

        case UINT64:
        case FIXED64:
          return parseUint64((JsonNode) json);

        case STRING:
          return parseString((JsonNode) json);

        case BYTES:
          return parseBytes((JsonNode) json);

        case ENUM:
          return parseEnum(field.getEnumType(), (JsonNode) json);

        case MESSAGE:
        case GROUP:
          if (currentDepth >= recursionLimit) {
            throw new InvalidProtocolBufferException("Hit recursion limit.");
          }
          ++currentDepth;
          Message.Builder subBuilder = builder.newBuilderForField(field);
          merge(json, subBuilder);
          --currentDepth;
          return subBuilder.build();

        default:
          throw new InvalidProtocolBufferException("Invalid field type: " + field.getType());
      }
    }
  }
}
