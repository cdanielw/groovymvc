package groovymvc.security

/**
 * @author Daniel Wiell
 */
interface UsernamePasswordVerifier {
    boolean verify(String username, String password)
}
