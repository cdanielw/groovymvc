package groovymvc.security

/**
 * @author Daniel Wiell
 */
interface User {
    String getName()

    String getEmail()

    String getUsername()

    Set<String> getRoles()

    boolean hasUsername(String username)

    boolean hasRole(String role)
}