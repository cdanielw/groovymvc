package groovymvc

import groovymvc.bind.Binder
import groovymvc.bind.Errors
import groovymvc.bind.PropertyError
import groovymvc.i18n.MessageSource
import groovymvc.internal.BeanValueEvaluator
import groovymvc.multipart.MultipartRequestParser
import groovymvc.security.User
import groovymvc.template.TemplateRenderer
import groovymvc.util.PathMatcher
import groovymvc.validate.Validator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.Part
import java.security.Principal

/**
 * Provides a context for the different callbacks invoked within a request.
 *
 * @author Daniel Wiell
 */
class RequestContext {
    private static final Logger LOG = LoggerFactory.getLogger(RequestContext)
    /** The session attribute where the current user is expected to be **/
    static final String CURRENT_USER_SESSION_ATTRIBUTE = 'groovymvc.currentUser'
    private static final PathMatcher PATH_MATCHER = new PathMatcher()
    private final Flash flash
    private final TemplateRenderer renderer
    private final Binder binder
    private final Validator validator
    private final MessageSource messageSource
    private Errors<PropertyError> errors = new Errors<>([:])
    private Params _params
    /** The current request **/
    final HttpServletRequest request
    /** The current response **/
    final HttpServletResponse response
    /** The path requested  **/
    final String path
    /** The path pattern matched **/
    final String pathPattern

    RequestContext(HttpServletRequest wrappedRequest, HttpServletResponse wrappedResponse, RequestContext requestContext) {
        this.flash = requestContext.flash
        this.renderer = requestContext.renderer
        this.binder = requestContext.binder
        this.validator = requestContext.validator
        this.messageSource = requestContext.messageSource
        this.errors = requestContext.errors
        this._params = requestContext._params
        this.request = new RequestWrapper(wrappedRequest)
        this.response = wrappedResponse
        this.path = requestContext.path
        this.pathPattern = requestContext.pathPattern
    }

    RequestContext(HttpServletRequest request, HttpServletResponse response,
                   String pathPattern,
                   TemplateRenderer renderer,
                   Binder binder,
                   Validator validator,
                   MessageSource messageSource,
                   MultipartRequestParser multipartRequestParser) {
        this.request = new RequestWrapper(request, multipartRequestParser)
        this.response = response
        this.renderer = renderer
        this.binder = binder
        this.validator = validator
        this.messageSource = messageSource

        path = determinePath(request)
        this.pathPattern = pathPattern
        flash = Flash.get(request)
    }

    RequestContext getRequestContext() { this }

    RequestContext wrap(HttpServletRequest request, HttpServletResponse response) {
        return new RequestContext(request, response, this)
    }

    String getRequestString() {
        "$request.requestURI${request.queryString == null ? '' : "?$request.queryString"}"
    }

    /**
     * Path and query parameters for request
     **/
    synchronized Params getParams() {
        if (_params == null)
            _params = parseParams(pathPattern, path)
        return _params
    }
    /**
     * Gives access to the flash-scope. Objects put into this scope will be exposed to the next request made by
     * the same user and will then be cleaned out.
     *
     * @return The flash-scope.
     */
    Map<String, Object> getFlash() { flash }

    /**
     * Gets the current user. If no user has been authenticated, null is returned.
     * @return
     */
    User getCurrentUser() {
        request.getSession(false)?.getAttribute(CURRENT_USER_SESSION_ATTRIBUTE) as User
    }

    /**
     * Halts execution of the current request. Any pending interceptors or routes will be called.
     *
     * @param status the HTTP Status Code to set for the response
     */
    def halt(int status = 200) {
        response.status = status
        throw new Controller.HaltedException()
    }

    /**
     * Redirects the user to the provided path.
     * @param path the path to redirect the user to
     */
    def redirect(String path) {
        response.sendRedirect(request.contextPath + path)
    }

    /**
     * Renders a template.
     * <p>Passes the model to the configured {@link TemplateRenderer}.
     *
     * <p>If no {@link TemplateRenderer} is configured, an {@link IllegalStateException} will be thrown.
     *
     * @param template the name of the template to render
     * @param model the model to pass to the renderer.
     */
    def render(String template, Map model = [:]) {
        if (!renderer)
            throw new IllegalStateException('No renderer configured')

        if (response.isCommitted()) {
            LOG.warn('Request already committed')
            return
        }
        request.setAttribute(RequestContext.name, this)
        renderer.render(template, model, this)
    }

    /**
     * Writes content to the response.
     *
     * @param content the content to write.
     */
    def send(content) {
        if (response.isCommitted()) {
            LOG.warn('Request already committed')
            return
        }

        def writer = response.writer
        writer.print(content)
        writer.close()
    }

    /**
     * Forwards a request to another server resource.
     *
     * @param path the path to forward to
     */
    def forward(String path = this.path) {
        def dispatcher = request.getRequestDispatcher(path)
        dispatcher.forward(request, response)
    }

    /**
     * Binds bean properties to query/path parameters.
     *
     * @param bean the bean to bind
     * @return Any binding errors.
     * @see Binder
     */
    Errors<PropertyError> bind(Object bean, Params params = this.params) {
        def errors = binder.bind(bean, params)
        appendErrors(errors)
        return errors
    }

    /**
     * Validates a bean.
     *
     * @param bean the bean to validate
     * @return Any validation errors.
     * @see Validator
     */
    Errors<PropertyError> validate(Object bean) {
        def errors = validator.validate(bean)
        appendErrors(errors)
        return errors
    }

    /**
     * Binds bean properties to query/path parameters, then validates the bean.
     *
     * @param bean the bean to bind and validate
     * @return Any binding and validation errors.
     */
    Errors<PropertyError> bindAndValidate(Object bean, Params params = this.params) {
        def errors = bind(bean, params) + validate(bean) as Errors<PropertyError>
        appendErrors(errors)
        return errors
    }

