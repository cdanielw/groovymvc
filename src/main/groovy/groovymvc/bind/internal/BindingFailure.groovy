package groovymvc.bind.internal

import groovymvc.bind.BeanProperty
import groovymvc.bind.PropertyError
/**
 * @author Daniel Wiell
 */
class BindingFailure extends PropertyError {
    BindingFailure(BindingContext ctx, BeanProperty property, String invalidValue) {
        super(ctx.messageSource, ctx.rootBean, ctx.leafBean, property.path, property.name, invalidValue, 'invalid', [:])
    }
}
