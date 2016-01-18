package groovymvc.bind

import groovymvc.i18n.MessageSource

/**
 * @author Daniel Wiell
 */
class PropertyError {
    final MessageSource messageSource
    final rootBean
    final leafBean
    final String propertyPath
    final String propertyName
    final invalidValue
    final String messageKey
    final Map<String, Object> attributes

    PropertyError(MessageSource messageSource, rootBean, leafBean, String propertyPath, String propertyName,
                  invalidValue, String messageKey, Map<String, Object> attributes) {
        this.messageSource = messageSource
        this.rootBean = rootBean
        this.leafBean = leafBean
        this.propertyPath = propertyPath
        this.propertyName = propertyName
        this.invalidValue = invalidValue
        this.messageKey = messageKey
        this.attributes = attributes
    }

    List<String> getMessageKeys() {
        [
                "${rootBean.class.simpleName}.${propertyPath}.${messageKey}",
                "${leafBean.class.simpleName}.${propertyName}.${messageKey}",
                "${leafBean.class.metaClass.properties.find { it.name == propertyName }.type.simpleName}.${messageKey}",
                messageKey,
        ].unique()
    }

    String getMessage(Locale locale = Locale.default) {
        def messageKey = getMessageKeys().find { messageSource.contains(it, locale) }
        messageKey == null ? this.messageKey : messageSource.message(messageKey, locale, attributes.values() as String[])
    }

    String toString() {
        "$messageKey: $invalidValue"
    }
}
