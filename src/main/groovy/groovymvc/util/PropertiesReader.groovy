package groovymvc.util
/**
 * @author Daniel Wiell
 */
class PropertiesReader {
    private final props = new Properties()
    private final String name

    private PropertiesReader(String name) {
        this.name = name
        def is = getClass().getResourceAsStream(name)
        if (!is)
            throw new PropertiesReaderException("$name not found on classpath")
        try {
            props.load(is)
        } catch (Exception e) {
            throw new PropertiesReaderException("Unable to load $name", e)
        }
    }

    static PropertiesReader forClassPathResource(String name) {
        new PropertiesReader(name)
    }

    /**
     * Gets value for the provided key, or null if none exist.
     * The value is coerced into the specified type. If that fails, a {@link PropertiesReaderException} is thrown.
     *
     * @param key the key to get the value for
     * @param expectedType the type to coerce the value into
     * @return The coerced value.
     */
    public <T> T optional(String key, Class<T> expectedType) throws PropertiesReaderException {
        def value = props.getProperty(key)
        return expectType(value, expectedType, "Failed to coerce value of $key to $expectedType. Value: $value")
    }

    /**
     * Gets value for the provided key, or null if none exist.
     *
     * @param key the key to get the value for
     * @return The value.
     */
    String optional(String key) throws PropertiesReaderException {
        optional(key, String)
    }

    /**
     * Gets value for the provided key, or throws {@link PropertiesReaderException} if none exist.
     * The value is coerced into the specified type. If that fails, a {@link PropertiesReaderException} is thrown.
     *
     * @param key the key to get the value for
     * @param expectedType the type to coerce the value into
     * @return The coerced value.
     */
    public <T> T required(String key, Class<T> expectedType) throws PropertiesReaderException {
        require(optional(key, expectedType), "$key is required")
    }

    /**
     * Gets value for the provided key, or throws {@link PropertiesReaderException} if none exist.
     *
     * @param key the key to get the value for
     * @return The value.
     */
    String required(String key) throws PropertiesReaderException {
        required(key, String)
    }

    private static <T> T require(T value, String message) {
        if (value == null)
            throw new PropertiesReaderException(message)
        return value
    }

    private static <T> T expectType(value, Class<T> expectedType, String message) {
        if (value == null || (value instanceof String && value.empty))
            return null
        try {
            T coercedValue = value.asType(expectedType)
            return coercedValue
        } catch (Exception ignore) {
            throw new PropertiesReaderException(message)
        }
    }

}
