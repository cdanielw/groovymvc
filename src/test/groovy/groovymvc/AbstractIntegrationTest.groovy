package groovymvc

import groovymvc.security.PathRestrictions
import groovymvc.security.RequestAuthenticator
import groovymvc.security.User
import groovymvc.security.UserProvider
import groovymvc.template.TemplateRenderer
import spock.lang.Specification

import javax.servlet.ServletContext

/**
 * @author Daniel Wiell
 */
abstract class AbstractIntegrationTest extends Specification {
    final servletContainer = new ServletContainer(MvcTestApp).start()
    final client = servletContainer.client
    def principalProvider = Mock(UserProvider)

    MvcTestApp app = MvcTestApp.instance
    Controller c = MvcTestApp.controller

    def setup() {
        MvcTestApp.principalProvider = principalProvider
        MvcTestApp.requestAuthenticator = requestAuthenticator
    }

    def cleanup() { servletContainer.stop() }

    TemplateRenderer getRenderer() { MvcTestApp.renderer }

    void setRenderer(TemplateRenderer renderer) { MvcTestApp.renderer = renderer }

    RequestAuthenticator getRequestAuthenticator() {
        new TestAuthenticator()
    }

    static class MvcTestApp extends AbstractMvcFilter {
        static instance
        static Controller controller
        static TemplateRenderer renderer
        static UserProvider principalProvider
        static RequestAuthenticator requestAuthenticator
        ServletContext servletContext

        Controller bootstrap(ServletContext servletContext) {
            this.servletContext = servletContext
            controller = Controller.builder(servletContext)
                    .pathRestrictions(
                    new PathRestrictions(
                            new UserProvider() {
                                User lookup(String username) { principalProvider.lookup(username) }
                            },
                            new RequestAuthenticator() {
                                void authenticate(RequestContext context, Closure callback) {
                                    requestAuthenticator.authenticate(context, callback)
                                }
                            }
                    ))
                    .templateRenderer(
                    new TemplateRenderer() {
                        void render(String template, Map<String, ?> model, RequestContext requestContext) {
                            renderer.render(template, model, requestContext)
                        }
                    })
                    .defaultContentType('text/html')
                    .build()
            instance = this
            return controller
        }
    }


    static class TestAuthenticator implements RequestAuthenticator {
        void authenticate(RequestContext context, Closure callback) {
            callback.call('some username')
        }
    }
}
