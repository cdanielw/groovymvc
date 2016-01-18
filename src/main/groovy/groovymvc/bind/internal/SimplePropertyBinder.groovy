package groovymvc.bind.internal

import groovymvc.bind.BeanProperty
import groovymvc.bind.Binder
import groovymvc.bind.PropertyError
import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 * @author Daniel Wiell
 */
class SimplePropertyBinder {
    private static final Logger LOG = LoggerFactory.getLogger(Binder)
    private final BindingContext ctx
    private final Map<Class, Converter> converters
    private final BeanProperty property
    private final String paramValue

    SimplePropertyBinder(BindingContext ctx, Map<Class, Converter> converters, BeanProperty property, String paramValue) {
        this.ctx = ctx
        this.converters = converters
        this.property = property
        this.paramValue = paramValue
    }

    Map<String, List<PropertyError>> bind() {
        def convertedValue = null
        def converter = findConverter(property.type)
        if (converter == null) {
            if (paramValue != null) {
                LOG.warn("Failed to bind ${ctx.rootBean.class.name}.${property.path} to '${paramValue}': No converter registered for ${property.type}")
                return [(property.path): [new BindingFailure(ctx, property, paramValue)]]
            }
        } else {
            try {
                convertedValue = converter.convert(property, paramValue)
            } catch (Exception ignore) {
                return [(property.path): [new BindingFailure(ctx, property, paramValue)]]
            }
        }
        ctx.leafBean[property.name] = convertedValue
        return [:]
    }

    private Converter findConverter(Class propertyType) {
        converters[propertyType] ?: converters.find { Class converterType, converter ->
            converterType.isAssignableFrom(propertyType)
        }?.value
    }

}
