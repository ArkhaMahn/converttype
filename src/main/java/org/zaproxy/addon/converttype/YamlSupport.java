package org.zaproxy.addon.converttype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * YAML helpers built on snakeyaml.
 *
 * <p>Parsing always uses {@link SafeConstructor} (snakeyaml 2.x default), which does not
 * instantiate arbitrary types from the document, preventing object-injection via YAML tags.
 */
final class YamlSupport {

    private static final int MAX_ALIASES_FOR_COLLECTIONS = 50;

    private YamlSupport() {}

    /** Parses a YAML document into a JSON model (object/array/scalar or {@code JSONObject.NULL}). */
    static Object parse(String yaml) {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(MAX_ALIASES_FOR_COLLECTIONS);
        Object value = new Yaml(new SafeConstructor(options)).load(yaml);
        return jsonify(value);
    }

    /** Returns {@code true} if the body parses as a YAML mapping or sequence (not a scalar). */
    static boolean isYaml(String body) {
        try {
            Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(body);
            return value instanceof Map || value instanceof List;
        } catch (Exception e) {
            return false;
        }
    }

    /** Serializes a JSON model to a YAML document (block style). */
    static String dump(Object model) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(javaify(model));
    }

    private static Object jsonify(Object value) {
        if (value == null) {
            return JSONObject.NULL;
        }
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.put(String.valueOf(entry.getKey()), jsonify(entry.getValue()));
            }
            return object;
        }
        if (value instanceof List) {
            JSONArray array = new JSONArray();
            for (Object item : (List<?>) value) {
                array.put(jsonify(item));
            }
            return array;
        }
        return value;
    }

    private static Object javaify(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : ((JSONObject) value).keySet()) {
                map.put(key, javaify(((JSONObject) value).get(key)));
            }
            return map;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                list.add(javaify(array.opt(i)));
            }
            return list;
        }
        return value;
    }
}