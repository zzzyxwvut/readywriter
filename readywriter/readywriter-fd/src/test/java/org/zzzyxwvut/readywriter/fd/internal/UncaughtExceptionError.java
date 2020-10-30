package org.zzzyxwvut.readywriter.fd.internal;

/** This class complements org.junit.jupiter.api.Assertions.fail. */
class UncaughtExceptionError extends OutOfMemoryError
{
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new {@code UncaughtExceptionError} object.
	 *
	 * @param message a detail message
	 */
	UncaughtExceptionError(String message)
	{
		super(message);
		super.setStackTrace(new StackTraceElement[0]);
	}
} /* See org.junit.platform.commons.util.BlacklistedExceptions */
