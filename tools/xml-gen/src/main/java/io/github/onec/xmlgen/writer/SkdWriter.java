package io.github.onec.xmlgen.writer;

import com.github._1c_syntax.bsl.mdo.support.DataSetType;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.dsl.SkdTypeSpec;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.SkdInclude;
import io.github.onec.xmlgen.model.TypeResolver;
import io.github.onec.xmlgen.writer.skd.SkdFieldRoleWriter;
import io.github.onec.xmlgen.writer.skd.SkdTemplateWriter;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Генератор XML для схемы компоновки данных 1С (DataCompositionSchema).
 *
 * <p>Реализует расширенный SKD DSL: наборы Query/Object/Union,
 * calculatedFields, dataSetLinks, templates (rows DSL), groupTemplates,
 * расширенные параметры и поля с ролями.</p>
 */
public class SkdWriter extends XmlWriter {

    private final OutputFormat format;
    private SkdInclude include = new SkdInclude(null);

    private static final Map<String, String> DCS_NAMESPACES = new HashMap<>();
    private static final String CURRENT_CONFIG_NS =
            "http://v8.1c.ru/8.1/data/enterprise/current-config";
    static {
        DCS_NAMESPACES.put("", "http://v8.1c.ru/8.1/data-composition-system/schema");
        DCS_NAMESPACES.put("dcscom", "http://v8.1c.ru/8.1/data-composition-system/common");
        DCS_NAMESPACES.put("dcscor", "http://v8.1c.ru/8.1/data-composition-system/core");
        DCS_NAMESPACES.put("dcsset", "http://v8.1c.ru/8.1/data-composition-system/settings");
        DCS_NAMESPACES.put("v8", "http://v8.1c.ru/8.1/data/core");
        DCS_NAMESPACES.put("v8ui", "http://v8.1c.ru/8.1/data/ui");
        DCS_NAMESPACES.put("xs", "http://www.w3.org/2001/XMLSchema");
        DCS_NAMESPACES.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }

    public SkdWriter(OutputFormat format) {
        this.format = format;
    }

    /** Указать базовую директорию для {@code @file:}-include. */
    public SkdWriter withIncludeBase(Path baseDir) {
        this.include = new SkdInclude(baseDir);
        return this;
    }

    public void create(SkdDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        createDesigner(dsl, outputPath);
    }

    private void createDesigner(SkdDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        // TASK-171 (Р-4): Designer-вывод СКД обязан содержать UTF-8 BOM. Все платформенные
        // Template.xml СКД начинаются с ef bb bf; без BOM наш файл байтово расходится с каноном
        // Конфигуратора. Эталон — RoleWriter/FormWriter Designer (createWriter(..., true, ...)).
        createWriter(outputPath, true, DCS_NAMESPACES);
        writeXmlDeclaration();

        Map<String, String> rootAttrs = new HashMap<>();
        writeRootElement("DataCompositionSchema", DCS_NAMESPACES, rootAttrs);

        // Data sources.
        if (dsl.getDataSources() != null && !dsl.getDataSources().isEmpty()) {
            for (SkdDsl.DataSource ds : dsl.getDataSources()) {
                writeDataSource(ds);
            }
        } else {
            writeDataSource(new SkdDsl.DataSource("ИсточникДанных1", "Local"));
        }

        // Data sets.
        if (dsl.getDataSets() != null) {
            for (SkdDsl.DataSet ds : dsl.getDataSets()) {
                writeDataSet(ds);
            }
        }

        //**agent TASK-174 [07.06.2026 11:35:00]
        // Порядок верхнего уровня DataCompositionSchema фиксирован (1c-dcs-spec.md §2):
        // dataSource, dataSet, dataSetLink, calculatedField, totalField, parameter,
        // template, groupTemplate, settingsVariant. dataSetLink писался ПОСЛЕ template —
        // перенесён на каноничное место сразу после dataSet.
        if (dsl.getDataSetLinks() != null) {
            for (SkdDsl.DataSetLink link : dsl.getDataSetLinks()) {
                writeDataSetLink(link);
            }
        }
        //**agent TASK-174

        // Calculated fields (top-level).
        if (dsl.getCalculatedFields() != null) {
            for (SkdDsl.CalculatedField cf : dsl.getCalculatedFields()) {
                writeCalculatedField(cf);
            }
        }

        // Total fields.
        if (dsl.getTotalFields() != null) {
            for (SkdDsl.TotalField tf : dsl.getTotalFields()) {
                writeTotalField(tf);
            }
        }

        // Parameters.
        if (dsl.getParameters() != null) {
            for (SkdDsl.Parameter param : dsl.getParameters()) {
                writeParameter(param);
            }
        }

        // Templates.
        if (dsl.getTemplates() != null) {
            for (SkdDsl.Template tpl : dsl.getTemplates()) {
                indentLevel = 1;
                writeIndent();
                SkdTemplateWriter.writeTemplate(writer, tpl, "");
            }
        }

        // Group templates.
        if (dsl.getGroupTemplates() != null) {
            for (SkdDsl.GroupTemplate gt : dsl.getGroupTemplates()) {
                indentLevel = 1;
                writeIndent();
                SkdTemplateWriter.writeGroupTemplate(writer, gt, "");
            }
        }

        //--agent TASK-174 [07.06.2026 11:35:00]
        //// Data set links.
        //if (dsl.getDataSetLinks() != null) {
        //    for (SkdDsl.DataSetLink link : dsl.getDataSetLinks()) {
        //        writeDataSetLink(link);
        //    }
        //}
        // dataSetLink перенесён выше — см. маркер TASK-174 после dataSet.
        //--agent TASK-174

        // Settings variants.
        if (dsl.getSettingsVariants() != null && !dsl.getSettingsVariants().isEmpty()) {
            for (SkdDsl.SettingsVariant sv : dsl.getSettingsVariants()) {
                writeSettingsVariant(sv);
            }
        } else {
            writeDefaultSettingsVariant();
        }

        writer.writeEndElement(); // DataCompositionSchema
        close();

        System.out.println("Created SKD schema: " + outputPath);
    }

