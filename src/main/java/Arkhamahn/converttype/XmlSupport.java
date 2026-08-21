package Arkhamahn.converttype;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * XML and SOAP helpers.
 *
 * <p>Parsing always goes through a hardened {@link DocumentBuilderFactory} (DOCTYPE declarations
 * and external entities are disabled) to prevent XXE. JSON to XML serialization uses
 * {@link org.json.XML} conventions: attributes are {@code @name} keys and text content is the
 * {@code #text} key.
 */
final class XmlSupport {

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    private XmlSupport() {}

    /** Creates a {@link DocumentBuilderFactory} with XXE protections enabled. */
    static DocumentBuilderFactory newSecureFactory() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setExpandEntityReferences(false);
            dbf.setXIncludeAware(false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Could not harden the XML parser.", e);
        }
        return dbf;
    }

    /** Returns the element local name, falling back to the tag name for namespaceless elements. */
    static String localName(Element element) {
        String localName = element.getLocalName();
        return localName != null ? localName : element.getTagName();
    }

    /** Returns the first child element of {@code parent}. */
    static Element firstElementChild(Node parent) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
        }
        return null;
    }

    /** Returns the first child element of {@code parent} whose local name equals {@code name}. */
    static Element firstChildElement(Node parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (name.equals(localName(element))) {
                    return element;
                }
            }
        }
        return null;
    }

    /** Returns {@code true} if the body is a SOAP envelope ({@code <...:Envelope>} root element). */
    static boolean isSoapEnvelope(byte[] body) {
        try {
            Document document =
                    newSecureFactory()
                            .newDocumentBuilder()
                            .parse(new ByteArrayInputStream(body));
            Element root = document.getDocumentElement();
            return root != null && "Envelope".equals(localName(root));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the payload from a SOAP envelope body: returns the child of the {@code Envelope}
     * {@code Body} as a JSON object keyed by the payload element name. If the document is not a
     * SOAP envelope it is converted with {@link #xmlToJson(String)}.
     */
    static JSONObject extractSoapPayload(String body) throws Exception {
        Document document =
                newSecureFactory()
                        .newDocumentBuilder()
                        .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        Element root = document.getDocumentElement();
        if (root == null || !"Envelope".equals(localName(root))) {
            return xmlToJson(body);
        }
        Element bodyElement = firstChildElement(root, "Body");
        if (bodyElement == null) {
            return new JSONObject();
        }
        Element payload = firstElementChild(bodyElement);
        if (payload == null) {
            return new JSONObject();
        }
        return new JSONObject().put(localName(payload), elementToValue(payload));
    }

    /**
     * Converts an XML document to a JSON object keyed by the root element name.
     *
     * <p>Uses the same conventions as the JSON to XML serialization: attributes are {@code @name}
     * keys and text content is the {@code #text} key, so conversions are lossless in both
     * directions.
     */
    static JSONObject xmlToJson(String body) throws Exception {
        Document document =
                newSecureFactory()
                        .newDocumentBuilder()
                        .parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        Element root = document.getDocumentElement();
        if (root == null) {
            return new JSONObject();
        }
        return new JSONObject().put(localName(root), elementToValue(root));
    }

    /**
     * Converts an element to a JSON value: a scalar string for elements with no attributes and no
     * child elements, otherwise an object keyed by the child element names.
     */
    static Object elementToValue(Element element) {
        if (!hasAttributes(element) && !hasChildElements(element)) {
            String text = textOf(element).trim();
            return text.isEmpty() ? "" : text;
        }
        return elementToObject(element);
    }

    private static boolean hasAttributes(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = ((Attr) attributes.item(i)).getName();
            if (!name.equals("xmlns") && !name.startsWith("xmlns:")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasChildElements(Element element) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private static String textOf(Element element) {
        StringBuilder text = new StringBuilder();
        for (Node child = element.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            short type = child.getNodeType();
            if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
        }
        return text.toString();
    }

    /** Builds the object form of an element: {@code @attr} keys, {@code #text} and children. */
    static JSONObject elementToObject(Element element) {
        JSONObject object = new JSONObject();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getName();
            if (name.equals("xmlns") || name.startsWith("xmlns:")) {
                continue;
            }
            object.put("@" + name, attribute.getValue());
        }
        String text = textOf(element).trim();
        if (!text.isEmpty()) {
            object.put("#text", text);
        }
        for (Node child = element.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                add(object, childElement.getTagName(), elementToValue(childElement));
            }
        }
        return object;
    }

    private static void add(JSONObject object, String key, Object value) {
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

    /** Serializes a JSON model to an XML document rooted at {@code <root>}. */
    static String toXml(Object model) throws Exception {
        DocumentBuilder builder = newSecureFactory().newDocumentBuilder();
        Document document = builder.newDocument();
        Element root = document.createElement("root");
        document.appendChild(root);
        appendModel(document, root, model);
        return serialize(document);
    }

    /** Serializes a JSON model to a SOAP 1.1 envelope ({@code Envelope/Header/Body/root}). */
    static String toSoap(Object model) throws Exception {
        DocumentBuilder builder = newSecureFactory().newDocumentBuilder();
        Document document = builder.newDocument();
        Element envelope = document.createElementNS(SOAP_NS, "soapenv:Envelope");
        document.appendChild(envelope);
        Element header = document.createElementNS(SOAP_NS, "soapenv:Header");
        envelope.appendChild(header);
        Element body = document.createElementNS(SOAP_NS, "soapenv:Body");
        envelope.appendChild(body);
        Element root = document.createElement("root");
        body.appendChild(root);
        appendModel(document, root, model);
        return serialize(document);
    }

    private static void appendModel(Document document, Element parent, Object model) {
        if (model instanceof JSONObject) {
            appendObject(document, parent, (JSONObject) model);
        } else if (model instanceof JSONArray) {
            JSONArray array = (JSONArray) model;
            for (int i = 0; i < array.length(); i++) {
                Element item = document.createElement("item");
                appendModel(document, item, array.opt(i));
                parent.appendChild(item);
            }
        } else {
            parent.appendChild(document.createTextNode(String.valueOf(model)));
        }
    }

    private static void appendObject(Document document, Element element, JSONObject object) {
        for (String key : object.keySet()) {
            Object value = object.get(key);
            if (key.startsWith("@")) {
                if (value != JSONObject.NULL) {
                    element.setAttribute(key.substring(1), String.valueOf(value));
                }
            } else if ("#text".equals(key)) {
                if (value != JSONObject.NULL) {
                    element.appendChild(document.createTextNode(String.valueOf(value)));
                }
            } else {
                appendValue(document, element, sanitizeName(key), value);
            }
        }
    }

    private static void appendValue(Document document, Element parent, String name, Object value) {
        if (value instanceof JSONObject) {
            Element child = document.createElement(name);
            appendObject(document, child, (JSONObject) value);
            parent.appendChild(child);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() == 0) {
                parent.appendChild(document.createElement(name));
            } else {
                for (int i = 0; i < array.length(); i++) {
                    appendValue(document, parent, name, array.opt(i));
                }
            }
        } else if (value == JSONObject.NULL || value == null) {
            parent.appendChild(document.createElement(name));
        } else {
            Element child = document.createElement(name);
            child.appendChild(document.createTextNode(String.valueOf(value)));
            parent.appendChild(child);
        }
    }

    /** Replaces characters that are not valid in XML element names with {@code _}. */
    static String sanitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return "item";
        }
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid =
                    Character.isLetter(c)
                            || (i > 0 && Character.isDigit(c))
                            || c == '_'
                            || c == '-'
                            || c == '.'
                            || c == ':';
            builder.append(valid ? c : '_');
        }
        return builder.toString();
    }

    private static String serialize(Node node) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }
}