package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.onec.xmlgen.model.MlText;

import java.util.List;
import java.util.Map;

/**
 * POJO для JSON batch-патча объекта метаданных 1С.
 *
 * <p>Поддерживаемый формат согласно SPEC §9.3 и skill batch-patch.md:
 * <pre>
 * {
 *   "operations": [
 *     { "op": "modify-property", "name": "Synonym", "value": {"ru": "...", "en": "..."} },
 *     { "op": "add-attribute",   "name": "Комментарий", "type": "string(255)" },
 *     { "op": "modify-attribute","name": "ИНН", "synonym": {"ru": "ИНН"}, "fillChecking": "ShowError" },
 *     { "op": "modify-tabularSection", "name": "Контакты",
 *       "operations": [ { "op": "add-attribute", "name": "Тип", "type": "EnumRef.ТипКонтакта" } ] },
 *     { "op": "set-property", "name": "BasedOn", "value": ["Documents.СчётНаОплату"] }
 *   ]
 * }
 * </pre>
 *
 * <p>Также поддерживается расширенный формат batch-patch.md с ключами {@code add}/{@code remove}/{@code modify}.
 * В текущей реализации используется формат {@code "operations":[...]} из SPEC §9.3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaBatchDsl {

    private final List<Operation> operations;

    @JsonCreator
    public MetaBatchDsl(@JsonProperty("operations") List<Operation> operations) {
        this.operations = operations != null ? operations : List.of();
    }

    public List<Operation> getOperations() {
        return operations;
    }

    // ── Operation ────────────────────────────────────────────────────────

    /**
     * Одна операция в batch.
     *
     * Поля {@code op} и {@code name} — обязательные.
     * Остальные зависят от конкретного {@code op}:
     * <ul>
     *   <li>{@code modify-property} / {@code set-property}: {@code value} (Object: String | List | MlText)</li>
     *   <li>{@code add-attribute}: {@code type}, {@code synonym}, {@code fillChecking}, {@code after}, {@code before}</li>
     *   <li>{@code modify-attribute}: {@code synonym} (MlText), {@code fillChecking}, {@code type}, {@code newName}</li>
     *   <li>{@code modify-tabularSection}: {@code operations} (вложенный список)</li>
     *   <li>{@code add-dimension}, {@code add-resource}: аналогично {@code add-attribute}</li>
     * </ul>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Operation {

        /** Тип операции. */
        private final String op;

        /** Имя элемента (реквизита, свойства, ТЧ). */
        private final String name;

        /**
         * Значение для modify-property / set-property.
         * Может быть:
         * <ul>
         *   <li>String — скалярное значение</li>
         *   <li>List&lt;String&gt; — массив значений (для BasedOn и т.п.)</li>
         *   <li>MlText — объект {ru:..., en:...} для MLText-свойств</li>
         * </ul>
         * Jackson десериализует как {@code Object}.
         */
        private final Object value;

        /** Тип для add-attribute / add-dimension / add-resource. */
        private final String type;

        /** Синоним (MLText) для add-attribute / modify-attribute. */
        private final MlText synonym;

        /** fillChecking для add-attribute / modify-attribute. */
        private final String fillChecking;

        /** Позиция вставки — после этого элемента. */
        private final String after;

        /** Позиция вставки — перед этим элементом. */
        private final String before;

        /** Новое имя для операций переименования. */
        private final String newName;

        /** Вложенные операции для modify-tabularSection. */
        private final List<Operation> operations;

        @JsonCreator
        public Operation(
                @JsonProperty("op")           String op,
                @JsonProperty("name")         String name,
                @JsonProperty("value")        Object value,
                @JsonProperty("type")         String type,
                @JsonProperty("synonym")      MlText synonym,
                @JsonProperty("fillChecking") String fillChecking,
                @JsonProperty("after")        String after,
                @JsonProperty("before")       String before,
                @JsonProperty("newName")      String newName,
                @JsonProperty("operations")   List<Operation> operations) {
            this.op           = op;
            this.name         = name;
            this.value        = value;
            this.type         = type;
            this.synonym      = synonym;
            this.fillChecking = fillChecking;
            this.after        = after;
            this.before       = before;
            this.newName      = newName;
            this.operations   = operations != null ? operations : List.of();
        }

        public String getOp()           { return op; }
        public String getName()         { return name; }
        public Object getValue()        { return value; }
        public String getType()         { return type; }
        public MlText getSynonym()      { return synonym; }
        public String getFillChecking() { return fillChecking; }
        public String getAfter()        { return after; }
        public String getBefore()       { return before; }
        public String getNewName()      { return newName; }
        public List<Operation> getOperations() { return operations; }
    }
}
