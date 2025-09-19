plugins {
  java
  application
  `maven-publish`
  signing
  id("com.diffplug.spotless") version "6.9.0"
  id("com.google.protobuf") version "0.9.4"
}

group = "io.github.safeuq"
version = "0.1"

val ossrhUsername: String by project
val ossrhPassword: String by project

repositories {
  mavenCentral()
}

dependencies {
  val protobufVersion = "3.23.4"
  val guavaVersion = "32.0.1-jre"
  implementation("com.google.protobuf:protobuf-java:$protobufVersion")
  implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")
  implementation("com.google.guava:guava:$guavaVersion")

//  implementation("com.google.errorprone:error_prone_annotations:2.5.1")
//  implementation("com.google.j2objc:j2objc-annotations:1.3")
  implementation("com.google.code.findbugs:jsr305:3.0.2")

  compileOnly("com.fasterxml.jackson.core:jackson-databind:2.15.1")
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.15.1")

  testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
  testImplementation("com.google.guava:guava-testlib:$guavaVersion")
//  testImplementation("org.mockito:mockito-core")
//  testImplementation("com.google.truth:truth")
}

spotless {
  java {
    toggleOffOn()
    googleJavaFormat().reflowLongStrings()
    targetExclude("build/**/*.*")
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

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.18.1"
  }
}

publishing {
  publications {
    create<MavenPublication>("library") {
      groupId = project.group.toString()
      artifactId = project.name
      version = project.version.toString()

      from(components["java"])

      pom {
        name = project.name
        description = "Library to serialize & deserialize protoc generated Java " +
                  "objects to/from Jackson supported data-formats"
        url = "https://github.com/safeuq/jackson-protobuf-java"

        scm {
          connection = "scm:git:git://github.com/safeuq/jackson-protobuf-java.git"
          developerConnection = "scm:git:https://github.com/safeuq/jackson-protobuf-java.git"
          url = "https://github.com/safeuq/jackson-protobuf-java/tree/main"
        }

        licenses {
          license {
            name = "Apache License, Version 2.0"
            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }

        developers {
          developer {
            id = "safeuq"
            name = "Safeuq Mohamed"
            email = "mohamedsafeuq@gmail.com"
          }
        }
      }

      repositories {
        maven {
          name = "sonatype-ossrh"

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

tasks {
  test {
    useJUnitPlatform()
  }
}
