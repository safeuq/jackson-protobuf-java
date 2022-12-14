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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;

public class JavaProtoModule extends Module {
  private final boolean treatDefaultFieldsAsIs;
  private final boolean preservingProtoFieldNames;
  private final TypeRegistry registry;
  private final JsonFormat.TypeRegistry oldRegistry;

  /**
   * No-arg constructor for registering the module as SPI provider. It is generally preferable to
   * use the {@link #builder()} to construct this module.
   */
  public JavaProtoModule() {
    this(false, false, null, null);
  }

  private JavaProtoModule(
      boolean treatDefaultFieldsAsIs,
      boolean preservingProtoFieldNames,
      TypeRegistry registry,
      JsonFormat.TypeRegistry oldRegistry) {
    this.treatDefaultFieldsAsIs = treatDefaultFieldsAsIs;
    this.preservingProtoFieldNames = preservingProtoFieldNames;
    this.registry = registry;
    this.oldRegistry = oldRegistry;
  }

  @Override
  public String getModuleName() {
    return getClass().getSimpleName();
  }

  @Override
  public Version version() {
    return new Version(0, 1, 0, null, "io.github.safeuq", "protobuf-jackson-util");
  }

  @Override
  public void setupModule(SetupContext context) {
    SimpleSerializers simpleSerializers = new SimpleSerializers();
    simpleSerializers.addSerializer(
        new MessageSerializer(
            treatDefaultFieldsAsIs, preservingProtoFieldNames, registry, oldRegistry));
    context.addSerializers(simpleSerializers);
    context.addDeserializers(new MessageDeserializers(registry, oldRegistry));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private boolean treatDefaultFieldsAsIs;
    private boolean preservingProtoFieldNames;
    private TypeRegistry registry;
    private JsonFormat.TypeRegistry oldRegistry;

    private Builder() {}

    public Builder treatDefaultFieldsAsIs() {
      this.treatDefaultFieldsAsIs = true;
      return this;
    }

    public Builder preservingProtoFieldNames() {
      this.preservingProtoFieldNames = true;
      return this;
    }

    public Builder usingTypeRegistry(TypeRegistry registry) {
      this.registry = registry;
      return this;
    }

    public Builder usingTypeRegistry(JsonFormat.TypeRegistry oldRegistry) {
      this.oldRegistry = oldRegistry;
      return this;
    }

    public JavaProtoModule build() {
      return new JavaProtoModule(
          treatDefaultFieldsAsIs, preservingProtoFieldNames, registry, oldRegistry);
    }
  }
}
