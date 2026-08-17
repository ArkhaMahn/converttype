Content Type Converter
======================

A ZAP 2.17.0 add-on that converts an HTTP request from one content type to
another, converting the request **body**, the `Content-Type` header and the
HTTP method (GET/POST) as needed.

It is a port of the Burp Suite extension
[`h0tak88r/Convert-Type-Convert-All`](https://github.com/h0tak88r/Convert-Type-Convert-All).

Author: Arkhamahn. Original idea and implementation credit goes to
[h0tak88r](https://github.com/h0tak88r) and their
`Convert-Type-Convert-All` Burp Suite extension.

Vibecoded with love 🖤

## Usage

Select a message (Request/Response tabs, History, Sites or Search) and right
click. Under the **Convert Content Type** submenu choose the target format:

| Menu item               | Resulting request                              |
|-------------------------|------------------------------------------------|
| To JSON                 | Body parsed to a JSON object/array             |
| To XML                  | Body serialized as XML (`<root>...</root>`)    |
| To SOAP                 | Body wrapped in a SOAP 1.1 envelope            |
| To URL-Encoded Form     | Body serialized as `application/x-www-form-urlencoded` |
| To Multipart Form-Data  | Body serialized as `multipart/form-data`       |
| To YAML                 | Body serialized as YAML                        |
| To GraphQL              | Body serialized as a GraphQL JSON payload      |
| To Plain Text           | Body serialized as `text/plain`                |
| To GET Request          | Model flattened to the query string, method changed to GET, body removed |
| To POST Request         | Model flattened to an URL-encoded body, method changed to POST |

The source content type is auto-detected from the `Content-Type` header and
body heuristics (JSON, XML, SOAP envelope, URL-encoded form, multipart
form-data, YAML, GraphQL, plain text, or query-string-only GET requests).

When invoked from the Request/Response tabs the converted request is applied
in place and the editor view is refreshed. When invoked from History, Sites or
Search the converted request is opened in ZAP's built-in Resend dialog
(`org.parosproxy.paros.extension.history.ExtensionHistory#getResendDialog`).

## Conversion notes

- The intermediate representation is an `org.json.JSONObject`/`JSONArray`.
  XML follows `org.json.XML` conventions: attributes are `@name` keys and text
  content is the `#text` key.
- JSON object/array keys that are not valid XML element names are sanitized
  when serializing to XML.
- Repeating keys and arrays are preserved:
  - as repeated elements when serializing to XML,
  - as `key[i]` bracket-notation parameters when flattening to a query string
    or URL-encoded body,
  - as repeated parts when serializing to multipart form-data.
- Multipart file parts are represented as `{"filename": "...", "content": "..."}`.
- Raw GraphQL queries (`Content-Type: application/graphql`) are wrapped as
  `{"query": "...", "variables": {}}`.

## Security

- XML is parsed with a hardened `DocumentBuilderFactory` (DOCTYPE declarations
  and external entities disabled) to prevent XXE.
- YAML is parsed with snakeyaml's safe constructor (no arbitrary object
  instantiation).
- The conversion is a safe operation (popup items are marked `isSafe()`).

## Building

Requires JDK 17+ and Gradle 8.13+.

```
gradle build
```

The ZAP add-on artifact is produced at
`build/zapAddOn/bin/converttype-beta-1.0.0.zap`. Install it in ZAP via
`Manage Add-ons -> Install...`.