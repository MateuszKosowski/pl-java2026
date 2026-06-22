plugins {
	java
	jacoco
	id("org.springframework.boot") version "3.5.11"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.sonarqube") version "7.2.3.7755"
	// Performance testing (criterion #3). Compile with :ai-service:gatlingClasses,
	// run a load test against a live instance with :ai-service:gatlingRun.
	id("io.gatling.gradle") version "3.13.5"
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
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-security")
        implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")
        implementation("org.springframework.cloud:spring-cloud-starter-config")
        implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
        implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
	// ONNX Runtime Java API — CPU only, no GPU
	implementation("com.microsoft.onnxruntime:onnxruntime:1.21.1")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// BDD testing (criterion #1) — Cucumber + JUnit Platform. AssertJ ships with starter-test.
	testImplementation(platform("io.cucumber:cucumber-bom:7.20.1"))
	testImplementation("io.cucumber:cucumber-java")
	testImplementation("io.cucumber:cucumber-spring")
	testImplementation("io.cucumber:cucumber-junit-platform-engine")
	testImplementation("org.junit.platform:junit-platform-suite")

	// Architecture testing (criterion #4) — ArchUnit.
	testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
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
