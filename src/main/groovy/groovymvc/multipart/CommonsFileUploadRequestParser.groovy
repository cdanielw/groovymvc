package groovymvc.multipart

import groovymvc.Builder
import org.apache.commons.fileupload.FileItem
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.Part

import static groovymvc.multipart.SizeUnit.MB

/**
 * @author Daniel Wiell
 */
interface MultipartRequestParser {
    Map<String, ExtendedPart> parse(HttpServletRequest request)
}

interface ExtendedPart extends Part {
    String getFileName()
}

class CommonsFileUploadRequestParser implements MultipartRequestParser {
    private File repository
    private final int maxSizeBytes
    private final int maxFileSizeBytes
    private final int maxMemorySizeBytes

    private CommonsFileUploadRequestParser(MultipartRequestParserBuilder builder) {
        verifyCommonsFileUploadDependency()
        maxMemorySizeBytes = builder.maxMemorySizeBytes
        maxSizeBytes = builder.maxSizeBytes
        maxFileSizeBytes = builder.maxFileSizeBytes
        repository = new File(builder.servletContext.getAttribute(ServletContext.TEMPDIR) as String)
    }

    static MultipartRequestParserBuilder builder(ServletContext servletContext) {
        new MultipartRequestParserBuilder(servletContext)
    }

    Map<String, ExtendedPart> parse(HttpServletRequest request) {
        if (!ServletFileUpload.isMultipartContent(request))
            return Collections.emptyMap()
        def items = fileUpload.parseRequest(request)
        def parts = [:]
        items.each { FileItem item ->
            parts[item.fieldName] = new FileItemPart(item, repository)
        }
        return parts
    }

    private ServletFileUpload getFileUpload() {
        def factory = new DiskFileItemFactory(maxMemorySizeBytes, repository);
        def upload = new ServletFileUpload(factory);
        upload.sizeMax = maxSizeBytes
        upload.fileSizeMax = maxFileSizeBytes
        return upload
    }


    private static class FileItemPart implements ExtendedPart {
        private final FileItem item
        private File repository

        FileItemPart(FileItem item, File repository) {
            this.item = item
            this.repository = repository
        }

        InputStream getInputStream() throws IOException { item.inputStream }

        String getContentType() { item.contentType }

        String getName() { item.fieldName }

        long getSize() { item.size }

        void write(String fileName) throws IOException {
            def file = new File(repository, fileName)
            item.write(file)
        }

        void delete() throws IOException { item.delete() }

        String getHeader(String name) { item.headers.getHeader(name) }

        Collection<String> getHeaders(String name) { item.headers.getHeaders(name).collect { it } }

        Collection<String> getHeaderNames() { item.headers.headerNames.collect { it } }

        String getFileName() { item.name }
    }

    private static void verifyCommonsFileUploadDependency() {
        try {
            Class.forName("org.apache.commons.fileupload.servlet.ServletFileUpload")
        } catch (Exception ignore) {
            throw new IllegalStateException("${CommonsFileUploadRequestParser.name} is dependent on Apache Commons FileUpload.\n" +
                    "Please add dependency:" +
                    "\n" +
                    "        <dependency>\n" +
                    "            <groupId>commons-fileupload</groupId>\n" +
                    "            <artifactId>commons-fileupload</artifactId>\n" +
                    "            <version>\${commons-fileupload.version}</version>\n" +
                    "        </dependency> \n")
        }
    }

    static class MultipartRequestParserBuilder implements Builder<CommonsFileUploadRequestParser> {
        private ServletContext servletContext
        private int maxSizeBytes = MB[10]
        private int maxFileSizeBytes = MB[10]
        private int maxMemorySizeBytes = MB[1]

        MultipartRequestParserBuilder(ServletContext servletContext) {
            this.servletContext = servletContext
        }

        CommonsFileUploadRequestParser build() {
            // Having maxSizeBytes smaller then maxFileSizeBytes doesn't make sense
            maxSizeBytes = Math.max(maxSizeBytes, maxFileSizeBytes)
            return new CommonsFileUploadRequestParser(this)
        }

        MultipartRequestParserBuilder maxSizeBytes(int size, SizeUnit unit) {
            maxSizeBytes = unit.getAt(size)
            return this
        }

        MultipartRequestParserBuilder maxFileSizeBytes(int size, SizeUnit unit) {
            maxFileSizeBytes = unit.getAt(size)
            return this
        }

        MultipartRequestParserBuilder maxMemorySizeBytes(int size, SizeUnit unit) {
            maxFileSizeBytes = unit.getAt(size)
            return this
        }
    }
}


enum SizeUnit {
    B(1), KB(1000), MB(1000 * 1000), GB(1000 * 1000 * 1000)

    final int factor

    SizeUnit(int factor) {
        this.factor = factor
    }

    int getAt(int amount) {
        amount * factor
    }
}