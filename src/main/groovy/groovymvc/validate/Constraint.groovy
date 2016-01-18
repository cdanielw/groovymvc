package groovymvc.validate

import groovymvc.bind.PropertyError

/**
 * @author wiell
 */
interface Constraint {
    Map<String, Object> getAttributes()

    Map<String, List<PropertyError>> check(ValidationContext ctx, String propertyPath, String propertyName)
}


