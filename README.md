# Content Type Converter

### A ZAP 2.17.0 add-on that converts HTTP requests between content types — body, headers and method together.

[![license](https://img.shields.io/badge/license-Apache--2.0-5B3AB6)](LICENSE) [![PRs welcome](https://img.shields.io/badge/PRs-welcome-5B3AB6)](https://github.com/ArkhaMahn/converttype/issues)

---

# Content Type Converter — ZAP add-on

A [ZAP](https://www.zaproxy.org/) add-on that converts an HTTP request from one content
type to another, converting the request **body**, the `Content-Type` header and the HTTP
method (GET/POST) as needed. It is a port of the Burp Suite extension
[`h0tak88r/Convert-Type-Convert-All`](https://github.com/h0tak88r/Convert-Type-Convert-All).

> **Status: beta.** Built and verified for ZAP 2.17.0. The extension loads cleanly with no errors. Please report any issues.

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [What it does](#what-it-does)
- [Build](#build)
- [Install in ZAP](#install-in-zap)
- [Usage](#usage)
- [Conversion notes](#conversion-notes)
- [Security notes](#security-notes)
- [Development](#development)
- [Credits](#credits)
- [License](#license)

---

## Overview

Modern APIs speak many dialects: JSON, XML, SOAP, YAML, GraphQL, multipart form-data.
Testing an endpoint often means re-authoring the same request in a different format by
hand. This add-on does the conversion for you — pick a target format from the context
menu and the whole request (body, `Content-Type`, method) is rewritten consistently.

## Requirements

- **ZAP 2.17.0** or later.
- **Java 17** or later.

The add-on bundles everything it needs — no external tools or libraries are required at runtime.

## What it does

- Adds a **Convert Content Type** right-click submenu to Request/Response tabs, History,
  Sites and Search.
- Converts bodies between JSON, XML, SOAP 1.1, URL-encoded form, multipart form-data,
  YAML, GraphQL and plain text.
- Auto-detects the source content type from the `Content-Type` header plus body
  heuristics (including query-string-only GET requests).
- Updates the method when the format demands it (`To GET Request` / `To POST Request`).
- Applies conversions in place in the editor views; from History/Sites/Search the result
  opens in ZAP's built-in Resend dialog.

---

## Build

Requires JDK 17+ and [Gradle](https://gradle.org/install/) 8.13+:

```
gradle build
```

The ZAP add-on artifact is produced at: `build/zapAddOn/bin/converttype-beta-1.0.0.zap`

> The `org.zaproxy.add-on` Gradle plugin derives the add-on id from the project directory name, so
> the project folder must be named `converttype`.

To run the test suite:

```
gradle test
```

## Install in ZAP

1. Build the `.zap` (above).
2. In ZAP: **File → Load Add-on File…** and select the built `.zap`, OR drop the `.zap` into ZAP's `plugin` directory and restart.
3. Select a message, right-click, and open **Convert Content Type**.

---

## Usage

Select a message (Request/Response tabs, History, Sites or Search) and right click. Under
the **Convert Content Type** submenu choose the target format:

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

When invoked from the Request/Response tabs the converted request is applied in place and
the editor view is refreshed. When invoked from History, Sites or Search the converted
request is opened in ZAP's built-in Resend dialog.

## Conversion notes

- The intermediate representation is an `org.json.JSONObject`/`JSONArray`. XML follows
  `org.json.XML` conventions: attributes are `@name` keys and text content is the
  `#text` key.
- JSON object/array keys that are not valid XML element names are sanitized when
  serializing to XML.
- Repeating keys and arrays are preserved:
  - as repeated elements when serializing to XML,
  - as `key[i]` bracket-notation parameters when flattening to a query string or
    URL-encoded body,
  - as repeated parts when serializing to multipart form-data.
- Multipart file parts are represented as `{"filename": "...", "content": "..."}`.
- Raw GraphQL queries (`Content-Type: application/graphql`) are wrapped as
  `{"query": "...", "variables": {}}`.

## Security notes

- XML is parsed with a hardened `DocumentBuilderFactory` (DOCTYPE declarations and
  external entities disabled) to prevent XXE.
- YAML is parsed with snakeyaml's safe constructor (no arbitrary object instantiation).
- The conversion is a safe operation (popup items are marked `isSafe()`).

---

## Development

```
src/main/java/Arkhamahn/converttype/
  ExtensionConvertType.java      # ExtensionAdaptor entry point + popup registration
  ConvertTypePopupMenuItem.java  # Right-click items (To JSON, To XML, ...)
  RequestConverter.java          # Conversion orchestration + auto-detection
  ContentType.java               # Supported content types
  ConversionResult.java          # Converted request + diagnostics
  XmlSupport.java                # org.json.XML conventions
  YamlSupport.java               # snakeyaml safe serialization
  MultipartSupport.java          # multipart/form-data parsing and building
```

---

## Credits

- Original idea and implementation:
  [`h0tak88r/Convert-Type-Convert-All`](https://github.com/h0tak88r/Convert-Type-Convert-All)
  by [h0tak88r](https://github.com/h0tak88r).

---

## License

[Apache-2.0](LICENSE) © 2026 Arkhamahn
