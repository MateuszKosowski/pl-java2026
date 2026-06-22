package pl.zzpj.ai_service.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * JUnit Platform Suite runner that lets {@code ./gradlew :ai-service:test} discover and
 * execute the Cucumber feature files under {@code src/test/resources/features}.
 *
 * <p>BDD test for issue #19 criterion #1 (Cucumber + AssertJ).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "pl.zzpj.ai_service.bdd")
public class RunCucumberTest {
}
