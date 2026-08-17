package org.zaproxy.addon.converttype;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.httpclient.URI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;

/**
 * Detects the source content type of a request, parses the request into a canonical JSON model
 * ({@link JSONObject} / {@link JSONArray}) and serializes it back to the target content type.
 */
public final class RequestConverter {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("(?i)charset\\s*=\\s*\"?([\\w.-]+)");

    private RequestConverter() {}

    /** Detects the source content type from the {@code Content-Type} header and body heuristics. */
    public static ContentType detectSource(HttpMessage message) {
        HttpRequestHeader header = message.getRequestHeader();
        if (header == null || header.isEmpty()) {
            return ContentType.TEXT;
        }
        byte[] body = message.getRequestBody().getBytes();
        boolean hasBody = body.length > 0;

        String contentType = header.getHeader(HttpHeader.CONTENT_TYPE);
        if (contentType == null || contentType.trim().isEmpty()) {
            if (hasBody) {
                return detectFromBody(body);
            }
            return ContentType.URL_ENCODED;
        }

        String base = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (isJsonMime(base)) {
            if (hasBody && isGraphQlJson(body)) {
                return ContentType.GRAPHQL;
            }
            return ContentType.JSON;
        }
        if (isGraphQlMime(base)) {
            return ContentType.GRAPHQL;
        }
        if (isUrlEncodedMime(base)) {
            return ContentType.URL_ENCODED;
        }
        if (isMultipartMime(base)) {
            return ContentType.MULTIPART;
        }
        if (isYamlMime(base)) {
            return ContentType.YAML;
        }
        if (isSoapMime(base)) {
            return ContentType.SOAP;
        }
        if (isXmlMime(base)) {
            if (hasBody && XmlSupport.isSoapEnvelope(body)) {
                return ContentType.SOAP;
            }
            return ContentType.XML;
        }
        if (base.startsWith("text/")) {
            return ContentType.TEXT;
        }
        if (hasBody) {
            return detectFromBody(body);
        }
        return ContentType.URL_ENCODED;
    }

    /** Converts {@code message} to the target content type, producing a {@link ConversionResult}. */
    public static ConversionResult convert(HttpMessage message, ContentType target)
            throws Exception {
        ContentType source = detectSource(message);
        Object model = parse(message, source);

        switch (target) {
            case GET:
                return toGet(message, model);
            case POST:
                return toPost(message, model);
            case MULTIPART:
                return toMultipart(message, model);
            default:
                Charset charset = charsetOf(message.getRequestHeader());
                String mime = target.getMime();
                String contentType = mime + "; charset=" + charset.name();
                byte[] body = serialize(model, target, charset).getBytes(charset);
                return new ConversionResult(bodyMethod(message), null, contentType, body);
        }
    }

    /** Applies a {@link ConversionResult} to the given message. */
    public static void apply(HttpMessage message, ConversionResult result) throws Exception {
        HttpRequestHeader header = message.getRequestHeader();
        header.setMethod(result.getMethod());
        if (result.getUri() != null) {
            header.setURI(new URI(result.getUri(), true));
        }
        if (result.getContentType() == null) {
            header.setHeader(HttpHeader.CONTENT_TYPE, null);
        } else {
            header.setHeader(HttpHeader.CONTENT_TYPE, result.getContentType());
        }
        byte[] body = result.getBody();
        if (body == null) {
            body = new byte[0];
        }
        message.setRequestBody(body);
        if (body.length == 0) {
            header.setHeader(HttpHeader.CONTENT_LENGTH, null);
        } else {
            header.setContentLength(body.length);
        }
    }

    private static Object parse(HttpMessage message, ContentType source) throws Exception {
        HttpRequestHeader header = message.getRequestHeader();
        byte[] bodyBytes = message.getRequestBody().getBytes();
        if (bodyBytes.length == 0) {
            return parseUrlEncoded(queryString(header));
        }
        Charset charset = charsetOf(header);
        String body = new String(bodyBytes, charset);
        if (body.startsWith("\uFEFF")) {
            body = body.substring(1);
        }

        switch (source) {
            case JSON:
                return new JSONTokener(body).nextValue();
            case XML:
                return XmlSupport.xmlToJson(body);
            case SOAP:
                return XmlSupport.extractSoapPayload(body);
            case URL_ENCODED:
                return parseUrlEncoded(body);
            case MULTIPART:
                String boundary =
                        MultipartSupport.extractBoundary(
                                header.getHeader(HttpHeader.CONTENT_TYPE));
                return MultipartSupport.parse(bodyBytes, boundary);
            case YAML:
                return YamlSupport.parse(body);
            case GRAPHQL:
                return parseGraphQl(body);
            case TEXT:
            default:
                return new JSONObject().put("content", body);
        }
    }

