package groovymvc

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovymvc.bind.BeanProperty
import groovymvc.bind.Binder
import groovymvc.bind.DefaultBinder
import groovymvc.i18n.DefaultMessageSource
import groovymvc.i18n.MessageSource
import groovymvc.internal.PathCallbacks
import groovymvc.multipart.MultipartRequestParser
import groovymvc.security.PathRestrictions
import groovymvc.template.TemplateRenderer
import groovymvc.template.jsp.JspRenderer
import groovymvc.util.PathMatcher
import groovymvc.validate.DefaultValidator
import groovymvc.validate.Validator

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Provides a DSL for setting up HTTP end-points, filters, access control, converters for bean binding,
 * and specifying bean constraints for validation.
 * A single instance would typically be created in an {@link AbstractMvcFilter} subclass,
 * and passed around to application specific controller classes.
 *
 * <p>Controller instances are build using the Controller.builder().
 *
 * <p>Many methods takes a Closure as a callback. It's delegate will always be set to a
 * {@link RequestContext} instance, linked to the current request.
 *
 * <p>Example:
 * <blockquote><pre>
 *
 * class Bookstore extends AbstractMvcFilter &#123;
 *     Controller bootstrap(ServletContext servletContext) &#123;
 *         def controller = Controller.builder(servletContext)
 *                 .messageSource('my.messages')
 *                 .build()
 *
 *         controller.with &#123;
 *             exclude '/css/*', '/js/*'
 *             constrain(Author, Author.constraints)
 *             converter(Date) &#123;
 *                 Date.parse('yyyy-MM-dd', it as String)
 *             &#125;
 *             restrict('/**', ['admin'])
 *             before('/**') &#123; response.characterEncoding = 'UTF-8' &#125;
 *         &#125;
 *
 *         new AuthorResources(new AuthorRepository(), controller)
 *         return controller
 *     &#125;
 * &#125;
 *
 * class AuthorResources &#123;
 *     AuthorResources(AuthorService authorService, Controller controller) &#123;
 *         controller.with &#123;
 *
 *             get '/author/show/&#123;id&#125;', [NO_AUTHORIZATION], &#123;
 *                 def author = authorService.byId(params.id as long)
 *                 if (!author)
 *                     return halt(404)
 *                 render 'author/show', [author: author]
 *             &#125;
 *
 *             get '/author/edit/&#123;id&#125;', ['admin'], &#123;
 *                 def author = authorService.byId(params.id as long) ?:
 *                         new Author()
 *                 render 'author/edit', [author: author]
 *             &#125;
 *
 *             post '/author/save', ['admin'], &#123;
 *                 def author = new Author()
 *                 if (bindAndValidate(author))
 *                     return render('author/edit', [author: author])
 *                 authorService.save(author)
 *                 redirect("/author/show/$&#123;author.id&#125;")
 *             &#125;
 *
 *         &#125;
 *     &#125;
 * &#125
 *
 *
 * class Author &#123;
 *     long id
 *     String name
 *     Date dateOfBirth
 *
 *     static constraints = [
 *             name: [notNull(), notBlank()],
 *             dateOfBirth: custom &#123; it < new Date() &#125;
 *     ]
 * &#125;
 * </pre></blockquote>
 *
 * @see PathRestrictions
 * @see TemplateRenderer
 * @see MessageSource
 * @see Binder
 * @see Validator
 * @see RequestContext
 * @author Daniel Wiell
 */
class Controller {
    private static final PATH_MATCHER = new PathMatcher()
    /** Message source base name of default messages. **/
    static final String DEFAULT_MESSAGES_BASE_NAME = 'groovymvc.messages'
    /** Magic role, allowing access to resource without authorization. **/
    public static final String NO_AUTHORIZATION = 'groovymvc.NO_AUTHORIZATION'
    /** The HTTP DELETE method. **/
    public static final String DELETE = 'DELETE'
    /** The HTTP HEAD method. **/
    public static final String HEAD = 'HEAD'
    /** The HTTP GET method. **/
    public static final String GET = 'GET'
    /** The HTTP OPTIONS method. **/
    public static final String OPTIONS = 'OPTIONS'
    /** The HTTP POST method. **/
    public static final String POST = 'POST'
    /** The HTTP PUT method. **/
    public static final String PUT = 'PUT'
    /** The HTTP TRACE method. **/
    public static final String TRACE = 'TRACE'
    /** The A list of all HTTP methods. **/
    public static final List<String> ALL_METHODS = [DELETE, HEAD, GET, OPTIONS, POST, PUT, TRACE]

