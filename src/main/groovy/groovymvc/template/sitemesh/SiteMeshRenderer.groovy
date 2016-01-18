package groovymvc.template.sitemesh

import groovymvc.Builder
import groovymvc.RequestContext
import groovymvc.internal.SimpleFilterConfig
import groovymvc.template.TemplateRenderer
import groovymvc.template.jsp.JspRenderer
import org.sitemesh.DecoratorSelector
import org.sitemesh.content.Content
import org.sitemesh.content.ContentProcessor
import org.sitemesh.content.ContentProperty
import org.sitemesh.content.tagrules.TagBasedContentProcessor
import org.sitemesh.content.tagrules.TagRuleBundle
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle
import org.sitemesh.content.tagrules.html.DivExtractingTagRuleBundle
import org.sitemesh.webapp.SiteMeshFilter
import org.sitemesh.webapp.WebAppContext
import org.sitemesh.webapp.contentfilter.BasicSelector
import org.sitemesh.webapp.contentfilter.ResponseMetaData

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletContext
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
/**
 * A {@link TemplateRenderer} allowing templates to be decorated using SiteMesh, and custom tags to be used.
 *
 * <p>A decorator will be selected specifying {@code <meta name="layout" content="[template]"/>} in a template.
 * Both the content template and the layout template will be rendered by the provided {@link TemplateRenderer}.
 *
 * <p>A number of attributes is extracted from the document and put on the model as 'attrs', when rendering
 * decorators:
 * <ul>
 * <li><b><code>body</code></b>: The contents of the <code>&lt;body&gt;</code> element.</li>
 * <li><b><code>title</code></b>: The contents of the <code>&lt;title&gt;</code> element.</li>
 * <li><b><code>head</code></b>: The contents of the <code>&lt;head&gt;</code> element,
 * <li><b><code>meta.XXX</code></b>: Each <code>&lt;meta&gt;</code> tag,
 * where <code>XXX</code> is the <code>name</code> of the tag.</li>
 * <li><b><code>meta.http-equiv.XXX</code></b>: Each <code>&lt;meta http-equiv&gt;</code> tag,
 * where <code>XXX</code> is the <code>http-equiv</code> attribute of the tag.</li>
 * <li><b><code>body.XXX</code></b>: Each attribute of the <code>&lt;body&gt;</code> tag,
 * where <code>XXX</code> is attribute name (e.g. body.bgcolor=white).</li>
 * </ul>
 *
 * <p>In additional to these, divs with id attributes are turned into attributes.
 * The body of {@code <div id="foo">} is available through ${attrs['div'].foo}.
 *
 * <p>Attributes can be outputted in a decorator through the 'attrs' model element. E.g. {@code ${attrs.[property-name]}}.
 * The model used to render the content template is also available when rendering any decorator or custom tag.
 *
 * <p>Example layout template (/WEB-INF/jsp/layout/main.jsp):
 * <blockquote><pre>
 * &lt;html&gt;
 *   &lt;head&gt;
 *     &lt;title&gt;${attrs.title}&lt;/title&gt;
 *     ${attrs.head}&nbsp;
 *   &lt;/head&gt;
 *   &lt;body&gt;
 *     Before
 *     ${attrs.body}&nbsp;
 *     After
 *   &lt;/body&gt;
 * &lt;/html&gt;
 * </pre></blockquote>
 *
 * <p>Example content template (/WEB-INF/jsp/foo/bar.jsp):
 * <blockquote><pre>
 * &lt;html&gt;
 *   &lt;head&gt;
 *     &lt;title&gt;A page title&lt;/title&gt;
 *     &lt;meta name="layout" content="layout/main"/&gt;
 *   &lt;/head&gt;
 *   &lt;body&gt;
 *     Some content
 *   &lt;/body&gt;
 * &lt;/html&gt;
 * </pre></blockquote>
 *
 * <p>Custom tags can be created by registering them in the constructor, and creating a template for the tag, at
 * the correct place, based on the following convention:
 * <ul>
 * <li>Tag without namespace: {@code [template-root-dir]/[tag-name].[template-extension]}</li>
 * <li>Tag with namespace: {@code [template-root-dir]/[namespace]/[tag-name].[template-extension]}</li>
 * </ul>
 *
 * <p>Example creating renderer with custom tags:
 * <blockquote><pre>
 * SiteMeshRenderer.builder(servletContext)
 *         .renderer(MvelRenderer.builder(servletContext)
 *         .tags('foo:bar')
 *         .build()
 * </pre></blockquote>
 *
 * <p>Example custom tag (/WEB-INF/mvel/foo/bar.mvel):
 * <blockquote><pre>
 * &lt;h1&gt;${attrs.baz}&lt;/h1&gt;
 * &lt;strong&gt;${attrs.body}&lt;/strong&gt;
 * </pre></blockquote>
 *
 * <p>Example custom tag usage (/WEB-INF/mvel/test.mvel):
 * <blockquote><pre>
 * &lt;html&gt;
 *   &lt;body&gt;
 *     &lt;foo:bar baz="Hello"&gt;World&lt;/foo:bar&gt;
 *   &lt;/body&gt;
 * &lt;/html&gt;
 * </pre></blockquote>
 *
 * <p><strong>Note:</strong> While custom tags are convenient, they are not as
 * performant as template language specific solutions. The are applied after the template is rendered, as opposed
 * to native tags.
 *
 * <p>This class is dependent on SiteMesh 3, which is an optional dependency.
 * Make sure to provide the following dependency:
 * <blockquote><pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;org.sitemesh&lt;/groupId&gt;
 *     &lt;artifactId&gt;sitemesh&lt;/artifactId&gt;
 *     &lt;version&gt;${sitemesh.version}&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre></blockquote>
 *
 * @author Daniel Wiell
 */
