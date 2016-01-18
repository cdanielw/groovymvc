package groovymvc

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Holder for path/query parameters.
 * <p>It follows a naming convension to split up parameters into nested maps and lists.
 *
 * <p>Examples:
 * <blockquote><pre>
 ''                                 | [:]
 'a=foo'                            | [a: 'foo']
 'a='                               | [a: [:]]
 'a'                                | [a: [:]]
 'a[]'                              | [a: [[:]]]
 'a&a'                              | [a: [:]]
 'a=&a='                            | [a: [:]]
 'a=foo&c=bar'                      | [a: 'foo', c: 'bar']
 'a=foo&&c=bar'                     | [a: 'foo', c: 'bar']
 'a=foo&a=bar'                      | [a: 'bar']
 'a=foo&a=bar&a=baz'                | [a: 'baz']
 'a.b=foo'                          | [a: [b: 'foo']]
 'a.b=foo'                          | [a: [b: 'foo']]
 'a.a=foo&a.b=bar'                  | [a: [a: 'foo', b: 'bar']]
 'a.a.a=foo&a.a.b=bar'              | [a: [a: [a: 'foo', b: 'bar']]]
 'a.b=foo&a.c=bar'                  | [a: [b: 'foo', c: 'bar']]
 'a[]=foo'                          | [a: ['foo']]
 'a[]=foo&a[]=bar'                  | [a: ['foo', 'bar']]
 'a[].b=foo&a[].b=bar&a[].c=baz'    | [a: [[b: 'foo'], [b: 'bar'], [c: 'baz']]]
 'a[0].b=foo&a[1].b=bar&a[0].c=baz' | [a: [[b: 'foo', c: 'baz'], [b: 'bar']]]
 'a[0]=foo'                         | [a: ['foo']]
 'a[1]=foo'                         | [a: [[:], 'foo']]
 'a[0]=foo&a[1]=bar'                | [a: ['foo', 'bar']]
 'a[1]=foo&a[0]=bar'                | [a: ['bar', 'foo']]
 'a[0].b=foo'                       | [a: [[b: 'foo']]]
 'a[0].b=foo&a[1].b=bar'            | [a: [[b: 'foo'], [b: 'bar']]]
 'a[0]=foo&a[0]=bar'                | [a: ['bar']]
 'a[0].b=foo&a[0].c=bar'            | [a: [[b: 'foo', c: 'bar']]]
 'a.b[0].c=foo&a.b[1].c=bar'        | [a: [b: [[c: 'foo'], [c: 'bar']]]]
 * </pre></blockquote>
 *
 * @author Daniel Wiell
 */
class Params implements Map<String, Object> {
    private static final Pattern INDEXED_PATTERN = Pattern.compile(/(.*)\[(.*)\]/)
    @Delegate
    private final Map<String, Object> map

    Params(Map<String, Object> map) {
        this.map = map
    }

    /**
     * Parses the provided query string.
     *
     * @param paramsString the query string to parse
     * @return A Params instance.
     */
    static Params parse(String paramsString) {
        new Params(new ParamsParser().parse(paramsString))
    }

    /**
     * Parses the provided parameter map.
     *
     * @param parameterMap the parameter map to parse
     * @return A Params instance.
     */
    static Params parse(Map<String, String[]> parameterMap) {
        new Params(new ParamsParser().parse(parameterMap))
    }

    /**
     * Create a new Params instance excluding the provided parameter names, or any nested below provided parameter names.
     *
     * <p>Example:
     * <blockquote><pre>
     * def params = Params.parse('a=a&a.b=b')
     * def p1 = params.excluding('a') // both a and a.b are removed
     * def p2 = params.excluding('a.b') // only a.b is removed
     * </pre></blockquote>
     *
     * @param paramNames the parameter names to remove
     * @return A new Params instance.
     */
    Params excluding(String... paramNames) {
        def result = [:]
        def m = this.map
        paramNames.each { excludePath ->
            def chain = excludePath.split(/\./) as List
            result = result + doExclude(chain.first(), chain.tail(), m)
        }
        return new Params(result)
    }

