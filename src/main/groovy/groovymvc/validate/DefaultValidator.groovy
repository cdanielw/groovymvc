package groovymvc.validate

import groovymvc.bind.Errors
import groovymvc.bind.PropertyError
import groovymvc.i18n.MessageSource

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Daniel Wiell
 */
class DefaultValidator implements Validator {
    private final Map<Class, Map<String, List<Constraint>>> constraintRegistry = new ConcurrentHashMap<>()
    private final MessageSource messageSource

    DefaultValidator(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    Validator register(Class beanClass, Map<String, ?> constraints) {
        initConstraints(beanClass, constraints)
        return this
    }

    private void initConstraints(Class beanClass, Map<String, ?> constraintsByProperty) {
        verifyConstraintConfiguration(beanClass, constraintsByProperty)
        def c = [:]
        constraintsByProperty.each { propertyName, constraints ->
            constraints = constraints instanceof Iterable ? constraints : [constraints]
            c[propertyName] = constraints
        }
        constraintRegistry[beanClass] = c
    }

    Errors<PropertyError> validate(bean) {
        if (bean == null) throw new IllegalArgumentException('bean cannot be null')
        if (!constraintRegistry.containsKey(bean.class))
            throw new IllegalStateException("Bean of type ${bean.class.name} isn't registered")

        new Errors(ValidationContext.createContext(constraintRegistry, bean, messageSource).validate())
    }

    private void verifyConstraintConfiguration(Class beanClass, Map<String, ?> constraints) {
        def propertyNames = beanClass.metaClass.properties.collect { it.name } - 'class'
        constraints.keySet().each { propertyName ->
            if (!propertyNames.contains(propertyName))
                throw new IllegalArgumentException("$beanClass.name does not contain property $propertyName. Valid properties are $propertyNames")
        }
        constraints.each { propertyName, propertyConstraint ->
            if (propertyConstraint == null || !propertyConstraint.every { it instanceof Constraint })
                throw new IllegalArgumentException("Constraints for property $propertyName must either be a Constraint instance or an iterable of Constraints.")
        }
    }
}
