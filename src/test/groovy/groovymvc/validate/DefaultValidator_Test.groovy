package groovymvc.validate

import spock.lang.Specification

import static groovymvc.validate.Constraints.*

/**
 * @author wiell
 */
class DefaultValidator_Test extends Specification {
    static final UNUSED_MESSAGE_SOURCE = null
    def validator = new DefaultValidator(UNUSED_MESSAGE_SOURCE)

    def 'Given a bean of a non-registered type, when validating, exception is thrown'() {
        when:
        validator.validate(new DummyValidatable())
        then:
        thrown IllegalStateException
    }

    def 'Given a bean with no constraints, when validating, an empty map is returned'() {
        validator.register(DummyValidatable, [:]
        )
        when:
        def result = validator.validate(new DummyValidatable())

        then:
        result instanceof Map
        result.isEmpty()
    }

    def 'When validating null, exception is thrown'() {
        when:
        validator.validate(null)
        then:
        thrown IllegalArgumentException
    }

    def 'When registering constraint with non-existing path, exception is thrown'() {
        when:
        validator.register(DummyValidatable, [nonExisting: notNull()])
        then:
        thrown IllegalArgumentException
    }

    def 'When registering map where values are not constrains or collections of constraints, exception is thrown'(Map constraints) {
        when:
        validator.register(DummyValidatable, constraints)
        then:
        thrown IllegalArgumentException
        where:
        constraints << [
                [property: null],
                [property: 'Not a constraint'],
                [property: ['Not a constraint']],
                [property: [notNull(), 'Not a constraint']],
                [property: [null]],
        ]
    }

    def 'Given constraint on super class property, when registering constraint,  no exception is thrown'() {
        when:
        validator.register(DummyValidatable, [parentProperty: notNull()])
        then:
        notThrown Exception
    }

    def 'Given a bean with single constraint violation, when validating, a map with the constraint violation is returned'() {
        validator.register DummyValidatable, [property: notNull()]
        def bean = new DummyValidatable()

        when:
        def result = validator.validate(bean)

        then:
        result.size() == 1
        result['property'].size() == 1
        result['property'].first().messageKey == 'notNull'
    }

    def 'Contains invalid value when property constraint is violated'() {
        validator.register DummyValidatable, [property: maxLength(1)]
        def bean = new DummyValidatable(property: 'Too long')

        when:
        def result = validator.validate(bean)

        then:
        result['property'].first().invalidValue == bean.property
    }

    def 'Can fail several constrains for a single property'() {
        validator.register DummyValidatable, [property: [notNull(), custom {
            it == null ? 'customFail' : null
        }]]

        when:
        def result = validator.validate(new DummyValidatable())

        then:
        result['property'].size() == 2
    }

    def 'Can validate nested objects'() {
        validator.register(DummyValidatable, [nested: valid()])
                .register(DummyValidatable2, [property2: notNull()])
        def nestedBean = new DummyValidatable2()
        def rootBean = new DummyValidatable(nested: nestedBean)

        when:
        def result = validator.validate(rootBean)

        then:
        result['nested.property2'].size() == 1
        def nestedViolation = result['nested.property2'].first()
        nestedViolation.messageKey == 'notNull'

        result['nested'].size() == 1
        def rootViolation = result['nested'].first()
        rootViolation.messageKey == 'invalid'
    }

    def 'Can validate objects nested three levels'() {
        validator.register(DummyValidatable, [nested: valid()])
                .register(DummyValidatable2, [nested2: valid()])
                .register(DummyValidatable3, [property3: notNull()])
        def deeplyNestedBeen = new DummyValidatable3()
        def nestedBean = new DummyValidatable2(nested2: deeplyNestedBeen)
        def rootBean = new DummyValidatable(nested: nestedBean)

        when:
        def result = validator.validate(rootBean)

        then:
        result['nested.nested2.property3'].size() == 1
        result['nested.nested2'].size() == 1
        result['nested'].size() == 1
        result['nested'].first().invalidValue == rootBean.nested
    }


    def 'Can validate elements of list'() {
        validator.register(DummyValidatable, [list: every(notNull())])

        def rootBean = new DummyValidatable(list: ['foo', null])
        when:
        def result = validator.validate(rootBean)

        then:
        result.size() == 2
        result['list[1]'].size() == 1
        result['list[1]'].first().invalidValue == null
        result['list[1]'].first().propertyPath == 'list[1]'
        result['list[1]'].first()
        result['list'].size() == 1
        result['list'].first().invalidValue == rootBean.list
    }


    def 'Can validate nested bean within a list'() {
        validator.register(DummyValidatable, [list: every(valid())])
                .register(DummyValidatable2, [property2: notNull()])
        when:
        def result = validator.validate(new DummyValidatable(list: [
                new DummyValidatable2()
        ]))

        then:
        result.size() == 3
        result['list[0].property2'].size() == 1
        result['list[0]'].size() == 1
        result['list'].size() == 1
    }


    def 'No violations when validating elements of null list'() {
        validator.register(DummyValidatable, [list: every(notNull())])

        expect:
        validator.validate(new DummyValidatable()).isEmpty()
    }

    def 'No violations when validating nested null bean'() {
        validator.register(DummyValidatable, [nested: valid()])

        expect:
        validator.validate(new DummyValidatable()).isEmpty()
    }


    def 'Constrained properties without violation errors not included in result'() {
        validator.register(DummyValidatable, [property: notNull(), nested: valid()])
                .register(DummyValidatable2, [property2: notNull()])

        when:
        def result = validator.validate(new DummyValidatable(
                property: 'valid',
                nested: new DummyValidatable2(property2: 'valid'))
        )

        then:
        result.isEmpty()
    }

    def 'Can validate multiple constraints for a property'() {
        validator.register(
                DummyValidatable, [
                property: [email(), maxLength(5)]
        ])

        when:
        def result = validator.validate(new DummyValidatable(property: 'Too long and not an email'))

        then:
        result.size() == 1
        result['property'].size() == 2
    }


    def 'Can validate objects with circular dependencies'() {
        validator.register(DummyValidatable, [property: notNull(), nested: valid()])
                .register(DummyValidatable2, [property2: notNull(), circular: valid()])

        def root = new DummyValidatable()
        def nested = new DummyValidatable2(circular: root)
        root.nested = nested

        when:
        def result = validator.validate(root)

        then:
        result.size() == 3
        result['property']
        result['nested']
        result['nested.property2']
    }


    def 'Custom constraint taking two closure arguments is passed value as first and the object validated as second argument'() {
        def first = null
        def second = null
        validator.register(DummyValidatable, [property: custom { value, bean ->
            first = value
            second = bean
        }])

        def property = 'The property'
        def bean = new DummyValidatable(property: property)
        when:
        validator.validate(bean)
        then:
        first == property
        second == bean
    }


    def 'Custom constraint with 0 arguments throws IllegalArgumentException when registring'() {
        when:
        validator.register(DummyValidatable, [property: custom { -> }])
        then:
        thrown IllegalArgumentException
    }

    def 'Custom constraint with 3 arguments throws IllegalArgumentException when registring'() {
        when:
        validator.register(DummyValidatable, [property: custom { a, b, c -> }])
        then:
        thrown IllegalArgumentException
    }


    static class DummyValidatable extends DummyValidatableParent {
        String property
        DummyValidatable2 nested
        List list
    }

    static class DummyValidatable2 {
        String property2
        DummyValidatable3 nested2
        DummyValidatable circular
    }

    static class DummyValidatable3 {
        String property3
    }

    static class DummyValidatableParent {
        String parentProperty
    }
}
