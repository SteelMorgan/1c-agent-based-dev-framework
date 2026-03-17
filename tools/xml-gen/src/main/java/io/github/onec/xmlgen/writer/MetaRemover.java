package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.model.MetadataTypeRegistry.TypeDescriptor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Удаление объекта метаданных из выгрузки конфигурации 1С.
 *
 * Алгоритм:
 * 1. Найти файлы объекта (Type/Name.xml + Type/Name/)
 * 2. Проверить ссылки на объект в XML и BSL файлах
 * 3. Удалить запись из Configuration.xml ChildObjects
 * 4. Удалить из Content всех подсистем
 * 5. Удалить файлы объекта
 */
public class MetaRemover {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Русские имена менеджеров для BSL-ссылок */
    private static final Map<String, String> TYPE_RU_MANAGER = new LinkedHashMap<>();
    static {
        TYPE_RU_MANAGER.put("Catalog", "Справочники");
        TYPE_RU_MANAGER.put("Document", "Документы");
        TYPE_RU_MANAGER.put("Enum", "Перечисления");
        TYPE_RU_MANAGER.put("Constant", "Константы");
        TYPE_RU_MANAGER.put("InformationRegister", "РегистрыСведений");
        TYPE_RU_MANAGER.put("AccumulationRegister", "РегистрыНакопления");
        TYPE_RU_MANAGER.put("AccountingRegister", "РегистрыБухгалтерии");
        TYPE_RU_MANAGER.put("CalculationRegister", "РегистрыРасчета");
        TYPE_RU_MANAGER.put("ChartOfAccounts", "ПланыСчетов");
        TYPE_RU_MANAGER.put("ChartOfCharacteristicTypes", "ПланыВидовХарактеристик");
        TYPE_RU_MANAGER.put("ChartOfCalculationTypes", "ПланыВидовРасчета");
        TYPE_RU_MANAGER.put("BusinessProcess", "БизнесПроцессы");
        TYPE_RU_MANAGER.put("Task", "Задачи");
        TYPE_RU_MANAGER.put("ExchangePlan", "ПланыОбмена");
        TYPE_RU_MANAGER.put("Report", "Отчеты");
        TYPE_RU_MANAGER.put("DataProcessor", "Обработки");
        TYPE_RU_MANAGER.put("DocumentJournal", "ЖурналыДокументов");
    }

    /** Типы ссылок в XML (<v8:Type>CatalogRef.Name</v8:Type>) */
    private static final Map<String, List<String>> TYPE_REF_NAMES = new LinkedHashMap<>();
    static {
        TYPE_REF_NAMES.put("Catalog", List.of("CatalogRef", "CatalogObject"));
        TYPE_REF_NAMES.put("Document", List.of("DocumentRef", "DocumentObject"));
        TYPE_REF_NAMES.put("Enum", List.of("EnumRef"));
        TYPE_REF_NAMES.put("ExchangePlan", List.of("ExchangePlanRef", "ExchangePlanObject"));
        TYPE_REF_NAMES.put("ChartOfAccounts", List.of("ChartOfAccountsRef", "ChartOfAccountsObject"));
        TYPE_REF_NAMES.put("ChartOfCharacteristicTypes",
                List.of("ChartOfCharacteristicTypesRef", "ChartOfCharacteristicTypesObject"));
        TYPE_REF_NAMES.put("ChartOfCalculationTypes",
                List.of("ChartOfCalculationTypesRef", "ChartOfCalculationTypesObject"));
        TYPE_REF_NAMES.put("BusinessProcess", List.of("BusinessProcessRef", "BusinessProcessObject"));
        TYPE_REF_NAMES.put("Task", List.of("TaskRef", "TaskObject"));
    }

    private final PrintStream out;

    public MetaRemover() {
        this(System.out);
    }

    public MetaRemover(PrintStream out) {
        this.out = out;
    }

