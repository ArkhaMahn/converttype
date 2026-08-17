package org.zaproxy.addon.converttype;

/**
 * The result of converting a request: the values to apply to the request header, the URI and the
 * body. {@code null} values mean "leave the current value unchanged".
 */
public final class ConversionResult {

    private final String method;
    private final String uri;
    private final String contentType;
    private final byte[] body;

    /**
     * @param method the HTTP method to set (never {@code null}).
     * @param uri the full URI to set, or {@code null} to keep the current URI.
     * @param contentType the {@code Content-Type} value to set, or {@code null} to remove the
     *     header (used for GET conversions).
     * @param body the body to set (never {@code null}).
     */
    public ConversionResult(String method, String uri, String contentType, byte[] body) {
        this.method = method;
        this.uri = uri;
        this.contentType = contentType;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }
}