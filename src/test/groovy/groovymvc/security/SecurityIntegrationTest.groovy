package groovymvc.security

import groovymvc.AbstractIntegrationTest
import groovymvc.security.User
/**
 * @author Daniel Wiell
 */
class SecurityIntegrationTest extends AbstractIntegrationTest {
    boolean accessed

    def 'Can restrict access when registering route'() {
        c.get('/{paramOne}/{paramTwo}', ['some role']) {
            accessed = true
        }

        principalProvider.lookup(_) >> new TestUser('some username', 'another role')

        when: client.get(path: 'foo/bar')
        then: !accessed
    }

    def 'Can restrict access directly on controller'() {
        c.restrict('/**', ['some role'])

        c.get('/{paramOne}/{paramTwo}') {
            accessed = true
        }

        principalProvider.lookup(_) >> new TestUser('some username', 'another role')

        when: client.get(path: 'foo/bar')
        then: !accessed
    }


    def 'First matching restriction applies'() {
        c.restrict('/**', ['some role'])
        c.restrict('/**', ['another role'])

        c.get('/{paramOne}/{paramTwo}') {
            accessed = true
        }

        principalProvider.lookup(_) >> new TestUser('some username', 'another role')

        when: client.get(path: 'foo/bar')
        then: !accessed
    }


    def 'Request contains user principal, remote user and roles'() {
        String principalName = null
        String remoteUser = null
        boolean inRole = false

        c.get('/{paramOne}/{paramTwo}', ['some role']) {
            principalName = request.userPrincipal?.name
            remoteUser = request.remoteUser
            inRole = request.isUserInRole('some role')
        }

        principalProvider.lookup(_) >> new TestUser('some username', 'some role')

        when: client.get(path: 'foo/bar')
        then:
        principalName == 'some username'
        remoteUser == 'some username'
        inRole
    }


    static class TestUser implements User {
        final String name = null
        final String email = null
        final String username
        final Set<String> roles

        TestUser(String username, String role) {
            this.username = username
            roles = [role] as Set
        }

        boolean hasUsername(String username) {
            username.equalsIgnoreCase(this.username)
        }

        boolean hasRole(String role) {
            roles?.find { it.equalsIgnoreCase(role) }
        }
    }
}
