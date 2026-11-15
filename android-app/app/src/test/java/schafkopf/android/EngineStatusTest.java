package schafkopf.android;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EngineStatusTest {
    @Test
    public void invokesTheExistingRuleEngine() {
        assertTrue(EngineStatus.describe().contains("14"));
    }
}