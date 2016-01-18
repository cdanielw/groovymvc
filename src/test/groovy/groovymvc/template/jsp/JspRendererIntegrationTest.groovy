package groovymvc.template.jsp

import groovymvc.AbstractIntegrationTest
/**
 * @author Daniel Wiell
 */
class JspRendererIntegrationTest extends AbstractIntegrationTest {
    def setup() {
        renderer = JspRenderer.builder().build()
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
}
