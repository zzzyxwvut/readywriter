/**
 * Provides a file-descriptor-writing implementation of {@code ReadyWriter}.
 *
 * @provides org.zzzyxwvut.readywriter.ReadyWriter
 * @see org.zzzyxwvut.readywriter.service.Lookup
 */
module org.zzzyxwvut.readywriter.fd
{
	requires static org.zzzyxwvut.impedimenta;
	requires static org.zzzyxwvut.julics.annotations;

	requires org.zzzyxwvut.julics.core;
	requires org.zzzyxwvut.julics.naming;
	requires org.zzzyxwvut.readywriter.service;

	provides org.zzzyxwvut.readywriter.ReadyWriter with
		org.zzzyxwvut.readywriter.fd.internal.FileDescriptorWriterProvider;
}