    private static Object parseGraphQl(String body) {
        try {
            Object parsed = new JSONTokener(body).nextValue();
            if (parsed instanceof JSONObject && ((JSONObject) parsed).has("query")) {
                return parsed;
            }
        } catch (Exception e) {
            // Not JSON: treat the whole body as a raw GraphQL query.
        }
        return new JSONObject().put("query", body).put("variables", new JSONObject());
    }

    private static String serialize(Object model, ContentType target, Charset charset)
            throws Exception {
        switch (target) {
            case JSON:
                return toJson(model);
            case XML:
                return XmlSupport.toXml(model);
            case SOAP:
                return XmlSupport.toSoap(model);
            case URL_ENCODED:
                return flattenToQueryString(model);
            case YAML:
                return YamlSupport.dump(model);
            case GRAPHQL:
                return toGraphQl(model);
            case TEXT:
                return toText(model);
            default:
                return flattenToQueryString(model);
        }
    }

    private static ConversionResult toGet(HttpMessage message, Object model) throws Exception {
        String query = flattenToQueryString(model);
        URI uri = message.getRequestHeader().getURI();
        String newUri = withQuery(uri, query);
        return new ConversionResult("GET", newUri, null, new byte[0]);
    }

    private static ConversionResult toPost(HttpMessage message, Object model) throws Exception {
        byte[] body = flattenToQueryString(model).getBytes(DEFAULT_CHARSET);
        String contentType = ContentType.URL_ENCODED.getMime() + "; charset=" + DEFAULT_CHARSET.name();
        return new ConversionResult("POST", null, contentType, body);
    }

    private static ConversionResult toMultipart(HttpMessage message, Object model) {
        MultipartSupport.MultipartData data = MultipartSupport.build(model);
        String contentType = "multipart/form-data; boundary=" + data.boundary;
        return new ConversionResult(bodyMethod(message), null, contentType, data.body);
    }

    /** Returns the body method: {@code POST} if the current method is GET or HEAD, else unchanged. */
    private static String bodyMethod(HttpMessage message) {
        String method = message.getRequestHeader().getMethod();
        if (method == null || method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("HEAD")) {
            return "POST";
        }
        return method;
    }

