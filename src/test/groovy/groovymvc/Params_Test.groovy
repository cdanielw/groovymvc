package groovymvc

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Daniel Wiell
 */
class Params_Test extends Specification {
    @Unroll
    def '"#s" results in #m'(String s, Map m) {
        expect:
        Params.parse(s) == m
        where:
        s                                  | m
        ''                                 | [:]
        'a=foo'                            | [a: 'foo']
        'a='                               | [a: [:]]
        'a'                                | [a: [:]]
        'a&a'                              | [a: [:]]
        'a=&a='                            | [a: [:]]
        'a=foo&a=bar'                      | [a: 'foo']
        'a=foo&a=bar&a=baz'                | [a: 'foo']
        'a[]=foo&a=bar'                    | [a: ['foo']]
        'a[0].b=foo&a[0].b=bar'            | [a: [[b: 'foo']]]
        'a[]'                              | [a: [[:]]]
        'a=foo&c=bar'                      | [a: 'foo', c: 'bar']
        'a=foo&&c=bar'                     | [a: 'foo', c: 'bar']
        'a.b=foo'                          | [a: [b: 'foo']]
        'a.b=foo'                          | [a: [b: 'foo']]
        'a.a=foo&a.b=bar'                  | [a: [a: 'foo', b: 'bar']]
        'a.a.a=foo&a.a.b=bar'              | [a: [a: [a: 'foo', b: 'bar']]]
        'a.b=foo&a.c=bar'                  | [a: [b: 'foo', c: 'bar']]
        'a[]=foo'                          | [a: ['foo']]
        'a[]=foo&a[]=bar'                  | [a: ['foo', 'bar']]
        'a[].b=foo&a[].b=bar&a[].c=baz'    | [a: [[b: 'foo'], [b: 'bar'], [c: 'baz']]]
        'a[0].b=foo&a[1].b=bar&a[0].c=baz' | [a: [[b: 'foo', c: 'baz'], [b: 'bar']]]
        'a[0]=foo'                         | [a: ['foo']]
        'a[1]=foo'                         | [a: [[:], 'foo']]
        'a[0]=foo&a[1]=bar'                | [a: ['foo', 'bar']]
        'a[1]=foo&a[0]=bar'                | [a: ['bar', 'foo']]
        'a[0].b=foo'                       | [a: [[b: 'foo']]]
        'a[0].b=foo&a[1].b=bar'            | [a: [[b: 'foo'], [b: 'bar']]]
        'a[0]=foo&a[0]=bar'                | [a: ['bar']]
        'a[0].b=foo&a[0].c=bar'            | [a: [[b: 'foo', c: 'bar']]]
        'a.b[0].c=foo&a.b[1].c=bar'        | [a: [b: [[c: 'foo'], [c: 'bar']]]]
        'a[0].b[]=foo&a[0].b[]=bar'        | [a: [[b: ['foo', 'bar']]]]
        '_a[]'                             | [a: []]
        'a[]=foo&_a[]'                     | [a: ['foo']]
        'a[0]._b[]'                        | [a: [[b: []]]]
    }

    @Unroll
    def 'Throws exception when invalid param string: #s'(String s) {
        when:
        Params.parse(s)
        then:
        thrown(ParamsException)
        where:
        s << [
                'a=foo&a.b.c=bar',
                'a.b=foo&a.b.c.d=bar',
                'a=foo&a.b=bar',
                'a.b=foo&a.b.c=bar',
                'a.b=bar&a=foo',
                'a.b.c.d=bar&a.b=foo',
                'a[]=foo&a.b=foo',
                'a.b=foo&a[]=foo',
                'a=foo&a[]=bar'

        ]
    }

    @Unroll
    def '#string excluding #excluding results in #expected'(String string, String excluding, Map expected) {
        expect:
        Params.parse(string).excluding(excluding) == expected
        where:
        string                             | excluding | expected
        'a=foo'                            | 'a'       | [:]
        'a=foo&b=bar'                      | 'b'       | [a: 'foo']
        'a=foo&b.c=bar'                    | 'b'       | [a: 'foo']
        'a=foo&b.c=bar'                    | 'b.c'     | [a: 'foo', b: [:]]
        'a=foo&b.c=bar'                    | 'b..c'    | [a: 'foo', b: [c: 'bar']]
        'a=foo'                            | 'b'       | [a: 'foo']
        'a=foo'                            | 'b.c'     | [a: 'foo']
        'a=foo'                            | 'a.c'     | [a: 'foo']
        'a[].b=foo&a[].b=bar'              | 'a'       | [:]
        'a[0].b=foo&a[1].b=bar'            | 'a.b'     | [a: [[:], [:]]]
        'a[].b=foo&a[].b=bar&a[].c=baz'    | 'a.b'     | [a: [[:], [:], [c: 'baz']]]
        'a[0].b=foo&a[1].b=bar&a[2].c=baz' | 'a.b'     | [a: [[:], [:], [c: 'baz']]]
        'a.b[0].c=foo&a.b[0].d=bar'        | 'a.b.c'   | [a: [b: [[d: 'bar']]]]
        'a.b[]'                            | 'a.b'     | [a: [:]]
        'a.b[]'                            | 'a.b.c'   | [a: [b: [[:]]]]
    }