    /**
     * Удалить объект метаданных из конфигурации.
     *
     * @param configDir корень выгрузки конфигурации
     * @param objectSpec спецификация "Type.Name" (напр. "Catalog.Товары")
     * @param dryRun только показать что будет сделано, не менять файлы
     * @param keepFiles не удалять файлы объекта (только дерегистрация)
     * @param force удалять даже при наличии ссылок
     * @return количество выполненных действий
     */
    public int remove(Path configDir, String objectSpec, boolean dryRun, boolean keepFiles, boolean force)
            throws IOException {

        // --- Parse object spec ---
        String[] parts = objectSpec.split("\\.", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid object format '" + objectSpec + "'. Expected: Type.Name (e.g. Catalog.Товары)");
        }

        String objType = parts[0];
        String objName = parts[1];

        TypeDescriptor td = MetadataTypeRegistry.get(objType);
        if (td == null) {
            throw new IllegalArgumentException("Unknown type '" + objType
                    + "'. Supported: " + String.join(", ", supportedTypes()));
        }

        String typePlural = td.directory();

        out.println("=== meta-remove: " + objType + "." + objName + " ===");
        out.println();

        if (dryRun) {
            out.println("[DRY-RUN] No changes will be made");
            out.println();
        }

        Path configXml = configDir.resolve("Configuration.xml");
        if (!Files.isRegularFile(configXml)) {
            throw new IllegalArgumentException("Configuration.xml not found in: " + configDir);
        }

        int actions = 0;

        // --- 1. Find object files ---
        Path typeDir = configDir.resolve(typePlural);
        Path objXml = typeDir.resolve(objName + ".xml");
        Path objDir = typeDir.resolve(objName);

        boolean hasXml = Files.isRegularFile(objXml);
        boolean hasDir = Files.isDirectory(objDir);

        if (!hasXml && !hasDir) {
            out.println("[WARN]  Object files not found: " + typePlural + "/" + objName + ".xml");
            out.println("        Proceeding with deregistration only...");
        } else {
            if (hasXml) {
                out.println("[FOUND] " + typePlural + "/" + objName + ".xml");
            }
            if (hasDir) {
                int fileCount = countFiles(objDir);
                out.println("[FOUND] " + typePlural + "/" + objName + "/ (" + fileCount + " files)");
            }
        }

        // --- 2. Reference check ---
        out.println();
        out.println("--- Reference check ---");

        List<String> searchPatterns = buildSearchPatterns(objType, objName, typePlural);

        List<Reference> references = findReferences(configDir, searchPatterns, objType, objName,
                hasXml ? objXml : null, hasDir ? objDir : null);

        if (!references.isEmpty()) {
            out.println("[WARN]  Found " + references.size() + " reference(s) to " + objType + "." + objName + ":");
            out.println();
            int shown = 0;
            for (Reference ref : references) {
                out.println("        " + ref.file);
                out.println("          pattern: " + ref.pattern);
                shown++;
                if (shown >= 20) {
                    int remaining = references.size() - shown;
                    if (remaining > 0) {
                        out.println("        ... and " + remaining + " more");
                    }
                    break;
                }
            }
            out.println();

            if (!force) {
                throw new IllegalStateException(
                        "Cannot remove: object has " + references.size() + " reference(s). "
                                + "Use --force to remove anyway, or fix references first.");
            }
            out.println("[WARN]  --force specified, proceeding despite references");
        } else {
            out.println("[OK]    No references found");
        }

        // --- 3. Remove from Configuration.xml ChildObjects ---
        out.println();
        out.println("--- Configuration.xml ---");

        boolean removedFromConfig = removeFromConfigurationXml(configXml, objType, objName, dryRun);
        if (removedFromConfig) {
            out.println("[OK]    Removed <" + objType + ">" + objName + "</" + objType + "> from ChildObjects");
            actions++;
        } else {
            out.println("[WARN]  <" + objType + ">" + objName + "</" + objType + "> not found in ChildObjects");
        }

        // --- 4. Remove from subsystem Content ---
        out.println();
        out.println("--- Subsystems ---");

        Path subsystemsDir = configDir.resolve("Subsystems");
        int subsystemsCleaned = 0;
        if (Files.isDirectory(subsystemsDir)) {
            subsystemsCleaned = removeFromSubsystems(subsystemsDir, objType, objName, dryRun);
            if (subsystemsCleaned == 0) {
                out.println("[OK]    Not referenced in any subsystem");
            }
        } else {
            out.println("[OK]    No Subsystems directory");
        }

        // --- 5. Delete object files ---
        out.println();
        out.println("--- Files ---");

        if (!keepFiles) {
            if (hasDir) {
                if (!dryRun) {
                    deleteDirectory(objDir);
                    out.println("[OK]    Deleted directory: " + typePlural + "/" + objName + "/");
                } else {
                    out.println("[DRY]   Would delete directory: " + typePlural + "/" + objName + "/");
                }
                actions++;
            }
            if (hasXml) {
                if (!dryRun) {
                    Files.delete(objXml);
                    out.println("[OK]    Deleted file: " + typePlural + "/" + objName + ".xml");
                } else {
                    out.println("[DRY]   Would delete file: " + typePlural + "/" + objName + ".xml");
                }
                actions++;
            }
            if (!hasXml && !hasDir) {
                out.println("[OK]    No files to delete");
            }
        } else {
            out.println("[SKIP]  File deletion skipped (--keep-files)");
        }

        // --- Summary ---
        out.println();
        int totalActions = actions + subsystemsCleaned;
        if (dryRun) {
            out.println("=== Dry run complete: " + totalActions + " actions would be performed ===");
        } else {
            out.println("=== Done: " + totalActions + " actions performed ("
                    + subsystemsCleaned + " subsystem references removed) ===");
        }

        return totalActions;
    }