    /**
     * Gets the accumulated binding and validation errors.
     *
     * @return The errors.
     */
    Errors<PropertyError> getErrors() { errors }

    private void appendErrors(Errors<? extends PropertyError> errors) {
        this.errors = this.errors + errors
    }

    /**
     * Gets the formatted String value of a bean for path. If provided errors contains an error for path,
     * the invalid value is returned
     * @param bean the bean to get value for
     * @param path the path to the value to get
     * @param errors any errors
     * @return The formatted String value.
     */
    String value(bean, String path, Errors errors = this.errors) {
        BeanValueEvaluator.value(bean, path, binder, errors)
    }

    String message(String key, String... arguments) {
        messageSource.message(key, request.locale, arguments)
    }

    void destroy() {
        flash.store(request)
    }

    private String determinePath(HttpServletRequest request) {
        request.requestURI - request.contextPath
    }

    private Params parseParams(String pathPattern, String path) {
        def pathParams = PATH_MATCHER.extractUriTemplateVariables(pathPattern, path)
        pathParams.each {
            pathParams[it.key] = URLDecoder.decode(it.value, 'UTF-8')
        }
        return Params.parse(joinParameters(request, pathParams))
    }

    private Map<String, String[]> joinParameters(HttpServletRequest request, Map<String, String> pathParams) {
        def result = pathParams.collectEntries { [it.key, [it.value] as String[]] } as Map<String, String[]>
        request.parameterMap.each { name, String[] value ->
            if (result.containsKey(name))
                result[name] = (result[name] + value) as String[]
            else
                result[name] = value
        }
        return result
    }

    /**
     * Returns a short description of the current request.
     */
    String getDescription() {
        "$request.method\t$requestString\t(${request.remoteUser ?: 'NOT_AUTHORIZED'})\t->\t$response.status"
    }

    /**
     * Returns a long description of the current request.
     */
    String getLongDescription() {
        """$description
Path pattern: $pathPattern
Response committed: $response.committed
remoteHost: $request.remoteHost
remoteAddr: $request.remoteAddr

HEADERS:
--------
${
            request.headerNames.collect {
                "$it: ${it.equalsIgnoreCase('authorization') ? '*****' : request.getHeaders(it).collect { it }}"
            }.join('\n')
        }

REQUEST ATTRIBUTES:
-------------------
${
            request.attributeNames.hasMoreElements() ? request.attributeNames.collect {
                "$it: ${request.getAttribute(it)}"
            }.join('\n') : '[empty]'
        }

SESSION ATTRIBUTES:
-------------------
${
            request.getSession(false) ?
                    request.session.attributeNames.hasMoreElements() ?
                            request.session.attributeNames.collect {
                                "$it: ${request.session.getAttribute(it)}"
                            }.join('\n') :
                            '[empty]' :
                    '[no session]'
        }
"""
    }

    String toString() { description }

    private static class Flash implements Map<String, Object> {
        @Delegate
        private final Map state = [:]
        private final Set<String> modifiedKeys = new HashSet<>()


        Object put(String key, Object value) {
            this.@modifiedKeys << key
            this.@state.put(key, value)
        }

        void setProperty(String property, Object newValue) {
            put(property, newValue)
        }

        void putAll(Map<? extends String, ?> m) {
            this.@modifiedKeys.addAll(m.keySet())
            this.@state.putAll(m)
        }

        void store(HttpServletRequest request) {
            def modifySession = !this.@state.isEmpty() || !this.@modifiedKeys.isEmpty()
            this.@state.keySet().removeAll { !this.@modifiedKeys.contains(it) }
            this.@modifiedKeys.clear()
            if (modifySession)
                request.session.setAttribute('groovymvc.flash', this)
        }

        static Flash get(HttpServletRequest request) {
            request.getSession(false)?.getAttribute('groovymvc.flash') as Flash ?: new Flash()
        }
    }

    private static class RequestWrapper extends HttpServletRequestWrapper {
        private final HttpServletRequest request
        private final MultipartRequestParser multipartRequestParser
        private Map<String, Part> _partsByName

        RequestWrapper(HttpServletRequest request, MultipartRequestParser multipartRequestParser = null) {
            super(request)
            this.request = request
            this.multipartRequestParser = multipartRequestParser
        }

        Principal getUserPrincipal() {
            def username = remoteUser
            if (!username)
                return null
            new UserPrincipal(username)
        }

        String getRemoteUser() {
            def user = request.getSession(false)?.getAttribute(CURRENT_USER_SESSION_ATTRIBUTE) as User
            return user?.username
        }

        boolean isUserInRole(String role) {
            def user = request.getSession(false)?.getAttribute(CURRENT_USER_SESSION_ATTRIBUTE) as User
            return user && role in user.roles
        }

        Collection<Part> getParts() throws IOException, ServletException {
            return partsByName.values().asImmutable()
        }

        Part getPart(String name) throws IOException, ServletException {
            return partsByName[name]
        }

        private synchronized Map<String, Part> getPartsByName() {
            if (multipartRequestParser == null) {  // Fallback to parts in original request
                _partsByName = [:]
                request.parts.each {
                    _partsByName[it.name] = it
                }
            } else if (_partsByName == null)
                _partsByName = multipartRequestParser.parse(request)
            return _partsByName
        }

        String toString() { request.toString() }
    }

    private static class UserPrincipal implements Principal {
        final String name

        UserPrincipal(String name) {
            this.name = name
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            UserPrincipal that = (UserPrincipal) o

            if (name != that.name) return false

            return true
        }

        int hashCode() {
            return (name != null ? name.hashCode() : 0)
        }

        String toString() {
            return name
        }
    }
}