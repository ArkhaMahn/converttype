# Changelog

## 1.0.0 - 2026-08-14

### Added
- Port of the Burp Suite extension `Convert-Type-Convert-All` to ZAP 2.17.0 as the "Content Type Converter" add-on.
- Popup menu (right click on a message) with the "Convert Content Type" submenu to convert a request to:
  - JSON
  - XML
  - SOAP (SOAP 1.1 envelope)
  - URL-Encoded Form
  - Multipart Form-Data
  - YAML
  - GraphQL
  - Plain Text
  - GET Request
  - POST Request
- Converts the request body, the `Content-Type` header and the HTTP method (GET/POST) as needed.
- Automatic source content-type detection from the `Content-Type` header and body heuristics.
- When triggered from the Request/Response tabs the converted request is applied in place and the editor is refreshed; when triggered from message panels (History, Sites, Search) the converted request is opened in the built-in Resend dialog.
- Conversion safety: XML is parsed with a hardened `DocumentBuilderFactory` (DOCTYPE/external entities disabled), YAML uses snakeyaml's safe constructor, and no sensitive data is ever sent.