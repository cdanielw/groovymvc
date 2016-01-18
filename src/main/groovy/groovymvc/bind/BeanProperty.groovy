package groovymvc.bind

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * @author Daniel Wiell
 */
class BeanProperty {
    final String name
    final String path
    final Class type
    final Type[] genericParameterTypes

    BeanProperty(String name, String path, Type type, Type[] genericParameterTypes = null) {
        if (genericParameterTypes == null)
            genericParameterTypes = type instanceof ParameterizedType ? [type] as Type[] : new Type[0]

        this.name = name
        this.path = path

        this.type = type instanceof ParameterizedType ? type.rawType as Class : type as Class
        this.genericParameterTypes = genericParameterTypes
    }
}