package groovymvc.security

/**
 * @author Daniel Wiell
 */
interface UserProvider<T extends User> {
    T lookup(String username)
}