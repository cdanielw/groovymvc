package groovymvc.security

import groovymvc.Controller
import groovymvc.RequestContext
import groovymvc.internal.PathCallbacks

import javax.servlet.http.HttpServletRequest

import static RequestContext.CURRENT_USER_SESSION_ATTRIBUTE

/**
 * @author Daniel Wiell
 */
class PathRestrictions {
    private final UserProvider userProvider
    private final RequestAuthenticator authenticator
    private final PathCallbacks registry = new PathCallbacks()

    PathRestrictions(UserProvider userProvider, RequestAuthenticator authenticator) {
        this.userProvider = userProvider
        this.authenticator = authenticator
    }

    void restrict(String method, String pathPattern, Collection<String> roles) {
        registry.register(method, pathPattern) { roles }
    }

    void enforce(RequestContext context) {
        def requiredRoles = requiredRoles(context)
        if (requiredRoles == null || requiredRoles.contains(Controller.NO_AUTHORIZATION))
            return

        context.with {
            authenticator.authenticate(context) { String username ->
                User user = getOrLookupUser(request, username)
                if (isDenied(user, requiredRoles))
                    halt(403)
            }
        }
    }

    private boolean isDenied(User user, Set<String> requiredRoles) {
        !user || (!requiredRoles.empty && !requiredRoles.any { user.hasRole(it) })
    }

    private User getOrLookupUser(HttpServletRequest request, String username) {
        def user = request.session.getAttribute(CURRENT_USER_SESSION_ATTRIBUTE) as User
        if (user == null || !user.hasUsername(username)) {
            user = userProvider.lookup(username)
            request.session.setAttribute(CURRENT_USER_SESSION_ATTRIBUTE, user)
        }
        return user
    }

    private Set<String> requiredRoles(RequestContext context) {
        registry.firstMatch(context.request.method, context.path)?.callback?.call() as Set
    }
}
