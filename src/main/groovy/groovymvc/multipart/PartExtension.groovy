package groovymvc.multipart

import javax.servlet.http.Part

/**
 * @author Daniel Wiell
 */
class PartExtension {
    static String getFileName(Part part) {
        if (part instanceof ExtendedPart)
            return part.fileName
        throw new IllegalArgumentException("Part must be instance of $ExtendedPart.name in order to extract fileName.")
    }
}
