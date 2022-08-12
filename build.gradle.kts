plugins {
  java
  application
  `maven-publish`
  signing
  id("com.diffplug.spotless") version "6.9.0"
}

group = "io.github.safeuq"
version = "0.1"

val ossrhUsername: String by project
val ossrhPassword: String by project

repositories {
  mavenCentral()
}

dependencies {
  val protobufVersion = "3.21.4"
  val guavaVersion = "31.0.1-jre"

  implementation("com.google.protobuf:protobuf-java:$protobufVersion")
  implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")
  implementation("com.google.guava:guava:$guavaVersion")

//  implementation("com.google.errorprone:error_prone_annotations:2.5.1")
//  implementation("com.google.j2objc:j2objc-annotations:1.3")
  implementation("com.google.code.findbugs:jsr305:3.0.2")
  compileOnly("com.fasterxml.jackson.core:jackson-databind:2.13.3")

  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("com.google.guava:guava-testlib:$guavaVersion")
//  testImplementation("org.mockito:mockito-core")
//  testImplementation("com.google.truth:truth")
}

spotless {
  format("default") {
    target("*.java")
    trimTrailingWhitespace()
    endWithNewline()
  }

  java {
    googleJavaFormat().reflowLongStrings()
  }
}

java {
  toolchain {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  withSourcesJar()
  withJavadocJar()
}

publishing {
  publications {
    create<MavenPublication>("library") {
      groupId = project.group.toString()
      artifactId = project.name
      version = project.version.toString()

      from(components["java"])

      pom {
        name set project.name
        description set "Library to serialize & deserialize protoc generated Java " +
            "objects to/from Jackson supported data-formats"
        url set "https://github.com/safeuq/jackson-protobuf-java"

        scm {
          connection set "scm:git:git://github.com/safeuq/jackson-protobuf-java.git"
          developerConnection set "scm:git:https://github.com/safeuq/jackson-protobuf-java.git"
          url set "https://github.com/safeuq/jackson-protobuf-java/tree/main"
        }

        licenses {
          license {
            name set "Apache License, Version 2.0"
            url set "http://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }

        developers {
          developer {
            id set "safeuq"
            name set "Safeuq Mohamed"
            email set "mohamedsafeuq@gmail.com"
          }
        }
      }

      repositories {
        maven {
          credentials {
            username = ossrhUsername
            password = ossrhPassword
          }

          url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        }
      }
    }
  }
}

signing {
  sign(publishing.publications["library"])
}

infix fun <T> Property<T>.set(value: T) = set(value)

