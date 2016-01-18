package groovymvc.util;

import javax.xml.bind.DatatypeConverter;

/**
 * @author Daniel Wiell
 */
public class Decode {
    public static String base64(String encodedCredentials) {
        return new String(DatatypeConverter.parseBase64Binary(encodedCredentials));
    }
}
