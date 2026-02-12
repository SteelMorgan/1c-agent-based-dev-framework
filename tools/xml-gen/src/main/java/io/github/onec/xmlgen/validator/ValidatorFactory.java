package io.github.onec.xmlgen.validator;

import java.util.*;

/**
 * Фабрика валидаторов.
 * Регистрирует все известные валидаторы и выбирает подходящий по типу или автодетекту.
 */
public class ValidatorFactory {

    private final List<XmlValidator> validators = new ArrayList<>();

    public ValidatorFactory() {
        // Регистрируем все известные валидаторы
        validators.add(new RoleValidator());
        validators.add(new SkdValidator());
        validators.add(new MxlValidator());
        validators.add(new EpfValidator());
        validators.add(new FormValidator());
        // Phase 4: validators.add(new SkdValidator());
        // Phase 5: validators.add(new MxlValidator());
        // Phase 6: validators.add(new EpfValidator());
    }

    /**
     * Получить валидатор по явному типу.
     *
     * @param type тип объекта ("form", "role", "skd", "mxl", "epf")
     * @return валидатор или empty если тип неизвестен
     */
    public Optional<XmlValidator> getValidator(String type) {
        return validators.stream()
                .filter(v -> v.objectType().equalsIgnoreCase(type))
                .findFirst();
    }

    /**
     * Автоматически определить валидатор по содержимому документа.
     *
     * @param document распарсенный XML
     * @return валидатор или empty если тип не распознан
     */
    public Optional<XmlValidator> detectValidator(XmlDocument document) {
        return validators.stream()
                .filter(v -> v.supports(document))
                .findFirst();
    }

    /**
     * Зарегистрировать валидатор (для тестирования и расширения).
     */
    public void register(XmlValidator validator) {
        validators.add(validator);
    }

    /**
     * Список зарегистрированных типов.
     */
    public List<String> registeredTypes() {
        List<String> types = new ArrayList<>();
        for (XmlValidator v : validators) {
            types.add(v.objectType());
        }
        return types;
    }
}
