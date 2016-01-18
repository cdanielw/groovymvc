package groovymvc.security

import groovymvc.RequestContext

/**
 * @author Daniel Wiell
 */
interface RequestAuthenticator {
    void authenticate(RequestContext context, Closure callback)
}

