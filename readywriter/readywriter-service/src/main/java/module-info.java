/**
 * Defines a sink-writing service.
 *
 * @uses org.zzzyxwvut.readywriter.ReadyWriter
 */
module org.zzzyxwvut.readywriter.service
{
	requires static org.zzzyxwvut.julics.annotations;

	requires transitive org.zzzyxwvut.julics.core;
	requires transitive org.zzzyxwvut.julics.naming;

	uses org.zzzyxwvut.readywriter.ReadyWriter;
}
