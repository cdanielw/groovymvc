package groovymvc.validate.internal

import groovymvc.bind.PropertyError
import groovymvc.validate.Constraint
import groovymvc.validate.ValidationContext
/**
 * @author Daniel Wiell
 */
class CascadingAssociation implements Constraint {
    final Map<String, Object> attributes = [:].asImmutable()

    Map<String, List<PropertyError>> check(ValidationContext ctx, String propertyPath, String propertyName) {
        def nestedBean = ctx.leafBean[propertyName]
        if (nestedBean == null)
            return [:]
        def nestedViolations = ctx.update(nestedBean, propertyPath).validate()
        def violation = new ConstraintViolation(ctx, this, propertyPath, propertyName, nestedBean, 'invalid')
        nestedViolations ? [(propertyPath): [violation]] + nestedViolations : null
    }
}
