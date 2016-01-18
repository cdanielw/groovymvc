package groovymvc.bind.internal

import groovymvc.bind.BeanProperty
import groovymvc.bind.Binder
import groovymvc.bind.PropertyError
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
/**
 * @author Daniel Wiell
 */
class CollectionBinder {
    private static final Logger LOG = LoggerFactory.getLogger(Binder)
    private final BindingContext ctx
    private final BeanProperty property
    private final Collection paramCollection

    CollectionBinder(BindingContext ctx, BeanProperty property, Collection paramCollection) {
        this.ctx = ctx
        this.property = property
        this.paramCollection = paramCollection
    }

    Map<String, List<PropertyError>> bind() {
        Type type = determineElementType(property)
        if (type == null)
            return failCollectionBinding(property, paramCollection, type,
                    "Raw collection types not supported. Parameterized type must be specified.")
        def collection = ctx.leafBean[property.name] as Collection
        if (collection == null) {
            collection = createCollection(property)
            if (collection == null)
                return failCollectionBinding(property, paramCollection, type,
                        "${type.name} is an unsupported collection type.")

            ctx.leafBean[property.name] = collection
        } else
            collection.clear()

        def failures = [:]
        paramCollection.eachWithIndex { paramValue, int i ->
            def bindResult = bindElement(property, type, paramValue, i)
            def element = bindResult[0]
            def elementFailures = bindResult[1]
            collection.add(element)
            failures.putAll(elementFailures as Map)
        }
        return failures
    }

    private Map<String, List<PropertyError>> failCollectionBinding(BeanProperty property, Collection paramCollection,
                                                                   Type type, String message) {
        LOG.warn("Failed to bind ${ctx.rootBean.class.name}.${property.path} to ${paramCollection}: ${message}")
        def failures = [:]
        paramCollection.eachWithIndex { paramValue, i ->
            def elementProperty = new BeanProperty('element', "${ctx.toPath(property.name)}[$i]", type)
            failures << [(elementProperty.path): [new BindingFailure(ctx, elementProperty, paramValue as String)]]
        }
        return failures
    }

    private List bindElement(BeanProperty property, Type type, paramValue, int i) {
        def elementParams = [element: paramValue]
        def elementPath = "${ctx.toPath(property.name)}[$i]"
        def elementHolder = new ElementHolder()
        def elementCtx = ctx.update(elementParams, elementPath, property, elementHolder)
        def elementProperty = new BeanProperty('element', "${ctx.toPath(property.name)}[$i]", type)
        def failures = elementCtx.bindSingleProperty(elementProperty, paramValue)
        return [elementHolder.element, failures]
    }

    private Type determineElementType(BeanProperty property) {
        def elementType = property.genericParameterTypes.first()
        if (elementType instanceof ParameterizedType) {
            return ((ParameterizedType) elementType).actualTypeArguments.first()
        } else
            return null
    }

    private Collection createCollection(BeanProperty property) {
        def type = property.type
        if (SortedSet.isAssignableFrom(type)) return new TreeSet()
        if (Set.isAssignableFrom(type)) return new HashSet()
        if (type.isAssignableFrom(List)) return []
        return null
    }

    private static class ElementHolder {
        def element
    }
}
