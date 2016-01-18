package groovymvc.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Utility for escaping HTML and JavaScript.
 * <p>
 * Stripped down version of org.springframework.web.util.JavaScriptUtils and org.springframework.web.util.HtmlUtils.
 */
public class Escape {

    /**
     * Shared instance of pre-parsed HTML character entity references.
     */
    private static final HtmlCharacterEntityReferences characterEntityReferences =
            new HtmlCharacterEntityReferences();

    /**
     * Turn special characters into HTML character references.
     * Handles complete character set defined in HTML 4.01 recommendation.
     * <p>Escapes all special characters to their corresponding
     * entity reference (e.g. {@code &lt;}).
     * <p>Reference:
     * <a href="http://www.w3.org/TR/html4/sgml/entities.html">
     * http://www.w3.org/TR/html4/sgml/entities.html
     * </a>
     *
     * @param input the (unescaped) input string
     * @return the escaped string
     */
    public static String html(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            String reference = characterEntityReferences.convertToReference(character);
            if (reference != null) {
                escaped.append(reference);
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }


    /**
     * Turn JavaScript special characters into escaped characters.
     *
     * @param input the input string
     * @return the string with escaped characters
     */
    public static String javaScript(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder filtered = new StringBuilder(input.length());
        char prevChar = '\u0000';
        char c;
        for (int i = 0; i < input.length(); i++) {
            c = input.charAt(i);
            if (c == '"') {
                filtered.append("\\\"");
            } else if (c == '\'') {
                filtered.append("\\'");
            } else if (c == '\\') {
                filtered.append("\\\\");
            } else if (c == '/') {
                filtered.append("\\/");
            } else if (c == '\t') {
                filtered.append("\\t");
            } else if (c == '\n') {
                if (prevChar != '\r') {
                    filtered.append("\\n");
                }
            } else if (c == '\r') {
                filtered.append("\\n");
            } else if (c == '\f') {
                filtered.append("\\f");
            } else if (c == '\b') {
                filtered.append("\\b");
            }
            // No '\v' in Java, use octal value for VT ascii char
            else if (c == '\013') {
                filtered.append("\\v");
            } else if (c == '<') {
                filtered.append("\\u003C");
            } else if (c == '>') {
                filtered.append("\\u003E");
            }
            // Unicode for PS (line terminator in ECMA-262)
            else if (c == '\u2028') {
                filtered.append("\\u2028");
            }
            // Unicode for LS (line terminator in ECMA-262)
            else if (c == '\u2029') {
                filtered.append("\\u2029");
            } else {
                filtered.append(c);
            }
            prevChar = c;

        }
        return filtered.toString();
    }

    /**
     * Represents a set of character entity references defined by the
     * HTML 4.0 standard.
     * <p>
     * <p>A complete description of the HTML 4.0 character set can be found
     * at http://www.w3.org/TR/html4/charset.html.
     *
     * @author Juergen Hoeller
     * @author Martin Kersten
     * @since 1.2.1
     */
    private static class HtmlCharacterEntityReferences {

        private static final String PROPERTIES_FILE = "HtmlCharacterEntityReferences.properties";

        static final char REFERENCE_START = '&';

        static final char REFERENCE_END = ';';


        private final String[] characterToEntityReferenceMap = new String[3000];

        /**
         * Returns a new set of character entity references reflecting the HTML 4.0 character set.
         */
        public HtmlCharacterEntityReferences() {
            Properties entityReferences = new Properties();

            // Load reference definition file
            InputStream is = HtmlCharacterEntityReferences.class.getResourceAsStream(PROPERTIES_FILE);
            if (is == null) {
                throw new IllegalStateException(
                        "Cannot find reference definition file [HtmlCharacterEntityReferences.properties] as class path resource");
            }
            try {
                try {
                    entityReferences.load(is);
                } finally {
                    is.close();
                }
            } catch (IOException ex) {
                throw new IllegalStateException(
                        "Failed to parse reference definition file [HtmlCharacterEntityReferences.properties]: " + ex.getMessage());
            }

            // Parse reference definition properties
            Enumeration keys = entityReferences.propertyNames();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                int referredChar = Integer.parseInt(key);
                assertIsTrue((referredChar < 1000 || (referredChar >= 8000 && referredChar < 10000)),
                        "Invalid reference to special HTML entity: " + referredChar);
                int index = (referredChar < 1000 ? referredChar : referredChar - 7000);
                String reference = entityReferences.getProperty(key);
                this.characterToEntityReferenceMap[index] = REFERENCE_START + reference + REFERENCE_END;
            }
        }

        /**
         * Return the reference mapped to the given character or {@code null}.
         */
        public String convertToReference(char character) {
            if (character < 1000 || (character >= 8000 && character < 10000)) {
                int index = (character < 1000 ? character : character - 7000);
                String entityReference = this.characterToEntityReferenceMap[index];
                if (entityReference != null) {
                    return entityReference;
                }
            }
            return null;
        }

        private static void assertIsTrue(boolean expression, String message) {
            if (!expression) {
                throw new IllegalArgumentException(message);
            }
        }

    }
}