    private final routes = new PathCallbacks()
    private final List<String> excludes = new CopyOnWriteArrayList<>()
    private final beforeInterceptors = new PathCallbacks()
    private final afterInterceptors = new PathCallbacks()
    private final filters = new PathCallbacks()
    private final exceptionHandlers = [:] as Map<List<Class<? extends Exception>>, Closure>
    private final statusCodeHandlers = [:] as Map<Integer, Closure>

    private final PathRestrictions restrictions
    private final TemplateRenderer renderer
    private final Binder binder
    private final Validator validator
    private final MultipartRequestParser multipartRequestParser
    private final String defaultContentType
    private final ThreadLocal<RequestContext> requestContextHolder = new ThreadLocal<>()

    /** Provides access to messages. **/
    final MessageSource messageSource

    private Controller(ControllerBuilder builder) {
        restrictions = builder.restrictions
        renderer = builder.templateRenderer
        binder = builder.binder
        validator = builder.validator
        messageSource = builder.messageSource
        defaultContentType = builder.defaultContentType
        multipartRequestParser = builder.multipartRequestParser
    }

    /**
     * Creates a builder used to create a Controller instance.
     *
     * <p>Example:
     * <blockquote><pre>
     * def controller = Controller.builder(servletContext)
     *         .messageSource('my.messages')
     *         .build()
     * </pre></blockquote>
     *
     * @return A ControllerBuilder instance.
     */
    static ControllerBuilder builder(ServletContext servletContext) {
        return new ControllerBuilder(servletContext)
    }

    /**
     * A Controller builder.
     *
     * <p>Example:
     * <blockquote><pre>
     * def controller = Controller.builder(servletContext)
     *         .messageSource('my.messages')
     *         .build()
     * </pre></blockquote>
     *
     * <p>Without specifying any property a Controller with the following is created:
     * <ul>
     *     <li>No {@link PathRestrictions}. Unless specified, any attempts to restrict access to a route throws
     *     exception
     *     <li>{@link JspRenderer} with default configuration</li>
     *     <li>{@link DefaultMessageSource} with {@link Controller#DEFAULT_MESSAGES_BASE_NAME}</li>
     *     <li>{@link DefaultBinder} with the above message source</li>
     *     <li>{@link DefaultValidator} with the above message source</li>
     * </ul>
     */
    static class ControllerBuilder implements Builder<Controller> {
        private final ServletContext servletContext
        private PathRestrictions restrictions
        private TemplateRenderer templateRenderer
        private Binder binder
        private Validator validator
        private MessageSource messageSource
        private String defaultContentType
        private MultipartRequestParser multipartRequestParser

        private ControllerBuilder(ServletContext servletContext) {
            this.servletContext = servletContext
        }

        /**
         * Builds the controller instance.
         *
         * @return The controller instance.
         */
        Controller build() {
            def messageSource = this.messageSource ?: DefaultMessageSource.messageSource(servletContext,
                    DEFAULT_MESSAGES_BASE_NAME)
            templateRenderer = templateRenderer ?: JspRenderer.builder().build()
            binder = binder ?: new DefaultBinder(messageSource)
            validator = validator ?: new DefaultValidator(messageSource)
            new Controller(this)
        }

        /**
         * Specifies the PathRestrictions to use.
         */
        ControllerBuilder pathRestrictions(PathRestrictions restrictions) {
            this.restrictions = restrictions
            return this
        }

