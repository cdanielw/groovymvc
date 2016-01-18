package groovymvc.util

import spock.lang.Specification

/**
 * @author Daniel Wiell
 */
class PropertiesReaderTest extends Specification {
    def properties = PropertiesReader.forClassPathResource('/properties-reader-test.properties')

    def 'Can read an optional property'() {
        expect:
        properties.optional('foo') == 'Foo'
    }

    def 'Can read a required property'() {
        expect:
        properties.required('foo') == 'Foo'
    }

    def 'When trying to get a non-existing optional property, null is returned'() {
        expect:
        properties.optional('non-existing') == null
    }

    def 'When trying to get a non-existing required property, PropertiesReaderException is thrown'() {
        when:
        properties.required('non-existing')
        then:
        thrown PropertiesReaderException
    }

    def 'When failing to coerce a property, PropertiesReaderException is thrown'() {
        when:
        properties.optional('foo', Long)
        then:
        thrown PropertiesReaderException
    }

    def 'Can coerce a property'() {
        when:
        def value = properties.optional('number', Double)
        then:
        value instanceof Double
        value == 123D
    }


    def 'When trying to load a non-existing properties file, PropertiesReaderException is thrown'() {
        when:
        PropertiesReader.forClassPathResource('/non-existing.properties')
        then:
        thrown PropertiesReaderException
    }

}
