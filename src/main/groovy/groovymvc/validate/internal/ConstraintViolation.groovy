package groovymvc.validate.internal

import groovymvc.bind.PropertyError
import groovymvc.validate.Constraint
import groovymvc.validate.ValidationContext

/**
 * @author wiell
 */
class ConstraintViolation extends PropertyError {

    ConstraintViolation(ValidationContext ctx, Constraint constraint, String propertyPath, String propertyName,
                        Object invalidValue, String messageKey) {
        super(ctx.messageSource, ctx.rootBean, ctx.leafBean, propertyPath, propertyName, invalidValue, messageKey, constraint.attributes)
    }
}

