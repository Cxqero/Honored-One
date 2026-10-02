package com.gojolimitless.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Puts a config field in a collapsible group inside its category. Applies to the field and all following fields until the next @Group. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Group {
    String value();
}
