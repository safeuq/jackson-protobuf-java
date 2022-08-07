plugins {
  java
}

repositories {
  mavenCentral()
}

dependencies {
  val protobufVersion = "3.21.4"
  val guavaVersion = "31.0.1-jre"
  implementation("com.google.protobuf:protobuf-java:$protobufVersion")
  implementation("com.google.guava:guava:$guavaVersion")

  implementation("com.google.errorprone:error_prone_annotations:2.5.1")
  implementation("com.google.j2objc:j2objc-annotations:1.3")
  implementation("com.google.code.findbugs:jsr305:3.0.2")
  implementation("com.google.code.gson:gson:2.8.9")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.13.2.1")

  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("com.google.guava:guava-testlib:$guavaVersion")
  testImplementation("org.mockito:mockito-core")
  testImplementation("com.google.truth:truth")
}