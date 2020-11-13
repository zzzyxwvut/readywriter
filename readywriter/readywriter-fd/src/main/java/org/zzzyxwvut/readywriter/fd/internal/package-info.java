/**
 * Provides file-descriptor-writing-related support.
 * <p>
 * This implementation of {@code ReadyWriter} shall make logs to a local file
 * system (see {@code logging.properties}) of any rejected attempts of binding
 * file descriptor numbers greater than 2 (and other data, if publishable).
 * By default, a {@code [user.home]/java%u.log} file shall be claimed by
 * {@code java.util.logging.FileHandler} after the first request for a file
 * descriptor number greater than 2. Therefore, clients are advised to supply
 * their own property values for the handler and have them parsed and accepted
 * before the request.
 * <p>
 * (As an example, see {@code logging.properties} under the {@code test}
 * directory and their use in the tests.)
 */
package org.zzzyxwvut.readywriter.fd.internal;
