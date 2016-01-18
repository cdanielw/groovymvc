package groovymvc.security

import groovymvc.AbstractIntegrationTest

/**
 * @author Daniel Wiell
 */
class BasicRequestAuthenticatorIntegrationTest extends AbstractIntegrationTest {
    def verifier = Mock(UsernamePasswordVerifier)
    RequestAuthenticator requestAuthenticator = new BasicRequestAuthenticator('test-realm', verifier)

    def handled = false
    def username = 'a username'
    def password = 'a password'

    def setup() {
        c.get("/**", []) {
            handled = true
        }
        principalProvider.lookup(_ as String) >> { new SecurityIntegrationTest.TestUser(it[0], 'some role') }
    }

    def 'When not authenticated, 401 is returned with a WWW-Authenticate header'() {
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 401
        resp.containsHeader('WWW-Authenticate')
        !handled
    }


    def 'Can authenticate'() {
        authenticated()

        when:
        def resp = client.get(path: '')

        then:
        1 * verifier.verify(username, password) >> true
        resp.status == 200
        handled
    }

    def 'An already verified user is not verified the next request'() {
        authenticated()

        when:
        client.get(path: '')
        def resp = client.get(path: '')

        then:
        1 * verifier.verify(username, password) >> true
        resp.status == 200
        handled
    }

    def 'When failing to verify username/password, 401 is returned with a WWW-Authenticate header'() {
        authenticated()

        when:
        def resp = client.get(path: '')

        then:
        1 * verifier.verify(username, password) >> false
        resp.status == 401
        resp.containsHeader('WWW-Authenticate')
        !handled
    }

    private void authenticated() {
        client.auth.basic(username, password)
    }

}
