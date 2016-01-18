package groovymvc

import groovy.transform.EqualsAndHashCode
import groovymvc.bind.Binder
import groovymvc.bind.Errors
import groovymvc.bind.PropertyError
import groovymvc.internal.BeanValueEvaluator
import spock.lang.Specification
import spock.lang.Unroll
/**
 * @author Daniel Wiell
 */
class BeanValueEvaluatorTest extends Specification {
    def binder = Mock(Binder)

    @Unroll('Can get value from bean path: #path')
    def 'Can get value from bean path'(bean, String path, String expectation) {
        binder.format(_) >> { it[0] as String }

        when:
        def value = BeanValueEvaluator.value(bean, path, binder, null)

        then: value == expectation
        where:
        bean                                                                     | path                  | expectation
        new Target()                                                             | 'nonexisting'         | null
        new Target(anInt: 123)                                                   | 'anInt'               | '123'
        new Target(nested: new Nested(anInt: 123))                               | 'nested.anInt'        | '123'
        new Target()                                                             | 'nested'              | null
        new Target()                                                             | 'nested.anInt'        | null
        new Target(intList: [123, 456])                                          | 'intList[1]'          | '456'
        new Target(intList: [123, 456])                                          | 'intList'             | ['123', '456']
        new Target(nested: new Nested(longList: [123L, 456L]))                   | 'nested.longList[1]'  | '456'
        new Target(nestedList: [new Nested(anInt: 123), new Nested(anInt: 456)]) | 'nestedList[1].anInt' | '456'
    }

    def 'Uses registered formatter when getting bean value'() {
        binder.format(_ as Date) >> { 'Formatted' }

        when:
        def value = BeanValueEvaluator.value(new Target(date: new Date()), 'date', binder, null)

        then:
        value == 'Formatted'
    }


    def 'If path has error, use invalid value'() {
        binder.format(_) >> { it[0] as String }
        def errors = new Errors([anInt: [new PropertyError(null, null, null, null, null, 'Invalid value', null, [:])]])

        when:
        def value = BeanValueEvaluator.value(new Target(anInt: 123), 'anInt', binder, errors)

        then:
        value == 'Invalid value'
    }


    @EqualsAndHashCode
    static class Target {
        enum AnEnum {
            FOO
        }

        int anInt
        Nested nested
        List<Integer> intList
        List<Nested> nestedList
        Date date
    }

    @EqualsAndHashCode
    static class Nested {
        int anInt
        List<Long> longList
    }

}