    // ─── Reference search ───────────────────────────────────────────────

    private List<String> buildSearchPatterns(String objType, String objName, String typePlural) {
        List<String> patterns = new ArrayList<>();

        // XML type references (CatalogRef.Name, DocumentObject.Name, etc.)
        List<String> refNames = TYPE_REF_NAMES.get(objType);
        if (refNames != null) {
            for (String refName : refNames) {
                patterns.add(refName + "." + objName);
            }
        }

        // BSL code references (Справочники.Name, Catalogs.Name)
        String ruMgr = TYPE_RU_MANAGER.get(objType);
        if (ruMgr != null) {
            patterns.add(ruMgr + "." + objName);
        }
        patterns.add(typePlural + "." + objName);

        // CommonModule: method calls and handler references
        if ("CommonModule".equals(objType)) {
            patterns.add(objName + ".");
            patterns.add("<Handler>" + objName + ".");
            patterns.add("<MethodName>" + objName + ".");
        }

        // ScheduledJob: MethodName references CommonModule.Proc
        if ("ScheduledJob".equals(objType)) {
            patterns.add("<MethodName>" + objName);
        }

        // EventSubscription: Handler references CommonModule.Proc
        if ("EventSubscription".equals(objType)) {
            patterns.add("<Handler>" + objName);
        }

        // DefinedType: type references
        if ("DefinedType".equals(objType)) {
            patterns.add("DefinedType." + objName);
        }

        return patterns;
    }