        /**
         * Specifies the TemplateRenderer to use.
         */
        ControllerBuilder templateRenderer(TemplateRenderer templateRenderer) {
            this.templateRenderer = templateRenderer
            return this
        }

        /**
         * Specifies the TemplateRenderer to use.
         */
        ControllerBuilder templateRenderer(Builder<? extends TemplateRenderer> templateRenderer) {
            this.templateRenderer = templateRenderer.build()
            return this
        }

        /**
         * Specifies the Binder to use.
         */
        ControllerBuilder binder(Binder binder) {
            this.binder = binder
            return this
        }

        /**
         * Specifies the Validator to use.
         */
        ControllerBuilder validator(Validator validator) {
            this.validator = validator
            return this
        }

        /**
         * Specifies the base names of the {@link DefaultMessageSource} to use.
         */
        ControllerBuilder messageSource(boolean reloadable = false, String... baseNames) {
            def bn = (baseNames ?: []) as SortedSet
            bn.add(DEFAULT_MESSAGES_BASE_NAME)
            messageSource = reloadable ?
                    DefaultMessageSource.reloadableMessageSource(servletContext, bn as String[]) :
                    DefaultMessageSource.messageSource(servletContext, bn as String[])
            return this
        }

        /**
         * Specifies the MessageSource to use.
         */
        ControllerBuilder messageSource(MessageSource messageSource) {
            this.messageSource = messageSource
            return this
        }

        /**
         * Specifies the content type to use when none has been specified for a response.
         */
        ControllerBuilder defaultContentType(String defaultContentType) {
            this.defaultContentType = defaultContentType
            return this
        }

        ControllerBuilder multipartRequestParser(MultipartRequestParser multipartRequestParser) {
            this.multipartRequestParser = multipartRequestParser
            return this
        }

        ControllerBuilder multipartRequestParser(Builder<MultipartRequestParser> multipartRequestParser) {
            this.multipartRequestParser = multipartRequestParser.build()
            return this
        }
    }

