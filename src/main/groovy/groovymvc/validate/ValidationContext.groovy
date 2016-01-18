package groovymvc.validate

import groovymvc.bind.PropertyError
import groovymvc.i18n.MessageSource

/**
 * @author Daniel Wiell
 */
class ValidationContext {
    private final Map<Class, Map<String, List<Constraint>>> constraintRegistry
    final rootBean
    final leafBean
    final String path
    final MessageSource messageSource
    private final Set validated

    private ValidationContext(Map<Class, Map<String, List<Constraint>>> constraintRegistry, rootBean, leafBean, String path, Set validated, MessageSource messageSource) {
        this.constraintRegistry = constraintRegistry
        this.rootBean = rootBean
        this.leafBean = leafBean
        this.path = path
        this.validated = validated
        this.messageSource = messageSource
    }

    static ValidationContext createContext(Map<Class, Map<String, List<Constraint>>> constraintRegistry, rootBean, MessageSource messageSource) {
        new ValidationContext(constraintRegistry, rootBean, rootBean, '', new HashSet(), messageSource)
    }

    ValidationContext update(leafBean, String path) {
        new ValidationContext(constraintRegistry, rootBean, leafBean, path, validated, messageSource)
    }

    Map<String, List<PropertyError>> validate() {
        if (validated.contains(leafBean)) return [:]
        validated << leafBean
        def constraintViolations = [:]
        constraintsByProperty.each { String propertyName, List constraints ->
            constraintViolations.putAll(validateProperty(constraints, propertyName))
        }
        return constraintViolations
    }

    private Map<String, List<PropertyError>> validateProperty(List<Constraint> constraints, String propertyName) {
        Map<String, List<PropertyError>> violationsByPath = [:]
        constraints.each { Constraint constraint ->
            def violationsForConstraint = constraint.check(this, propertyPath(propertyName), propertyName)
            if (violationsForConstraint)
                violationsForConstraint.each { String path, List<PropertyError> violations ->
                    def allViolations = violationsByPath.containsKey(path) ? violations + violationsByPath[path] : violations
                    violationsByPath[path] = allViolations as List<PropertyError>
                }
        }
        return violationsByPath
    }


    private Map<String, List> getConstraintsByProperty() {
        constraintRegistry[leafBean.class]
    }

    private String propertyPath(String propertyName) {
        path ? path + '.' + propertyName : propertyName
    }
}
