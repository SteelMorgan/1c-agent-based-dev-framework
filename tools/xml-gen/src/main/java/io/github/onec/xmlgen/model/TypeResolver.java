package io.github.onec.xmlgen.model;

import com.github._1c_syntax.bsl.types.AllowedLength;
import com.github._1c_syntax.bsl.types.DateFractions;
import lombok.Value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Преобразование DSL типов в XML типы 1С.
 * 
 * Использует enum-ы AllowedLength и DateFractions из mdclasses (bsl-common-library).
 * 
 * Примеры:
 * - string -> xs:string (Length=0, AllowedLength=Variable)
 * - string(100) -> xs:string (Length=100, AllowedLength=Variable)
 * - string!(50) -> xs:string (Length=50, AllowedLength=Fixed)
 * - number(10,2) -> xs:decimal (Digits=10, FractionDigits=2, AllowedSign=Any)
 * - number+(10,2) -> xs:decimal (Digits=10, FractionDigits=2, AllowedSign=Nonnegative)
 * - boolean -> xs:boolean
 * - date -> xs:dateTime (DateFractions=Date)
 * - datetime -> xs:dateTime (DateFractions=DateTime)
 * - uuid -> v8:UUID
 * - ref:Catalog.Name -> cfg:CatalogRef.Name
 */
public class TypeResolver {
    
    private static final Pattern STRING_PATTERN = Pattern.compile("string(!)?(?:\\((\\d+)\\))?");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("number([+])?\\((\\d+)(?:,(\\d+))?\\)");
    private static final Pattern REF_PATTERN = Pattern.compile("ref:(.+)\\.(.+)");
    
