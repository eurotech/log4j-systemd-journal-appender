package de.bwaldvogel.log4j;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test helper that decodes datagrams written in the systemd journal <i>native protocol</i>.
 * <p>
 * Fields are serialized either as {@code KEY=value\n} or, when the value contains a newline,
 * as {@code KEY\n} followed by a 64-bit little-endian length, the raw value and a trailing
 * {@code \n}. See https://systemd.io/JOURNAL_NATIVE_PROTOCOL/
 */
final class JournalProtocol {

    private JournalProtocol() {
    }

    static Map<String, String> parse(byte[] datagram) {
        Map<String, String> fields = new LinkedHashMap<>();
        int position = 0;
        while (position < datagram.length) {
            int keyStart = position;
            while (position < datagram.length && datagram[position] != '=' && datagram[position] != '\n') {
                position++;
            }
            if (position >= datagram.length) {
                throw new IllegalArgumentException("Malformed journal datagram: unterminated key");
            }
            String key = new String(datagram, keyStart, position - keyStart, StandardCharsets.UTF_8);

            if (datagram[position] == '=') {
                position++;
                int valueStart = position;
                while (datagram[position] != '\n') {
                    position++;
                }
                fields.put(key, new String(datagram, valueStart, position - valueStart, StandardCharsets.UTF_8));
                position++; // skip trailing '\n'
            } else {
                position++; // skip '\n' after the key
                long length = 0;
                for (int i = 0; i < 8; i++) {
                    length |= (long) (datagram[position + i] & 0xFF) << (8 * i);
                }
                position += 8;
                fields.put(key, new String(datagram, position, (int) length, StandardCharsets.UTF_8));
                position += (int) length;
                if (datagram[position] != '\n') {
                    throw new IllegalArgumentException("Malformed journal datagram: binary field not terminated by newline");
                }
                position++;
            }
        }
        return fields;
    }

    static String firstKey(byte[] datagram) {
        return parse(datagram).keySet().iterator().next();
    }
}
