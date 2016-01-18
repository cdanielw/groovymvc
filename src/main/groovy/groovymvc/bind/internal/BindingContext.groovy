package groovymvc.bind.internal

import groovymvc.bind.BeanProperty
import groovymvc.bind.PropertyError
import groovymvc.i18n.MessageSource
import org.codehaus.groovy.reflection.CachedMethod

import java.lang.reflect.ParameterizedType

/**
 * @author Daniel Wiell
 */
@SuppressWarnings("GroovyUnusedDeclaration")
class BindingContext {
    private final Map<Class, Converter> converters
    final Map params
    final rootBean
    final leafBean
    final BeanProperty leafBeanProperty
    final String path
    final MessageSource messageSource

    private BindingContext(Map<Class, Converter> converters, Map params, rootBean, leafBean, BeanProperty leafBeanProperty, String path, MessageSource messageSource) {
        this.converters = converters
        this.params = params
        this.rootBean = rootBean
        this.leafBean = leafBean
        this.leafBeanProperty = leafBeanProperty
        this.path = path
        this.messageSource = messageSource
    }

    static BindingContext createContext(Map<Class, Converter> converters, Map params, rootBean, MessageSource messageSource) {
        new BindingContext(converters, params, rootBean, rootBean, null, '', messageSource)
    }

    BindingContext update(Map params, String path, BeanProperty leafBeanProperty, leafBean) {
        new BindingContext(converters, params, rootBean, leafBean, leafBeanProperty, path, messageSource)
    }

    Map<String, List<PropertyError>> bind() {
        if (leafBean instanceof Map && leafBean.metaClass == null)
            return bindMap()
        Map<String, List<PropertyError>> errors = [:]
        propertiesToBind(leafBean, params).each { BeanProperty property ->
            errors.putAll(bindSingleProperty(property, params[property.name]))
        }
        errors.values().removeAll { !it }
        return errors
    }

    Map<String, List<PropertyError>> bindMap() {
        Map<String, List<PropertyError>> errors = [:]
        def map = leafBean as Map

        def genericParameterType = leafBeanProperty.genericParameterTypes.first()
        if (genericParameterType instanceof ParameterizedType) {
            def types = genericParameterType.actualTypeArguments
            params.each { key, value ->
                def keyProperty = new BeanProperty('key', toPath(key as String), types[0])
                def keyHolder = new KeyHolder()
                def keyCtx = update([:], keyProperty.path, keyProperty, keyHolder)
                errors.putAll(keyCtx.bindSingleProperty(keyProperty, key))

                def valueProperty = new BeanProperty('value', toPath(key as String), types[1])
                def valueHolder = new ValueHolder()
                def valueCtx = update([:], keyProperty.path, valueProperty, valueHolder)
                errors.putAll(valueCtx.bindSingleProperty(valueProperty, value))
                map[keyHolder.key] = valueHolder.value
            }
        } else
            map.putAll(params)
        return errors
    }

    private static class ValueHolder {
        def value
    }

    private static class KeyHolder {
        def key
    }

    Map<String, List<PropertyError>> bindSingleProperty(BeanProperty property, propertyParam) {
        switch (propertyParam.class) {
            case Map: return bindProperty(property, propertyParam as Map)
            case Collection: return bindProperty(property, propertyParam as Collection)
            default: return bindProperty(property, propertyParam)
        }
    }

    private Map<String, List<PropertyError>> bindProperty(BeanProperty property, Map nestedParams) {
        if (nestedParams.isEmpty())
            new SimplePropertyBinder(this, converters, property, null).bind()
        else
            new NestedBinder(this, property, nestedParams).bind()

    }


    private Map<String, List<PropertyError>> bindProperty(BeanProperty property, Collection paramCollection) {
        new CollectionBinder(this, property, paramCollection).bind()
    }

    private Map<String, List<PropertyError>> bindProperty(BeanProperty property, paramValue) {
        new SimplePropertyBinder(this, converters, property, paramValue as String).bind()
    }

    String toPath(String propertyName) {
        path.empty ? propertyName : "${path}.${propertyName}"
    }

    private List<BeanProperty> propertiesToBind(target, Map params) {
        target.metaClass.properties.findAll { MetaProperty property ->
            property.name != 'class' && params.containsKey(property.name)
        }.collect { MetaBeanProperty property ->
            createBeanProperty(property)
        }
    }

    private BeanProperty createBeanProperty(MetaBeanProperty property) {
        new BeanProperty(property.name, toPath(property.name), property.type, ((CachedMethod) property.setter).cachedMethod.genericParameterTypes)
    }

}
