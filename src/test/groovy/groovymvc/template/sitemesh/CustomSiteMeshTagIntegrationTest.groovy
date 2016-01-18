package groovymvc.template.sitemesh

import groovy.xml.XmlUtil
import groovymvc.AbstractIntegrationTest

/**
 * @author Daniel Wiell
 */
class CustomSiteMeshTagIntegrationTest extends AbstractIntegrationTest {
    def setup() {
        renderer = SiteMeshRenderer.builder(app.servletContext)
                .tags('t:customTag', 't:none-existing', 'tag-without-namespace')
                .build()
    }

    def 'Can render tag with namespace'() {
        c.get("/**") {
            render('pageWithCustomSiteMeshTag')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 200
        resp.data.BODY.text().contains 'Hello'
        resp.data.BODY.text().contains 'Bar'
        resp.data.BODY.text().contains 'Body'
    }

    def 'Can render tag without namespace'() {
        c.get("/**") {
            render('pageWithCustomSiteMeshTagWithoutNamespace')
        }
        when:
        def resp = client.get(path: '')

        then:
        resp.status == 200
        println XmlUtil.serialize(resp.data)
        resp.data.BODY.text().contains 'Hello'
        resp.data.BODY.text().contains 'Bar'
        resp.data.BODY.text().contains 'Body'
    }
}
