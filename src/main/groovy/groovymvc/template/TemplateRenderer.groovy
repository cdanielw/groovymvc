package groovymvc.template

import groovymvc.RequestContext
/**
 * @author Daniel Wiell
 */
interface TemplateRenderer {
    void render(String template, Map<String, ?> model, RequestContext requestContext) throws TemplateNotFoundException
}

