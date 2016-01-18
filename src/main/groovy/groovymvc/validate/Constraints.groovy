package groovymvc.validate

import groovymvc.bind.PropertyError
import groovymvc.validate.internal.CascadingAssociation
import groovymvc.validate.internal.ConstraintViolation
import groovymvc.validate.internal.IndexedConstraints

import java.util.regex.Pattern
/**
 * Container for constraints.
 *
 * @author Daniel Wiell
 */
class Constraints {
    /**
     * Value is not null.
     */
    static Constraint notNull() {
        newConstraint() {
            it == null ?
                    'notNull' : null
        }
    }

    /**
     * Value is not blank.
     */
    static Constraint notBlank() {
        newConstraint() {
            it?.isEmpty() ?
                    'notBlank' : null
        }
    }

    /**
     * Value is of the specified length.
     */
    static Constraint length(int length) {
        newConstraint(length: length) {
            it?.size() != length ?
                    'length' : null
        }
    }

    /**
     * Value is not shorter then the specified length.
     */
    static Constraint minLength(int minLength) {
        newConstraint(minLength: minLength) {
            it?.size() < minLength ?
                    'minLength' : null
        }
    }

    /**
     * Value is not longer then the specified length.
     */
    static Constraint maxLength(int maxLength) {
        newConstraint(maxLength: maxLength) {
            it?.size() > maxLength ?
                    'maxLength' : null
        }
    }

    /**
     * Value is not smaller then what is specified.
     */
    static Constraint min(min) {
        newConstraint(min: min) {
            it < min ?
                    'min' : null
        }
    }

    /**
     * Value is not larger then what is specified.
     */
    static Constraint max(max) {
        newConstraint(max: max) {
            it > max ?
                    'max' : null
        }
    }

    /**
     * Value is an email.
     */
    static Constraint email() {
        newConstraint() {
            EmailValidator.isValid(it) ?
                    null : 'email'
        }
    }

    /**
     * Value is self is to be passed to the validator.
     * This requires the value type itself to have constraints registered.
     */
    static Constraint valid() {
        new CascadingAssociation()
    }

    /**
     * Every element in iterable should be checked against provided constraints.
     */
    static every(Constraint... elementConstraints) {
        new IndexedConstraints(elementConstraints)
    }

    /**
     * Creates a custom constraint.
     * @param attributes (optional) attached constraint attributes. Defaults to an empty map.
     * @param constraintCheck the callback performing the constraint check, taking one or two arguments.
     * The first argument passed is the value, the (optional) second is the object being validated.
     */
    static Constraint custom(Map<String, Object> attributes = [:], Closure constraintCheck) {
        newConstraint(attributes, constraintCheck)
    }

    private static Constraint newConstraint(Map<String, Object> attributes = [:], Closure<String> constraintCheck) {
        if (![1, 2].contains(constraintCheck.maximumNumberOfParameters))
            throw new IllegalArgumentException('constraintCheck must take one or two arguments')
        new PropertyConstraint(attributes, constraintCheck)
    }

    private static class PropertyConstraint implements Constraint {
        final Map<String, Object> attributes
        private final Closure<String> constraintCheck

        PropertyConstraint(Map<String, Object> attributes, Closure<String> constraintCheck) {
            this.attributes = attributes
            this.constraintCheck = constraintCheck
        }

        Map<String, List<PropertyError>> check(ValidationContext ctx, String propertyPath, String propertyName) {
            def value = ctx.leafBean[propertyName]
            String violationMessageKey = determineViolationMessageKey(value, ctx.leafBean)
            def violation = new ConstraintViolation(ctx, this, propertyPath, propertyName, value, violationMessageKey)
            violationMessageKey == null ? null : [(propertyPath): [violation]]
        }

        private String determineViolationMessageKey(Object value, Object bean) {
            def result
            if (constraintCheck.maximumNumberOfParameters == 1)
                result = constraintCheck(value)
            else if (constraintCheck.maximumNumberOfParameters == 2)
                result = constraintCheck(value, bean)
            else
                throw new IllegalStateException('Constraint check does not take one or two arguments')
            if (result == null || (result instanceof String && result != 'true')) return result
            return result ? null : 'invalid'
        }
    }

    /**
     * Based of org.hibernate.validator.constraints.impl.EmailValidator
     *
     * @author Emmanuel Bernard
     * @author Hardy Ferentschik
     * @author Daniel Wiell
     */
    private static class EmailValidator {
        private final static String ATOM = '''[a-z0-9!#$%&'*+/=?^_`{|}~-]'''
        private final static String DOMAIN = "(" + ATOM + "+(\\." + ATOM + "+)*"
        private final static String IP_DOMAIN = "\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\]"

        private final static Pattern PATTERN = Pattern.compile(
                "^" + ATOM + "+(\\." + ATOM + "+)*@"
                        + DOMAIN
                        + "|"
                        + IP_DOMAIN
                        + ')$',
                Pattern.CASE_INSENSITIVE
        )

        static boolean isValid(String value) {
            if (value == null || value.trim().length() == 0)
                return true
            PATTERN.matcher(value).matches()
        }
    }

}