    /**
     * Registers a route that forwards requests of the provided method that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param method the HTTP method a request must have for the callback to be called
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void route(String method, String pathPattern, Collection<String> roles = null,
               @DelegatesTo(RequestContext) Closure callback) {
        def normalizedPathPattern = pathPattern
        if (!pathPattern.startsWith('/'))
            normalizedPathPattern = '/' + pathPattern
        if (roles != null) {
            if (restrictions == null)
                throw new IllegalStateException('PathRestrictions was not specified when constructing Controller')
            restrictions.restrict(method, pathPattern, roles)
        }
        routes.register(method, normalizedPathPattern, callback)
    }

    /**
     * Registers a route that forwards HTTP DELETE requests that match the path pattern to the callback.
     * No access restrictions is specified.
     * If a request is made that matches more then one route, the first one registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void delete(String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        route(DELETE, pathPattern, callback)
    }

    /**
     * Registers a route that forwards HTTP DELETE requests that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void delete(String pathPattern, List<String> roles, @DelegatesTo(RequestContext) Closure callback) {
        route(DELETE, pathPattern, roles, callback)
    }

    /**
     * Registers a route that forwards HTTP HEAD requests that match the path pattern to the callback.
     * No access restrictions is specified.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void head(String pathPattern, @DelegatesTo(RequestContext) Closure callback) { route(HEAD, pathPattern, callback) }

    /**
     * Registers a route that forwards HTTP HEAD requests that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void head(String pathPattern, List<String> roles, @DelegatesTo(RequestContext) Closure callback) {
        route(HEAD, pathPattern, roles, callback)
    }

    /**
     * Registers a route that forwards HTTP GET requests that match the path pattern to the callback.
     * No access restrictions is specified.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void get(String pathPattern, @DelegatesTo(RequestContext) Closure callback) { route(GET, pathPattern, callback) }

    /**
     * Registers a route that forwards HTTP GET requests that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void get(String pathPattern, List<String> roles, @DelegatesTo(RequestContext) Closure callback) {
        route(GET, pathPattern, roles, callback)
    }

    /**
     * Registers a route that forwards HTTP OPTIONS requests that match the path pattern to the callback.
     * No access restrictions is specified.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void options(String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        route(OPTIONS, pathPattern, callback)
    }

    /**
     * Registers a route that forwards HTTP OPTIONS requests that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void options(String pathPattern, List<String> roles, @DelegatesTo(RequestContext) Closure callback) {
        route(OPTIONS, pathPattern, roles, callback)
    }

    /**
     * Registers a route that forwards HTTP POST requests that match the path pattern to the callback.
     * No access restrictions is specified.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void post(String pathPattern, @DelegatesTo(RequestContext) Closure callback) { route(POST, pathPattern, callback) }

    /**
     * Registers a route that forwards HTTP POST requests that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void post(String pathPattern, List<String> roles, @DelegatesTo(RequestContext) Closure callback) {
        route(POST, pathPattern, roles, callback)
    }

    /**
     * Registers a route that forwards HTTP PUT requests that match the path pattern to the callback.
     * No access restrictions is specified.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void put(String pathPattern, @DelegatesTo(RequestContext) Closure callback) { route(PUT, pathPattern, callback) }

    /**
     * Registers a route that forwards HTTP PUT requests that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void put(String pathPattern, List<String> roles, @DelegatesTo(RequestContext) Closure callback) {
        route(PUT, pathPattern, roles, callback)
    }

    /**
     * Registers a route that forwards HTTP TRACE requests that match the path pattern to the callback.
     * No access restrictions is specified.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void trace(String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        route(TRACE, pathPattern, callback)
    }

    /**
     * Registers a route that forwards HTTP TRACE requests that match the path pattern to the callback.
     * If a request matches more then one route, the first route registered is used.
     *
     * @param pathPattern pathPattern the Ant-style pattern a request must match for the callback to be called
     * @param roles the roles a user must have for the callback to be called.
     * If null, no restriction is specified. If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void trace(String pathPattern, List<String> roles, @DelegatesTo(RequestContext) Closure callback) {
        route(TRACE, pathPattern, roles, callback)
    }

    /**
     * Prevents requests matching one of the provided path from being processed by the controller.
     *
     * <p>A typical use of this is to allow the server to serve up static files, such as images, CSS, and JavaScript.
     *
     * @param pathPatterns Ant-style patterns a request must match to be excluded from processing
     */
    void exclude(String... pathPatterns) {
        excludes.addAll(pathPatterns)
    }

