package groovymvc.security

import spock.lang.Specification

/**
 * @author Daniel Wiell
 */
class PasswordEncoderTest extends Specification {

    def 'Can encode password'() {
        def password = 'a password'

        when:
        def encoded = PasswordEncoder.encode(password, 'salt')

        then:
        encoded
        encoded != password
    }

    def 'Same password with different salt returns different encoded password'() {
        def password = 'a password'

        expect:
        PasswordEncoder.encode(password, 'salt1') != PasswordEncoder.encode(password, 'salt2')
    }

    def 'Can validate correct password'() {
        def password = 'a password'
        def salt = 'salt1'
        def encoded = PasswordEncoder.encode(password, salt)

        expect: PasswordEncoder.isPasswordValid(encoded, password, salt)
    }

    def 'Can validate incorrect password'() {
        def salt = 'salt1'
        def encoded = PasswordEncoder.encode('a password', salt)

        expect: !PasswordEncoder.isPasswordValid(encoded, 'another password', salt)
    }
}
