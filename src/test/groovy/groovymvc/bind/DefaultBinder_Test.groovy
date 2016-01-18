package groovymvc.bind

import groovy.transform.EqualsAndHashCode
import groovymvc.Params
import groovymvc.i18n.MessageSource
import spock.lang.Specification
import spock.lang.Unroll

import java.text.SimpleDateFormat

class DefaultBinder_Test extends Specification {
    def binder = new DefaultBinder(Mock(MessageSource))
    def target = new Target()

    @Unroll
    def 'Can bind #propertyName'(String propertyName, String paramValue, boundValue) {
        when:
        binder.bind(target, new Params((propertyName): paramValue))
        then:
        target[propertyName] == boundValue
        where:
        propertyName       | paramValue | boundValue
        'anInt'            | '123'      | 123
        'anIntegerWrapper' | '123'      | 123
        'anIntegerWrapper' | '0'        | 0
        'aLong'            | '123'      | 123L
        'aLongWrapper'     | '123'      | 123L
        'aString'          | 'foo'      | 'foo'
        'anEnum'           | 'FOO'      | AnEnum.FOO
        'aBoolean'         | 'true'     | true
        'aBoolean'         | 'on'       | true
        'aBoolean'         | 'false'    | false
        'aBoolean'         | 'foo'      | true
        'aBoolean'         | ''         | false
        'aBooleanWrapper'  | 'true'     | Boolean.TRUE
    }

    @Unroll
    def 'Can bind #propertyName to empty property'(String propertyName, startValue, boundValue) {
        target[propertyName] = startValue

        when:
        binder.bind(target, new Params((propertyName): [:]))
        then:
        target[propertyName] == boundValue
        where:
        propertyName       | startValue     | boundValue
        'anInt'            | 1              | 0
        'anIntegerWrapper' | new Integer(1) | null
        'aLong'            | 1L             | 0
        'aLongWrapper'     | new Long(1)    | null
        'aString'          | 'value'        | null
        'anEnum'           | AnEnum.FOO     | null
        'aBoolean'         | true           | false
        'aBooleanWrapper'  | Boolean.TRUE   | null
    }


    def 'Can bind nested properties'() {
        when:
        binder.bind(target, new Params(nested: [anInt: '123']))
        then:
        target.nested.anInt == 123
    }

    def 'Successfully binding returns empty map'() {
        expect:
        binder.bind(target, new Params(nested: [anInt: '123'])).isEmpty()
    }

    def 'Failing a binding returns PropertyError'() {
        when:
        def result = binder.bind(target, new Params(anInt: 'not an int'))

        then:
        result.size() == 1
        result['anInt'] instanceof List
        result['anInt'].size() == 1
        result['anInt'].first() instanceof PropertyError
        result['anInt'].first().propertyName == 'anInt'
        def keys = result['anInt'].first().messageKeys
        keys
    }

    def 'Failing a nested binding returns PropertyError'() {
        when:
        def failures = binder.bind(target, new Params(nested: [anInt: 'not an int']))

        then:
        failures.size() == 1
        failures['nested.anInt'] instanceof List
        failures['nested.anInt'].size() == 1
        failures['nested.anInt'].first() instanceof PropertyError
        failures['nested.anInt'].first().propertyName == 'anInt'
    }

    def 'Can bind list of properties'() {
        when:
        binder.bind(target, new Params(intList: ['123', '456', '789']))
        then:
        target.intList == [123, 456, 789]
    }

    def 'Can bind list of properties with empty values'() {
        when:
        binder.bind(target, new Params(intList: [[:], '456', '789']))
        then:
        target.intList == [null, 456, 789]
    }

    def 'Failing binding in list returns PropertyError'() {
        when:
        def failures = binder.bind(target, new Params(intList: ['123', 'Not an int']))
        then:
        failures.size() == 1
        failures['intList[1]'].size() == 1
    }