    /**
     * Register an interceptor that is called before requests of any method, that match the path pattern.
     * If no route match the request, the interceptor will not be invoked.
     *
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void before(String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        before(ALL_METHODS, pathPattern, callback)
    }

    /**
     * Register an interceptor that is called before requests of the provided method, that match the path pattern.
     * If no route match the request, the interceptor will not be invoked.
     *
     * @param method the HTTP method a request must have for the interceptor to be called
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void before(String method, String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        beforeInterceptors.register(method, pathPattern, callback)
    }

    /**
     * Register an interceptor that is called before requests of one of provided methods, that match the path pattern.
     * If no route match the request, the interceptor will not be invoked.
     *
     * @param methods the HTTP methods a request must have for the interceptor to be called
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void before(List<String> methods, String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        methods.each { before(it, pathPattern, callback) }
    }

    /**
     * Register an interceptor that is called after requests of any method, that match the path pattern.
     * If no route match the request, the interceptor will not be invoked.
     *
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void after(String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        after(ALL_METHODS, pathPattern, callback)
    }

    /**
     * Register an interceptor that is called after requests of the provided method, that match the path pattern.
     * If no route match the request, the interceptor will not be invoked.
     *
     * @param method the HTTP method a request must have for the interceptor to be called
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void after(String method, String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        afterInterceptors.register(method, pathPattern, callback)
    }

    /**
     * Register an interceptor that is called after requests of one of provided methods, that match the path pattern.
     * If no route match the request, the interceptor will not be invoked.
     *
     * @param methods the HTTP methods a request must have for the interceptor to be called
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void after(List<String> methods, String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        methods.each { after(it, pathPattern, callback) }
    }

    /**
     * Registers an interceptor that is called for all non-excluded requests of any method, that match the path pattern.
     * It will be called before any authentication and authorization is done, and will be called
     * whether there is a route matching the request or not.
     * <p>The filter is passed a {@code Closure}, representing the next step in the request processing chain.
     * This closure must be called, without any arguments, for the request processing to proceed.
     *
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void filter(String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        filter(ALL_METHODS, pathPattern, callback)
    }

    /**
     * Registers an interceptor that is called for all non-excluded requests of the provided method,
     *  that match the path pattern.
     * It will be called before any authentication and authorization is done, and will be called
     * whether there is a route matching the request or not.
     * <p>The filter is passed a {@code Closure}, representing the next step in the request processing chain.
     * This closure must be called, without any arguments, for the request processing to proceed.
     *
     * @param method the HTTP method a request must have for the interceptor to be called
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void filter(String method, String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        filters.register(method, pathPattern, callback)
    }

    /**
     * Registers an interceptor that is called for all non-excluded requests of one of provided methods,
     * that match the path pattern.
     * It will be called before any authentication and authorization is done, and will be called
     * whether there is a route matching the request or not.
     * <p>The filter is passed a {@code Closure}, representing the next step in the request processing chain.
     * This closure must be called, without any arguments, for the request processing to proceed.
     *
     * @param methods the HTTP methods a request must have for the interceptor to be called
     * @param pathPattern the Ant-style pattern a request must match for the interceptor to be called
     * @param callback the interceptor. It's delegate will be set to a {@link RequestContext} instance
     */
    void filter(List<String> methods, String pathPattern, @DelegatesTo(RequestContext) Closure callback) {
        methods.each { filter(it, pathPattern, callback) }
    }

    /**
     * Restricts access for requests of any method that matches the path pattern to users with one of the specified roles.
     * If a request matches several restrictions, the first one registered is used.
     *
     * @param pathPattern the Ant-style pattern a request must match for restriction to apply
     * @param roles the roles a user must have.
     * If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     */
    void restrict(String pathPattern, List<String> roles) {
        restrict(ALL_METHODS, pathPattern, roles)
    }

    /**
     * Restricts access for requests of the provided method that matches the path pattern to users with one of the
     * specified roles.
     * If a request matches several restrictions, the first one registered is used.
     *
     * @param method the HTTP method a request must have for restriction to apply
     * @param pathPattern the Ant-style pattern a request must match for restriction to apply
     * @param roles the roles a user must have.
     * If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     */
    void restrict(String method, String pathPattern, List<String> roles) {
        if (restrictions == null)
            throw new IllegalStateException('PathRestrictions was not specified when constructing Controller')
        restrictions.restrict(method, pathPattern, roles)
    }

    /**
     * Restricts access for requests of one of the provided methods that matches the path pattern to users with one
     * of the specified roles.
     * If a request matches several restrictions, the first one registered is used.
     *
     * @param methods the HTTP methods a request must have for restriction to apply
     * @param pathPattern the Ant-style pattern a request must match for restriction to apply
     * @param roles the roles a user must have.
     * If empty, user must be authenticated but no specific roles are needed.
     * If it contains {@link Controller#NO_AUTHORIZATION}, authorization will not event be triggered.
     */
    void restrict(List<String> methods, String pathPattern, List<String> roles) {
        methods.each { restrict(it, pathPattern, roles) }
    }

