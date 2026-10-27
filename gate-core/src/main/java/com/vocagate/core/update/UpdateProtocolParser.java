package com.vocagate.core.update;

import com.vocagate.core.GateConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UpdateProtocolParser {
    private UpdateProtocolParser() {
    }

    public static UpdateManifest parseManifest(String json) {
        Map<String, Object> root = SimpleJsonParser.parseObject(json);
        List<ManifestAsset> assets = new ArrayList<>();
        for (Object item : array(root, "assets")) {
            Map<String, Object> asset = asObject(item, "assets[]");
            assets.add(new ManifestAsset(
                    text(asset, "id"),
                    text(asset, "kind"),
                    text(asset, "engine"),
                    text(asset, "platform"),
                    text(asset, "version"),
                    text(asset, "min_app_version"),
                    text(asset, "url"),
                    text(asset, "sha256"),
                    optionalText(asset, "signature"),
                    optionalLong(asset, "size_bytes", 0L)
            ));
        }
        return new UpdateManifest(
                integer(root, "schema_version"),
                text(root, "channel"),
                text(root, "generated_at"),
                assets
        );
    }

    public static AlgorithmProfile parseAlgorithmProfile(String json) {
        Map<String, Object> root = SimpleJsonParser.parseObject(json);
        Map<String, Object> gate = object(root, "gate");
        Map<String, Object> score = object(root, "score");
        Map<String, Object> firstWord = object(root, "first_word");
        GateConfig gateConfig = new GateConfig(
                floating(gate, "open_threshold"),
                floating(gate, "pre_open_threshold"),
                floating(gate, "close_threshold"),
                integer(gate, "pre_open_required_frames"),
                integer(gate, "close_required_frames"),
                integer(gate, "min_open_ms"),
                integer(gate, "hold_ms"),
                integer(gate, "cooldown_ms")
        );
        AlgorithmProfile.ScoreWeights scoreWeights = new AlgorithmProfile.ScoreWeights(
                floating(score, "rhythm_weight"),
                floating(score, "open_weight"),
                floating(score, "frontal_weight"),
                floating(score, "stability_weight"),
                floating(score, "occlusion_penalty_weight"),
                floating(score, "long_open_penalty_weight")
        );
        AlgorithmProfile.FirstWordConfig firstWordConfig = new AlgorithmProfile.FirstWordConfig(
                bool(firstWord, "enabled"),
                floating(firstWord, "rise_threshold"),
                integer(firstWord, "hold_ms")
        );
        return new AlgorithmProfile(
                integer(root, "schema_version"),
                text(root, "profile_id"),
                text(root, "engine"),
                text(root, "version"),
                text(root, "min_app_version"),
                gateConfig,
                scoreWeights,
                firstWordConfig
        );
    }

    private static Map<String, Object> object(Map<String, Object> source, String key) {
        return asObject(required(source, key), key);
    }

    private static List<Object> array(Map<String, Object> source, String key) {
        Object value = required(source, key);
        if (value instanceof List) {
            return castList(value);
        }
        throw new UpdateProtocolException(key + " must be an array");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String label) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new UpdateProtocolException(label + " must be an object");
    }

    private static String text(Map<String, Object> source, String key) {
        Object value = required(source, key);
        if (value instanceof String) {
            return (String) value;
        }
        throw new UpdateProtocolException(key + " must be a string");
    }

    private static String optionalText(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        throw new UpdateProtocolException(key + " must be a string");
    }

    private static int integer(Map<String, Object> source, String key) {
        Object value = required(source, key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new UpdateProtocolException(key + " must be a number");
    }

    private static long optionalLong(Map<String, Object> source, String key, long fallback) {
        Object value = source.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new UpdateProtocolException(key + " must be a number");
    }

    private static float floating(Map<String, Object> source, String key) {
        Object value = required(source, key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        throw new UpdateProtocolException(key + " must be a number");
    }

    private static boolean bool(Map<String, Object> source, String key) {
        Object value = required(source, key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new UpdateProtocolException(key + " must be a boolean");
    }

    private static Object required(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            throw new UpdateProtocolException(key + " is required");
        }
        return source.get(key);
    }
}
