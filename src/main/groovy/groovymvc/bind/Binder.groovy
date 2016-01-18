package groovymvc.bind

import groovymvc.Params
/**
 * Populates a bean based on a {@link Params} instance.
 *
 * <p>In order to bind a property, a converter must be registered for the property type.
 * Converters for int, Integer, long, Long, double, Double, and Enum are registered upon instantiation.
 *
 * <p>Example:
 * <blockquote><pre>
 * def binder = new DefaultBinder(DefaultMessageSource.messageSource())
 * binder.register(Date) &#123;
 *         Date.parse('yyyy-MM-dd', it as String)
 * &#125;
 * def params = Params.parse('id=123&name=Asimov&dateOfBirth=no-date')
 * def author = new Author()
 * def errors = binder.bind(author, params)
 * assert author.name == 'Asimov'
 * assert errors.dateOfBirth.first().invalidValue == 'no-date'
 * </pre></blockquote>
 * @author Daniel Wiell
 */
interface Binder {
    /**
     * Registers a converter.
     *
     * @param type the type to convert to
     * @param converter callback performing the conversion from String to T.
     * It takes either one or two arguments: The value, or the {@link BeanProperty} and the value.
     * @return The binder instance.
     */
    public <T> Binder register(Class<T> type, Closure<T> converter)

    /**
     * Registers a converter and a formatter.
     * @param type the type to convert to and format
     * @param converter callback performing the conversion from String to T.
     * @param formatter callback performing the formatting from T to String.
     * @return The binder instance.
     */
    public <T> Binder register(Class<T> type, Closure<T> converter, Closure<String> formatter)

    /**
     * Populates a bean based on a {@link Params} instance.
     *
     * @param bean the bean to populate
     * @param params the parameters to take values from
     * @return Any binding errors.
     */
    Errors<PropertyError> bind(Object bean, Params params)

    /**
     * Formats a value using provided formatter for the type, or coerces the value to String.
     *
     * @param value the value to format
     * @return The formatted value.
     */
    String format(Object value)
}