    /**
     * Create a new Params instance only including the provided parameter names, or any nested below provided parameter names.
     *
     * <p>Example:
     * <blockquote><pre>
     * def params = Params.parse('a=a&a.b=b&a.c=c&d=d')
     * def p1 = params.including('a') // both a, a.b, and a.c are present, d is removed
     * def p2 = params.excluding('a.b') // a and a.b are present, a.c and d are removed
     * </pre></blockquote>
     *
     * @param paramNames the parameter names to include
     * @return A new Params instance.
     */
    Params including(String... paramNames) {
        def result = [:]
        def m = this.map
        paramNames.each { includePath ->
            def chain = includePath.split(/\./) as List
            Map i = doInclude(chain.first(), chain.tail(), m)
            result = result + i
        }
        return new Params(result)
    }

    Params subParams(String... paramNames) {
        def result = [:]
        def m = this.map
        paramNames.each { p ->
            def subset = m[p]
            if (subset instanceof Map)
                result.putAll(subset)
        }
        return new Params(result)
    }

    Params trim() {
        new Params(trimMap(map))
    }

    private Map<String, Object> trimMap(Map<String, Object> map) {
        def result = [:]
        map.each { key, value ->
            result[key] = trimValue(value)
        }
        return result
    }

    private trimValue(value) {
        if (value == null)
            return null
        if (value instanceof String)
            return value.trim()
        if (value instanceof List)
            return value.collect { trimValue(it) }
        if (value instanceof Map)
            return trimMap(value as Map)
        throw new IllegalStateException("Invalid type in params map: $value of type ${value.getClass()}")

    }

