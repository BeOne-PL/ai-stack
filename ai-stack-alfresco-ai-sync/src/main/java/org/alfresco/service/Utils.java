package org.alfresco.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility service providing common Alfresco-related helper methods.
 */
@Service
public class Utils {

    /**
     * Decodes a QName segment from Alfresco's encoded format to a human-readable name.
     * <p>
     * This method:
     * <ul>
     *   <li>Removes the {@code cm:} prefix if present</li>
     *   <li>Decodes any encoded characters in the format {@code _xXXXX_}, where {@code XXXX} is a hex Unicode value</li>
     * </ul>
     * For example, {@code cm:Knowledge_x0020_Base} becomes {@code Knowledge Base}.
     *
     * @param segment the encoded QName segment (e.g. {@code cm:Knowledge_x0020_Base})
     * @return the decoded folder or node name (e.g. {@code Knowledge Base})
     */
    public String decodeQNameSegment(String segment) {
        // Remove cm: prefix if present
        if (segment.startsWith("cm:")) {
            segment = segment.substring(3);
        }

        // Decode _xXXXX_ sequences (e.g. _x0020_ → ' ')
        Pattern pattern = Pattern.compile("_x([0-9A-Fa-f]{4})_");
        Matcher matcher = pattern.matcher(segment);
        StringBuilder decoded = new StringBuilder();

        while (matcher.find()) {
            int charCode = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(decoded, Character.toString((char) charCode));
        }

        matcher.appendTail(decoded);
        return decoded.toString();
    }


}
