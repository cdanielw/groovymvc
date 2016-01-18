package groovymvc

import groovy.xml.XmlUtil
import groovymvc.template.sitemesh.SiteMeshRenderer
import spock.lang.Specification

import javax.servlet.ServletContext
/**
 * @author Daniel Wiell
 */
class UsageScenarioIntegrationTest extends Specification {
    final servletContainer = new ServletContainer(App).start()
    final client = servletContainer.client

    def cleanup() { servletContainer.stop() }

    @SuppressWarnings(["GroovyAssignabilityCheck"])
    def 'Save, fail validation, edit, save'() {
        when: 'Invalid POST'
        def invalidPostResponse = client.post(path: 'author/123/save',
                query: [
                        name: 'Asimov',
                        dateOfBirth: 'This is not a date',
                        'books[0].id': '10',
                        'books[0].title': 'Foundation',
                        'books[0].year': '1951',
                        'books[0].author.id': 'strange id',
                        'books[1].id': '20',
                        'books[1].title': 'Caves',
                        'books[1].year': '1954',
                        'books[1].author.id': '1',
                ])
        then:
        invalidPostResponse.status == 200
        def editPageContent = XmlUtil.serialize invalidPostResponse.data
        editPageContent.contains('This is not a date')

        when: 'Valid POST'
        def validPostResponse = client.post(path: 'author/123/save',
                query: [
                        name: 'Asimov',
                        dateOfBirth: '1920-01-02',
                        'books[0].id': '10',
                        'books[0].title': 'Foundation',
                        'books[0].year': '1951',
                        'books[0].author.id': '1',
                        'books[1].id': '20',
                        'books[1].title': 'Caves',
                        'books[1].year': '1954',
                        'books[1].author.id': '1',
                ])
        then:
        validPostResponse.status == 302
        def location = validPostResponse.headers['location']?.value
        location == "${ServletContainer.URL}author/123/show"

        when:
        def showResponse = client.get(path: 'author/123/show')

        then:
        showResponse.status == 200
        def showPageContent = XmlUtil.serialize showResponse.data
        showPageContent.contains('Asimov')
        showPageContent.contains("Saved Asimov")
    }
}

interface AuthorService {
    void save(Author author)

    Author byId(long id)
}

class AuthorRepository implements AuthorService {
    Map<Long, Author> authors = [:]

    void save(Author author) { authors[author.id] = author }

    Author byId(long id) { authors[id] }
}

class AuthorController {
    AuthorController(AuthorService authorService, Controller controller) {
        controller.with {
            get('/author/{id}/show') {
                def author = authorService.byId(params.id as long)
                render('author/show', [author: author])
            }

            get('/author/{id}/edit') {
                def author = authorService.byId(params.id as long)
                render('/author/edit', [author: author])
            }

            post('/author/{id}/save') {
                def author = new Author()
                if (bindAndValidate(author))
                    return render('author/edit', [author: author])
                authorService.save(author)
                flash.message = "Saved $author.name"
                redirect("/author/${author.id}/show")
            }
        }
    }
}

class App extends AbstractMvcFilter {
    Controller bootstrap(ServletContext servletContext) {
        def controller = Controller.builder(servletContext)
                .templateRenderer(SiteMeshRenderer.builder(servletContext))
                .messageSource('test-messages')
                .build()
        controller.with {
            converter(Date) { Date.parse('yyyy-MM-dd', it as String) }
            constrain(Author, Author.constraints)
            constrain(Book, Book.constraints)
        }

        def authorService = new AuthorRepository()
        new AuthorController(authorService, controller)
        return controller
    }

}

