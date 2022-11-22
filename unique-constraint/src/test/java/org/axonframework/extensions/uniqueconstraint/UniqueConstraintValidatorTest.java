package org.axonframework.extensions.uniqueconstraint;

import org.axonframework.common.AxonConfigurationException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UniqueConstraintValidatorTest {

    private final UniqueConstraintStore store = Mockito.mock(UniqueConstraintStore.class);
    private final UniqueConstraintValidator validator = UniqueConstraintValidator.builder()
                                                                                 .constraintStore(store).build();

    static class MyValueHolder {

        String myValue1 = "MyValue1";
        String myValue2 = "MyValue1";
        String myValue3 = "MyValue1";
    }

    @Test
    void doesNothingWhenValueRemainsTheSame() throws Exception {
        MyValueHolder myValueHolder = new MyValueHolder();
        validator.forAggregate(() -> "AGG_ID")
                 .addConstraint("MyConstraint", () -> myValueHolder.myValue1)
                 .checkForInterceptor(() -> {
                     return null;
                 });

        verifyNoInteractions(store);
    }

    @Test
    void claimsValueWhenValueIsSet() throws Exception {
        MyValueHolder myValueHolder = new MyValueHolder();
        validator.forAggregate(() -> "AGG_ID")
                 .addConstraint("MyConstraint", () -> myValueHolder.myValue1)
                 .checkForInterceptor(() -> {
                     myValueHolder.myValue1 = "MyValue2";
                     return null;
                 });

        verify(store).checkAndClaimValue("MyConstraint", "MyValue2", "AGG_ID");
    }

    @Test
    void claimsMultipleValuesWhenTheyAreChanged() throws Exception {
        MyValueHolder myValueHolder = new MyValueHolder();
        validator.forAggregate(() -> "AGG_ID")
                 .addConstraint("MyConstraint1", () -> myValueHolder.myValue1)
                 .addConstraint("MyConstraint2", () -> myValueHolder.myValue2)
                 .addConstraint("MyConstraint3", () -> myValueHolder.myValue3)
                 .checkForInterceptor(() -> {
                     myValueHolder.myValue1 = "MyValue2";
                     myValueHolder.myValue2 = "MyValue2";
                     return null;
                 });

        verify(store).checkAndClaimValue("MyConstraint1", "MyValue2", "AGG_ID");
        verify(store).checkAndClaimValue("MyConstraint2", "MyValue2", "AGG_ID");
        // Third is not changed, so should not be claimed
        verify(store, times(0)).checkAndClaimValue("MyConstraint3", "MyValue1", "AGG_ID");
        verify(store, times(0)).checkAndClaimValue("MyConstraint3", "MyValue2", "AGG_ID");
    }

    @Test
    void releasesValueWhenValueIsRemoved() throws Exception {
        MyValueHolder myValueHolder = new MyValueHolder();
        validator.forAggregate(() -> "AGG_ID")
                 .addConstraint("MyConstraint", () -> myValueHolder.myValue1)
                 .checkForInterceptor(() -> {
                     myValueHolder.myValue1 = null;
                     return null;
                 });

        verify(store).releaseClaimValue("MyConstraint", "MyValue1", "AGG_ID");
    }

    @Test
    void releasesAndClaimsValuesWhenValueIsChanges() throws Exception {
        MyValueHolder myValueHolder = new MyValueHolder();
        validator.forAggregate(() -> "AGG_ID")
                 .addConstraint("MyConstraint", () -> myValueHolder.myValue1)
                 .checkForInterceptor(() -> {
                     myValueHolder.myValue1 = "MyValue2";
                     return null;
                 });

        verify(store).releaseClaimValue("MyConstraint", "MyValue1", "AGG_ID");
        verify(store).checkAndClaimValue("MyConstraint", "MyValue2", "AGG_ID");
    }

    @Test
    void onCheckAlwaysChecksValue() throws Exception {
        MyValueHolder myValueHolder = new MyValueHolder();
        validator.forAggregate(() -> "AGG_ID")
                 .addConstraint("MyConstraint", () -> myValueHolder.myValue1)
                 .check();

        verify(store).checkAndClaimValue("MyConstraint", "MyValue1", "AGG_ID");
    }

    @Test
    void builderCannotBeBuiltWithoutStore() {
        UniqueConstraintValidator.Builder builder = UniqueConstraintValidator.builder();

        assertThrows(AxonConfigurationException.class, () -> {
            builder.build();
        });
    }

    @Test
    void builderThrowsExceptionOnNullConstraintStore() {
        UniqueConstraintValidator.Builder builder = UniqueConstraintValidator.builder();

        assertThrows(AxonConfigurationException.class, () -> {
            builder.constraintStore(null);
        });
    }
}
