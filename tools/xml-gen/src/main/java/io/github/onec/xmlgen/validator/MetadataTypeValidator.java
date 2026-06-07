package io.github.onec.xmlgen.validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Валидатор ссылочной целостности типов метаданных.
 * Проверяет существование объектов (Справочников, Документов и т.д.) в файловой системе проекта.
 */
public class MetadataTypeValidator {

    private final Path sourceRoot;
    private final Set<String> knownMetadata = new HashSet<>();
    private boolean initialized = false;

    public MetadataTypeValidator(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    /**
     * Инициализирует кэш известных метаданных сканированием sourceRoot.
     * Выполняется лениво при первом обращении.
     */
    public void initialize() {
        if (initialized || sourceRoot == null || !Files.exists(sourceRoot)) {
            return;
        }

        scanMetadata("Catalogs", "CatalogRef");
        scanMetadata("Documents", "DocumentRef");
        scanMetadata("Enums", "EnumRef");
        scanMetadata("DataProcessors", "DataProcessor"); // Не Ref, но может пригодиться
        scanMetadata("Reports", "Report");

        // Стандартные типы платформы (базовый набор)
        knownMetadata.add("Boolean");
        knownMetadata.add("String");
        knownMetadata.add("Date");
        knownMetadata.add("Number");
        knownMetadata.add("UUID");
        knownMetadata.add("ValueStorage");
        
        initialized = true;
    }

    private void scanMetadata(String folderName, String typePrefix) {
        Path folder = sourceRoot.resolve(folderName);
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            return;
        }

        try (Stream<Path> paths = Files.list(folder)) {
            paths.filter(Files::isDirectory) // В формате Source каталоги - это объекты
                 .map(p -> p.getFileName().toString())
                 .filter(name -> !name.startsWith(".")) // Игнорим .gitkeep и прочее
                 .forEach(name -> knownMetadata.add(typePrefix + "." + name));
                 
            // Также поддерживаем формат выгрузки в файлы .xml (старый формат)
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".xml"))
                     .map(p -> p.getFileName().toString().replace(".xml", ""))
                     .forEach(name -> knownMetadata.add(typePrefix + "." + name));
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to scan metadata folder " + folder + ": " + e.getMessage());
        }
    }

    /**
     * Проверяет валидность типа.
     *
     * @param typeName имя типа (например, "CatalogRef.Users")
     * @param contextNode узел XML, в котором встретился тип (для информации о строке)
     * @param path путь к узлу (для сообщения об ошибке)
     * @return список ошибок (пустой, если все ок или sourceRoot не задан)
     */
    public List<ValidationIssue> validateType(String typeName, XmlNode contextNode, String path) {
        if (sourceRoot == null) {
            return Collections.emptyList(); // Режим без проверки контекста
        }
        if (!initialized) {
            initialize();
        }

        List<ValidationIssue> issues = new ArrayList<>();
        for (String part : splitTypeNames(typeName)) {
            issues.addAll(validateSingleType(part, contextNode, path));
        }
        return issues;
    }

    private List<String> splitTypeNames(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return Collections.emptyList();
        }
        if (typeName.contains("|")) {
            return Arrays.stream(typeName.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return List.of(typeName.trim());
    }

    private List<ValidationIssue> validateSingleType(String typeName, XmlNode contextNode, String path) {
        // Убираем namespace prefix (cfg:CatalogRef.X -> CatalogRef.X)
        if (typeName.contains(":")) {
            typeName = typeName.substring(typeName.indexOf(":") + 1);
        }

        if (knownMetadata.contains(typeName)) {
            return Collections.emptyList();
        }
        
        // Попытка угадать: может это стандартный тип 1С, который мы забыли?
        // Если начинается на CatalogRef/DocumentRef - мы точно должны знать о нем.
        if (typeName.startsWith("CatalogRef.") || typeName.startsWith("DocumentRef.") || typeName.startsWith("EnumRef.")) {
            return List.of(ValidationIssue.error("SEM-001", 
                "Unknown metadata type: '" + typeName + "'. Check if object exists in " + sourceRoot, 
                contextNode.getLine(), path));
        }

        return Collections.emptyList();
    }
}
