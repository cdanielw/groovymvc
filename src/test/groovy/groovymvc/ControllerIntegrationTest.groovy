package groovymvc

import groovymvc.template.TemplateRenderer
import groovyx.net.http.ContentType
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.BasicHttpParams
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponseWrapper

/**
 * @author Daniel Wiell
 */
class ControllerIntegrationTest extends AbstractIntegrationTest {
    @Unroll
    def 'Given #pattern: #path -> #status'(String pattern, String path, int status) {
        c.get(pattern) { send 'ok' }

        expect:
        client.get(path: path).status == status

        where:
        pattern            | path                  | status
        '/path'            | 'path'                | 200
        '/path'            | 'path'                | 200
        '/path'            | 'path/'               | 404
        '/some/path'       | 'some/path'           | 200
        '/some/path'       | 'some/'               | 404
        'some/path'        | 'some/path'           | 200
        '/some/*'          | 'some/match'          | 200
        '/some/*'          | 'some/'               | 200
        '/some/*'          | 'some'                | 404
        '/some/*'          | 'some/deep/path'      | 404
        '/*/*'             | 'some/path'           | 200
        '/*/*'             | 'some/'               | 200
        '/*/*'             | 'some'                | 404
        '/some/**'         | 'some/path'           | 200
        '/some/**'         | 'some/deep/path'      | 200
        '/some/**'         | 'some/'               | 200
        '/some/**'         | 'some'                | 200
        '/some/**'         | 'other/path'          | 404
        '/**/**'           | 'some/path'           | 200
        '/**/**'           | 'path'                | 200
        '/some*'           | 'something'           | 200
        '/some*'           | 'some/thing'          | 404
        '/some**'          | 'something'           | 200
        '/some**'          | 'some/thing'          | 404
        '/some/{param}'    | 'some/path'           | 200
        '/some/{param}'    | 'some/'               | 404
        '/some/{param}'    | 'some'                | 404
        '/some/**/{param}' | 'some/other/path'     | 200
        '/some/{param}/**' | 'some/path/and/more'  | 200
        '/some/{param}/**' | 'some/path/and/more'  | 200
        '/{many}/{params}' | 'some/path'           | 200
        '/{params}'        | 'some/path/'          | 404
        '/path'            | 'path;jsessionid=foo' | 200
    }


    def 'Path params are url decoded'() {
        def key = 'Key & needing = encoding'
        def value = 'Value & needing = encoding'
        def valueFromParams = null
        c.get("/{$key}") {
            valueFromParams = params[key]
        }
        when: client.get(path: value)
        then:
        valueFromParams == value
    }


    def 'URL encoded bodies are included in params'() {
        def containsParam = false
        c.post('/**') {
            containsParam = params.containsKey('param')
        }

        when: client.post(path: '', body: [param: 'value'], requestContentType: ContentType.URLENC)
        then: containsParam
    }


    def 'Query params are decoded'() {
        def key = 'Key & needing = encoding'
        def value = 'Value & needing = encoding'
        def valueFromParams = null
        c.get('/**') {
            valueFromParams = params[key]
        }
        when: client.get(path: '', query: [(key): value])
        then:
        valueFromParams == value
    }

    def 'Params contains both query and path params'() {
        def paramsInRequest = null
        c.get('/{paramOne}') {
            paramsInRequest = params
        }
        when: client.get(path: 'foo', query: [paramTwo: 'bar'])
        then: paramsInRequest == [paramOne: 'foo', paramTwo: 'bar']
    }

    def 'A POST route cannot be accessed with a GET'() {
        c.post('/**') {}

        expect:
        client.get(path: '').status == 404
        client.post(path: '').status == 200
    }


    @SuppressWarnings("GroovyAssignabilityCheck")
    def 'Can render template'() {
        renderer = Mock(TemplateRenderer)

        c.get('/**') {
            render('some template', [paramOne: 'foo'])
        }
        when: client.get(path: '')
        then: 1 * renderer.render('some template', {
            'paramOne' in it.keySet()
        } as Map, _)
    }


    def 'When both query and path param contain same param name, with braces, both values are part of params'() {
        renderer = Mock(TemplateRenderer)
        def foo = null

        c.get('/{foo[]}') {
            foo = params.foo
        }
        when: client.get(path: 'bar', query: ['foo[]': 'baz'])
        then: foo == ['bar', 'baz']
    }


    def 'Filters are invoked in correct order'() {
        def invocations = []
        c.get('/**') { invocations << 'route' }
        c.after(Controller.GET, '/**') { invocations << 'first after' }
        c.before(Controller.GET, '/**') { invocations << 'first before' }
        c.filter(Controller.GET, '/**') {
            invocations << 'first filter before'; it.call(); invocations << 'first filter after'
        }
        c.filter(Controller.GET, '/**') {
            invocations << 'second filter before'; it.call(); invocations << 'second filter after'
        }
        c.before(Controller.GET, '/**') { invocations << 'second before' }
        c.after(Controller.GET, '/**') { invocations << 'second after' }

        when: client.get(path: '')
        then: invocations == ['first filter before', 'second filter before', 'first before', 'second before', 'route',
                'first after', 'second after', 'second filter after', 'first filter after']
    }

