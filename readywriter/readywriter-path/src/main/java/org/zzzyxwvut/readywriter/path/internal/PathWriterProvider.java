package org.zzzyxwvut.readywriter.path.internal;

import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.annotation.Namable;

/** A path-writing {@code ReadyWriter}. */
@Namable("org.zzzyxwvut.readywriter.PathWriterProvider")
public interface PathWriterProvider extends ReadyWriter
{
	/**
	 * Creates an instance of this service provider. It is set to write to
	 * a new templet-named path, appending to the end of a file and using
	 * the UTF-8 character set and the big-endian byte order.
	 *
	 * @return an instance of this service provider
	 * @see java.util.ServiceLoader.Provider#type()
	 * @see org.zzzyxwvut.readywriter.service.Lookup#names()
	 * @see org.zzzyxwvut.readywriter.service.PathWriterVisitor
	 */
	static PathWriterProvider provider()
	{
		return new PathWriter();
	}
}
