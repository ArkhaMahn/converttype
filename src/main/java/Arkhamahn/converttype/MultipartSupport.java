package Arkhamahn.converttype;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Multipart form-data helpers.
 *
 * <p>Parts are parsed/serialized with bracket-notation names ({@code a[b][0]}). File parts are
 * represented in the JSON model as {@code {"filename": "...", "content": "..."}} so they survive
 * round trips.
 */
final class MultipartSupport {

    private static final Pattern BOUNDARY_PATTERN =
            Pattern.compile("(?i)(?:^|;)\\s*boundary\\s*=\\s*\"?([^\";]+)\"?");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?i)name\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("(?i)filename\\s*=\\s*\"([^\"]*)\"");

    private MultipartSupport() {}

    /** A multipart body together with its boundary, so the {@code Content-Type} can be set. */
    static final class MultipartData {
        final String boundary;
        final byte[] body;

        MultipartData(String boundary, byte[] body) {
            this.boundary = boundary;
            this.body = body;
        }
    }

    /** Extracts the {@code boundary} attribute from a {@code Content-Type} header value. */
    static String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher matcher = BOUNDARY_PATTERN.matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Serializes a JSON model to a multipart body. */
    static MultipartData build(Object model) {
        String boundary =
                "----ZAPConvertType"
                        + Long.toHexString(new SecureRandom().nextLong());
        StringBuilder body = new StringBuilder();
        flattenParts(model, null, body, boundary);
        return new MultipartData(boundary, body.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Parses a multipart body into a JSON model. */
    static JSONObject parse(byte[] body, String boundary) {
        JSONObject object = new JSONObject();
        if (boundary == null || boundary.isEmpty() || body.length == 0) {
            return object;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        String[] chunks = text.split("--" + Pattern.quote(boundary));
        for (String chunk : chunks) {
            String part = chunk;
            if (part.startsWith("\r\n")) {
                part = part.substring(2);
            }
            if (part.endsWith("\r\n")) {
                part = part.substring(0, part.length() - 2);
            }
            if (part.isEmpty() || "--".equals(part)) {
                continue;
            }
            int separator = part.indexOf("\r\n\r\n");
            String headersBlock = separator >= 0 ? part.substring(0, separator) : "";
            String content = separator >= 0 ? part.substring(separator + 4) : part;

            String name = null;
            String filename = null;
            for (String line : headersBlock.split("\r\n")) {
                if (line.toLowerCase().startsWith("content-disposition:")) {
                    Matcher nameMatcher = NAME_PATTERN.matcher(line);
                    if (nameMatcher.find()) {
                        name = nameMatcher.group(1);
                    }
                    Matcher fileMatcher = FILENAME_PATTERN.matcher(line);
                    if (fileMatcher.find()) {
                        filename = fileMatcher.group(1);
                    }
                }
            }
            if (name == null) {
                continue;
            }

            Object value = content;
            if (filename != null) {
                JSONObject fileObject = new JSONObject();
                fileObject.put("filename", filename);
                fileObject.put("content", content);
                value = fileObject;
            }
            add(object, name, value);
        }
        return object;
    }

    private static void flattenParts(
            Object value, String name, StringBuilder body, String boundary) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.length() == 2 && object.has("filename") && object.has("content")) {
                String filename = object.getString("filename");
                Object content = object.get("content");
                String valueStr =
                        content == null || content == JSONObject.NULL ? "" : String.valueOf(content);
                appendFilePart(body, boundary, name, filename, valueStr);
                return;
            }
            for (String key : object.keySet()) {
                String newName = (name == null || name.isEmpty()) ? key : name + "[" + key + "]";
                flattenParts(object.get(key), newName, body, boundary);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                flattenParts(array.opt(i), name, body, boundary);
            }
        } else {
            String valueStr = value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
            appendPart(body, boundary, name == null ? "" : name, valueStr);
        }
    }

    private static void appendPart(StringBuilder body, String boundary, String name, String value) {
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"").append(escape(name)).append("\"\r\n");
        body.append("\r\n").append(value).append("\r\n");
    }

    private static void appendFilePart(
            StringBuilder body, String boundary, String name, String filename, String content) {
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"")
                .append(escape(name))
                .append("\"; filename=\"")
                .append(escape(filename))
                .append("\"\r\n");
        body.append("Content-Type: application/octet-stream\r\n\r\n");
        body.append(content).append("\r\n");
    }

    private static void add(JSONObject object, String name, Object value) {
        if (object.has(name)) {
            Object current = object.get(name);
            if (current instanceof JSONArray) {
                ((JSONArray) current).put(value);
            } else {
                object.put(name, new JSONArray().put(current).put(value));
            }
        } else {
            object.put(name, value);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}