    @Unroll
    def '#string including #including results in #expected'(String string, String including, Map expected) {
        expect:
        Params.parse(string).including(including) == expected
        where:
        string                   | including | expected
        'a=foo'                  | 'a'       | [a: 'foo']
        'a=foo'                  | 'b'       | [:]
        'a.b=bar'                | 'a'       | [a: [b: 'bar']]
        'a=foo&b=bar'            | 'b'       | [b: 'bar']
        'a=foo&b.c=bar'          | 'b'       | [b: [c: 'bar']]
        'a=foo&b.c=bar'          | 'b.c'     | [b: [c: 'bar']]
        'a=foo&b.c=bar&&b.d=baz' | 'b.c'     | [b: [c: 'bar']]
        'a=foo&b.c=bar'          | 'b.d'     | [b: [:]]
        'a=foo'                  | 'a.b'     | [a: [:]]
        'a[].b=foo'              | 'a'       | [a: [[b: 'foo']]]
        'a[].b=foo&&a[].c=bar'   | 'a.b'     | [a: [[b: 'foo'], [:]]]
        'a.b[]'                  | 'a.b'     | [a: [b: [[:]]]]
    }


    def 'Can parse a map'() {
        expect:
        Params.parse([foo: ['bar'] as String[]]) == [foo: 'bar']
    }


    @Unroll
    def 'Value for path #path in #params is #expectation'(Map params, String path, expectation) {
        when:
        def value = new Params(params).optional(path)

        then:
        value == expectation
        where:
        params                | path              | expectation
        [foo: 'bar']          | 'nonexisting'     | null
        [foo: 'bar']          | 'foo'             | 'bar'
        [foo: [bar: 'baz']]   | 'foo'             | [bar: 'baz']
        [foo: [bar: 'baz']]   | 'foo.bar'         | 'baz'
        [foo: [bar: 'baz']]   | 'foo..bar'        | null
        [foo: [bar: 'baz']]   | 'nonexisting.bar' | null
        [foo: ['bar', 'baz']] | 'foo[1]'          | 'baz'
        [foo: ['bar', 'baz']] | 'foo[]'           | null
        [foo: ['bar', 'baz']] | 'foo[2]'          | null
        [foo: ['bar', 'baz']] | 'foo[not an int]' | null
        [foo: [[bar: 'baz']]] | 'foo[0].bar'      | 'baz'
    }

    def 'If required value is null, ParamsException is thrown'() {
        when:
        new Params([:]).required('foo')
        then:
        thrown ParamsException
    }

    def 'If getting value and value is not of expected type, ParamsException is thrown'() {
        when:
        new Params([foo: 'not an int']).optional('foo', int)
        then:
        thrown ParamsException
    }

    def 'When unable to coerce to type, ParamsException is thrown'() {
        when:
        new Params([foo: 'bar']).required('foo', Author)
        then:
        thrown ParamsException
    }

    @Unroll
    def 'When expecting type Map and type is #value.class.simpleName, ParamsException is thrown'(value) {
        when:
        Params.expectType(value, Map, 'Wrong type')
        then:
        thrown ParamsException
        where:
        value << [
                'Some string', ['Some list']
        ]
    }

    def 'If value is of type #value.class.simpleName and expected type is #type, then #expectation is returned'(value, Class type, expectation) {
        when:
        def result = Params.expectType(value, type, 'Wrong type')

        then:
        result.class == expectation.class
        result == expectation

        where:
        value           | type | expectation
        ['Some string'] | List | ['Some string']
        'Some string'   | List | ['Some string']
        [foo: 'bar']    | List | [[foo: 'bar']]
    }

    def 'Can get value with expected type'(Map params, String path, Class type, expectation) {
        when:
        def value = new Params(params).optional(path, type)

        then:
        value == expectation

        where:
        params                | path  | type | expectation
        [foo: '123']          | 'foo' | int  | 123
        [foo: [bar: 'baz']]   | 'foo' | Map  | [bar: 'baz']
        [foo: ['bar', 'baz']] | 'foo' | List | ['bar', 'baz']
    }

    @Unroll
    def '#params .subParams(#path) == #expectation'() {
        when:
        def value = new Params(params).subParams(path)

        then:
        value == expectation

        where:
        params                     | path                   | expectation
        [a: [b: 'c']]              | 'a'                    | [b: 'c']
        [a: 'b']                   | 'a'                    | [:]
        [a: [b: 'c'], d: [e: 'f']] | ['a', 'd'] as String[] | [b: 'c', e: 'f']
    }


    def 'Can create sub-params based of map'() {
        def params = new Params(a: [b: 'c'])
        expect:
        new Params(params.a) == [b: 'c']
    }


    def 'Can trim Strings'() {
        def params = new Params([a: ' foo ', b: [c: ' bar ']])

        when:
        def trimmedParams = params.trim()

        then:
        trimmedParams.a == 'foo'
        trimmedParams.b.c == 'bar'

    }


    def 'Can trim lists'() {
        def params = new Params([a: [' foo '], b: [[c: ' bar ']]])

        when:
        def trimmedParams = params.trim()

        then:
        trimmedParams.a.first() == 'foo'
        trimmedParams.b.first().c == 'bar'

    }
}
