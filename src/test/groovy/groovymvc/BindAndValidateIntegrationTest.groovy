package groovymvc

import groovymvc.bind.DefaultBinder
import groovymvc.bind.PropertyError
import groovymvc.i18n.MessageSource
import groovymvc.validate.DefaultValidator
import spock.lang.Specification

import java.text.SimpleDateFormat

/**
 * @author Daniel Wiell
 */
class BindAndValidateIntegrationTest extends Specification {
    private MessageSource messageSource = Mock(MessageSource)

    def binder = new DefaultBinder(messageSource).register(Date,
            { Date.parse('yyyy-MM-dd', it as String) },
            { new SimpleDateFormat('yyyy-MM-dd').format(it as Date) })

    def validator = new DefaultValidator(messageSource)
            .register(Author, Author.constraints)
            .register(Book, Book.constraints)

    def 'Valid request'() {
        def queryString = 'id=1&name=Asimov&dateOfBirth=1920-01-02&' +
                'books[0].id=10&books[0].title=Foundation&books[0].year=1951&books[0].author.id=1&' +
                'books[1].id=20&books[1].title=Caves&books[1].year=1954&books[1].author.id=1'

        when:
        def params = Params.parse(queryString)
        def author = new Author()
        def errors = binder.bind(author, params) + validator.validate(author)

        then:
        author.id == 1
        author.name == 'Asimov'
        author.dateOfBirth == Date.parse('yyyy-MM-dd', '1920-01-02')
        author.books.size() == 2
        author.books[0].id == 10
        author.books[0].title == 'Foundation'
        author.books[0].year == 1951
        author.books[0].author.id == 1
        author.books[1].id == 20
        author.books[1].title == 'Caves'
        author.books[1].year == 1954
        author.books[1].author.id == 1
        errors.isEmpty()
    }

    def 'Invalid request'() {
        def queryString = 'id=1&dateOfBirth=2920-01-02&' +
                'books[0].id=10&books[0].title=Foundation&books[0].year=1951&' +
                'books[1].id=20&books[1].title=A too long title&books[1].year=Not a number&books[1].author.id=1'


        when:
        def params = Params.parse(queryString)
        def author = new Author()
        def errors = binder.bind(author, params) + validator.validate(author)

        then:
        author.id == 1
        author.name == null
        author.dateOfBirth == Date.parse('yyyy-MM-dd', '2920-01-02')
        author.books.size() == 2
        author.books[0].id == 10
        author.books[0].title == 'Foundation'
        author.books[0].year == 1951
        author.books[0].author == null
        author.books[1].id == 20
        author.books[1].title == 'A too long title'
        author.books[1].year == 0
        author.books[1].author.id == 1

        binder.bind(author, params).size() == 1
        binder.bind(author, params)['books[1].year']

        validator.validate(author).size() == 8
        validator.validate(author)['name']
        validator.validate(author)['dateOfBirth']
        validator.validate(author)['books']
        validator.validate(author)['books[0]']
        validator.validate(author)['books[0].author']
        validator.validate(author)['books[1]']
        validator.validate(author)['books[1].title']
        validator.validate(author)['books[1].year']

        errors.size() == 8
        errors['books[1].year'].size() == 1
        errors['books[1].year'].first() instanceof PropertyError
    }

}
