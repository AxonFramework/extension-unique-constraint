package org.axonframework.extensions.uniqueconstraint;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class Sha256ConstraintKeyProviderTest {

    private final ConstraintKeyProvider provider = new Sha256ConstraintKeyProvider();

    @Test
    void providesStableValuesOnMultipleCalls() {
        String value1 = provider.determineValue("MyConstraint", "Value1");
        String value2 = provider.determineValue("MyConstraint", "Value1");

        assertEquals(value1, value2);
    }

    @Test
    void providesNonStableValuesOnMultipleCallsWithDifferentConstraintNames() {
        String value1 = provider.determineValue("MyConstraint", "Value1");
        String value2 = provider.determineValue("MyConstraint2", "Value1");

        assertNotEquals(value1, value2);
    }

    @Test
    void providesNonStableValuesOnMultipleCallsWithDifferentConstraintValues() {
        String value1 = provider.determineValue("MyConstraint", "Value1");
        String value2 = provider.determineValue("MyConstraint", "Value2");

        assertNotEquals(value1, value2);
    }
}