    def 'Failing binding in nested object from list returns PropertyError'() {
        when:
        def failures = binder.bind(target, new Params(nestedList: [[:], [anInt: 'Not an int']]))
        then:
        failures.size() == 1
        failures['nestedList[1].anInt'].size() == 1
    }

    def 'Can bind an empty list'() {
        when:
        binder.bind(target, new Params(intList: []))
        then:
        target.intList == []
    }

    def 'A property of an unregistered type fails, with the property in the error'() {
        when:
        def failures = binder.bind(target, new Params(unregistered: 'foo'))

        then:
        failures['unregistered'].size() == 1
        failures['unregistered'].first().propertyName == 'unregistered'
    }


    def 'Can format using a registered formatter'() {
        def unusedConverter = { new Date() }
        binder.register(Date, unusedConverter,
                { new SimpleDateFormat('dd MM yyyy').format(it as Date) })

        when:
        def value = binder.format(Date.parse('yyyy MM dd', '2013 10 18'))

        then:
        value == '18 10 2013'
    }


    def 'Can format a subtype of registered formatter'() {
        def unusedConverter = { new Date() }
        binder.register(Date, unusedConverter,
                { new SimpleDateFormat('dd MM yyyy').format(it as Date) })

        when:
        def value = binder.format(new java.sql.Date(Date.parse('yyyy MM dd', '2013 10 18').time))

        then:
        value == '18 10 2013'
    }

    def 'Formatting a type without registered formatter, type is coerced to String'() {
        def date = new Date()
        expect:
        binder.format(date) == date as String
    }


    def 'Unspecified properties on nested properties are unchanged'() {
        target = new Target(nested: new Nested(aLong: 456L))
        when:
        binder.bind(target, new Params(nested: [anInt: '123']))
        then:
        target.nested.anInt == 123
        target.nested.aLong == 456L
    }

    def 'Uninitialized nested property being an interface throws exception'() {
        when:
        def failures = binder.bind(target, new Params(uninitializedNestedInterface: [foo: 'bar']))

        then:
        failures.size() == 1
        failures['uninitializedNestedInterface'].size() == 1
    }

    def 'Bind raw list fails'() {
        when:
        def failures = binder.bind(target, new Params(rawList: ['123', '456', '789']))

        then:
        failures.size() == 3
        failures['rawList[0]']
        failures['rawList[1]']
        failures['rawList[2]']
    }

    def 'Bind list of properties with incompatible types fails'() {
        when:
        def failures = binder.bind(target, new Params(intList: ['123', 'asdf', '789']))

        then:
        target.intList == [123, null, 789]
        failures['intList[1]'].size() == 1
        failures['intList[1]'].first().propertyPath == 'intList[1]'
    }

    def 'Can bind list of properties in nested object'() {
        when:
        binder.bind(target, new Params(nested: [longList: ['123', '456', '789']]))
        then:
        target.nested.longList == [123, 456, 789]
    }

    def 'Can bind list of nested properties'() {
        when:
        binder.bind(target, new Params(nestedList: [[anInt: '123'], [anInt: '456']]))
        then:
        target.nestedList == [new Nested(anInt: 123), new Nested(anInt: 456)]
    }

    def 'Can bind sorted set of properties'() {
        when:
        binder.bind(target, new Params(sortedSet: ['456', '123', '789']))
        then:
        target.sortedSet instanceof SortedSet
        target.sortedSet as List == [123, 456, 789]
    }

    def 'Can bind collection of properties'() {
        when:
        binder.bind(target, new Params(collection: ['123', '456', '789']))
        then:
        target.collection instanceof List
        target.collection == [123, 456, 789]
    }

    def 'Can bind set of properties'() {
        when:
        binder.bind(target, new Params(set: ['456', '123', '789']))
        then:
        target.set instanceof HashSet
        target.set == [123, 456, 789] as Set
    }

    def 'Can bind vector of properties'() {
        when:
        def failures = binder.bind(target, new Params(vector: ['123', '456', '789']))
        then:
        failures.size() == 3
        failures['vector[0]']
        failures['vector[1]']
        failures['vector[2]']
    }