    private List<Reference> findReferences(Path configDir, List<String> searchPatterns,
                                           String objType, String objName,
                                           Path excludeXml, Path excludeDir) throws IOException {
        List<Reference> references = new ArrayList<>();
        Set<String> foundFiles = new HashSet<>();
        String typeNameRef = objType + "." + objName;

        Files.walkFileTree(configDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString().toLowerCase();
                if (!fileName.endsWith(".xml") && !fileName.endsWith(".bsl")) {
                    return FileVisitResult.CONTINUE;
                }

                // Skip own files
                if (excludeXml != null && file.equals(excludeXml)) {
                    return FileVisitResult.CONTINUE;
                }
                if (excludeDir != null && file.startsWith(excludeDir)) {
                    return FileVisitResult.CONTINUE;
                }

                String relPath = configDir.relativize(file).toString().replace('\\', '/');

                // Skip auto-cleaned files
                if (relPath.equals("Configuration.xml")
                        || relPath.equals("ConfigDumpInfo.xml")
                        || relPath.startsWith("Subsystems")) {
                    return FileVisitResult.CONTINUE;
                }

                try {
                    String content = readFileContent(file);

                    // Check search patterns
                    for (String pat : searchPatterns) {
                        if (content.contains(pat)) {
                            references.add(new Reference(relPath, pat));
                            foundFiles.add(relPath);
                            break;
                        }
                    }

                    // Also check Type.Name reference (only in XML, skip already found)
                    if (fileName.endsWith(".xml") && !foundFiles.contains(relPath)
                            && content.contains(typeNameRef)) {
                        references.add(new Reference(relPath, typeNameRef));
                    }
                } catch (IOException e) {
                    // Skip unreadable files
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return references;
    }

    // ─── Configuration.xml ──────────────────────────────────────────────

    /**
     * Удалить элемент из ChildObjects в Configuration.xml.
     * Использует текстовый поиск+замену для сохранения форматирования.
     */
    private boolean removeFromConfigurationXml(Path configXml, String objType, String objName, boolean dryRun)
            throws IOException {
        String content = readFileContent(configXml);

        // Match whole line: \n\t\t\t<Type>Name</Type> (capture leading newline + indent)
        String linePattern = "\\r?\\n[ \\t]*<" + Pattern.quote(objType) + ">"
                + Pattern.quote(objName) + "</" + Pattern.quote(objType) + ">";

        java.util.regex.Matcher matcher = Pattern.compile(linePattern).matcher(content);
        if (!matcher.find()) {
            return false;
        }

        if (!dryRun) {
            String newContent = matcher.replaceFirst("");
            writeFileWithBom(configXml, newContent);
        }

        return true;
    }

    // ─── Subsystems ─────────────────────────────────────────────────────

    /**
     * Рекурсивно удалить объект из Content всех подсистем.
     */
    private int removeFromSubsystems(Path subsystemsDir, String objType, String objName, boolean dryRun)
            throws IOException {
        int[] cleaned = {0};
        String targetRef = objType + "." + objName;

        removeFromSubsystemsRecursive(subsystemsDir, targetRef, dryRun, cleaned);
        return cleaned[0];
    }

    private void removeFromSubsystemsRecursive(Path dir, String targetRef, boolean dryRun, int[] cleaned)
            throws IOException {
        if (!Files.isDirectory(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.xml")) {
            for (Path xmlFile : stream) {
                if (!Files.isRegularFile(xmlFile)) continue;

                String content = readFileContent(xmlFile);

                // Match whole line: \n\t\t\t\t<v8:Item>Type.Name</v8:Item>
                String linePattern = "\\r?\\n[ \\t]*<v8:Item>" + Pattern.quote(targetRef) + "</v8:Item>";
                java.util.regex.Matcher matcher = Pattern.compile(linePattern).matcher(content);

                if (matcher.find()) {
                    String ssName = xmlFile.getFileName().toString().replaceFirst("\\.xml$", "");
                    if (!dryRun) {
                        String newContent = matcher.replaceAll("");
                        writeFileWithBom(xmlFile, newContent);
                    }
                    out.println("[OK]    Removed from subsystem '" + ssName + "'");
                    cleaned[0]++;
                }

                // Recurse into child subsystems: same-name-dir/Subsystems/
                String baseName = xmlFile.getFileName().toString().replaceFirst("\\.xml$", "");
                Path childSubsystems = dir.resolve(baseName).resolve("Subsystems");
                if (Files.isDirectory(childSubsystems)) {
                    removeFromSubsystemsRecursive(childSubsystems, targetRef, dryRun, cleaned);
                }
            }
        }
    }

    // ─── Utility ────────────────────────────────────────────────────────

    private String readFileContent(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        // Skip BOM if present
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) {
            offset = 3;
        }
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private void writeFileWithBom(Path path, String content) throws IOException {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + contentBytes.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
        Files.write(path, result);
    }

    private static int countFiles(Path dir) throws IOException {
        int[] count = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static List<String> supportedTypes() {
        List<String> types = new ArrayList<>();
        for (MetadataTypeRegistry.TypeDescriptor td : MetadataTypeRegistry.all()) {
            types.add(td.type());
        }
        return types;
    }

    private record Reference(String file, String pattern) {}
}
