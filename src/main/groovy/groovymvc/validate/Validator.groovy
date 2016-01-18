package groovymvc.validate

import groovymvc.bind.Errors
import groovymvc.bind.PropertyError

/**
 * Validates a bean against registered constraints.
 *
 * <p>Example:
 * <blockquote><pre>
 * import static groovymvc.validate.Constraints.*
 *
 * class Author &#123;
 *     long id
 *     String name
 *     Date dateOfBirth
 *
 *     static constraints = [
 *             name: [notNull(), notBlank()],
 *             dateOfBirth: custom &#123; it < new Date() &#125;
 *     ]
 * &#125;
 *
 * def validator = new DefaultValidator(DefaultMessageSource.messageSource())
 * validator.register(Author, Author.constraints)
 * def future = new Date() + 5
 * def author = new Author(name: 'Asimov', dateOfBirth: future)
 * def errors = validator.validate(author)
 * assert errors.dateOfBirth.first().invalidValue == future
 * </pre></blockquote>
 *
 * @author wiell
 */
interface Validator {
    /**
     * Registers constraints for a bean class.
     *
     * <p>The constraints should be a map where the key is the name of a property in the specified bean class.
     * The value should be a constraint, or an iterable containing constraints.
     *
     * @param beanClass the class to apply the constraints to
     * @param constraints the constraints
     * @return The validator instance.
     */
    Validator register(Class beanClass, Map<String, ?> constraints)

    /**
     * Validates bean against registered constraints. If constraints are not registered for bean type,
     *
     * @param bean the bean to validate
     * @return Any validation errors.
     */
    Errors<PropertyError> validate(bean)
}