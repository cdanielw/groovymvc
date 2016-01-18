package groovymvc.bind

import groovymvc.Params
import groovymvc.bind.internal.BindingContext
import groovymvc.bind.internal.Converter
import groovymvc.i18n.MessageSource

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Daniel Wiell
 */
class DefaultBinder implements Binder {
    private final Map<Class, Converter> converters = new ConcurrentHashMap<>()
    private final Map<Class, Closure> formatters = new ConcurrentHashMap<>()
    private final MessageSource messageSource

    DefaultBinder(MessageSource messageSource) {
        this.messageSource = messageSource
        register(String) {
            if (it == null || it.empty) return null
            return it
        }
        register(int) {
            if (it == null || it.empty) return Integer.valueOf(0)
            return Integer.parseInt(it)
        }
        register(Integer) {
            if (it == null || it.empty) return null
            return Integer.parseInt(it)
        }
        register(long) {
            if (it == null || it.empty) return Long.valueOf(0)
            return Long.parseLong(it)
        }
        register(Long) {
            if (it == null || it.empty) return null
            return Long.parseLong(it)
        }
        register(double) {
            if (it == null || it.empty) return Double.valueOf(0)
            return Double.parseDouble(it)
        }
        register(Double) {
            if (it == null || it.empty) return null
            return Double.parseDouble(it)
        }
        register(boolean) {
            if (it == null || it.empty) return false
            return !'false'.equalsIgnoreCase(it) || Boolean.parseBoolean(it)
        }
        register(Boolean) {
            if (it == null || it.empty) return null
            return !'false'.equalsIgnoreCase(it) || Boolean.parseBoolean(it)
        }
        register(Enum) { BeanProperty property, String value ->
            if (value == null || value.isEmpty()) return null
            Enum.valueOf(property.type, value)
        }
    }

    def <T> Binder register(Class<T> type, Closure<T> converter) {
        converters[type] = new DefaultConverter<T>(type, converter)
        return this
    }

    def <T> Binder register(Class<T> type, Closure<T> converter, Closure<String> formatter) {
        converters[type] = new DefaultConverter<T>(type, converter)
        formatters[type] = formatter
        return this
    }

    Errors<PropertyError> bind(Object bean, Params params) {
        new Errors(BindingContext.createContext(converters, params, bean, messageSource).bind())
    }

    String format(Object value) {
        if (value == null)
            return ''
        def formatter = formatters[value.class] ?: formatters.find { Class type, converter ->
            type.isAssignableFrom(value.class)
        }?.value
        return formatter ? formatter.call(value) : value as String
    }

    private static class DefaultConverter<T> implements Converter<T> {
        final Class<T> type
        private final Closure<T> converterCallback

        DefaultConverter(Class<T> type, Closure<T> converter) {
            this.type = type
            this.converterCallback = converter
            if (![1, 2].contains(converter.maximumNumberOfParameters))
                throw new IllegalArgumentException('Conversion callback must take one or two parameter. ' +
                        'The value, or BeanProperty and value')
        }

        T convert(BeanProperty property, String value) {
            if (converterCallback.maximumNumberOfParameters == 1)
                return converterCallback(value)
            else
                return converterCallback(property, value)
        }
    }
}