class SiteMeshRenderer implements TemplateRenderer {
    private static final String MODEL_ATTRIBUTE_NAME = SiteMeshRenderer.name + ".model"
    private final TemplateRenderer renderer
    private final Filter filter

    private SiteMeshRenderer(TemplateRendererBuilder builder) {
        this.renderer = builder.renderer
        def contentProcessor = new TagBasedContentProcessor([
                new CoreHtmlTagRuleBundle(),
                new DivExtractingTagRuleBundle(),
                new CustomTagRuleBundle(builder.customTags)
        ] as TagRuleBundle[])
        filter = new GroovyMvcSiteMeshFilter(renderer, contentProcessor)
        filter.init(new SimpleFilterConfig(builder.servletContext, 'SiteMesh'))
    }

    static TemplateRendererBuilder builder(ServletContext servletContext) {
        verifySiteMeshDependency()
        new TemplateRendererBuilder(servletContext)
    }

    void render(String template, Map<String, ?> model, RequestContext requestContext) {
        reset(requestContext)
        requestContext.request.setAttribute(MODEL_ATTRIBUTE_NAME, model)
        def filterChain = { HttpServletRequest req, HttpServletResponse resp ->
            RequestContext wrappedContext = requestContext.wrap(req, resp)
            renderer.render(template, model, wrappedContext)
        } as FilterChain
        filter.doFilter(requestContext.request, requestContext.response, filterChain)
    }

    private void reset(RequestContext requestContext) {
        requestContext.request.attributeNames.findAll { it.startsWith('org.sitemesh') }.each { String name ->
            requestContext.request.removeAttribute(name)
        }
    }

    private static void verifySiteMeshDependency() {
        try {
            Class.forName("org.sitemesh.webapp.SiteMeshFilter")
        } catch (Exception ignore) {
            throw new IllegalStateException("${SiteMeshRenderer.name} is dependent on SiteMesh 3.\n" +
                    "Please add dependency:" +
                    "\n" +
                    "        <dependency>\n" +
                    "            <groupId>org.sitemesh</groupId>\n" +
                    "            <artifactId>sitemesh</artifactId>\n" +
                    "            <version>\${sitemesh.version}</version>\n" +
                    "        </dependency> \n")
        }
    }

