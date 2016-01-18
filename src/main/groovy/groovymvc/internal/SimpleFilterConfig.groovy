package groovymvc.internal

import javax.servlet.FilterConfig
import javax.servlet.ServletContext

/**
 * @author Daniel Wiell
 */
class SimpleFilterConfig implements FilterConfig{
    private final Map<String, String> initParams
    final ServletContext servletContext
    final String filterName

    SimpleFilterConfig(ServletContext servletContext, String filterName, Map<String, String> initParams = [:]) {
        this.initParams = initParams
        this.servletContext = servletContext
        this.filterName = filterName
    }

    String getInitParameter(String name) {
        initParams[name]
    }

    Enumeration<String> getInitParameterNames() {
        Collections.enumeration(initParams.keySet())
    }
}