    /**
     * Разрешить DSL тип в XML тип.
     */
    public static TypeInfo resolve(String dslType) {
        if (dslType == null || dslType.isEmpty()) {
            throw new IllegalArgumentException("Type cannot be null or empty");
        }

        // Runtime-типы 1С — не существуют в XML-схеме формы, вызывают XDTO-ошибку при загрузке.
        // Используйте CatalogObject.X/DocumentObject.X/DataProcessorObject.X, ValueTable, ValueTree.
        if ("FormDataStructure".equals(dslType)
                || "FormDataCollection".equals(dslType)
                || "FormDataTree".equals(dslType)) {
            throw new IllegalArgumentException(
                    "Forbidden runtime type '" + dslType + "' — не существует в XML-схеме формы. "
                    + "Используйте CatalogObject.X / DocumentObject.X / DataProcessorObject.X, "
                    + "ValueTable или ValueTree.");
        }

        // String types
        Matcher stringMatcher = STRING_PATTERN.matcher(dslType);
        if (stringMatcher.matches()) {
            boolean fixed = stringMatcher.group(1) != null;
            String lengthStr = stringMatcher.group(2);
            int length = lengthStr != null ? Integer.parseInt(lengthStr) : 0;
            AllowedLength allowedLength = fixed ? AllowedLength.FIXED : AllowedLength.VARIABLE;
            
            return new TypeInfo(
                "xs:string",
                new StringQualifiers(length, allowedLength)
            );
        }
        
        // Number types
        Matcher numberMatcher = NUMBER_PATTERN.matcher(dslType);
        if (numberMatcher.matches()) {
            boolean nonnegative = numberMatcher.group(1) != null;
            int digits = Integer.parseInt(numberMatcher.group(2));
            String fractionStr = numberMatcher.group(3);
            int fraction = fractionStr != null ? Integer.parseInt(fractionStr) : 0;
            
            return new TypeInfo(
                "xs:decimal",
                new NumberQualifiers(digits, fraction, nonnegative ? "Nonnegative" : "Any")
            );
        }
        
        // Простой "number" без параметров (по умолчанию 15,2)
        if ("number".equalsIgnoreCase(dslType) || "decimal".equalsIgnoreCase(dslType)) {
            return new TypeInfo(
                "xs:decimal",
                new NumberQualifiers(15, 2, "Any")
            );
        }
        
        // Boolean
        if ("boolean".equals(dslType)) {
            return new TypeInfo("xs:boolean", null);
        }
        
        // Date types — используем DateFractions enum из mdclasses
        if ("date".equals(dslType)) {
            return new TypeInfo("xs:dateTime", new DateQualifiers(DateFractions.DATE));
        }
        if ("time".equals(dslType)) {
            return new TypeInfo("xs:dateTime", new DateQualifiers(DateFractions.TIME));
        }
        if ("datetime".equals(dslType)) {
            return new TypeInfo("xs:dateTime", new DateQualifiers(DateFractions.DATE_TIME));
        }
        
        // UUID
        if ("uuid".equals(dslType)) {
            return new TypeInfo("v8:UUID", null);
        }
        
        // ValueTable/ValueTree (case-insensitive)
        if ("valuetable".equalsIgnoreCase(dslType)) {
            return new TypeInfo("v8:ValueTable", null);
        }
        if ("valuetree".equalsIgnoreCase(dslType)) {
            return new TypeInfo("v8:ValueTree", null);
        }
        
        // SpreadsheetDocument
        if ("spreadsheet".equals(dslType)) {
            return new TypeInfo("mxl:SpreadsheetDocument", null);
        }
        
        // Reference types (ref:Catalog.Name)
        Matcher refMatcher = REF_PATTERN.matcher(dslType);
        if (refMatcher.matches()) {
            String mdoType = refMatcher.group(1);
            String mdoName = refMatcher.group(2);
            return new TypeInfo("cfg:" + mdoType + "Ref." + mdoName, null);
        }
        
        // Object types (object:EPF.Name)
        if (dslType.startsWith("object:")) {
            String objectPath = dslType.substring(7);
            String[] parts = objectPath.split("\\.");
            if (parts.length == 2) {
                return new TypeInfo("cfg:" + parts[0] + "Object." + parts[1], null);
            }
        }
        
        // ExternalDataProcessorObject.Name (прямой формат)
        if (dslType.startsWith("ExternalDataProcessorObject.")) {
            String name = dslType.substring("ExternalDataProcessorObject.".length());
            return new TypeInfo("cfg:ExternalDataProcessorObject." + name, null);
        }
        
        // ExternalReportObject.Name
        if (dslType.startsWith("ExternalReportObject.")) {
            String name = dslType.substring("ExternalReportObject.".length());
            return new TypeInfo("cfg:ExternalReportObject." + name, null);
        }
        
        // DocumentObject.Name
        if (dslType.startsWith("DocumentObject.")) {
            String name = dslType.substring("DocumentObject.".length());
            return new TypeInfo("cfg:DocumentObject." + name, null);
        }
        
        // CatalogObject.Name
        if (dslType.startsWith("CatalogObject.")) {
            String name = dslType.substring("CatalogObject.".length());
            return new TypeInfo("cfg:CatalogObject." + name, null);
        }
        
        // DataProcessorObject.Name
        if (dslType.startsWith("DataProcessorObject.")) {
            String name = dslType.substring("DataProcessorObject.".length());
            return new TypeInfo("cfg:DataProcessorObject." + name, null);
        }
        
        // ReportObject.Name
        if (dslType.startsWith("ReportObject.")) {
            String name = dslType.substring("ReportObject.".length());
            return new TypeInfo("cfg:ReportObject." + name, null);
        }

        // DynamicList (используется bare в Form.xml без v8/cfg префикса)
        if ("DynamicList".equals(dslType)) {
            return new TypeInfo("DynamicList", null);
        }

        // InformationRegister* / AccumulationRegister* / AccountingRegister*
        if (dslType.startsWith("InformationRegisterRecordSet.")
                || dslType.startsWith("InformationRegisterRecordManager.")
                || dslType.startsWith("AccumulationRegisterRecordSet.")
                || dslType.startsWith("AccountingRegisterRecordSet.")) {
            return new TypeInfo("cfg:" + dslType, null);
        }

        // ChartOfAccountsObject / ChartOfCharacteristicTypesObject / ExchangePlanObject и подобные
        if (dslType.startsWith("ChartOfAccountsObject.")
                || dslType.startsWith("ChartOfCharacteristicTypesObject.")
                || dslType.startsWith("ChartOfCalculationTypesObject.")
                || dslType.startsWith("ExchangePlanObject.")
                || dslType.startsWith("BusinessProcessObject.")
                || dslType.startsWith("TaskObject.")) {
            return new TypeInfo("cfg:" + dslType, null);
        }

        // Общий паттерн для *Ref.Name (CatalogRef, DocumentRef и т.д.)
        if (dslType.contains("Ref.")) {
            return new TypeInfo("cfg:" + dslType, null);
        }

        // Общий паттерн для *Object.Name
        if (dslType.contains("Object.")) {
            return new TypeInfo("cfg:" + dslType, null);
        }

        throw new IllegalArgumentException("Unknown type: " + dslType);
    }
    
    /**
     * Информация о типе.
     */
    @Value
    public static class TypeInfo {
        String xmlType;
        Object qualifiers;
    }
    
    /**
     * Квалификаторы строки.
     * Использует AllowedLength enum из mdclasses.
     */
    @Value
    public static class StringQualifiers {
        int length;
        AllowedLength allowedLength;
        
        /** XML-значение для AllowedLength. */
        public String getAllowedLengthXml() {
            return allowedLength.fullName().getEn();
        }
    }
    
    /**
     * Квалификаторы числа.
     */
    @Value
    public static class NumberQualifiers {
        int digits;
        int fractionDigits;
        String allowedSign; // "Any" | "Nonnegative"
    }
    
    /**
     * Квалификаторы даты.
     * Использует DateFractions enum из mdclasses.
     */
    @Value
    public static class DateQualifiers {
        DateFractions dateFractions;
        
        /** XML-значение для DateFractions. */
        public String getDateFractionsXml() {
            return dateFractions.fullName().getEn();
        }
    }
}