    def 'Around filter is invoked for non-existing resources'() {
        def invocations = []
        c.filter(Controller.GET, '/**') { invocations << 'before'; it.call(); invocations << 'after' }
        c.error(404) { invocations << 'error' }
        when: client.get(path: '')
        then: invocations == ['before', 'error', 'after']
    }

    def 'Error handling is done within around filter'() {
        def invocations = []
        c.get('/**') { throw new IllegalStateException() }
        c.filter(Controller.GET, '/**') {
            invocations << 'before'; it.call(); invocations << 'after'; println response.status
        }
        c.error(IllegalStateException) { invocations << 'error' }
        when: client.get(path: '')
        then: invocations == ['before', 'error', 'after']
    }

    def 'Around filter can wrap request and response'() {
        c.filter(Controller.GET, '/**') {
            def wrappedReq = new HttpServletRequestWrapper(request) { String toString() { 'wrapped' } }
            def wrappedResp = new HttpServletResponseWrapper(response) { String toString() { 'wrapped' } }
            it.call(wrap(wrappedReq, wrappedResp))
        }
        boolean requestIsWrapped = false
        boolean responseIsWrapped = false
        c.get('/**') {
            requestIsWrapped = request.toString() == 'wrapped'
            responseIsWrapped = response.toString() == 'wrapped'
        }

        when: client.get(path: '')
        then:
        requestIsWrapped
        responseIsWrapped
    }

    def 'Multiple Around filter can wrap requests and responses'() {
        c.filter(Controller.GET, '/**') {
            def wrappedReq = new HttpServletRequestWrapper(request) { String toString() { 'wrapped1' } }
            def wrappedResp = new HttpServletResponseWrapper(response) { String toString() { 'wrapped1' } }
            it.call(wrap(wrappedReq, wrappedResp))
        }
        c.filter(Controller.GET, '/**') {
            assert request.toString() == 'wrapped1'
            assert response.toString() == 'wrapped1'
            def wrappedReq = new HttpServletRequestWrapper(request) { String toString() { 'wrapped2' } }
            def wrappedResp = new HttpServletResponseWrapper(response) { String toString() { 'wrapped2' } }
            it.call(wrap(wrappedReq, wrappedResp))
        }
        c.get('/**') {
            assert request.toString() == 'wrapped2'
            assert response.toString() == 'wrapped2'
        }
        expect: client.get(path: '')
    }

    def 'Exception handlers defaults to 500 response'() {
        c.get('/**') { throw new IllegalStateException() }
        c.error(IllegalStateException) {}
        expect: client.get(path: '').status == 500
    }

    def 'Filters are not invoked when no route match'() {
        def invocations = []
        c.before(Controller.GET, '/**') { invocations << 'before' }
        c.after(Controller.GET, '/**') { invocations << 'after' }

        when:
        def status = client.get(path: '').status

        then:
        status == 404
        invocations.empty
    }

    def 'A POST filters are not invoked on GET'() {
        def invocations = []
        c.before(Controller.POST, '/**') { invocations << 'before' }
        c.after(Controller.POST, '/**') { invocations << 'after' }
        c.get('/**') {}

        when: client.get(path: '')
        then: invocations.empty
    }


    def 'Can register an error handler for exception'() {
        c.error(IllegalStateException) {
            response.status = 555
            send(it.message)
        }
        c.get('/**') { throw new IllegalStateException('An exception') }

        when:
        def r = client.get(path: '')

        then:
        r.status == 555
        r.data == 'An exception'
    }


    def 'Can register an error handler for exception cause'() {
        c.error(IllegalStateException, IllegalArgumentException) {
            response.status = 555
            send(it.message)
        }
        c.get('/**') { throw new IllegalStateException('Root exception', new IllegalArgumentException('The cause exception')) }

        when:
        def r = client.get(path: '')

        then:
        r.status == 555
        r.data == 'The cause exception'
    }


    def 'Error handler is invoked for exception subclasses'() {
        c.error(Exception) {
            response.status = 555
            send(it.message)
        }
        c.get('/**') { throw new IllegalStateException('An exception') }

        when:
        def r = client.get(path: '')

        then:
        r.status == 555
        r.data == 'An exception'
    }

    def 'Exception without handler falls back on servlet container'() {
        c.get('/**') { throw new InvalidObjectException('An exception to be handled by servlet container') }

        when:
        def r = client.get(path: '')

        then:
        r.status == 500
        r.data.contains('An exception to be handled by servlet container')
    }


    def 'Execution can be halted'() {
        boolean routeInvoked = false
        c.before(Controller.GET, '/**') {
            halt(234)
        }
        c.get('/**') { routeInvoked = true }

        when: client.get(path: '')
        then: !routeInvoked
    }


