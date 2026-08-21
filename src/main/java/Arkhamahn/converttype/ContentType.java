package Arkhamahn.converttype;

/**
 * The supported content types. The {@link #getKey()} value is used as the suffix of the i18n keys
 * and as the ordinal-based popup menu weight.
 */
public enum ContentType {
    /** JSON document. */
    JSON("json", "application/json"),
    /** XML document. */
    XML("xml", "application/xml"),
    /** SOAP 1.1 envelope. */
    SOAP("soap", "application/soap+xml"),
    /** URL-encoded form. */
    URL_ENCODED("urlencoded", "application/x-www-form-urlencoded"),
    /** Multipart form-data. */
    MULTIPART("multipart", "multipart/form-data"),
    /** YAML document. */
    YAML("yaml", "application/yaml"),
    /** GraphQL JSON payload ({"query": "...", "variables": {...}}). */
    GRAPHQL("graphql", "application/json"),
    /** Plain text. */
    TEXT("text", "text/plain"),
    /** GET request: the model is flattened to the query string. */
    GET("get", null),
    /** POST request: the model is flattened to an URL-encoded body. */
    POST("post", "application/x-www-form-urlencoded");

    private final String key;
    private final String mime;

    ContentType(String key, String mime) {
        this.key = key;
        this.mime = mime;
    }

    /** The i18n key suffix, also used as the query-key / part name for the POST forms. */
    public String getKey() {
        return key;
    }

    /** The MIME type to set in the {@code Content-Type} header, or {@code null} if none. */
    public String getMime() {
        return mime;
    }
}
