package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github._1c_syntax.bsl.mdo.support.RoleRight;
import com.github._1c_syntax.bsl.types.MDOType;
import lombok.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JSON DSL для роли 1С.
 */
@Value
public class RoleDsl {

    private static final Map<String, String> MDO_TYPE_ALIASES = buildMdoTypeAliases();
    private static final Map<String, String> ROLE_RIGHT_ALIASES = buildRoleRightAliases();
    private static final Map<String, String> PRESET_ALIASES = Map.of(
            "просмотр", "view",
            "чтение", "view",
            "редактирование", "edit",
            "изменение", "edit",
            "полные", "full",
            "полный", "full"
    );
    
    /**
     * Имя роли (латиница или кириллица).
     */
    String name;
    
    /**
     * Синоним (представление).
     */
    String synonym;
    
    /**
     * Комментарий.
     */
    String comment;
    
    /**
     * Устанавливать права для новых объектов конфигурации.
     */
    @JsonProperty("setForNewObjects")
    Boolean setForNewObjects;
    
    /**
     * Устанавливать права для реквизитов по умолчанию.
     */
    @JsonProperty("setForAttributesByDefault")
    Boolean setForAttributesByDefault;
    
    /**
     * Независимые права подчинённых объектов.
     */
    @JsonProperty("independentRightsOfChildObjects")
    Boolean independentRightsOfChildObjects;
    
    /**
     * Объекты метаданных с правами.
     */
    @JsonDeserialize(contentUsing = ObjectRights.Deserializer.class)
    List<ObjectRights> objects;
    
    /**
     * Шаблоны ограничений (RLS).
     */
    List<RestrictionTemplate> templates;
    
    @JsonCreator
    public RoleDsl(
            @JsonProperty("name") String name,
            @JsonProperty("synonym") String synonym,
            @JsonProperty("comment") String comment,
            @JsonProperty("setForNewObjects") Boolean setForNewObjects,
            @JsonProperty("setForAttributesByDefault") Boolean setForAttributesByDefault,
            @JsonProperty("independentRightsOfChildObjects") Boolean independentRightsOfChildObjects,
            @JsonProperty("objects")
            @JsonDeserialize(contentUsing = ObjectRights.Deserializer.class)
            List<ObjectRights> objects,
            @JsonProperty("templates") List<RestrictionTemplate> templates) {
        this.name = name;
        this.synonym = synonym;
        this.comment = comment;
        this.setForNewObjects = setForNewObjects;
        this.setForAttributesByDefault = setForAttributesByDefault;
        this.independentRightsOfChildObjects = independentRightsOfChildObjects;
        this.objects = objects;
        this.templates = templates;
    }
    
    /**
     * Права объекта метаданных.
     */
    @Value
    public static class ObjectRights {
        /**
         * Полное имя объекта: Catalog.Name, Document.Name и т.д.
         */
        String name;
        
        /**
         * Пресет прав: "view", "edit", "full".
         */
        String preset;
        
        /**
         * Права: {"Read": true, "Insert": true} или ["Read", "Insert"].
         */
        Object rights;
        
        /**
         * RLS (Row Level Security): {"Read": "условие или #шаблон"}.
         */
        Map<String, String> rls;
        
        @JsonCreator
        public ObjectRights(
                @JsonProperty("name") String name,
                @JsonProperty("preset") String preset,
                @JsonProperty("rights") Object rights,
                @JsonProperty("rls") Map<String, String> rls) {
            this.name = normalizeObjectName(name);
            this.preset = normalizePreset(preset);
            this.rights = normalizeRights(rights);
            this.rls = normalizeRls(rls);
        }

        public static class Deserializer extends JsonDeserializer<ObjectRights> {
            @Override
            public ObjectRights deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                if (node.isTextual()) {
                    return fromShorthand(node.asText(), ctxt);
                }
                if (!node.isObject()) {
                    throw JsonMappingException.from(p,
                            "Role object entry must be object or shorthand string, got " + node.getNodeType());
                }

                String name = text(node, "name");
                String preset = text(node, "preset");
                Object rights = readRights(node.get("rights"), ctxt);
                Map<String, String> rls = readStringMap(node.get("rls"), "rls", ctxt);
                return new ObjectRights(name, preset, rights, rls);
            }

            private static ObjectRights fromShorthand(String value, DeserializationContext ctxt)
                    throws JsonMappingException {
                String[] parts = value.split(":", 2);
                if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                    throw JsonMappingException.from(ctxt,
                            "Role object shorthand must be '<ObjectName>: @preset' or '<ObjectName>: Right1, Right2'");
                }

