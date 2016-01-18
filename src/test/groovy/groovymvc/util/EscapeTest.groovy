package groovymvc.util

import spock.lang.Specification

/**
 * @author Daniel Wiell
 */
class EscapeTest extends Specification {

    def 'Can escape HTML'() {
        expect:
        Escape.html('<') == '&lt;'
    }


    def 'Can escape JavaScript'() {
        expect:
        Escape.javaScript('\b') == '\\b'
    }

}
