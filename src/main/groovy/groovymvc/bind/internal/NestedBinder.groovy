package groovymvc.bind.internal

import groovymvc.bind.BeanProperty
import groovymvc.bind.Binder
import groovymvc.bind.PropertyError
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Daniel Wiell
 */
class NestedBinder {
    private static final Logger LOG = LoggerFactory.getLogger(Binder)
    private final BindingContext ctx
    private final BeanProperty property
    private final Map nestedParams

    NestedBinder(BindingContext bindingContext, BeanProperty property, Map nestedParams) {
        this.ctx = bindingContext
        this.property = property
        this.nestedParams = nestedParams
    }

    Map<String, List<PropertyError>> bind() {
        def nested = ctx.leafBean[property.name]
        if (nested == null) {
            try {
                nested = property.type.newInstance()
            } catch (Exception ignore) {
                if (property.type.isAssignableFrom(Map))
                    nested = [:]
                else {
                    LOG.warn("Failed to bind nested bean ${ctx.rootBean.class.name}.${property.path} to '${nestedParams}': " +
                            "Property is null and ${ctx.leafBean.class.name} is not a concrete class with no-args constructor")
                    return [(property.path): [new BindingFailure(ctx, property, nestedParams as String)]]
                }
            }
            ctx.leafBean[property.name] = nested
        }

        def nestedCtx = ctx.update(nestedParams, property.path, property, nested)
        return nestedCtx.bind()
    }


    private static class Holder {
        def value
    }

}