    static class TemplateRendererBuilder implements Builder<SiteMeshRenderer> {
        private final ServletContext servletContext
        private TemplateRenderer renderer = JspRenderer.builder().build()
        private List<String> customTags = []

        TemplateRendererBuilder(ServletContext servletContext) {
            this.servletContext = servletContext
        }

        TemplateRendererBuilder renderer(TemplateRenderer renderer) {
            this.renderer = renderer
            return this
        }

        TemplateRendererBuilder renderer(Builder<? extends TemplateRenderer> renderer) {
            this.renderer = renderer.build()
            return this
        }

        TemplateRendererBuilder tags(String... names) {
            customTags.addAll(names as List)
            return this
        }

        SiteMeshRenderer build() {
            return new SiteMeshRenderer(this)
        }
    }

    private static class MetaTagDecoratorSelector implements DecoratorSelector<WebAppContext> {
        private final TemplateRenderer renderer

        MetaTagDecoratorSelector(TemplateRenderer renderer) {
            this.renderer = renderer
        }

        public String[] selectDecoratorPaths(Content content, WebAppContext context) {
            String decorator = content.getExtractedProperties()
                    .getChild("meta")
                    .getChild("layout")
                    .getValue()

            if (decorator != null)
                return decorator.split(",")*.trim().findAll { it }

            return new String[0]
        }
    }

    private static class GroovyMvcSiteMeshFilter extends SiteMeshFilter {
        private final TemplateRenderer renderer
        private final ContentProcessor contentProcessor

        GroovyMvcSiteMeshFilter(TemplateRenderer renderer, ContentProcessor contentProcessor) {
            super(new GroovyMvcSiteMeshSelector(), contentProcessor,
                    new MetaTagDecoratorSelector(renderer)
            )
            this.renderer = renderer
            this.contentProcessor = contentProcessor
        }

        protected WebAppContext createContext(String contentType, HttpServletRequest request, HttpServletResponse response, ResponseMetaData metaData) {
            new GroovyMvcSiteMeshContext(contentType, request, response,
                    filterConfig.servletContext, contentProcessor, metaData, renderer)
        }
    }

    private static class GroovyMvcSiteMeshSelector extends BasicSelector {
        GroovyMvcSiteMeshSelector() { super(true) }

        boolean shouldBufferForContentType(String contentType, String mimeType, String encoding) { true }
    }

    private static class GroovyMvcSiteMeshContext extends WebAppContext {
        private final TemplateRenderer renderer

        GroovyMvcSiteMeshContext(String contentType, HttpServletRequest request, HttpServletResponse response,
                                 ServletContext servletContext, ContentProcessor contentProcessor, ResponseMetaData metaData,
                                 TemplateRenderer renderer) {
            super(contentType, request, response, servletContext, contentProcessor, metaData)
            this.renderer = renderer
        }

        protected void dispatch(HttpServletRequest request, HttpServletResponse response, String path)
                throws ServletException, IOException {
            RequestContext requestContext = request.getAttribute(RequestContext.name) as RequestContext
            def model = request.getAttribute(MODEL_ATTRIBUTE_NAME) as Map
            def siteMeshProperties = extractSiteMeshProperties(request)
            renderer.render(path, model + siteMeshProperties, requestContext.wrap(request, response))
        }

        private Map extractSiteMeshProperties(HttpServletRequest request) {
            def content = request.getAttribute(Content.name) as Content
            [attrs: extract(content.extractedProperties)]
        }

        private extract(ContentProperty contentProperty) {
            def p = [:]
            contentProperty.children.each {
                p[it.name] = it.children.iterator().hasNext() ? extract(it) : it.value
            }
            return p
        }
    }

}