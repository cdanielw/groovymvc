package groovymvc.security

import groovymvc.RequestContext
import groovymvc.internal.SimpleFilterConfig
import org.jasig.cas.client.authentication.AuthenticationFilter
import org.jasig.cas.client.util.AbstractCasFilter
import org.jasig.cas.client.validation.Assertion
import org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter

import javax.servlet.*
/**
 * A {@link RequestAuthenticator} for Jasig CAS.
 *
 * <p>This class is dependent on CAS Client, which is an optional dependency.
 * Make sure to provide the following dependency:
 * <blockquote><pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;org.jasig.cas.client&lt;/groupId&gt;
 *     &lt;artifactId&gt;cas-client-core&lt;/artifactId&gt;
 *     &lt;version&gt;3.2.1&lt;/version&gt;
 *     &lt;exclusions&gt;
 *         &lt;exclusion&gt;
 *             &lt;groupId&gt;javax.servlet&lt;/groupId&gt;
 *             &lt;artifactId&gt;servlet-api&lt;/artifactId&gt;
 *         &lt;/exclusion&gt;
 *     &lt;/exclusions&gt;
 * &lt;/dependency&gt;
 * </pre></blockquote>
 *
 * @author Daniel Wiell
 */
class CasRequestAuthenticator implements RequestAuthenticator {
    private final authenticationFilter
    private final ticketValidationFilter

    CasRequestAuthenticator(String casServerUrl, String casServerLoginUrl, String applicationServerUrl,
                            ServletContext servletContext) {
        verifyCasClientDependency()
        require(casServerUrl, 'casServerUrl is required')
        require(casServerLoginUrl, 'casServerLoginUrl is required')
        require(applicationServerUrl, 'applicationServerUrl is required')
        authenticationFilter = new AuthenticationFilter()
        authenticationFilter.init(new SimpleFilterConfig(servletContext, "authenticationFilter", [
                casServerLoginUrl: casServerLoginUrl,
                serverName: applicationServerUrl,
        ]))
        ticketValidationFilter = new Cas20ProxyReceivingTicketValidationFilter()
        ticketValidationFilter.init(new SimpleFilterConfig(servletContext, "ticketValidationFilter", [
                casServerUrlPrefix: casServerUrl,
                serverName: applicationServerUrl,
        ]))
    }

    void authenticate(RequestContext context, Closure callback) {
        context.with {
            doFilter(request, response, { req, res ->
                def assertion = request.session.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION) as Assertion
                callback.call(assertion.principal.name)
            } as FilterChain)
            if (response.committed)
                halt()
        }
    }

    void destroy() {
        authenticationFilter.destroy()
        ticketValidationFilter.destroy()
    }

    private void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
        authenticationFilter.doFilter(servletRequest, servletResponse, { req, res ->
            ticketValidationFilter.doFilter(req, res, filterChain)
        } as FilterChain)
    }

    private void require(String s, String message) {
        if (!s)
            throw new IllegalArgumentException(message)
    }

    private void verifyCasClientDependency() {
        try {
            Class.forName("org.jasig.cas.client.authentication.AuthenticationFilter")
        } catch (Exception ignore) {
            throw new IllegalStateException("${CasRequestAuthenticator.name} is dependent on CAS Client.\n" +
                    "Please add dependency:" +
                    "\n" +
                    "        <dependency>\n" +
                    "            <groupId>org.jasig.cas.client</groupId>\n" +
                    "            <artifactId>cas-client-core</artifactId>\n" +
                    "            <version>\${cas-client.version}</version>\n" +
                    "            <exclusions>\n" +
                    "                <exclusion>\n" +
                    "                    <groupId>javax.servlet</groupId>\n" +
                    "                    <artifactId>servlet-api</artifactId>\n" +
                    "                </exclusion>\n" +
                    "            </exclusions>\n" +
                    "        </dependency> \n")
        }
    }
}