    /** Запасной индент для XMLStreamWriter (используется при ручной отрисовке). */
    private void writeIndent() throws XMLStreamException {
        for (int i = 0; i < indentLevel; i++) writer.writeCharacters("\t");
    }

    // ============================================================
    // Data source
    // ============================================================

    private void writeDataSource(SkdDsl.DataSource ds) throws XMLStreamException {
        startElement("dataSource");
        writeElement("name", ds.getName());
        writeElement("dataSourceType", ds.getType() != null ? ds.getType() : "Local");
        endElement();
    }

    // ============================================================
    // Data set (Query / Object / Union)
    // ============================================================

    private void writeDataSet(SkdDsl.DataSet ds) throws XMLStreamException {
        writer.writeCharacters("\t");
        writer.writeStartElement("dataSet");

        String xsiType = ds.getXsiType();
        // Маппим через mdclasses enum для надёжности (DataSetType.DATA_SET_QUERY etc.)
        normalizeDataSetType(xsiType);
        writer.writeAttribute("xsi:type", xsiType);
        writer.writeCharacters("\n");
        indentLevel = 2;

        writeElement("name", ds.getName() != null ? ds.getName() : "НаборДанных1");

        // Fields.
        if (ds.getFields() != null) {
            for (SkdDsl.Field field : ds.getFields()) {
                writeField(field);
            }
        }

        // Calculated fields (dataset-level).
        if (ds.getCalculatedFields() != null) {
            for (SkdDsl.CalculatedField cf : ds.getCalculatedFields()) {
                writeCalculatedField(cf);
            }
        }

        String sourceName = ds.getSource() != null ? ds.getSource() : "ИсточникДанных1";
        writeElement("dataSource", sourceName);

        // Query.
        if (ds.getQuery() != null) {
            String resolved = include.resolve(ds.getQuery());
            writeElement("query", resolved);
        }

        // Object name.
        if (ds.getObjectName() != null) {
            writeElement("objectName", ds.getObjectName());
        }

        // Union: nested datasets OR sourceDataSets.
        if (ds.getItems() != null) {
            for (SkdDsl.DataSet item : ds.getItems()) {
                writeDataSet(item);
            }
        }
        if (ds.getSourceDataSets() != null) {
            for (String src : ds.getSourceDataSets()) {
                writeElement("dataSet", src);
            }
        }

        if (ds.getAutoFillFields() != null && !ds.getAutoFillFields()) {
            //**agent TASK-174 [07.06.2026 11:35:00]
            //writeElement("autoFillAvailableFields", "false");
            // Платформенный элемент — 'autoFillFields' (1c-dcs-spec.md §4.1; канон GBIG PAM:
            // 2 вхождения autoFillFields, 0 autoFillAvailableFields). Прежнее имя — ошибка порта.
            writeElement("autoFillFields", "false");
            //**agent TASK-174
        }

        indentLevel = 1;
        writer.writeCharacters("\t");
        writer.writeEndElement(); // dataSet
        writer.writeCharacters("\n");
    }

    /** Маппит xsi:type через mdclasses enum для валидации, бросает при unknown. */
    static DataSetType normalizeDataSetType(String xsiType) {
        if ("DataSetQuery".equals(xsiType)) return DataSetType.DATA_SET_QUERY;
        if ("DataSetObject".equals(xsiType)) return DataSetType.DATA_SET_OBJECT;
        if ("DataSetUnion".equals(xsiType)) return DataSetType.DATA_SET_UNION;
        // unknown — оставляем строку как есть, валидатор пометит.
        return DataSetType.UNKNOWN;
    }

    // ============================================================
    // Field (with role + restrictions + presentationExpression).
    // ============================================================

