package groovymvc.security

import groovymvc.RequestContext
import groovymvc.util.Decode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * @author Daniel Wiell
 */
class BasicRequestAuthenticator implements RequestAuthenticator {
    private static final String AUTHORIZATION_HEADER = "Authorization"
    private static final String AUTHENTICATE_HEADER = "WWW-Authenticate"
    private final String realm
    private final UsernamePasswordVerifier verifier

    BasicRequestAuthenticator(String realm, UsernamePasswordVerifier verifier) {
        this.verifier = verifier
        this.realm = realm
    }

    void authenticate(RequestContext context, Closure callback) {
        context.with {
            new Authentication(context, callback).authenticateOrChallenge()
        }
    }

    private class Authentication {
        static final Logger LOG = LoggerFactory.getLogger(BasicRequestAuthenticator)
        @Delegate
        private final RequestContext requestContext
        private final Closure callback

        Authentication(RequestContext requestContext, Closure callback) {
            this.requestContext = requestContext
            this.callback = callback
        }

        void authenticateOrChallenge() {
            if (loginAttempt)
                authenticate()
            else
                challenge()
        }

        private boolean isLoginAttempt() {
            authorizationHeader?.toUpperCase()?.startsWith(HttpServletRequest.BASIC_AUTH.toUpperCase())
        }

        private authenticate() {
            String[] authTokens = authorizationHeader.split(" ")
            if (authTokens.length < 2) {
                LOG.warn("Invalid authorization header: $authorizationHeader")
                return halt(400)
            }
            def encodedCredentials = authTokens[1]
            def decodedCredentials = Decode.base64(encodedCredentials)
            def usernamePassword = decodedCredentials.split(":", 2)
            check(usernamePassword[0], usernamePassword[1])
        }

        void check(String username, String password) {
            def alreadyAuthenticated = currentUser && currentUser.username == username
            if (alreadyAuthenticated || verifier.verify(username, password))
                callback.call(username)
            else
                challenge()
        }

        private void challenge() {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
            def header = $/$HttpServletRequest.BASIC_AUTH realm="$realm"/$
            response.setHeader(AUTHENTICATE_HEADER, header)
            halt(401)
        }

        private String getAuthorizationHeader() {
            request.getHeader(AUTHORIZATION_HEADER)
        }
    }
}
