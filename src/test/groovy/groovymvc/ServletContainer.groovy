package groovymvc

import groovyx.net.http.RESTClient
import org.apache.jasper.servlet.JspServlet
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.Resource

import javax.servlet.DispatcherType
import javax.servlet.ServletContext
/**
 * @author Daniel Wiell
 */
class ServletContainer {
    private static final int PORT = 1234
    public static final URL = "http://localhost:$PORT/some-context-path/"
    private final server = new Server(PORT)
    private final handler = new ServletContextHandler()
    final ServletContext servletContext
    final client = new RESTClient(URL)

    ServletContainer(Class<? extends AbstractMvcFilter> filterClass) {
        System.setProperty('org.apache.jasper.compiler.disablejsr199', 'true')
        servletContext = handler.servletContext
        server.handler = handler
        handler.with {
            classLoader = ServletContainer.classLoader
            contextPath = '/some-context-path'
            baseResource = Resource.newClassPathResource('/')
            sessionHandler = new SessionHandler()
            addServlet(DefaultServlet, "/")
            addServlet(JspServlet, '*.jsp')
            addFilter(new FilterHolder(filterClass), '/*', EnumSet.of(DispatcherType.REQUEST))
        }
        client.handler.failure = {
            it.data = it.entity.content.text
            return it
        }
    }

    ServletContainer start() {
        server.start()
        return this
    }

    void stop() {
        server.stop()
    }
}