package org.zzzyxwvut.readywriter.fd.internal;

import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.annotation.Namable;

/** A file-descriptor-writing {@code ReadyWriter}. */
@Namable("org.zzzyxwvut.readywriter.FileDescriptorWriterProvider")
public interface FileDescriptorWriterProvider extends ReadyWriter
{
	/**
	 * Creates an instance of this service provider. It is set to write to
	 * the standard output channel, using the UTF-8 character set.
	 *
	 * @return an instance of a service provider
	 * @see java.lang.System#out
	 * @see java.util.ServiceLoader.Provider#type()
	 * @see org.zzzyxwvut.readywriter.service.Lookup#names()
	 * @see org.zzzyxwvut.readywriter.service.FileDescriptorWriterVisitor
	 */
	static FileDescriptorWriterProvider provider()
	{
		return new FileDescriptorWriter();
	}
}
