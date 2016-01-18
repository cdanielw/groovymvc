package groovymvc

import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * A {@link Filter} that encapsulates a web application.
 *
 * <p>Subclasses must implement {@link AbstractMvcFilter#bootstrap(ServletContext)},
 * where a {@link Controller}, to be used for request dispatching, is constructed.
 *
 * <p>Subclasses can override {@link AbstractMvcFilter#destroy()},
 * which is called by the web container to indicate to a filter that it is being
 * taken out of service.
 *
 * <p>
 * Example:
 * <blockquote><pre>
 * import static groovymvc.i18n.DefaultMessageSource.messageSource
 *
 * class MyApp extends AbstractMvcFilter &#123;
 *     final myService
 *
 *     Controller bootstrap(ServletContext servletContext) &#123;
 *         myService = new MyService(new SomeDependency())
 *         def controller = Controller.builder(servletContext)
 *                 .messageSource('my.messages')
 *                 .build()
 *
 *         controller.with &#123;
 *             get '/hello' &#123;
 *                 def name = myService.name(params.foo)
 *                 send "Hello, $name"
 *             &#125;
 *         &#125;
 *
 *         return controller
 *     &#125;
 *
 *     void destroy() &#123;
 *         myServlce?.stop()
 *     &#125;
 * &#125;
 * </pre></blockquote>
 * @see Controller
 * @author Daniel Wiell
 */
abstract class AbstractMvcFilter implements Filter {
    private Controller controller

    final void init(FilterConfig filterConfig) throws ServletException {
        this.controller = bootstrap(filterConfig.servletContext)
    }

    final void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = res as HttpServletResponse
        if (!controller.process(req as HttpServletRequest, response))
            chain.doFilter(req, res)
    }

    /**
     * Bootstraps the application.
     * An implementation would typically create a {@link Controller} instance,
     * configure the HTTP end-points on it, and finally return it.
     *
     * <p>Called when the filter is being placed into service.
     *
     * @return the {@link Controller} requests will be dispatched to.
     */
    abstract Controller bootstrap(ServletContext servletContext)

    void destroy() {}

}
