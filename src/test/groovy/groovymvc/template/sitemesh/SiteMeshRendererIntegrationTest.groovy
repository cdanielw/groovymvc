package groovymvc.template.sitemesh

import groovy.xml.XmlUtil
import groovymvc.AbstractIntegrationTest
/**
 * @author Daniel Wiell
 */
class SiteMeshRendererIntegrationTest extends AbstractIntegrationTest {
    def setup() {
        renderer = SiteMeshRenderer.builder(servletContainer.servletContext).build()
    }

    def 'Can render'() {
        c.get("/**") {
            render('author/edit', [:])
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 200
        resp.data.HEAD.TITLE.text() == 'Edit'
    }

    def 'When JSP is not found, status is 404'() {
        c.get("/**") {
            render('not-there', [:])
        }

        when:
        def resp = client.get(path: '')

        then:
        resp.status == 404
    }

    def 'An error page can be decorated'() {
        c.get('/**') {
            throw new RuntimeException()
        }
        c.error(RuntimeException) {
            response.status = 543
            render('error')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 543
        resp.data.contains('Error page')
    }


    def 'Can apply multiple layouts'() {
        c.get("/**") {
            render('pageWithTwoLayouts')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 200
        resp.data.HEAD.TITLE.text() == 'title'
        resp.data.BODY.text().contains('page')
        resp.data.BODY.text().contains('layout1')
        resp.data.BODY.text().contains('layout2')
    }

    def 'List of layouts to apply can contain spaces and extra commas'() {
        c.get("/**") {
            render('pageWithTwoLayouts')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 200
        resp.data.HEAD.TITLE.text() == 'title'
        resp.data.BODY.text().contains('page')
        resp.data.BODY.text().contains('layout1')
        resp.data.BODY.text().contains('layout2')
    }

    def 'List of layouts can be empty'() {
        c.get("/**") {
            render('pageWithEmptyLayoutList')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 200
        resp.data.HEAD.TITLE.text() == 'title'
        resp.data.BODY.text().contains('page')
    }

    def 'Fails when layout cannot be found'() {
        c.get("/**") {
            render('pageWithNonExistingLayout')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 404
    }


    def 'Divs are extracted'() {
        c.get("/**") {
            render('pageWithDiv')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 200
        println XmlUtil.serialize(resp.data)
        resp.data.BODY.text().contains('First Div')
        resp.data.BODY.text().contains('Second Div')
    }

}