    def 'Can route the different HTTP methods'() {
        def invokedMethods = []
        c.delete('/*') { invokedMethods << Controller.DELETE }
        c.head('/*') { invokedMethods << Controller.HEAD }
        c.get('/*') { invokedMethods << Controller.GET }
        c.options('/*') { invokedMethods << Controller.OPTIONS }
        c.post('/*') { invokedMethods << Controller.POST }
        c.put('/*') { invokedMethods << Controller.PUT }
        c.trace('/*') { invokedMethods << Controller.TRACE }

        when:
        Controller.ALL_METHODS.each { method ->
            def client = new DefaultHttpClient(new BasicHttpParams())
            HttpRequestBase request = new HttpRequestBase() {
                String getMethod() { method }
            }
            request.setURI(new URI(this.client.uri as String))
            client.execute(request, new BasicResponseHandler())
        }

        then: invokedMethods == Controller.ALL_METHODS
    }

    def 'Can redirect'() {
        def redirected = false
        c.get('/bar') { redirected = true }
        c.get('/foo') { redirect('/bar') }

        when: client.get(path: 'foo')
        then: redirected
    }


    def 'Request matching an exclude is not handled'() {
        boolean handled = false
        c.exclude('/**/*.css')
        c.get('/**') { handled = true }

        when: client.get(path: 'excluded-file.css')
        then: !handled
    }


    def 'Can store object in flash state'() {
        def counted = []
        c.get('/**') {
            counted << flash.counter
            flash.counter = (flash.counter ?: 0) + 1
        }

        when:
        client.get(path: '')
        client.get(path: '')
        client.get(path: '')

        then:
        counted == [null, 1, 2]
    }

    def 'Flash state is removed in next request'() {
        def foundInFlash = []
        c.get('/addToFlash') {
            flash.foo = 'bar'
        }
        c.get('/checkFlash') {
            foundInFlash << flash.foo
        }

        when:
        client.get(path: 'addToFlash')
        client.get(path: 'checkFlash')
        client.get(path: 'checkFlash')

        then:
        foundInFlash == ['bar', null]
    }


    def 'When providing bad parameters, error handler can be registered'() {
        def errorHandled = false
        c.get('/**') { send params.a }
        c.error(ParamsException) { errorHandled = true }
        when: client.get(path: 'test', query: [a: 'foo', 'a.b': 'bar'])
        then: errorHandled
    }


    def 'When route not found error handler for 404 is called'() {
        def errorHandled = false
        c.error(404) { errorHandled = true }
        when: client.get(path: '')
        then: errorHandled
    }

    def 'When route halts, registered error handler is called'() {
        def errorHandled = false
        c.get('/**') { halt(456) }
        c.error(456) { errorHandled = true }

        when: client.get(path: '')
        then: errorHandled
    }

    def 'When route sets status code, registered error handler is called'() {
        def errorHandled = false
        c.get('/**') { response.status = 456 }
        c.error(456) { errorHandled = true }

        when: client.get(path: '')
        then: errorHandled
    }

    def 'When exception handler sets status, registered status error handler is not called'() {
        def errorHandled = false
        c.get('/**') { throw new IllegalStateException() }
        c.error(IllegalStateException) { response.status = 456 }
        c.error(456) { errorHandled = true }

        when: client.get(path: '')
        then: !errorHandled
    }

    def 'When exception handler halts, correct status code is returned'() {
        c.get('/**') { throw new IllegalStateException() }
        c.error(IllegalStateException) { halt(456) }

        when:
        def response = client.get(path: '')

        then: response.status == 456
    }


    def 'Can register a range of status codes'() {
        def errorsHandled = []
        c.get('/**') { halt(params.required('status', int)) }
        c.error(400..600) { errorsHandled << response.status }

        when:
        client.get(path: '', query: [status: 399])
        client.get(path: '', query: [status: 400])
        client.get(path: '', query: [status: 401])
        client.get(path: '', query: [status: 599])
        client.get(path: '', query: [status: 600])
        client.get(path: '', query: [status: 601])

        then: errorsHandled == [400, 401, 599, 600]
    }


    def 'If a status code is registered multiple times, the first one registered is used'() {
        def firstHandlerCalled = false
        def secondHandlerCalled = false

        c.get('/**') { halt(456) }
        c.error(456) { firstHandlerCalled = true }
        c.error(456) { secondHandlerCalled = true }

        when: client.get(path: '')
        then:
        firstHandlerCalled
        !secondHandlerCalled
    }

    def 'If an exception is registered multiple times, the first one registered is used'() {
        def firstHandlerCalled = false
        def secondHandlerCalled = false

        c.get('/**') { throw new IllegalStateException() }
        c.error(IllegalStateException) { firstHandlerCalled = true }
        c.error(IllegalStateException) { secondHandlerCalled = true }

        when: client.get(path: '')
        then:
        firstHandlerCalled
        !secondHandlerCalled
    }


    def 'If no content type is specified, text/html is used'() {
        c.get('/**') {}
        when:
        def resp = client.get(path: '')

        then: resp.contentType == 'text/html'
    }

    def 'If no character encoding is specified, UTF-8 is used'() {
        c.get('/**') {}
        when:
        def resp = client.get(path: '')

        then: resp.entity.contentType.value.contains('UTF-8')
    }

}
