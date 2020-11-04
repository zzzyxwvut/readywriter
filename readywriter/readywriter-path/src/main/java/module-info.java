/**
 * Provides a path-writing implementation of {@code ReadyWriter}.
 *
 * @provides org.zzzyxwvut.readywriter.ReadyWriter
 * @see org.zzzyxwvut.readywriter.service.Lookup
 */
module org.zzzyxwvut.readywriter.path
{
	requires static org.zzzyxwvut.impedimenta;
	requires static org.zzzyxwvut.julics.annotations;

	/* See configuration for maven-compiler-plugin. */
//	requires org.zzzyxwvut.julics.core;
//	requires org.zzzyxwvut.julics.naming;
	requires org.zzzyxwvut.readywriter.service;
}
