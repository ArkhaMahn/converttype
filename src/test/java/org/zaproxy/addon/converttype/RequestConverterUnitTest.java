package org.zaproxy.addon.converttype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;

class RequestConverterUnitTest {

    private static HttpMessage request(String header, String body) {
        try {
            HttpMessage message = new HttpMessage();
            message.setRequestHeader(header);
            if (body != null) {
                message.setRequestBody(body);
            }
            return message;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String bodyOf(HttpMessage message) {
        return new String(message.getRequestBody().getBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String headerOf(HttpMessage message, String name) {
        return message.getRequestHeader().getHeader(name);
    }

    private static String methodOf(HttpMessage message) {
        return message.getRequestHeader().getMethod();
    }

    private static String sorted(String query) {
        String[] parts = query.split("&");
        java.util.Arrays.sort(parts);
        return String.join("&", parts);
    }

    @Test
    void jsonPostToUrlEncoded() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json\r\nContent-Length: 100",
                        "{\"name\":\"John Doe\",\"age\":30,\"nested\":{\"a\":\"b\"},\"tags\":[\"x\",\"y\"]}");

        ConversionResult result = RequestConverter.convert(message, ContentType.URL_ENCODED);
        RequestConverter.apply(message, result);

        assertEquals("POST", methodOf(message));
        assertEquals(
                "application/x-www-form-urlencoded; charset=UTF-8", headerOf(message, "Content-Type"));
        assertEquals(
                sorted("name=John+Doe&age=30&nested%5Ba%5D=b&tags%5B0%5D=x&tags%5B1%5D=y"),
                sorted(bodyOf(message)));
    }

    @Test
    void jsonPostToGet() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json",
                        "{\"name\":\"John\",\"age\":30}");

        ConversionResult result = RequestConverter.convert(message, ContentType.GET);
        RequestConverter.apply(message, result);

        assertEquals("GET", methodOf(message));
        assertEquals("http://example.com/api?name=John&age=30", message.getRequestHeader().getURI().toString());
        assertEquals("", bodyOf(message));
        assertEquals(null, headerOf(message, "Content-Type"));
    }