    def 'Can bind deeply nested properties'() {
        when:
        binder.bind(target, new Params(nested: [nested: [anInt: '123']]))
        then:
        target.nested.nested.anInt == 123
    }


    def 'Binding failure contains invalid value'() {
        when:
        def result = binder.bind(target, new Params(anInt: 'not an int'))

        then:
        result['anInt'].first().invalidValue == 'not an int'
    }

    def 'Can bind false booleans'() {
        target.aBoolean = true

        when:
        binder.bind(target, new Params(aBoolean: 'false'))

        then:
        !target.aBoolean
    }


    def 'Can bind a map'() {
        when:
        binder.bind(target, new Params(anInitializedMap: [a: 'foo', b: 'bar']))
        then:
        target.anInitializedMap.a == 'foo'
        target.anInitializedMap.b == 'bar'
    }


    def 'Can bind a typed map'() {
        when:
        binder.bind(target, new Params(aTypedMap: [FOO: '123', BAR: '456']))
        then:
        target.aTypedMap[AnEnum.FOO] == 123
        target.aTypedMap[AnEnum.BAR] == 456
    }

    def 'Can bind a map with nested values'() {
        when:
        binder.bind(target, new Params(aNestedMap: [a: [nested: [anInt: '123']]]))
        then:
        target.aNestedMap.a.nested.anInt == 123
    }

    def 'Can bind a typed map in nested'() {
        when:
        binder.bind(target, new Params(nested: [aTypedMap: [FOO: '123', BAR: '456']]))
        then:
        target.nested.aTypedMap[AnEnum.FOO] == 123
    }

    def 'Can bind a collection of typed maps'() {
        when:
        binder.bind(target, new Params('typedMapList': [[FOO: '123'], [BAR: '345']]))
        then:
        target.typedMapList[0][AnEnum.FOO] == 123
        then:
        target.typedMapList[1][AnEnum.BAR] == 345
    }

    def 'Failure binding typed map key returns error'() {
        when:
        def failures = binder.bind(target, new Params(aTypedMap: [invalidKey: '123']))

        then:
        failures['aTypedMap.invalidKey'].first().invalidValue == 'invalidKey'
    }

    def 'Failure binding typed map value returns error'() {
        when:
        def failures = binder.bind(target, new Params(aTypedMap: [FOO: 'not a number']))

        then:
        failures['aTypedMap.FOO'].first().invalidValue == 'not a number'
    }

    def 'Can bind an uninitialized map'() {
        when:
        binder.bind(target, new Params(anUninitializedMap: [a: 'foo', b: 'bar']))
        then:
        target.anUninitializedMap.a == 'foo'
        target.anUninitializedMap.b == 'bar'
    }

    @EqualsAndHashCode
    @SuppressWarnings("GroovyUnusedDeclaration")
    static class Target {
        int anInt
        Integer anIntegerWrapper
        long aLong
        Long aLongWrapper
        String aString
        AnEnum anEnum
        InputStream unregistered
        Nested nested
        Nested initializedNested = new Nested()
        Readable uninitializedNestedInterface
        List<Integer> intList
        List rawList = [123]
        List<Nested> nestedList
        List<Map<AnEnum, Integer>> typedMapList
        Set<Integer> set
        SortedSet<Integer> sortedSet
        Collection<Integer> collection
        Vector<Vector> vector
        java.sql.Date date
        boolean aBoolean
        Boolean aBooleanWrapper
        Map anInitializedMap = [:]
        Map anUninitializedMap
        Map<AnEnum, Integer> aTypedMap
        Map<String, Nested> aNestedMap
    }

    @EqualsAndHashCode
    static class Nested {
        int anInt
        long aLong
        List<Long> longList
        Map<AnEnum, Integer> aTypedMap
        Nested nested
    }

    enum AnEnum {
        FOO, BAR
    }

}

