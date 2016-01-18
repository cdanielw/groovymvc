package groovymvc.template

import groovy.json.StringEscapeUtils
import groovy.text.SimpleTemplateEngine
import groovymvc.Params
import groovymvc.RequestContext
import groovymvc.bind.Errors
import groovymvc.security.User
import groovymvc.template.sitemesh.SiteMeshRenderer
import groovymvc.util.Escape
/**
 * @author Daniel Wiell
 */
class TemplateSupport {
    private static final String VALID_SCHEME_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+.-";

    private final RequestContext requestContext
    private final Map<String, ?> model
    private final templateEngine = new SimpleTemplateEngine()

    TemplateSupport(Map<String, ?> model, RequestContext requestContext) {
        this.requestContext = requestContext
        this.model = model
    }

    String msg(String key) { message(key) }

    String msg(String key, String arg) { message(key, arg) }

    String msg(String key, String arg0, String arg1) {
        message(key, arg0, arg1)
    }

    String msg(String key, String arg0, String arg1, String arg2) {
        message(key, arg0, arg1, arg2)
    }

    String msg(String key, String arg0, String arg1, String arg2, String arg3) {
        message(key, arg0, arg1, arg2, arg3)
    }

    String msg(String key, String arg0, String arg1, String arg2, String arg3, String arg4) {
        message(key, arg0, arg1, arg2, arg3, arg4)
    }

    String msg(String key, String arg0, String arg1, String arg2, String arg3, String arg4, String arg5) {
        message(key, arg0, arg1, arg2, arg3, arg4, arg5)
    }

    private String message(String key, String... args) {
        html(requestContext.message(key, args))
    }

    String rawMsg(String key) { rawMessage(key) }

    String rawMsg(String key, String arg) { rawMessage(key, arg) }

    String rawMsg(String key, String arg0, String arg1) {
        rawMessage(key, arg0, arg1)
    }

    String rawMsg(String key, String arg0, String arg1, String arg2) {
        rawMessage(key, arg0, arg1, arg2)
    }

    String rawMsg(String key, String arg0, String arg1, String arg2, String arg3) {
        rawMessage(key, arg0, arg1, arg2, arg3)
    }

    String rawMsg(String key, String arg0, String arg1, String arg2, String arg3, String arg4) {
        rawMessage(key, arg0, arg1, arg2, arg3, arg4)
    }

    String rawMsg(String key, String arg0, String arg1, String arg2, String arg3, String arg4, String arg5) {
        rawMessage(key, arg0, arg1, arg2, arg3, arg4, arg5)
    }

    void contentType(String contentType, String characterEncoding = 'UTF-8') {
        requestContext.response.contentType = contentType
        requestContext.response.characterEncoding = characterEncoding
    }

    private String rawMessage(String key, String... args) {
        requestContext.message(key, args)
    }

    String html(String s) {
        Escape.html(s)
    }

    String javaScript(String s) {
        StringEscapeUtils.escapeJavaScript(s)
    }

    String url(String url) {
        if (isAbsoluteUrl(url))
            return url

        def quest = requestContext.request
        if (url.startsWith("/"))
            return (quest.contextPath + url)
        else
            return url
    }

    private static boolean isAbsoluteUrl(String url) {
        int colonPos
        if ((colonPos = url.indexOf(":")) == -1)
            return false

        for (int i = 0; i < colonPos; i++)
            if (VALID_SCHEME_CHARS.indexOf(url.charAt(i)) == -1)
                return false
        return true
    }

    Params getParams() { requestContext.params }

    String getPath() { requestContext.path }

    String getPathPattern() { requestContext.pathPattern }

    Map<String, Object> getFlash() { requestContext.flash }

    User getCurrentUser() { requestContext.currentUser }

    Errors getErrors() { requestContext.errors }

    String value(bean, String path, Errors errors = requestContext.errors) {
        html(rawValue(bean, path, errors))
    }

    String rawValue(bean, String path, Errors errors = requestContext.errors) {
        requestContext.value(bean, path, errors)
    }

    String eval(String s) {
        templateEngine.createTemplate(s).make(model)
    }

    String join(Iterable iterable) {
        join(iterable, ', ')
    }

    String join(Iterable iterable, String separator) {
        iterable.join(separator)
    }

    def List<? extends Enum> enumValues(String enumType) {
        Class.forName(enumType).values()
    }
}
