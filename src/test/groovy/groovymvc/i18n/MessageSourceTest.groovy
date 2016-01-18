package groovymvc.i18n

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Daniel Wiell
 */
class MessageSourceTest extends Specification {
    def messages = DefaultMessageSource.messageSource('foo', 'bar')

    @Unroll
    def '#key: "#value"'(String key, String value) {
        expect:
        messages.message(key, Locale.ITALIAN) == value
        where:
        key           | value
        'foo.only'    | 'Foo only'
        'bar.only'    | 'Bar only'
        'foo.and.bar' | 'From foo'
        'utf8'        | '新闻'
        'in.none'     | 'in.none'
        'translated'  | 'Tradotto'
    }

    def 'Arguments can be provided'() {
        when:
        def message = messages.message('with.argument', 'A test argument')

        then:
        message == 'Property with argument: A test argument'
    }

    def 'Arguments can be provided, even if message contains no placeholder'() {
        when:
        def message = messages.message('without.argument', 'A test argument')

        then:
        message == 'Property without argument'
    }

    def 'When given non-existing base name, MissingResourceException is thrown'() {
        when:
        DefaultMessageSource.messageSource('foo', 'nonExisting')
        then:
        thrown(MissingResourceException)
    }
}
