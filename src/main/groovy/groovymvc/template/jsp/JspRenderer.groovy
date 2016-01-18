package groovymvc.template.jsp

import groovymvc.Builder
import groovymvc.RequestContext
import groovymvc.template.TemplateNotFoundException
import groovymvc.template.TemplateRenderer
import groovymvc.template.TemplateSupport
/**
 * A {@link TemplateRenderer} forwarding to JSP pages.
 * @author Daniel Wiell
 */
class JspRenderer implements TemplateRenderer {
    private final String rootDir
    private final String fileExtension

    private JspRenderer(TemplateRendererBuilder builder) {
        this.rootDir = builder.rootDir
        this.fileExtension = builder.fileExtension
    }

    static TemplateRendererBuilder builder() { new TemplateRendererBuilder() }

    void render(String template, Map<String, ?> model, RequestContext requestContext) {
        def request = requestContext.request
        model.each { request.setAttribute(it.key as String, it.value) }
        request.setAttribute('g', new TemplateSupport(model, requestContext))
        def templatePath = rootDir + '/' + template + '.' + fileExtension
        def response = requestContext.response
        def status = response.status
        def dispatcher = request.getRequestDispatcher(templatePath)
        dispatcher.forward(request, response)
        if (status != 404 && response.status == 404)
            throw new TemplateNotFoundException("JSP not found: $templatePath")
    }

    static class TemplateRendererBuilder implements Builder<JspRenderer> {
        private String rootDir = '/WEB-INF/jsp'
        private String fileExtension = 'jsp'

        /** The directory where JSP templates are stored at. Defaults to {@code '/WEB-INF/jsp'}. */
        TemplateRendererBuilder rootDir(String rootDir) {
            this.rootDir = rootDir
            return this
        }

        /** The file extension for templates. Defaults to {@code 'jsp'}. */
        TemplateRendererBuilder fileExtension(String fileExtension) {
            this.fileExtension = fileExtension
            return this
        }

        JspRenderer build() {
            new JspRenderer(this)
        }
    }
}
