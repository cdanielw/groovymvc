package groovymvc.i18n

/**
 * @author Daniel Wiell
 */
interface MessageSource {
    String message(String key, String... arguments)

    String message(String key, Locale locale, String... arguments)

    boolean contains(String key, Locale locale)
}