    private static ContentType detectFromBody(byte[] body) {
        String text = new String(body, DEFAULT_CHARSET);
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                new JSONTokener(trimmed).nextValue();
                return ContentType.JSON;
            } catch (Exception e) {
                // Not JSON, continue.
            }
        }
        if (trimmed.startsWith("<")) {
            return XmlSupport.isSoapEnvelope(body) ? ContentType.SOAP : ContentType.XML;
        }
        if (YamlSupport.isYaml(trimmed)) {
            return ContentType.YAML;
        }
        return ContentType.TEXT;
    }

    private static boolean isGraphQlJson(byte[] body) {
        try {
            Object parsed = new JSONTokener(new String(body, DEFAULT_CHARSET)).nextValue();
            return parsed instanceof JSONObject && ((JSONObject) parsed).has("query");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isJsonMime(String mime) {
        return "application/json".equals(mime)
                || "text/json".equals(mime)
                || mime.endsWith("+json");
    }

    private static boolean isGraphQlMime(String mime) {
        return "application/graphql".equals(mime)
                || "text/graphql".equals(mime)
                || mime.endsWith("/graphql");
    }

    private static boolean isUrlEncodedMime(String mime) {
        return "application/x-www-form-urlencoded".equals(mime);
    }

    private static boolean isMultipartMime(String mime) {
        return mime.startsWith("multipart/");
    }

    private static boolean isYamlMime(String mime) {
        return "application/yaml".equals(mime)
                || "application/x-yaml".equals(mime)
                || "text/yaml".equals(mime)
                || "text/x-yaml".equals(mime);
    }

    private static boolean isSoapMime(String mime) {
        return "application/soap+xml".equals(mime) || "application/soap".equals(mime);
    }

    private static boolean isXmlMime(String mime) {
        return "application/xml".equals(mime)
                || "text/xml".equals(mime)
                || mime.endsWith("+xml");
    }

    private static String queryString(HttpRequestHeader header) {
        try {
            URI uri = header.getURI();
            String query = uri != null ? uri.getQuery() : null;
            return query == null ? "" : query;
        } catch (Exception e) {
            return "";
        }
    }

    private static String withQuery(URI uri, String query) throws Exception {
        String url = uri.toString();
        int question = url.indexOf('?');
        String base = question >= 0 ? url.substring(0, question) : url;
        return query.isEmpty() ? base : base + "?" + query;
    }

    /** Parses an URL-encoded string into a JSON object, grouping repeated keys into arrays. */
    private static JSONObject parseUrlEncoded(String data) {
        JSONObject object = new JSONObject();
        if (data == null || data.isEmpty()) {
            return object;
        }
        for (String pair : data.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key =
                    decode(equals >= 0 ? pair.substring(0, equals) : pair);
            String value = equals >= 0 ? decode(pair.substring(equals + 1)) : "";
            if (object.has(key)) {
                Object current = object.get(key);
                if (current instanceof JSONArray) {
                    ((JSONArray) current).put(value);
                } else {
                    object.put(key, new JSONArray().put(current).put(value));
                }
            } else {
                object.put(key, value);
            }
        }
        return object;
    }

    /** Flattens a JSON model to an URL-encoded query string using bracket notation. */
    static String flattenToQueryString(Object model) throws Exception {
        StringBuilder builder = new StringBuilder();
        flatten(model, null, builder);
        return builder.toString();
    }

    private static void flatten(Object value, String prefix, StringBuilder builder)
            throws Exception {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String newKey =
                        (prefix == null || prefix.isEmpty()) ? key : prefix + "[" + key + "]";
                flatten(object.get(key), newKey, builder);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String newKey = (prefix == null ? "" : prefix) + "[" + i + "]";
                flatten(array.opt(i), newKey, builder);
            }
        } else if (value == null || value == JSONObject.NULL) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(prefix == null ? "" : prefix)).append('=');
        } else {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(prefix == null ? "" : prefix))
                    .append('=')
                    .append(encode(String.valueOf(value)));
        }
    }

    private static String toGraphQl(Object model) throws Exception {
        JSONObject output = new JSONObject();
        if (model instanceof JSONObject) {
            JSONObject object = (JSONObject) model;
            if (object.has("query")) {
                output.put("query", object.getString("query"));
                if (object.has("operationName")
                        && object.get("operationName") != JSONObject.NULL) {
                    output.put("operationName", object.getString("operationName"));
                }
                Object variables = object.opt("variables");
                output.put(
                        "variables",
                        variables instanceof JSONObject ? variables : new JSONObject());
                return output.toString(2);
            }
        }
        String query = model instanceof JSONObject ? ((JSONObject) model).toString() : String.valueOf(model);
        output.put("query", query);
        output.put("variables", new JSONObject());
        return output.toString(2);
    }

    private static String toJson(Object model) {
        if (model instanceof JSONObject) {
            return ((JSONObject) model).toString(2);
        }
        if (model instanceof JSONArray) {
            return ((JSONArray) model).toString(2);
        }
        return JSONObject.quote(String.valueOf(model));
    }

    private static String toText(Object model) {
        if (model instanceof JSONObject) {
            JSONObject object = (JSONObject) model;
            if (object.length() == 1 && object.has("content")) {
                Object content = object.get("content");
                return content == null || content == JSONObject.NULL ? "" : String.valueOf(content);
            }
        }
        return String.valueOf(model);
    }

    private static Charset charsetOf(HttpRequestHeader header) {
        String contentType = header.getHeader(HttpHeader.CONTENT_TYPE);
        if (contentType != null) {
            Matcher matcher = CHARSET_PATTERN.matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1));
                } catch (Exception e) {
                    // Unknown charset, fall back to UTF-8.
                }
            }
        }
        return DEFAULT_CHARSET;
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}