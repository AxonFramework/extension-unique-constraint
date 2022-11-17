package org.axonframework.extensions.uniqueconstraint;

public class ConstraintUnclaimedEvent {
    private String constraintKey;
    private String constraintValue;
    private String aggregateId;

    public ConstraintUnclaimedEvent() {
    }

    public ConstraintUnclaimedEvent(String constraintKey, String constraintValue, String aggregateId) {
        this.constraintKey = constraintKey;
        this.constraintValue = constraintValue;
        this.aggregateId = aggregateId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getConstraintValue() {
        return constraintValue;
    }

    public String getConstraintKey() {
        return constraintKey;
    }

    public void setConstraintKey(String constraintKey) {
        this.constraintKey = constraintKey;
    }

    public void setConstraintValue(String constraintValue) {
        this.constraintValue = constraintValue;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }
}
