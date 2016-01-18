package groovymvc.bind.internal

import groovymvc.bind.BeanProperty

/**
 * @author Daniel Wiell
 */
interface Converter<T> {
    Class<T> getType()

    T convert(BeanProperty property, String value)
}