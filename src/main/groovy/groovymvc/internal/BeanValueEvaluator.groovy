package groovymvc.internal

import groovymvc.bind.Binder
import groovymvc.bind.Errors

import java.util.regex.Pattern
/**
 * @author Daniel Wiell
 */
class BeanValueEvaluator {
    private static final Pattern INDEXED_PATTERN = Pattern.compile(/(.*)\[(.*)\]/)

    static String value(bean, String path, Binder binder, Errors errors) {
        def value
        if (errors?.containsKey(path))
            value = errors[path].first().invalidValue
        else
            value = getPathValue(bean, path)
        return binder.format(value)
    }

    private static Object getPathValue(bean, String path) {
        def it = path.split(/\./).iterator()
        def value = bean
        while (value != null && it.hasNext()) {
            def property = it.next()
            def matcher = INDEXED_PATTERN.matcher(property)
            if (matcher.matches() && matcher.group(2).isInteger()) {
                def list = value[matcher.group(1)]
                if (list instanceof List) {
                    def i = matcher.group(2) as int
                    value = list[i]
                } else
                    return null
            } else {
                try {
                    value = value[property]
                } catch (Exception e) {
                    return null
                }
            }
        }
        return value
    }
}
