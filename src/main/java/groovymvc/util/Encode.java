package groovymvc.util;

/**
 * @author Daniel Wiell
 */
public class Encode {
    private static final char[] HEX = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    public static char[] hex(byte[] bytes) {
        final int nBytes = bytes.length;
        char[] result = new char[2 * nBytes];

        int j = 0;
        for (byte aByte : bytes) {
            // Char for top 4 bits
            result[j++] = HEX[(0xF0 & aByte) >>> 4];
            // Bottom 4
            result[j++] = HEX[(0x0F & aByte)];
        }

        return result;
    }

}