    private void writeField(SkdDsl.Field field) throws XMLStreamException {
        writer.writeCharacters("\t\t");
        writer.writeStartElement("field");
        writer.writeAttribute("xsi:type", "DataSetFieldField");
        writer.writeCharacters("\n");
        indentLevel = 3;

        String dataPath = field.getDataPath() != null ? field.getDataPath() : field.getField();
        writeElement("dataPath", dataPath);

        String fieldName = field.getField() != null ? field.getField() : field.getDataPath();
        writeElement("field", fieldName);

        // Title (multilang aware).
        String titleStr = field.getTitleString();
        if (titleStr != null) {
            writeLocalStringType("title", titleStr);
        }

        // Value type.
        if (field.getType() != null) {
            writeValueType(field.getType());
        }

        // Role.
        if (field.getRole() != null) {
            String indent = "\t".repeat(indentLevel);
            SkdFieldRoleWriter.write(writer, field.getRole(), field.getRoleAttributes(), indent);
        }

        // Presentation expression.
        if (field.getPresentationExpression() != null) {
            writeElement("presentationExpression",
                    include.resolve(field.getPresentationExpression()));
        }

        // Use restriction.
        Map<String, Boolean> restr = effectiveRestriction(field);
        if (!restr.isEmpty()) {
            writeUseRestriction(restr);
        }

        // Available values (для строк/перечислений).
        if (field.getAvailableValues() != null && !field.getAvailableValues().isEmpty()) {
            writeAvailableValues(field.getAvailableValues());
        }
        if (field.getAppearance() != null && !field.getAppearance().isEmpty()) {
            startElement("appearance");
            for (Map.Entry<String, Object> e : field.getAppearance().entrySet()) {
                writeSettingsParameterValue(e.getKey(), e.getValue());
            }
            endElement();
        }

        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // field
        writer.writeCharacters("\n");
    }

