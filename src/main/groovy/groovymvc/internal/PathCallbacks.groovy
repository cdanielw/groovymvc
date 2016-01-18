package groovymvc.internal

import groovymvc.util.PathMatcher

/**
 * Encapsulates logic for registering and looking up callbacks.
 *
 * @author Daniel Wiell
 */
class PathCallbacks {
    private final Map<String, List<Map<String, Closure>>> callbacks = [:]
    private static final PathMatcher PATH_MATCHER = new PathMatcher()

    def register(String method, String pathPattern, Closure callback) {
        String normalizedMethod = method.toUpperCase()
        List<Map> callbacksForMethod = callbacks[normalizedMethod]
        if (!callbacksForMethod) {
            callbacksForMethod = []
            callbacks[normalizedMethod] = callbacksForMethod
        }
        callbacksForMethod << [pathPattern: pathPattern, callback: callback]
    }

    List<Map> matches(String method, String path) {
        def callbacksForMethod = callbacks[method]
        callbacksForMethod.findAll {
            PATH_MATCHER.match(it.pathPattern, path)
        }
    }

    Map firstMatch(String method, String path) {
        def callbacksForMethod = callbacks[method]
        callbacksForMethod.find {
            PATH_MATCHER.match(it.pathPattern, path)
        }
    }
}