    /**
     * Registered an error handler for the provided exception type or one of its subtypes.
     * <p>If more then one error handler matches an exception, the first one registered is used.
     *
     * @param type the type of exceptions to invoke the callback for
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    public <T extends Exception> void error(
            Class<T> type,
            @ClosureParams(FirstParam.FirstGenericType.class)
            @DelegatesTo(RequestContext)
                    Closure callback) {
        registerErrorHandler([type], callback)
    }

    /**
     * Registered an error handler for the provided exception and cause types or one of their subtypes.
     * <p>If more then one error handler matches an exception, the first one registered is used.
     *
     * @param rootType the type of exceptions to invoke the callback for
     * @param causeType the type of causes to invoke the callback for
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    public <T extends Exception> void error(
            Class<? extends Exception> rootType,
            Class<T> causeType,
            @ClosureParams(FirstParam.FirstGenericType.class)
            @DelegatesTo(RequestContext)
                    Closure callback) {
        registerErrorHandler([rootType, causeType], callback)
    }


    private void registerErrorHandler(ArrayList<Class<? extends Exception>> key, Closure callback) {
        if (!exceptionHandlers.containsKey(key))
            exceptionHandlers[key] = callback
    }

    /**
     * Registered an error handler for the provided HTTP Status Code.
     * <p>If there already is a callback registered for this status code, this callback will be ignored.
     *
     * @param statusCode the HTTP Status Code to invoke the callback for
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void error(int statusCode, @DelegatesTo(RequestContext) Closure callback) {
        if (!statusCodeHandlers.containsKey(statusCode))
            statusCodeHandlers[statusCode] = callback
    }

    /**
     * Registered an error handler for the provided HTTP Status Codes.
     * <p>If there already is a callback registered for some of these status codes,
     * this callback will be ignored for these statuses.
     *
     * @param statusCodes the HTTP Status Codes to invoke the callback for
     * @param callback the callback to call. It's delegate will be set to a {@link RequestContext} instance
     * before called.
     */
    void error(List<Integer> statusCodes, @DelegatesTo(RequestContext) Closure callback) {
        statusCodes.each { error(it, callback) }
    }

    /**
     * Registers constraints for a bean class. This is required to validate beans of the specified type.
     *
     * <p>The constraints should be a map where the key is the name of a property in the specified bean class.
     * The value should be a constraint, or an iterable containing constraints.
     *
     * <p>Example:
     * <blockquote><pre>
     * import static groovymvc.validate.Constraints.*
     *
     * class Author &#123;
     *     long id
     *     String name
     *     Date dateOfBirth
     *
     *     static constraints = [
     *             name: [notNull(), notBlank()],
     *             dateOfBirth: custom &#123; it < new Date() &#125;
     *     ]
     * &#125;
     *
     * ...
     *
     * controller.with &#123;
     *     constrain(Author, Author.constraints)
     *
     *     post '/save', &#123;
     *         def author = new Author()
     *         def errors = bindAndValidate(author)
     *         if (errors)
     *             return render 'edit', [author: author]
     *         // authorService.save(author)
     *         redirect "/show/$author.id"
     *     &#125;
     * &#125;
     * </pre></blockquote>
     *
     * @param beanClass classof bean to register the constraints for
     * @param constraints the constraints
     * @see Validator
     */
    void constrain(Class beanClass, Map<String, ?> constraints) {
        validator.register(beanClass, constraints)
    }

    /**
     * Registers a converter for a type. This is required to bind a bean property of a type
     * not among the built in converters.
     *
     * <p>Example:
     * <blockquote><pre>
     * controller.with &#123;
     *     converter(Date) &#123;
     *         Date.parse('yyyy-MM-dd', it as String)
     *     &#125;
     *
     *     post '/save', &#123;
     *         def author = new Author()
     *         bind(author)
     *         // authorService.save(author)
     *         redirect "/show/$author.id"
     *     &#125;
     * &#125;
     * </pre></blockquote>
     *
     * @param type the type to convert to
     * @param converter callback performing the conversion from String to T.
     * It takes either one or two arguments: The value, or the {@link BeanProperty} and the value.
     */
    def <T> void converter(Class<T> type, Closure<T> converter) {
        binder.register(type, converter)
    }

    // TODO: Document...
    def <T> void converter(Class<T> type, Closure<T> converter, Closure<String> formatter) {
        binder.register(type, converter, formatter)
    }

    RequestContext getRequestContext() { requestContextHolder.get() }

