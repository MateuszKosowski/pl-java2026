plugins {
	java
	jacoco
	id("org.springframework.boot") version "3.5.11"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.sonarqube") version "7.2.3.7755"
	// Spring Cloud Contract Verifier — version aligned with the spring-cloud BOM (2025.0.1 manages 4.3.1).
	id("org.springframework.cloud.contract") version "4.3.1"
	`maven-publish`
}

group = "pl.zzpj"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.0.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")
	implementation("org.springframework.cloud:spring-cloud-starter-config")
	implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Architecture tests
	testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

	// Spring Cloud Contract — provider (verifier) + consumer (stub runner) sides.
	testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
	testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Spring Cloud Contract Verifier configuration.
// Generates provider-side tests (run via the `contractTest` task / source set) from the
// Groovy contracts in src/contractTest/resources/contracts. The base class wires RestAssuredMockMvc
// standalone against the real controller so no full app context / DB is required.
contracts {
	testFramework.set(org.springframework.cloud.contract.verifier.config.TestFramework.JUNIT5)
	baseClassForTests.set("pl.zzpj.subscription_service.contract.SubscriptionStatusContractBase")
}

// Publish the generated WireMock stubs jar to the local Maven repository so the consumer-side
// StubRunner test (StubsMode.LOCAL, which resolves from ~/.m2) can boot WireMock from it.
publishing {
	publications {
		create<MavenPublication>("stubs") {
			artifactId = "subscription-service"
			artifact(tasks.named("verifierStubsJar"))
		}
	}
}

// The consumer StubRunner test runs in the `test` task and needs the stubs available in ~/.m2
// before it executes.
tasks.named("test") {
	dependsOn("publishStubsPublicationToMavenLocal")
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		csv.required.set(false)
		html.required.set(true)
	}
}

tasks.named("sonar") {
	dependsOn(tasks.jacocoTestReport)
}
