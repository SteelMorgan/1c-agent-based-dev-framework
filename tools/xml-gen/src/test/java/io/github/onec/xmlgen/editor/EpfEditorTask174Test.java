package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-174 XG-09: epf add-attribute должен резолвить переданный тип в каноническую
 * XML-форму (xs:dateTime + DateQualifiers и т.п.), а не писать строку сырой.
 */
class EpfEditorTask174Test {

    private XmlDocument document;
    private EpfEditor editor;

    @BeforeEach
    void setUp() {
        XmlNode root = XmlNode.builder()
                .name("ExternalDataProcessor")
                .build();
        document = new XmlDocument(null, false, null, "ExternalDataProcessor", "", Map.of(), root.getChildren(), root);
        editor = new EpfEditor(document);
    }

    private XmlNode typeNodeOfFirstAttr() {
        return document.getRoot().child("ChildObjects")
                .getChildren().get(0).child("Properties").child("Type");
    }

    private List<String> v8Types(XmlNode typeNode) {
        return typeNode.getChildren().stream()
                .filter(c -> "Type".equals(c.getName()))
                .map(XmlNode::getText)
                .collect(Collectors.toList());
    }

    @Test
    void xg09_dateType_resolvedToDateTimeWithQualifiers() {
        editor.addAttribute("НачалоПериода", "Date", null);

        XmlNode type = typeNodeOfFirstAttr();
        assertEquals(List.of("xs:dateTime"), v8Types(type));
        XmlNode dq = type.child("DateQualifiers");
        assertNotNull(dq, "DateQualifiers expected for Date");
        assertEquals("Date", dq.childText("DateFractions"));
    }

    @Test
    void xg09_numberType_resolvedToDecimalWithQualifiers() {
        editor.addAttribute("Сумма", "Number(15,2)", null);

        XmlNode type = typeNodeOfFirstAttr();
        assertEquals(List.of("xs:decimal"), v8Types(type));
        XmlNode nq = type.child("NumberQualifiers");
        assertNotNull(nq);
        assertEquals("15", nq.childText("Digits"));
        assertEquals("2", nq.childText("FractionDigits"));
    }

    @Test
    void xg09_stringType_resolvedWithLength() {
        editor.addAttribute("Комментарий", "String(100)", null);

        XmlNode type = typeNodeOfFirstAttr();
        assertEquals(List.of("xs:string"), v8Types(type));
        XmlNode sq = type.child("StringQualifiers");
        assertNotNull(sq);
        assertEquals("100", sq.childText("Length"));
    }

    @Test
    void xg09_refType_getsCfgPrefix() {
        editor.addAttribute("Договор", "CatalogRef.Договоры", null);

        assertEquals(List.of("cfg:CatalogRef.Договоры"), v8Types(typeNodeOfFirstAttr()));
    }

    @Test
    void xg09_compositeType_separateV8TypesQualifiersAfter() {
        editor.addAttribute("Ссылка", "CatalogRef.А|String(50)", null);

        XmlNode type = typeNodeOfFirstAttr();
        assertEquals(List.of("cfg:CatalogRef.А", "xs:string"), v8Types(type));
        // Квалификаторы — после всех v8:Type
        List<String> childNames = type.getChildren().stream()
                .map(XmlNode::getName).collect(Collectors.toList());
        assertEquals(childNames.size() - 1, childNames.lastIndexOf("StringQualifiers"));
    }

    @Test
    void xg09_alreadyCanonicalXsType_keptAsIs() {
        editor.addAttribute("Флаг", "xs:boolean", null);

        assertEquals(List.of("xs:boolean"), v8Types(typeNodeOfFirstAttr()));
    }
}
