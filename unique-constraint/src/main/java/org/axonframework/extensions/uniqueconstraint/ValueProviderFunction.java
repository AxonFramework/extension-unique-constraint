package org.axonframework.extensions.uniqueconstraint;

@FunctionalInterface
public interface ValueProviderFunction {
    String determineValue(Object value);
}