                String name = parts[0].trim();
                String[] tokens = parts[1].split(",");
                String preset = null;
                List<String> rights = new ArrayList<>();
                for (String rawToken : tokens) {
                    String token = rawToken.trim();
                    if (token.isEmpty()) {
                        continue;
                    }
                    if (token.startsWith("@")) {
                        if (preset != null) {
                            throw JsonMappingException.from(ctxt,
                                    "Role object shorthand must contain at most one preset: " + value);
                        }
                        preset = normalizePreset(token.substring(1));
                    } else {
                        rights.add(normalizeRightNameStrict(token));
                    }
                }
                Object rightsObject = rights.isEmpty() ? null : rights;
                return new ObjectRights(name, preset, rightsObject, null);
            }

            private static String text(JsonNode node, String fieldName) {
                JsonNode value = node.get(fieldName);
                return value == null || value.isNull() ? null : value.asText();
            }

            private static Object readRights(JsonNode node, DeserializationContext ctxt) throws JsonMappingException {
                if (node == null || node.isNull()) {
                    return null;
                }
                if (node.isArray()) {
                    List<String> rights = new ArrayList<>();
                    for (JsonNode item : node) {
                        rights.add(normalizeRightNameStrict(item.asText()));
                    }
                    return rights;
                }
                if (node.isObject()) {
                    Map<String, Object> rights = new LinkedHashMap<>();
                    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        JsonNode value = field.getValue();
                        Object rightValue = value.isBoolean() ? value.asBoolean() : value.asText();
                        rights.put(normalizeRightNameStrict(field.getKey()), rightValue);
                    }
                    return rights;
                }
                throw JsonMappingException.from(ctxt,
                        "Role object rights must be array or object, got " + node.getNodeType());
            }

            private static Map<String, String> readStringMap(JsonNode node, String fieldName,
                                                            DeserializationContext ctxt)
                    throws JsonMappingException {
                if (node == null || node.isNull()) {
                    return null;
                }
                if (!node.isObject()) {
                    throw JsonMappingException.from(ctxt,
                            "Role object " + fieldName + " must be object, got " + node.getNodeType());
                }
                Map<String, String> result = new LinkedHashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    result.put(normalizeRightNameStrict(field.getKey()), field.getValue().asText());
                }
                return result;
            }
        }
    }
    
    /**
     * Шаблон ограничения (RLS).
     */
    @Value
    public static class RestrictionTemplate {
        /**
         * Имя шаблона с параметрами: "ДляОбъекта(Модификатор)".
         */
        String name;
        
        /**
         * Текст условия на языке шаблонов ограничений 1С.
         */
        String condition;
        
        @JsonCreator
        public RestrictionTemplate(
                @JsonProperty("name") String name,
                @JsonProperty("condition") String condition) {
            this.name = name;
            this.condition = condition;
        }
    }

    public static String normalizeObjectName(String objectName) {
        if (objectName == null) {
            return null;
        }
        String[] segments = objectName.split("\\.", -1);
        for (int i = 0; i < segments.length; i += 2) {
            segments[i] = MDO_TYPE_ALIASES.getOrDefault(segments[i], segments[i]);
        }
        return String.join(".", segments);
    }

    public static String normalizeRightNameStrict(String rightName) {
        if (rightName == null) {
            throw new IllegalArgumentException("Right name must not be null");
        }
        String normalized = ROLE_RIGHT_ALIASES.get(rightName.trim());
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Invalid right name '" + rightName + "'. Right names are case-sensitive XML identifiers " +
                            "(e.g. Read, View, Insert, Update, Delete, Edit, Use), or Russian aliases " +
                            "(e.g. Чтение, Просмотр).");
        }
        return normalized;
    }

    public static boolean isKnownRightName(String rightName) {
        return rightName != null && ROLE_RIGHT_ALIASES.containsKey(rightName.trim());
    }

    public static String normalizePreset(String preset) {
        if (preset == null) {
            return null;
        }
        String value = preset.trim();
        return PRESET_ALIASES.getOrDefault(value.toLowerCase(), value);
    }

    private static Object normalizeRights(Object rights) {
        if (rights instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(normalizeRightNameStrict(Objects.toString(entry.getKey(), null)), entry.getValue());
            }
            return result;
        }
        if (rights instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object right : list) {
                result.add(normalizeRightNameStrict(Objects.toString(right, null)));
            }
            return result;
        }
        return rights;
    }

    private static Map<String, String> normalizeRls(Map<String, String> rls) {
        if (rls == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rls.entrySet()) {
            result.put(normalizeRightNameStrict(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Map<String, String> buildMdoTypeAliases() {
        Map<String, String> result = new LinkedHashMap<>();
        for (MDOType type : MDOType.values()) {
            if (type == MDOType.UNKNOWN) {
                continue;
            }
            String en = type.fullName().getEn();
            result.put(en, en);
            result.put(type.fullName().getRu(), en);
        }
        return result;
    }

    private static Map<String, String> buildRoleRightAliases() {
        Map<String, String> result = new LinkedHashMap<>();
        for (RoleRight right : RoleRight.values()) {
            if (right == RoleRight.UNKNOWN) {
                continue;
            }
            String en = right.fullName().getEn();
            result.put(en, en);
            result.put(right.fullName().getRu(), en);
        }
        return result;
    }
}