    /**
     * Gets value for the provided path, or null if none exist.
     * The value is coerced into the specified type. If that fails, a {@link ParamsException} is thrown.
     *
     * @param path the path to get the value for
     * @param expectedType the type to coerce the value into
     * @return The coerced value.
     */
    public <T> T optional(String path, Class<T> expectedType = Object) {
        def it = path.split(/\./).iterator()
        def value = map
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
                value = value[property]
            }
        }
        return expectType(value, expectedType, "Expected value for $path to be of type $expectedType, was ${value?.class?.name}")
    }

    /**
     * Gets value for the provided path, or throws {@link ParamsException} if none exist.
     * The value is coerced into the specified type. If that fails, a {@link ParamsException} is thrown.
     *
     * @param path the path to get the value for
     * @param expectedType the type to coerce the value into
     * @return The coerced value.
     */
    public <T> T required(String path, Class<T> expectedType = Object) {
        require(optional(path, expectedType), "$path parameter is required")
    }

    /**
     * Asserts that the provided value if not null.
     * Otherwise {@link ParamsException} with the provided message is thrown.
     *
     * @param value the value to check
     * @param message the message to provide an eventual {@link ParamsException}
     * @return The value.
     */
    private static <T> T require(T value, String message) {
        if (value == null)
            throw new ParamsException(message)
        return value
    }

    /**
     * Asserts the value can be coerced into the specified type.
     * If not, a {@link ParamsException} with the provided message is thrown.
     *
     * @param value the value to check
     * @param expectedType the type to coerce the value to
     * @param message the message to provide an eventual {@link ParamsException}
     * @return The coerced value.
     */
    private static <T> T expectType(value, Class<T> expectedType, String message) {
        if (value == null || (value instanceof String && value.empty))
            return null
        try {
            if (Map.isAssignableFrom(expectedType)) {
                if (value instanceof Map)
                    return value
                else
                    throw new ParamsException(message)
            } else if (Iterable.isAssignableFrom(expectedType)) {
                if (value instanceof Iterable)
                    return value.asType(expectedType)
                else
                    return [value].asType(expectedType)
            } else {
                T coercedValue = value.asType(expectedType)
                return coercedValue
            }
        } catch (Exception ignore) {
            throw new ParamsException(message)
        }
    }

    private doExclude(String first, List<String> tail, value) {
        if (value == null)
            [:]
        else if (value instanceof List)
            value.collect { doExclude(first, tail, it) }
        else if (!(value instanceof Map))
            value
        else if (tail.empty || !value.containsKey(first))
            value.findAll { it.key != first }
        else {
            def notMatching = value.findAll { it.key != first }
            def matchingExcluded = [(first): doExclude(tail.first(), tail.tail(), value[first])]
            notMatching + matchingExcluded
        }
    }

    private doInclude(String first, List<String> tail, value) {
        def result
        if (value instanceof List)
            result = value.collect { doInclude(first, tail, it) }
        else if (!(value instanceof Map))
            result = [:]
        else if (tail.empty)
            result = value.findAll { it.key == first }
        else
            result = [(first): doInclude(tail.first(), tail.tail(), value[first])]
        return result
    }


    private static class ParamsParser {
        Map<String, Object> parse(String paramsString) {
            def params = [:]
            paramsString.split(/\&/).findAll { it != null }.each {
                addParam(it, params)
            }
            return params
        }

        Map<String, Object> parse(Map<String, String[]> params) {
            def result = [:]
            params.each { String key, String[] values ->
                values.each {
                    addParam(key, it, result)
                }
            }
            return result
        }

        private void addParam(String param, Map params) {
            if (param.empty) return
            def pair = param.split(/\=/)
            pair.eachWithIndex { String s, int i ->
                pair[i] = URLDecoder.decode(s, 'UTF-8')
            }
            addParam(pair[0], pair.length > 1 ? pair[1] as String : [:], params)
        }

        private void addParam(String key, value, Map params) {
            def iter = key.split(/\./).iterator()
            def parentParams = params
            while (iter.hasNext()) {
                def pathKey = iter.next() as String
                def pathValue
                if (iter.hasNext())
                    pathValue = parentParams[pathKey] ?: [:]
                else
                    pathValue = value
                if (pathValue instanceof String && iter.hasNext())
                    throw new ParamsException("Cannot parse '${key}=${value}'. Parent already got value '${pathValue}'")
                if (parentParams instanceof List)
                    throw new ParamsException("Cannot parse '${key}=${value}'. Parent is a list")
                if (pathValue instanceof String && parentParams[pathKey] instanceof Map)
                    throw new ParamsException("Cannot parse '${key}=${value}'. '${pathKey}' already got value '${parentParams[pathKey]}")
                parentParams = put(pathKey, pathValue, parentParams)
            }
        }

        private put(String pathKey, pathValue, Map parentParams) {
            def matcher = INDEXED_PATTERN.matcher(pathKey)
            if (matcher.matches()) {
                return putIndexed(matcher, parentParams, pathValue)
            } else {
                return putSimple(pathKey, pathValue, parentParams)
            }
        }

        private putSimple(pathKey, pathValue, parentParams) {
            if ((parentParams instanceof List && parentParams[pathKey] == null) ||
                    (parentParams instanceof Map && !parentParams.containsKey(pathKey)))
                parentParams[pathKey] = pathValue
            else if (parentParams[pathKey] instanceof Map && !parentParams[pathKey].isEmpty())
                parentParams[pathKey].putAll(pathValue)
            else if (parentParams instanceof List)
                parentParams[pathKey] = pathValue
            return pathValue
        }

        private putIndexed(Matcher matcher, Map parentParams, pathValue) {
            def underscore = matcher.group(1).startsWith('_')
            def key = underscore ? matcher.group(1).substring(1) : matcher.group(1)
            def list = parentParams[key]
            if (list != null && !(list instanceof List))
                throw new ParamsException("${key} is not a list")
            def index = matcher.group(2) ? matcher.group(2) as int : list?.size() ?: 0
            if (!list) {
                if (underscore) {
                    parentParams[key] = []
                    return null
                }
                list = []
                parentParams[key] = list
            } else if (underscore) {
                return null
            }
            putSimple(index, pathValue, list)
            (0..index).each {
                if (list[it] == null)
                    list[it] = [:]
            }
            return list[index]
        }
    }
}