    /**
     * Processes an incoming request. Invokes interceptors and executes an eventual matching route.
     */
    boolean process(HttpServletRequest request, HttpServletResponse response) {
        if (excluded(request))
            return false
        request.characterEncoding = 'UTF-8'
        response.characterEncoding = 'UTF-8'
        response.contentType = defaultContentType
        def path = determinePath(request)
        def route = routes.firstMatch(request.method, path)
        if (route)
            executeRoute(route, request, response)
        else
            handleRouteNotFound(request, response)

        return true
    }

    private boolean excluded(HttpServletRequest request) {
        def path = request.requestURI - request.contextPath
        excludes.any { String pattern -> PATH_MATCHER.match(pattern, path) }
    }

    private String determinePath(HttpServletRequest request) {
        request.requestURI - request.contextPath
    }

    private void handleRouteNotFound(HttpServletRequest request, HttpServletResponse response) {
        response.status = 404
        def context = new RequestContext(request, response, '/**',
                renderer, binder, validator, messageSource, multipartRequestParser)

        processRequest(context) { RequestContext ctx ->
            if (notifyStatusCodeHandlers(ctx))
                response.writer.close()
            else
                response.sendError(404)
        }
    }

    private void executeRoute(Map route, HttpServletRequest request, HttpServletResponse response) {
        def context = new RequestContext(request, response, route.pathPattern as String,
                renderer, binder, validator, messageSource, multipartRequestParser)

        processRequest(context) { RequestContext ctx ->
            try {
                restrictions?.enforce(ctx)
                notifyInterceptors(beforeInterceptors, ctx)
                invokeCallback(route.callback, ctx)
                notifyInterceptors(afterInterceptors, ctx)
                notifyStatusCodeHandlers(ctx)
            } catch (HaltedException ignore) {
                notifyStatusCodeHandlers(ctx)
            } catch (Exception e) {
                handleException(e, ctx)
            }
        }
    }

    private void processRequest(RequestContext context, Closure requestHandler) {
        try {
            requestContextHolder.set(context)
            filterChain(context, requestHandler).call(context)
        } finally {
            requestContextHolder.remove()
            context.destroy()
        }
    }

    private Closure filterChain(RequestContext context, Closure requestHandler) {
        def lastInChain = {
            def ctx = it instanceof RequestContext ? it : context
            requestHandler.call(ctx)
        }
        def matchingFilters = filters.matches(context.request.method, context.path)
        def filterChain = matchingFilters.reverse().inject(lastInChain) { nextInChain, filter ->
            def previousInChain = {
                def ctx = it instanceof RequestContext ? it : context
                requestContextHolder.set(ctx)
                invokeCallback(filter.callback, ctx, nextInChain)
            }
            return previousInChain
        }
        return filterChain
    }

    private boolean notifyStatusCodeHandlers(RequestContext context) {
        def handler = statusCodeHandlers[context.response.status]
        if (handler == null)
            return false
        def clone = handler.clone() as Closure
        clone.delegate = context
        clone.call(context)
        return true
    }

    private void handleException(Exception e, RequestContext context) {
        context.response.status = 500
        def exception = e
        def handler = exceptionHandlers.find {
            def keys = it.key
            def matchExceptionType = keys.first().isAssignableFrom(e.class)
            if (!matchExceptionType)
                return false
            if (keys.size() == 2) {
                if (!e.cause)
                    return false
                def causeType = keys.last()
                def matchCause = causeType.isAssignableFrom(e.cause.class)
                if (matchCause)
                    exception = e.cause
                return matchCause
            } else
                return matchExceptionType

        }?.value
        if (!handler)
            throw e
        try {
            invokeCallback(handler, context, exception)
        } catch (HaltedException ignore) {
        }
    }


    private void notifyInterceptors(PathCallbacks callbacks, RequestContext context) {
        List<Map> matches = callbacks.matches(context.request.method, context.path)
        matches.each {
            invokeCallback(it.callback, context)
        }
    }


    private void invokeCallback(Closure callback, RequestContext context, arg = null) {
        def clone = callback.clone() as Closure
        clone.delegate = context
        clone.call(arg ?: context)
    }

    static class HaltedException extends RuntimeException {}
}
