package io.github.onec.xmlgen.model;

import java.util.UUID;

/**
 * Генератор UUID для метаданных 1С.
 * 
 * В Designer-формате каждый объект имеет пару UUID в InternalInfo:
 * - ObjectId (основной UUID объекта)
 * - TypeId (UUID типа объекта, для EPF это константа)
 */
public class UuidGenerator {
    
    /**
     * ClassId для ExternalDataProcessor (константа из спеки).
     */
    public static final String EPF_CLASS_ID = "c3831ec8-d8d5-4f93-8a22-f9bfae07327f";
    
    /**
     * Генерировать новый UUID v4.
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Генерировать пару UUID для InternalInfo объекта.
     * 
     * @param classId UUID типа объекта (например, EPF_CLASS_ID)
     * @return массив [objectId, classId]
     */
    public static String[] generatePair(String classId) {
        return new String[] {
            generate(),  // ObjectId
            classId      // TypeId (константа для типа)
        };
    }
    
    /**
     * Генерировать пару UUID для EPF.
     */
    public static String[] generateEpfPair() {
        return generatePair(EPF_CLASS_ID);
    }
}