    private Map<String, Boolean> effectiveRestriction(SkdDsl.Field field) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        if (field.getUseRestriction() != null) {
            out.putAll(field.getUseRestriction());
        }
        if (field.getRestrict() != null) {
            for (String flag : field.getRestrict()) {
                switch (flag) {
                    case "noField": out.put("field", false); break;
                    case "noFilter": case "noCondition": out.put("condition", false); break;
                    case "noGroup": out.put("group", false); break;
                    case "noOrder": out.put("order", false); break;
                    default: break;
                }
            }
        }
        return out;
    }

    private void writeUseRestriction(Map<String, Boolean> restr) throws XMLStreamException {
        startElement("useRestriction");
        for (Map.Entry<String, Boolean> e : restr.entrySet()) {
            writeElement(e.getKey(), Boolean.toString(e.getValue()));
        }
        endElement();
    }

    private void writeAvailableValues(List<Object> values) throws XMLStreamException {
        startElement("availableValues");
        for (Object v : values) {
            String text;
            String presentation = null;
            if (v instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) v;
                text = String.valueOf(m.get("value"));
                Object p = m.get("presentation");
                presentation = p != null ? p.toString() : null;
            } else {
                text = String.valueOf(v);
            }
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("item");
            writer.writeCharacters("\n");
            indentLevel++;
            writeElement("value", text);
            if (presentation != null) {
                writeElement("presentation", presentation);
            }
            indentLevel--;
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        endElement();
    }

    // ============================================================
    // Value type (supports composite + nonneg).
    // ============================================================

    /**
     * Записать valueType, поддерживает строку (одиночный тип),
     * список ({@code List<String>}) — составной тип,
     * либо записи вида {@code "decimal(15,2),nonneg"}.
     */
    private void writeValueType(Object dslType) throws XMLStreamException {
        SkdTypeSpec.Parsed parsed = SkdTypeSpec.parse(dslType);

        startElement("valueType");
        for (SkdTypeSpec.Component c : parsed.getComponents()) {
            writeTypeComponent(c);
        }
        endElement();
    }

    private void writeTypeComponent(SkdTypeSpec.Component c) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("v8:Type");
        if (c.isReference() && c.getXmlType().startsWith("d5p1:")) {
            writer.writeNamespace("d5p1", CURRENT_CONFIG_NS);
        }
        writer.writeCharacters(c.getXmlType());
        writer.writeEndElement();
        writer.writeCharacters("\n");

        switch (c.getKind()) {
            case STRING:
                startElement("v8:StringQualifiers");
                writeElement("v8:Length", String.valueOf(c.getLength()));
                writeElement("v8:AllowedLength",
                        Boolean.TRUE.equals(c.getFixedLength()) ? "Fixed" : "Variable");
                endElement();
                break;
            case DECIMAL:
                startElement("v8:NumberQualifiers");
                writeElement("v8:Digits", String.valueOf(c.getDigits()));
                writeElement("v8:FractionDigits", String.valueOf(c.getFractionDigits()));
                writeElement("v8:AllowedSign",
                        Boolean.TRUE.equals(c.getNonNegative()) ? "Nonnegative" : "Any");
                endElement();
                break;
            case DATE:
                startElement("v8:DateQualifiers");
                writeElement("v8:DateFractions", c.getDateFractions());
                endElement();
                break;
            default:
                break;
        }
    }

    // ============================================================
    // Calculated field.
    // ============================================================

    private void writeCalculatedField(SkdDsl.CalculatedField cf) throws XMLStreamException {
        startElement("calculatedField");
        writeElement("dataPath", cf.getName());
        //--agent TASK-174 [07.06.2026 11:35:00]
        //writeElement("field", cf.getName());
        // У calculatedField НЕТ дочернего <field> (1c-dcs-spec.md §6: dataPath+expression+...;
        // канон GBIG PAM: 53 calculatedField, 0 с <field>). Узел был самодеятельностью порта.
        //--agent TASK-174
        if (cf.getTitle() != null) {
            String t = cf.getTitle() instanceof String ? (String) cf.getTitle()
                    : cf.getTitle().toString();
            writeLocalStringType("title", t);
        }
        if (cf.getExpression() != null) {
            writeElement("expression", include.resolve(cf.getExpression()));
        }
        if (cf.getType() != null) {
            writeValueType(cf.getType());
        }
        if (cf.getRole() != null) {
            String indent = "\t".repeat(indentLevel);
            SkdFieldRoleWriter.write(writer, cf.getRole(), cf.getRoleAttributes(), indent);
        }
        if (cf.getUseRestriction() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> restr = (Map<String, Boolean>) cf.getUseRestriction();
            if (!restr.isEmpty()) {
                writeUseRestriction(restr);
            }
        }
        endElement();
    }

    // ============================================================
    // Total field.
    // ============================================================

    private void writeTotalField(SkdDsl.TotalField tf) throws XMLStreamException {
        startElement("totalField");
        writeElement("dataPath", tf.getDataPath());
        writeElement("expression", tf.getExpression());
        if (tf.getGroup() != null) {
            for (String g : tf.getGroup()) {
                writeElement("group", g);
            }
        }
        endElement();
    }

    // ============================================================
    // Parameter (extended flags + availableValues + autoDates).
    // ============================================================

    private void writeParameter(SkdDsl.Parameter param) throws XMLStreamException {
        startElement("parameter");
        writeElement("name", param.getName());

        String titleStr = param.getTitleString();
        if (titleStr != null) {
            writeLocalStringType("title", titleStr);
        }

        if (param.getType() != null) {
            writeValueType(param.getType());
        }

        if (param.getValue() != null) {
            writeParameterValue(param.getValue(), param.getType());
        }

        if (Boolean.TRUE.equals(param.getValueListAllowed())) {
            writeElement("valueListAllowed", "true");
        }
        if (Boolean.TRUE.equals(param.getHidden())) {
            writeElement("availableAsField", "false");
        }
        if (Boolean.FALSE.equals(param.getAvailableAsField())) {
            writeElement("availableAsField", "false");
        }
        if (Boolean.TRUE.equals(param.getDenyIncompleteValues())) {
            writeElement("denyIncompleteValues", "true");
        }
        if (param.getUse() != null) {
            writeElement("use", param.getUse());
        } else if (Boolean.TRUE.equals(param.getAutoDates())) {
            writeElement("use", "Always");
        }

        if (param.getAvailableValues() != null && !param.getAvailableValues().isEmpty()) {
            writeAvailableValues(param.getAvailableValues());
        }

        endElement();

        //**agent TASK-174 [07.06.2026 11:35:00]
        // autoDates — производные параметры ДатаНачала / ДатаОкончания.
        // Канон (skd-dsl-spec.md §6 + _ДемоФайлы Template.xml): производный параметр —
        // это name + valueType(dateTime) + useRestriction true + <expression>&Период.ДатаНачала</expression>.
        // Прежняя эмиссия порта писала несуществующий в платформе элемент <dataPath>
        // и НЕ писала expression — параметры не вычислялись.
        if (Boolean.TRUE.equals(param.getAutoDates())) {
            writeDerivedDateParameter("ДатаНачала", "&" + param.getName() + ".ДатаНачала");
            writeDerivedDateParameter("ДатаОкончания", "&" + param.getName() + ".ДатаОкончания");
        }
    }

    private void writeDerivedDateParameter(String name, String expression) throws XMLStreamException {
        startElement("parameter");
        writeElement("name", name);
        startElement("valueType");
        writeElement("v8:Type", "xs:dateTime");
        startElement("v8:DateQualifiers");
        writeElement("v8:DateFractions", "DateTime");
        endElement();
        endElement();
        writeElement("useRestriction", "true");
        writeElement("expression", expression);
        writeElement("availableAsField", "false");
        endElement();
    }
    //**agent TASK-174

    private void writeParameterValue(Object value, String type) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("value");

        String xsiType = "xs:string";
        if (type != null) {
            if ("StandardPeriod".equals(type) || "v8:StandardPeriod".equals(type)) xsiType = "v8:StandardPeriod";
            else if (type.contains("date")) xsiType = "xs:dateTime";
            else if (type.contains("boolean")) xsiType = "xs:boolean";
            else if (type.contains("decimal") || type.contains("number")) xsiType = "xs:decimal";
        }

        writer.writeAttribute("xsi:type", xsiType);
        if ("v8:StandardPeriod".equals(xsiType)) {
            writer.writeCharacters("\n");
            indentLevel++;
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("v8:variant");
            writer.writeAttribute("xsi:type", "v8:StandardPeriodVariant");
            writer.writeCharacters(value.toString());
            writer.writeEndElement();
            writer.writeCharacters("\n");
            indentLevel--;
            writer.writeCharacters("\t".repeat(indentLevel));
        } else {
            writer.writeCharacters(value.toString());
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    // ============================================================
    // Data set link.
    // ============================================================

    private void writeDataSetLink(SkdDsl.DataSetLink link) throws XMLStreamException {
        if (link.getItems() != null && !link.getItems().isEmpty()) {
            for (SkdDsl.DataSetLinkItem it : link.getItems()) {
                writeSingleLink(link.getSource(), link.getDest(),
                        it.getSourceExpression(), it.getDestExpression(),
                        it.getParameter() != null ? it.getParameter() : link.getParameter(),
                        it.getParameterListAllowed() != null
                                ? it.getParameterListAllowed() : link.getParameterListAllowed());
            }
        } else {
            writeSingleLink(link.getSource(), link.getDest(),
                    link.getSourceExpression(), link.getDestExpression(),
                    link.getParameter(), link.getParameterListAllowed());
        }
    }

    private void writeSingleLink(String source, String dest, String sourceExpr, String destExpr,
                                 String parameter, Boolean parameterListAllowed)
            throws XMLStreamException {
        startElement("dataSetLink");
        if (source != null) writeElement("sourceDataSet", source);
        // TASK-171 (Р-5): платформенный элемент — 'destinationDataSet' (48 вхождений в _Демо),
        // а не 'destDataSet'. Иначе writer расходится с каноном и SkdValidator SKD-109 не видит назначение.
        if (dest != null) writeElement("destinationDataSet", dest);
        if (sourceExpr != null) writeElement("sourceExpression", sourceExpr);
        //**agent TASK-174 [07.06.2026 11:35:00]
        //if (destExpr != null) writeElement("destExpression", destExpr);
        // Парный к destinationDataSet (TASK-171 Р-5) элемент тоже платформенный:
        // 'destinationExpression' (44 вхождения в каноне GBIG PAM, 0 destExpression).
        if (destExpr != null) writeElement("destinationExpression", destExpr);
        //**agent TASK-174
        if (parameter != null) writeElement("parameter", parameter);
        if (parameterListAllowed != null) {
            writeElement("parameterListAllowed", Boolean.toString(parameterListAllowed));
        }
        endElement();
    }

    // ============================================================
    // Settings variant.
    // ============================================================

    private void writeSettingsVariant(SkdDsl.SettingsVariant sv) throws XMLStreamException {
        startElement("settingsVariant");
        writeElement("dcsset:name", sv.getName());

        String presentation = sv.getPresentationString();
        if (presentation != null) {
            writer.writeCharacters("\t\t");
            writer.writeStartElement("dcsset:presentation");
            writer.writeAttribute("xsi:type", "xs:string");
            writer.writeCharacters(presentation);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        if (sv.getSettings() != null) {
            writeSettings(sv.getSettings());
        } else {
            writeEmptySettings();
        }

        endElement();
    }

    private void writeSettings(SkdDsl.Settings settings) throws XMLStreamException {
        writer.writeCharacters("\t\t");
        writer.writeStartElement("dcsset:settings");
        writer.writeNamespace("style", "http://v8.1c.ru/8.1/data/ui/style");
        writer.writeNamespace("sys", "http://v8.1c.ru/8.1/data/ui/fonts/system");
        writer.writeNamespace("web", "http://v8.1c.ru/8.1/data/ui/colors/web");
        writer.writeNamespace("win", "http://v8.1c.ru/8.1/data/ui/colors/windows");
        writer.writeCharacters("\n");
        indentLevel = 3;

        if (settings.getSelection() != null && !settings.getSelection().isEmpty()) {
            startElement("dcsset:selection");
            for (String f : settings.getSelection()) writeSelectionItem(f);
            endElement();
        }
        if (settings.getFilter() != null && !settings.getFilter().isEmpty()) {
            startElement("dcsset:filter");
            for (String f : settings.getFilter()) writeFilterItem(f);
            endElement();
        }
        if (settings.getOrder() != null && !settings.getOrder().isEmpty()) {
            startElement("dcsset:order");
            for (String o : settings.getOrder()) writeOrderItem(o);
            endElement();
        }
        if (settings.getConditionalAppearance() != null && !settings.getConditionalAppearance().isEmpty()) {
            startElement("dcsset:conditionalAppearance");
            for (SkdDsl.ConditionalAppearanceItem it : settings.getConditionalAppearance()) {
                writeConditionalAppearanceItem(it);
            }
            endElement();
        }
        if (settings.getOutputParameters() != null && !settings.getOutputParameters().isEmpty()) {
            writeSettingsParameters("dcsset:outputParameters", settings.getOutputParameters());
        }
        if (settings.getDataParameters() != null) {
            writeDataParameters(settings.getDataParameters());
        }
        if (settings.getStructure() != null && !settings.getStructure().isEmpty()) {
            //**agent TASK-174 [07.06.2026 11:35:00]
            //startElement("dcsset:structure");
            //for (SkdDsl.Structure s : settings.getStructure()) writeStructure(s);
            //endElement();
            // Канон платформы: structure items (<dcsset:item xsi:type="dcsset:StructureItemGroup">)
            // лежат ПРЯМО под dcsset:settings (1c-dcs-spec.md §11.1/§11.8). Обёртки
            // <dcsset:structure> в платформенной сериализации НЕТ (0 вхождений в каноне GBIG PAM) —
            // схема с ней не читается Конфигуратором как структура отчёта.
            for (SkdDsl.Structure s : settings.getStructure()) writeStructure(s);
            //**agent TASK-174
        }

        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // dcsset:settings
        writer.writeCharacters("\n");
    }

    private void writeSelectionItem(String field) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        boolean auto = "Auto".equalsIgnoreCase(field);
        writer.writeAttribute("xsi:type", auto ? "dcsset:SelectedItemAuto" : "dcsset:SelectedItemField");
        writer.writeCharacters("\n");
        if (!auto) {
            indentLevel++;
            writeElement("dcsset:field", field);
            indentLevel--;
        }
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private void writeStructure(SkdDsl.Structure struct) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        String t = struct.getType();
        String xsi = "dcsset:StructureItemGroup";
        if ("table".equalsIgnoreCase(t)) xsi = "dcsset:StructureItemTable";
        else if ("chart".equalsIgnoreCase(t)) xsi = "dcsset:StructureItemChart";
        writer.writeAttribute("xsi:type", xsi);
        writer.writeCharacters("\n");
        indentLevel++;

        if (struct.getName() != null) {
            writeElement("dcsset:name", struct.getName());
        }
        if (struct.getGroupBy() != null && !struct.getGroupBy().isEmpty()) {
            startElement("dcsset:groupItems");
            for (String f : struct.getGroupBy()) {
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeStartElement("dcsset:item");
                writer.writeAttribute("xsi:type", "dcsset:GroupItemField");
                writer.writeCharacters("\n");
                indentLevel++;
                writeElement("dcsset:field", f);
                indentLevel--;
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeEndElement();
                writer.writeCharacters("\n");
            }
            endElement();
        }
        if (struct.getSelection() != null && !struct.getSelection().isEmpty()) {
            startElement("dcsset:selection");
            for (String f : struct.getSelection()) writeSelectionItem(f);
            endElement();
        }
        if (struct.getFilter() != null && !struct.getFilter().isEmpty()) {
            startElement("dcsset:filter");
            for (String f : struct.getFilter()) writeFilterItem(f);
            endElement();
        }
        if (struct.getOrder() != null && !struct.getOrder().isEmpty()) {
            startElement("dcsset:order");
            for (String o : struct.getOrder()) writeOrderItem(o);
            endElement();
        }
        if (struct.getOutputParameters() != null && !struct.getOutputParameters().isEmpty()) {
            writeSettingsParameters("dcsset:outputParameters", struct.getOutputParameters());
        }
        if (struct.getChildren() != null) {
            for (SkdDsl.Structure child : struct.getChildren()) {
                writeStructure(child);
            }
        }

        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private void writeEmptySettings() throws XMLStreamException {
        writer.writeCharacters("\t\t");
        writer.writeStartElement("dcsset:settings");
        writer.writeNamespace("style", "http://v8.1c.ru/8.1/data/ui/style");
        writer.writeNamespace("sys", "http://v8.1c.ru/8.1/data/ui/fonts/system");
        writer.writeNamespace("web", "http://v8.1c.ru/8.1/data/ui/colors/web");
        writer.writeNamespace("win", "http://v8.1c.ru/8.1/data/ui/colors/windows");
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private void writeDefaultSettingsVariant() throws XMLStreamException {
        startElement("settingsVariant");
        writeElement("dcsset:name", "Основной");
        writer.writeCharacters("\t\t");
        writer.writeStartElement("dcsset:presentation");
        writer.writeAttribute("xsi:type", "xs:string");
        writer.writeCharacters("Основной");
        writer.writeEndElement();
        writer.writeCharacters("\n");
        writeEmptySettings();
        endElement();
    }

    // ============================================================
    // LocalStringType helper.
    // ============================================================

    private void writeLocalStringType(String elementName, String text) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement(elementName);
        writer.writeAttribute("xsi:type", "v8:LocalStringType");
        writer.writeCharacters("\n");
        indentLevel++;
        startElement("v8:item");
        writeElement("v8:lang", "ru");
        writeElement("v8:content", text);
        endElement();
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    // ============================================================
    // Filter (with Or/And/Not group support).
    // ============================================================

    private void writeFilterItem(String filterStr) throws XMLStreamException {
        // Поддержка "Field op value" — операторы могут быть многосимвольными.
        FilterParts fp = parseFilterString(filterStr);
        if (fp == null) {
            throw new IllegalArgumentException("Unsupported SKD filter shorthand: " + filterStr);
        }
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeAttribute("xsi:type", "dcsset:FilterItemComparison");
        writer.writeCharacters("\n");
        indentLevel++;

        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:left");
        writer.writeAttribute("xsi:type", "dcscor:Field");
        writer.writeCharacters(fp.field);
        writer.writeEndElement();
        writer.writeCharacters("\n");

        writeElement("dcsset:comparisonType", mapOperatorToComparisonType(fp.op));

        if (fp.value != null && !fp.value.equals("_")) {
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("dcsset:right");
            writer.writeAttribute("xsi:type", detectValueType(fp.value));
            writer.writeCharacters(fp.value);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    /** Парсер строкового фильтра, понимает многосимвольные операторы. */
    static FilterParts parseFilterString(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        String[] ops = {"<>", ">=", "<=", "=", ">", "<", "InHierarchy",
                "notIn", "in", "contains", "filled", "notFilled"};
        // ищем оператор как отдельное слово (с разделителями-пробелами вокруг).
        for (String op : ops) {
            int idx = findOpToken(trimmed, op);
            if (idx >= 0) {
                String left = trimmed.substring(0, idx).trim();
                String right = trimmed.substring(idx + op.length()).trim();
                FilterParts fp = new FilterParts();
                fp.field = left;
                fp.op = op;
                fp.value = right.isEmpty() ? null : right;
                return fp;
            }
        }
        return null;
    }

    private static int findOpToken(String s, String op) {
        int start = 0;
        while (true) {
            int idx = s.indexOf(op, start);
            if (idx < 0) return -1;
            // оператор должен быть отделён пробелом слева (или начало).
            boolean leftOk = idx == 0 || Character.isWhitespace(s.charAt(idx - 1));
            int endPos = idx + op.length();
            boolean rightOk = endPos == s.length() || Character.isWhitespace(s.charAt(endPos));
            if (leftOk && rightOk) return idx;
            start = idx + 1;
        }
    }

    static class FilterParts {
        String field;
        String op;
        String value;
    }

    private String mapOperatorToComparisonType(String op) {
        return switch (op) {
            case "=" -> "Equal";
            case "<>" -> "NotEqual";
            case ">" -> "Greater";
            case ">=" -> "GreaterOrEqual";
            case "<" -> "Less";
            case "<=" -> "LessOrEqual";
            case "in" -> "InList";
            case "notIn" -> "NotInList";
            case "contains" -> "Contains";
            case "filled" -> "Filled";
            case "notFilled" -> "NotFilled";
            case "InHierarchy" -> "InHierarchy";
            default -> "Equal";
        };
    }

    private String detectValueType(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return "xs:boolean";
        if (value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) return "xs:dateTime";
        if (value.matches("-?\\d+(\\.\\d+)?")) return "xs:decimal";
        return "xs:string";
    }

    private void writeOrderItem(String orderStr) throws XMLStreamException {
        if ("Auto".equalsIgnoreCase(orderStr)) {
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeEmptyElement("dcsset:item");
            writer.writeAttribute("xsi:type", "dcsset:OrderItemAuto");
            writer.writeCharacters("\n");
            return;
        }
        String[] parts = orderStr.split("\\s+");
        String field = parts[0];
        String direction = parts.length > 1 ? parts[1] : "asc";

        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeAttribute("xsi:type", "dcsset:OrderItemField");
        writer.writeCharacters("\n");
        indentLevel++;

        writeElement("dcsset:field", field);
        writeElement("dcsset:orderType",
                "desc".equalsIgnoreCase(direction) ? "Desc" : "Asc");

        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    // ============================================================
    // Conditional appearance + filter groups.
    // ============================================================

    private void writeConditionalAppearanceItem(SkdDsl.ConditionalAppearanceItem item) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeCharacters("\n");
        indentLevel++;

        if (item.getSelection() != null && !item.getSelection().isEmpty()) {
            startElement("dcsset:selection");
            for (String f : item.getSelection()) {
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeStartElement("dcsset:item");
                writer.writeAttribute("xsi:type", "dcsset:SelectedItemField");
                writer.writeCharacters("\n");
                indentLevel++;
                writeElement("dcsset:field", f);
                indentLevel--;
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeEndElement();
                writer.writeCharacters("\n");
            }
            endElement();
        }

        if (item.getFilterGroup() != null) {
            startElement("dcsset:filter");
            writeFilterGroup(item.getFilterGroup());
            endElement();
        } else if (item.getFilter() != null && !item.getFilter().isEmpty()) {
            startElement("dcsset:filter");
            for (String f : item.getFilter()) writeFilterItem(f);
            endElement();
        }

        if (item.getAppearance() != null && !item.getAppearance().isEmpty()) {
            startElement("dcsset:appearance");
            for (Map.Entry<String, Object> e : item.getAppearance().entrySet()) {
                writeSettingsParameterValue(e.getKey(), e.getValue());
            }
            endElement();
        }

        String presentation = item.getPresentationString();
        if (presentation != null) {
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("dcsset:presentation");
            writer.writeAttribute("xsi:type", "xs:string");
            writer.writeCharacters(presentation);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        if (item.getViewMode() != null) {
            writeElement("dcsset:viewMode", item.getViewMode());
        }
        if (item.getUserSettingID() != null) {
            writeElement("dcsset:userSettingID",
                    "auto".equalsIgnoreCase(item.getUserSettingID())
                            ? java.util.UUID.randomUUID().toString()
                            : item.getUserSettingID());
        }

        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    @SuppressWarnings("unchecked")
    private void writeFilterGroup(SkdDsl.FilterGroup group) throws XMLStreamException {
        String groupKind = group.getGroup() != null ? group.getGroup() : "And";
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeAttribute("xsi:type", "dcsset:FilterItemGroup");
        writer.writeCharacters("\n");
        indentLevel++;
        writeElement("dcsset:groupType", "Group" + capitalize(groupKind));
        if (group.getItems() != null) {
            for (Object child : group.getItems()) {
                if (child instanceof SkdDsl.FilterGroup) {
                    writeFilterGroup((SkdDsl.FilterGroup) child);
                } else if (child instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) child;
                    if (m.get("group") != null) {
                        // вложенная группа
                        SkdDsl.FilterGroup nested = new SkdDsl.FilterGroup(
                                m.get("group").toString(),
                                (List<Object>) m.get("items"));
                        writeFilterGroup(nested);
                    } else {
                        Object fld = m.get("field");
                        Object op = m.get("op");
                        Object val = m.get("value");
                        String s = (fld != null ? fld.toString() : "") + " "
                                + (op != null ? op.toString() : "") + " "
                                + (val != null ? val.toString() : "");
                        writeFilterItem(s.trim());
                    }
                } else if (child instanceof String) {
                    writeFilterItem((String) child);
                }
            }
        }
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private void writeSettingsParameters(String elementName, Map<String, Object> parameters)
            throws XMLStreamException {
        startElement(elementName);
        for (Map.Entry<String, Object> e : parameters.entrySet()) {
            writeSettingsParameterValue(e.getKey(), e.getValue());
        }
        endElement();
    }

    @SuppressWarnings("unchecked")
    private void writeDataParameters(Object dataParameters) throws XMLStreamException {
        if (dataParameters instanceof String && "auto".equalsIgnoreCase((String) dataParameters)) {
            return;
        }
        startElement("dcsset:dataParameters");
        if (dataParameters instanceof List) {
            for (Object item : (List<Object>) dataParameters) {
                writeDataParameterItem(item);
            }
        } else if (dataParameters instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) dataParameters).entrySet()) {
                writeSettingsParameterValue(e.getKey().toString(), e.getValue());
            }
        }
        endElement();
    }

    @SuppressWarnings("unchecked")
    private void writeDataParameterItem(Object item) throws XMLStreamException {
        if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            Object name = map.get("name") != null ? map.get("name") : map.get("parameter");
            if (name != null) {
                writeSettingsParameterValue(name.toString(), map.get("value"));
            }
            return;
        }
        String text = item.toString();
        String name = text;
        Object value = null;
        int eq = text.indexOf('=');
        if (eq >= 0) {
            name = text.substring(0, eq).trim();
            value = text.substring(eq + 1).replaceAll("@\\w+", "").trim();
        }
        writeSettingsParameterValue(name, value);
    }

    private void writeSettingsParameterValue(String paramName, Object value) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcscor:item");
        writer.writeAttribute("xsi:type", "dcsset:SettingsParameterValue");
        writer.writeCharacters("\n");
        indentLevel++;

        writeElement("dcscor:parameter", paramName);

        String valueStr = value != null ? value.toString() : "";
        String valueType = detectAppearanceValueType(paramName, valueStr);

        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcscor:value");
        writer.writeAttribute("xsi:type", valueType);

        if ("v8:StandardPeriod".equals(valueType)) {
            writer.writeCharacters("\n");
            indentLevel++;
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("v8:variant");
            writer.writeAttribute("xsi:type", "v8:StandardPeriodVariant");
            writer.writeCharacters(valueStr);
            writer.writeEndElement();
            writer.writeCharacters("\n");
            indentLevel--;
            writer.writeCharacters("\t".repeat(indentLevel));
        } else if ("v8:LocalStringType".equals(valueType)) {
            writer.writeCharacters("\n");
            indentLevel++;
            startElement("v8:item");
            writeElement("v8:lang", "ru");
            writeElement("v8:content", valueStr);
            endElement();
            indentLevel--;
            writer.writeCharacters("\t".repeat(indentLevel));
        } else {
            writer.writeCharacters(valueStr);
        }

        writer.writeEndElement();
        writer.writeCharacters("\n");

        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private String detectAppearanceValueType(String paramName, String value) {
        if (value.startsWith("style:") || value.startsWith("web:") || value.startsWith("win:")) {
            return "v8ui:Color";
        }
        if ("LastMonth".equals(value) || "ThisMonth".equals(value) || "ThisYear".equals(value)
                || "LastYear".equals(value) || "ThisQuarter".equals(value) || "LastQuarter".equals(value)) {
            return "v8:StandardPeriod";
        }
        if ("Текст".equals(paramName) || "Заголовок".equals(paramName)) {
            return "v8:LocalStringType";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "xs:boolean";
        }
        return "xs:string";
    }
}
