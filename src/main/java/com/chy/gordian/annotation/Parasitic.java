package com.chy.gordian.annotation;

import com.chy.gordian.extend.Gordian;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Parasitic {

    Class<? extends Gordian>[] gordians();

    boolean factoryMode() default false;

}
