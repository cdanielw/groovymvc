package groovymvc.validate

import groovymvc.i18n.MessageSource
import groovymvc.validate.internal.ConstraintViolation
import spock.lang.Specification

import static groovymvc.validate.Constraints.newConstraint
import static groovymvc.validate.Constraints.notNull

/**
 * @author Daniel Wiell
 */
@SuppressWarnings("GroovyPointlessArithmetic")
class ConstraintViolation_Test extends Specification {
    private static final Constraint CONSTRAINT = notNull()
    private static final Map UNUSED_CONSTRAINT_REGISTRY = null
    def messageSource = Mock(MessageSource)

    def 'Returns message keys for constraint violation on property'() {
        def root = new RootBean()
        def ctx = createContext(root)
        def violation = new ConstraintViolation(ctx, CONSTRAINT, 'middle', 'middle', root.middle, 'theMessageKey')

        expect:
        violation.messageKeys == [
                "RootBean.middle.theMessageKey",
                "MiddleBean.theMessageKey",
                "theMessageKey",
        ]
    }

    def 'Returns message keys for constraint violation on nested been'() {
        def root = new RootBean(middle: new MiddleBean(leaf: new LeafBean(value: 'invalid value')))
        def ctx = createContext(root).update(root.middle.leaf, 'middle.leaf')
        def violation = new ConstraintViolation(ctx, CONSTRAINT,
                'middle.leaf.value', 'value', root.middle.leaf.value, 'theMessageKey')

        expect:
        violation.messageKeys == [
                "RootBean.middle.leaf.value.theMessageKey",
                "LeafBean.value.theMessageKey",
                "String.theMessageKey",
                "theMessageKey",
        ]
    }

    def 'Gets messages for first defined message key'() {
        def root = new RootBean(middle: new MiddleBean(leaf: new LeafBean(value: 'invalid value')))
        def ctx = createContext(root).update(root.middle.leaf, 'middle.leaf')
        def attributes = [foo: 'bar']
        def violation = new ConstraintViolation(ctx, newConstraint(attributes, { null }),
                'middle.leaf.value', 'value', root.middle.leaf.value, 'theMessageKey')
        def locale = Locale.FRENCH

        when:
        def message = violation.getMessage(locale)

        then:
        1 * messageSource.contains('RootBean.middle.leaf.value.theMessageKey', locale) >> false
        1 * messageSource.contains('LeafBean.value.theMessageKey', locale) >> true
        1 * messageSource.message('LeafBean.value.theMessageKey', locale, {
            'bar' in it
        } as String[]) >> 'resolved message'
        message == 'resolved message'
    }


    def 'Getting message for non-existing key returns key'() {
        def root = new RootBean(middle: new MiddleBean(leaf: new LeafBean(value: 'invalid value')))
        def ctx = createContext(root).update(root.middle.leaf, 'middle.leaf')
        def attributes = [foo: 'bar']
        def violation = new ConstraintViolation(ctx, newConstraint(attributes, { null }),
                'middle.leaf.value', 'value', root.middle.leaf.value, 'theMessageKey')
        def locale = Locale.FRENCH

        when:
        def message = violation.getMessage(locale)

        then:
        message == 'theMessageKey'
    }

    private ValidationContext createContext(RootBean root) {
        ValidationContext.createContext(UNUSED_CONSTRAINT_REGISTRY, root, messageSource)
    }

    static class RootBean {
        MiddleBean middle
    }

    static class MiddleBean {
        LeafBean leaf
    }

    static class LeafBean {
        String value
    }
}
