package org.stella.rest;

/**
 * An interface which may be used both on the client and on the server side.
 *
 * It describes the java end point as well as the expected String urls along with the parameters.
 * Parameters are between {} which works both with retrofit (client side Java/android) and spring web (server side)
 */
public interface StellaUserService {
    public static final String WELCOME_ANONYMOUS_COMMAND = "/welcome-anonymous";

    public static final String WELCOME_COMMAND = "/welcome";

    // Currently, this header is expected to be set with a simple username. This is a convenient,
    // first implementation but is NOT spec compliant.
    public static final String AUTHORIZATION_HEADER = "Authorization";
}