    @Test
    void jsonToXml() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json",
                        "{\"id\":\"5\",\"items\":[1,2],\"empty\":null}");

        ConversionResult result = RequestConverter.convert(message, ContentType.XML);
        RequestConverter.apply(message, result);

        assertEquals("application/xml; charset=UTF-8", headerOf(message, "Content-Type"));
        String xml = bodyOf(message);
        assertTrue(xml.contains("<root>"), xml);
        assertTrue(xml.contains("<id>5</id>"), xml);
        assertTrue(xml.contains("<items>1</items>"), xml);
        assertTrue(xml.contains("<items>2</items>"), xml);
        assertTrue(xml.contains("<empty/>"), xml);
    }

    @Test
    void xmlToJson() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/xml",
                        "<root id=\"42\"><name>Alice</name></root>");

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        assertEquals("application/json; charset=UTF-8", headerOf(message, "Content-Type"));
        JSONObject expected =
                new JSONObject().put("root", new JSONObject().put("@id", "42").put("name", "Alice"));
        assertTrue(expected.similar(new JSONObject(bodyOf(message))));
    }

    @Test
    void xmlAttributeRoundTripPreservesAttributes() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/xml",
                        "<root id=\"42\"><name>Alice</name></root>");

        ConversionResult json = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, json);
        ConversionResult xml = RequestConverter.convert(message, ContentType.XML);
        RequestConverter.apply(message, xml);

        String xmlBody = bodyOf(message);
        assertTrue(xmlBody.contains("id=\"42\""), xmlBody);
    }

    @Test
    void urlEncodedToJson() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/x-www-form-urlencoded",
                        "a=1&b=2&b=3");

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        assertEquals("{\"a\":\"1\",\"b\":[\"2\",\"3\"]}", bodyOf(message).replaceAll("\\s+", ""));
    }

    @Test
    void getQueryStringToJson() throws Exception {
        HttpMessage message =
                request("GET http://example.com/search?q=zap&page=2 HTTP/1.1\r\nHost: example.com", null);

        assertEquals(ContentType.URL_ENCODED, RequestConverter.detectSource(message));

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        assertEquals("POST", methodOf(message));
        assertEquals("{\"q\":\"zap\",\"page\":\"2\"}", bodyOf(message).replaceAll("\\s+", ""));
    }

    @Test
    void yamlToJson() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/yaml",
                        "a: 1\nb:\n  - x\n  - y\n");

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        assertEquals("{\"a\":1,\"b\":[\"x\",\"y\"]}", bodyOf(message).replaceAll("\\s+", ""));
    }

    @Test
    void jsonToYaml() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json",
                        "{\"a\":1,\"b\":[\"x\",\"y\"]}");

        ConversionResult result = RequestConverter.convert(message, ContentType.YAML);
        RequestConverter.apply(message, result);

        assertEquals("application/yaml; charset=UTF-8", headerOf(message, "Content-Type"));
        String yaml = bodyOf(message);
        assertTrue(yaml.contains("a: 1"), yaml);
        assertTrue(yaml.contains("- x"), yaml);
        assertTrue(yaml.contains("- y"), yaml);
    }

    @Test
    void jsonToSoap() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/ws HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json",
                        "{\"Add\":{\"a\":1,\"b\":2}}");

        ConversionResult result = RequestConverter.convert(message, ContentType.SOAP);
        RequestConverter.apply(message, result);

        assertEquals("application/soap+xml; charset=UTF-8", headerOf(message, "Content-Type"));
        String soap = bodyOf(message);
        assertTrue(soap.contains("<soapenv:Envelope"), soap);
        assertTrue(soap.contains("<soapenv:Body>"), soap);
        assertTrue(soap.contains("<Add>"), soap);
    }

    @Test
    void soapToJson() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/ws HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/soap+xml",
                        "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Header/><soapenv:Body><Add><a>1</a><b>2</b></Add></soapenv:Body></soapenv:Envelope>");

        assertEquals(ContentType.SOAP, RequestConverter.detectSource(message));

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        Object parsed = new JSONTokener(bodyOf(message)).nextValue();
        JSONObject expected =
                new JSONObject().put("Add", new JSONObject().put("a", "1").put("b", "2"));
        assertTrue(expected.similar(parsed));
    }

    @Test
    void rawGraphQlToJson() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/graphql HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/graphql",
                        "query { hero { name } }");

        assertEquals(ContentType.GRAPHQL, RequestConverter.detectSource(message));

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        assertEquals("application/json; charset=UTF-8", headerOf(message, "Content-Type"));
        String json = bodyOf(message);
        assertTrue(json.contains("\"query\": \"query { hero { name } }\""), json);
        assertTrue(json.contains("\"variables\": {}"), json);
    }

    @Test
    void graphQlJsonPreserved() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/graphql HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json",
                        "{\"query\":\"{ hero { name } }\",\"variables\":{\"id\":\"1\"}}");

        assertEquals(ContentType.GRAPHQL, RequestConverter.detectSource(message));

        ConversionResult result = RequestConverter.convert(message, ContentType.YAML);
        RequestConverter.apply(message, result);

        String yaml = bodyOf(message);
        assertTrue(yaml.contains("query: '{ hero { name } }'"), yaml);
        assertTrue(yaml.contains("id: '1'"), yaml);
    }

    @Test
    void multipartToJson() throws Exception {
        String boundary = "----TestBoundary";
        String body =
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"field1\"\r\n"
                        + "\r\n"
                        + "value1\r\n"
                        + "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"upload\"; filename=\"a.txt\"\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "\r\n"
                        + "hello\r\n"
                        + "--" + boundary + "--\r\n";

        HttpMessage message =
                request(
                        "POST http://example.com/upload HTTP/1.1\r\nHost: example.com\r\nContent-Type: multipart/form-data; boundary=" + boundary,
                        body);

        assertEquals(ContentType.MULTIPART, RequestConverter.detectSource(message));

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        assertEquals(
                "{\"field1\":\"value1\",\"upload\":{\"filename\":\"a.txt\",\"content\":\"hello\"}}",
                bodyOf(message).replaceAll("\\s+", ""));
    }

    @Test
    void jsonToMultipart() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/upload HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json",
                        "{\"field1\":\"value1\",\"upload\":{\"filename\":\"a.txt\",\"content\":\"hello\"}}");

        ConversionResult result = RequestConverter.convert(message, ContentType.MULTIPART);
        RequestConverter.apply(message, result);

        String contentType = headerOf(message, "Content-Type");
        assertTrue(contentType.startsWith("multipart/form-data; boundary="), contentType);
        String boundary = MultipartSupport.extractBoundary(contentType);
        assertNotNull(boundary);

        String mpBody = bodyOf(message);
        assertTrue(mpBody.contains("name=\"field1\""), mpBody);
        assertTrue(mpBody.contains("value1"), mpBody);
        assertTrue(mpBody.contains("filename=\"a.txt\""), mpBody);
        assertTrue(mpBody.contains("hello"), mpBody);

        ConversionResult back = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, back);
        assertEquals(
                "{\"field1\":\"value1\",\"upload\":{\"filename\":\"a.txt\",\"content\":\"hello\"}}",
                bodyOf(message).replaceAll("\\s+", ""));
    }

    @Test
    void textToJsonWrapsContent() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/echo HTTP/1.1\r\nHost: example.com\r\nContent-Type: text/plain",
                        "hello world");

        ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
        RequestConverter.apply(message, result);

        Object parsed = new JSONTokener(bodyOf(message)).nextValue();
        assertEquals("hello world", ((JSONObject) parsed).getString("content"));
    }

    @Test
    void xxeIsNotResolved() throws Exception {
        String xxe =
                "<?xml version=\"1.0\"?>\n"
                        + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n"
                        + "<root>&xxe;</root>";
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: text/xml",
                        xxe);

        try {
            ConversionResult result = RequestConverter.convert(message, ContentType.JSON);
            RequestConverter.apply(message, result);
            assertFalse(bodyOf(message).contains("root:"), "XXE must not be resolved");
        } catch (Exception e) {
            // A clean parse failure is acceptable; the external entity must not be read.
        }
    }

    @Test
    void plainPutKeepsMethod() throws Exception {
        HttpMessage message =
                request(
                        "PUT http://example.com/resource HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json",
                        "{\"a\":1}");

        ConversionResult result = RequestConverter.convert(message, ContentType.URL_ENCODED);
        RequestConverter.apply(message, result);

        assertEquals("PUT", methodOf(message));
    }

    @Test
    void bodyHeuristicDetectsJson() {
        HttpMessage message =
                request("POST http://example.com/ HTTP/1.1\r\nHost: example.com", "{\"a\":1}");
        assertEquals(ContentType.JSON, RequestConverter.detectSource(message));
    }

    @Test
    void charsetFromContentTypeHonored() throws Exception {
        HttpMessage message =
                request(
                        "POST http://example.com/api HTTP/1.1\r\nHost: example.com\r\nContent-Type: application/json; charset=ISO-8859-1",
                        "{\"name\":\"caf\u00e9\"}");

        assertEquals(ContentType.JSON, RequestConverter.detectSource(message));
        ConversionResult result = RequestConverter.convert(message, ContentType.URL_ENCODED);
        RequestConverter.apply(message, result);

        assertTrue(headerOf(message, "Content-Type").contains("charset=ISO-8859-1"));
        assertTrue(bodyOf(message).contains("name=caf%C3%A9"), bodyOf(message));
    }
}
