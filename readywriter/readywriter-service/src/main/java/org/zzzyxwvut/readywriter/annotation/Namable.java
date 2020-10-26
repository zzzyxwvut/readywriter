package org.zzzyxwvut.readywriter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** This annotation makes its targets bear arbitrary names. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Namable
{
	/**
	 * Returns an arbitrary name.
	 *
	 * @return an arbitrary name
	 */
	String value();
}
