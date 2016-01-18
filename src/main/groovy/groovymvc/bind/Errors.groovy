package groovymvc.bind
/**
 * Container for {@link PropertyError}s. Keys are paths to properties.
 *
 * @author Daniel Wiell
 */
class Errors<T extends PropertyError> {
    @Delegate
    private final Map<String, List<T>> errors

    Errors(Map<String, List<T>> errors) {
        this.errors = errors
    }

    Errors<PropertyError> plus(Errors<T> errors) {
        def result = new Errors([:])
        result.putAll(errors)
        result.putAll(this)
        return result
    }
}
