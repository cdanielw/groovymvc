package groovymvc.util

/**
 * @author Daniel Wiell
 */
class PropertiesReaderException extends RuntimeException {
    PropertiesReaderException(String s) {
        super(s)
    }

    PropertiesReaderException(String s, Throwable throwable) {
        super(s, throwable)
    }
}

