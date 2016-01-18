package groovymvc.template

/**
 * @author Daniel Wiell
 */
class TemplateNotFoundException extends RuntimeException {
    TemplateNotFoundException(String s) {
        super(s)
    }
}
