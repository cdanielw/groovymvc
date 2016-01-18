package groovymvc.validate.internal

import groovymvc.bind.PropertyError
import groovymvc.validate.Constraint
import groovymvc.validate.ValidationContext
/**
 * @author Daniel Wiell
 */
class IndexedConstraints implements Constraint {
    final Map<String, Object> attributes = [:].asImmutable()
    final List<Constraint> constraints

    IndexedConstraints(Constraint... constraints) {
        this.constraints = Arrays.asList(constraints)
    }

    Map<String, List<PropertyError>> check(ValidationContext ctx, String propertyPath, String propertyName) {
        def violationsByPath = [:]
        def propertyValue = ctx.leafBean[propertyName]
        propertyValue?.eachWithIndex { element, i ->
            def elementPath = "${propertyPath}[$i]"
            def elementContext = ctx.update(new ElementHolder(element), propertyPath)
            violationsByPath.putAll(validateElement(elementContext, elementPath))
        }
        violationsByPath.values().removeAll { !it }
        if (violationsByPath)
            violationsByPath[propertyPath] = [new ConstraintViolation(ctx, this, propertyPath, propertyName, propertyValue, 'invalid')]
        return violationsByPath
    }

    private Map<String, List<PropertyError>> validateElement(ValidationContext elementCtx, String elementPath) {
        def elementViolations = [:]
        constraints.each { Constraint elementConstraint ->
            def violations = elementConstraint.check(elementCtx, elementPath, 'element')
            if (violations)
                elementViolations.putAll(violations)
        }
        return elementViolations
    }


    private static class ElementHolder {
        final element

        ElementHolder(element) {
            this.element = element
        }
    }
}
