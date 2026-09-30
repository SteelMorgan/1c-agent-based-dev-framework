package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.model.MetadataTypeRegistry.TypeDescriptor;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.util.*;

/**
 * Анализ структуры объекта метаданных 1С.
 * Режимы: brief, overview, full.
 * Поддержка 23 типов объектов через MetadataTypeRegistry.
 */
public class MetaInfoPrinter {

    /**
     * @param document  распарсенный XML объекта
     * @param mode      режим: brief, overview, full
     * @param out       поток вывода
     */
    public void print(XmlDocument document, String mode, PrintStream out) {
        XmlNode root = document.getRoot();

        // Navigate: MetaDataObject → <TypeElement>
        XmlNode typeNode = root;
        String detectedType = null;

        if ("MetaDataObject".equals(root.getName())) {
            // Find the first child that is a known metadata type
            for (XmlNode child : root.getChildren()) {
                TypeDescriptor td = MetadataTypeRegistry.byXmlElement(child.getName());
                if (td != null) {
                    typeNode = child;
                    detectedType = td.type();
                    break;
                }
            }
            if (detectedType == null) {
                out.println("ERROR: No recognized metadata type element found inside <MetaDataObject>");
                out.println("  Children: " + childNames(root));
                return;
            }
        } else {
            TypeDescriptor td = MetadataTypeRegistry.byXmlElement(root.getName());
            if (td != null) {
                detectedType = td.type();
            } else {
                out.println("ERROR: Unknown root element <" + root.getName() + ">");
                return;
            }
        }

        switch (mode) {
            case "brief":
                printBrief(typeNode, detectedType, out);
                break;
            case "overview":
                printOverview(typeNode, detectedType, out);
                break;
            case "full":
                printFull(typeNode, detectedType, out);
                break;
            default:
                throw new IllegalArgumentException("Unknown meta info mode: '" + mode
                        + "'. Supported: brief, overview, full");
        }
    }

    // ==================== Brief ====================

    private void printBrief(XmlNode typeNode, String type, PrintStream out) {
        XmlNode props = typeNode.child("Properties");
        if (props == null) { out.println("ERROR: <Properties> not found"); return; }

        String name = safe(props.childText("Name"));
        String synonym = getMlText(props.child("Synonym"));
        int attrCount = countChildren(typeNode, "Attribute");
        int tsCount = countChildren(typeNode, "TabularSection");
        int formCount = countChildren(typeNode, "Form");

        StringBuilder sb = new StringBuilder();
        sb.append(type).append(": ").append(name);
        if (!synonym.isEmpty() && !synonym.equals(name)) sb.append(" — \"").append(synonym).append("\"");

        if (MetadataTypeRegistry.isRegister(type)) {
            int dimCount = countChildren(typeNode, "Dimension");
            int resCount = countChildren(typeNode, "Resource");
            sb.append(" | ").append(dimCount).append(" изм");
            sb.append(", ").append(resCount).append(" рес");
            sb.append(", ").append(attrCount).append(" рекв");
        } else if ("Enum".equals(type)) {
            int valCount = countChildren(typeNode, "EnumValue");
            sb.append(" | ").append(valCount).append(" значений");
        } else if ("Constant".equals(type) || "DefinedType".equals(type)) {
            String typeText = getTypeText(props);
            if (!typeText.isEmpty()) sb.append(" | тип: ").append(typeText);
        } else if ("CommonModule".equals(type)) {
            sb.append(" | ").append(getModuleFlags(props));
        } else {
            sb.append(" | ").append(attrCount).append(" рекв");
            sb.append(", ").append(tsCount).append(" ТЧ");
            sb.append(", ").append(formCount).append(" форм");
        }

        out.println(sb);
    }

    // ==================== Overview ====================

    private void printOverview(XmlNode typeNode, String type, PrintStream out) {
        XmlNode props = typeNode.child("Properties");
        if (props == null) { out.println("ERROR: <Properties> not found"); return; }

        String name = safe(props.childText("Name"));
        String synonym = getMlText(props.child("Synonym"));
        String synStr = !synonym.isEmpty() ? " — \"" + synonym + "\"" : "";
        String uuid = typeNode.attr("uuid");

        out.println(type + ": " + name + synStr);
        if (uuid != null) out.println("  UUID: " + uuid);

        String comment = props.childText("Comment");
        if (comment != null && !comment.isEmpty()) out.println("  Comment: " + comment);

        // Type-specific properties
        printTypeSpecificProps(props, type, out);

        // ChildObjects summary
        XmlNode co = typeNode.child("ChildObjects");
        if (co == null) return;

        out.println();

        // Attributes
        List<XmlNode> attrs = co.children("Attribute");
        if (!attrs.isEmpty()) {
            out.println("  Реквизиты (" + attrs.size() + "):");
            for (XmlNode attr : attrs) {
                printChildSummary(attr, "    ", out);
            }
        }

        // Dimensions + Resources (registers)
        printRegisterFields(co, out);

        // Tabular Sections
        List<XmlNode> tss = co.children("TabularSection");
        if (!tss.isEmpty()) {
            out.println("  Табличные части (" + tss.size() + "):");
            for (XmlNode ts : tss) {
                XmlNode tsProps = ts.child("Properties");
                String tsName = tsProps != null ? safe(tsProps.childText("Name")) : "?";
                XmlNode tsCo = ts.child("ChildObjects");
                int colCount = tsCo != null ? tsCo.children("Attribute").size() : 0;
                out.println("    " + tsName + " (" + colCount + " колонок)");
            }
        }

        // EnumValues
        List<XmlNode> enumValues = co.children("EnumValue");
        if (!enumValues.isEmpty()) {
            out.println("  Значения (" + enumValues.size() + "):");
            for (XmlNode ev : enumValues) {
                XmlNode evProps = ev.child("Properties");
                out.println("    " + (evProps != null ? safe(evProps.childText("Name")) : "?"));
            }
        }

        // Forms
        List<XmlNode> forms = co.children("Form");
        if (!forms.isEmpty()) {
            out.println("  Формы (" + forms.size() + "): " + collectNames(forms));
        }

        // Templates
        List<XmlNode> templates = co.children("Template");
        if (!templates.isEmpty()) {
            out.println("  Макеты (" + templates.size() + "): " + collectNames(templates));
        }

        // Commands
        List<XmlNode> commands = co.children("Command");
        if (!commands.isEmpty()) {
            out.println("  Команды (" + commands.size() + "): " + collectNames(commands));
        }

        // HTTPService: URLTemplates
        List<XmlNode> urlTemplates = co.children("URLTemplate");
        if (!urlTemplates.isEmpty()) {
            out.println("  URL-шаблоны (" + urlTemplates.size() + "):");
            for (XmlNode ut : urlTemplates) {
                XmlNode utProps = ut.child("Properties");
                String utName = utProps != null ? safe(utProps.childText("Name")) : "?";
                String template = utProps != null ? safe(utProps.childText("Template")) : "";
                XmlNode utCo = ut.child("ChildObjects");
                int methodCount = utCo != null ? utCo.children("Method").size() : 0;
                out.println("    " + utName + " (" + template + ") — " + methodCount + " методов");
            }
        }

        // WebService: Operations
        List<XmlNode> operations = co.children("Operation");
        if (!operations.isEmpty()) {
            out.println("  Операции (" + operations.size() + "):");
            for (XmlNode op : operations) {
                XmlNode opProps = op.child("Properties");
                String opName = opProps != null ? safe(opProps.childText("Name")) : "?";
                String retType = opProps != null ? safe(opProps.childText("XDTOReturningValueType")) : "";
                out.println("    " + opName + (retType.isEmpty() ? "" : " → " + retType));
            }
        }

        // Columns (DocumentJournal)
        List<XmlNode> columns = co.children("Column");
        if (!columns.isEmpty()) {
            out.println("  Графы (" + columns.size() + "):");
            for (XmlNode col : columns) {
                printChildSummary(col, "    ", out);
            }
        }

        // AccountingFlags, ExtDimensionAccountingFlags
        List<XmlNode> accFlags = co.children("AccountingFlag");
        if (!accFlags.isEmpty()) {
            out.println("  Признаки учёта (" + accFlags.size() + "): " + collectNames(accFlags));
        }
        List<XmlNode> extDimFlags = co.children("ExtDimensionAccountingFlag");
        if (!extDimFlags.isEmpty()) {
            out.println("  Признаки учёта субконто (" + extDimFlags.size() + "): " + collectNames(extDimFlags));
        }

        // AddressingAttributes (Task)
        List<XmlNode> addrAttrs = co.children("AddressingAttribute");
        if (!addrAttrs.isEmpty()) {
            out.println("  Реквизиты адресации (" + addrAttrs.size() + "):");
            for (XmlNode aa : addrAttrs) {
                printChildSummary(aa, "    ", out);
            }
        }
    }

    // ==================== Full ====================

    private void printFull(XmlNode typeNode, String type, PrintStream out) {
        XmlNode props = typeNode.child("Properties");
        if (props == null) { out.println("ERROR: <Properties> not found"); return; }

        String name = safe(props.childText("Name"));
        String uuid = typeNode.attr("uuid");
        out.println("=== " + type + ": " + name + " ===");
        if (uuid != null) out.println("  UUID: " + uuid);

        // Version
        XmlNode mdRoot = typeNode;
        if (typeNode.getLine() > 0) {
            // typeNode is inside MetaDataObject — we can't easily access parent
        }

        out.println();

        // All scalar properties
        out.println("--- Свойства ---");
        for (XmlNode propChild : props.getChildren()) {
            String pName = propChild.getName();
            String pText = propChild.getText();
            // LocalString (Synonym, Explanation, etc.)
            if (propChild.child("item") != null) {
                String mlText = getMlText(propChild);
                if (!mlText.isEmpty()) {
                    out.println("  " + pad(pName, 35) + mlText);
                }
            }
            // Type composite
            else if ("Type".equals(pName) || "Source".equals(pName)) {
                List<String> types = collectTypeChildren(propChild);
                if (!types.isEmpty()) {
                    out.println("  " + pad(pName, 35) + String.join(", ", types));
                }
            }
            // Scalar
            else if (pText != null && !pText.isEmpty()) {
                out.println("  " + pad(pName, 35) + pText);
            }
            // Collections (RegisterRecords, Owners, BasedOn, InputByString, etc.)
            else if (!propChild.getChildren().isEmpty() && propChild.child("Item") != null) {
                List<String> items = collectItemTexts(propChild);
                if (!items.isEmpty()) {
                    out.println("  " + pad(pName, 35) + String.join(", ", items));
                }
            }
        }

        // ChildObjects detail
        XmlNode co = typeNode.child("ChildObjects");
        if (co == null) return;

        // Attributes
        List<XmlNode> attrs = co.children("Attribute");
        if (!attrs.isEmpty()) {
            out.println();
            out.println("--- Реквизиты (" + attrs.size() + ") ---");
            for (XmlNode attr : attrs) {
                printChildDetail(attr, out);
            }
        }

        // Dimensions
        List<XmlNode> dims = co.children("Dimension");
        if (!dims.isEmpty()) {
            out.println();
            out.println("--- Измерения (" + dims.size() + ") ---");
            for (XmlNode dim : dims) {
                printChildDetail(dim, out);
            }
        }

        // Resources
        List<XmlNode> resources = co.children("Resource");
        if (!resources.isEmpty()) {
            out.println();
            out.println("--- Ресурсы (" + resources.size() + ") ---");
            for (XmlNode res : resources) {
                printChildDetail(res, out);
            }
        }

        // TabularSections
        List<XmlNode> tss = co.children("TabularSection");
        if (!tss.isEmpty()) {
            out.println();
            out.println("--- Табличные части (" + tss.size() + ") ---");
            for (XmlNode ts : tss) {
                XmlNode tsProps = ts.child("Properties");
                String tsName = tsProps != null ? safe(tsProps.childText("Name")) : "?";
                String tsUuid = ts.attr("uuid");
                out.println("  " + tsName + (tsUuid != null ? " [" + tsUuid + "]" : ""));

                XmlNode tsCo = ts.child("ChildObjects");
                if (tsCo != null) {
                    for (XmlNode tsAttr : tsCo.children("Attribute")) {
                        XmlNode ap = tsAttr.child("Properties");
                        if (ap != null) {
                            String aName = safe(ap.childText("Name"));
                            String aType = getTypeText(ap);
                            out.println("    " + pad(aName, 30) + aType);
                        }
                    }
                }
            }
        }

        // EnumValues
        List<XmlNode> enumValues = co.children("EnumValue");
        if (!enumValues.isEmpty()) {
            out.println();
            out.println("--- Значения перечисления (" + enumValues.size() + ") ---");
            for (XmlNode ev : enumValues) {
                XmlNode evProps = ev.child("Properties");
                String evName = evProps != null ? safe(evProps.childText("Name")) : "?";
                String evSyn = evProps != null ? getMlText(evProps.child("Synonym")) : "";
                String evUuid = ev.attr("uuid");
                out.println("  " + evName
                        + (!evSyn.isEmpty() && !evSyn.equals(evName) ? " — \"" + evSyn + "\"" : "")
                        + (evUuid != null ? " [" + evUuid + "]" : ""));
            }
        }

        // Forms
        List<XmlNode> forms = co.children("Form");
        if (!forms.isEmpty()) {
            out.println();
            out.println("--- Формы (" + forms.size() + ") ---");
            for (XmlNode form : forms) {
                XmlNode fp = form.child("Properties");
                String fName = fp != null ? safe(fp.childText("Name")) : "?";
                String fType = fp != null ? safe(fp.childText("FormType")) : "";
                out.println("  " + fName + (!fType.isEmpty() ? " (" + fType + ")" : ""));
            }
        }

        // Templates
        List<XmlNode> templates = co.children("Template");
        if (!templates.isEmpty()) {
            out.println();
            out.println("--- Макеты (" + templates.size() + ") ---");
            for (XmlNode t : templates) {
                XmlNode tp = t.child("Properties");
                String tName = tp != null ? safe(tp.childText("Name")) : "?";
                String tType = tp != null ? safe(tp.childText("TemplateType")) : "";
                out.println("  " + tName + (!tType.isEmpty() ? " (" + tType + ")" : ""));
            }
        }

        // Commands
        List<XmlNode> commands = co.children("Command");
        if (!commands.isEmpty()) {
            out.println();
            out.println("--- Команды (" + commands.size() + ") ---");
            for (XmlNode cmd : commands) {
                XmlNode cp = cmd.child("Properties");
                String cName = cp != null ? safe(cp.childText("Name")) : "?";
                String cGroup = cp != null ? safe(cp.childText("Group")) : "";
                out.println("  " + cName + (!cGroup.isEmpty() ? " [" + cGroup + "]" : ""));
            }
        }

        // URLTemplates (HTTPService)
        List<XmlNode> urlTemplates = co.children("URLTemplate");
        if (!urlTemplates.isEmpty()) {
            out.println();
            out.println("--- URL-шаблоны (" + urlTemplates.size() + ") ---");
            for (XmlNode ut : urlTemplates) {
                XmlNode utProps = ut.child("Properties");
                String utName = utProps != null ? safe(utProps.childText("Name")) : "?";
                String template = utProps != null ? safe(utProps.childText("Template")) : "";
                out.println("  " + utName + " — " + template);
                XmlNode utCo = ut.child("ChildObjects");
                if (utCo != null) {
                    for (XmlNode method : utCo.children("Method")) {
                        XmlNode mp = method.child("Properties");
                        String mName = mp != null ? safe(mp.childText("Name")) : "?";
                        String httpMethod = mp != null ? safe(mp.childText("HTTPMethod")) : "";
                        out.println("    " + httpMethod + " → " + mName);
                    }
                }
            }
        }

        // Operations (WebService)
        List<XmlNode> operations = co.children("Operation");
        if (!operations.isEmpty()) {
            out.println();
            out.println("--- Операции (" + operations.size() + ") ---");
            for (XmlNode op : operations) {
                XmlNode opProps = op.child("Properties");
                String opName = opProps != null ? safe(opProps.childText("Name")) : "?";
                String retType = opProps != null ? safe(opProps.childText("XDTOReturningValueType")) : "";
                out.println("  " + opName + (retType.isEmpty() ? "" : " → " + retType));
                XmlNode opCo = op.child("ChildObjects");
                if (opCo != null) {
                    for (XmlNode param : opCo.children("Parameter")) {
                        XmlNode pp = param.child("Properties");
                        String pName = pp != null ? safe(pp.childText("Name")) : "?";
                        String pType = pp != null ? safe(pp.childText("XDTOValueType")) : "";
                        String dir = pp != null ? safe(pp.childText("TransferDirection")) : "";
                        out.println("    " + pName + ": " + pType + (!dir.isEmpty() ? " [" + dir + "]" : ""));
                    }
                }
            }
        }

        // Columns (DocumentJournal)
        List<XmlNode> columns = co.children("Column");
        if (!columns.isEmpty()) {
            out.println();
            out.println("--- Графы (" + columns.size() + ") ---");
            for (XmlNode col : columns) {
                printChildDetail(col, out);
            }
        }

        // AccountingFlag, ExtDimensionAccountingFlag, AddressingAttribute
        printNamedGroup(co, "AccountingFlag", "Признаки учёта", out);
        printNamedGroup(co, "ExtDimensionAccountingFlag", "Признаки учёта субконто", out);
        printNamedGroup(co, "AddressingAttribute", "Реквизиты адресации", out);
    }

    // ==================== Helpers ====================

    private void printTypeSpecificProps(XmlNode props, String type, PrintStream out) {
        switch (type) {
            case "Catalog":
                printPropIfPresent(props, "Hierarchical", out);
                printPropIfPresent(props, "HierarchyType", out);
                printPropIfPresent(props, "CodeLength", out);
                printPropIfPresent(props, "DescriptionLength", out);
                printOwners(props, out);
                break;
            case "Document":
                printPropIfPresent(props, "NumberLength", out);
                printPropIfPresent(props, "Posting", out);
                printRegisterRecords(props, out);
                break;
            case "InformationRegister":
                printPropIfPresent(props, "InformationRegisterPeriodicity", out);
                printPropIfPresent(props, "WriteMode", out);
                break;
            case "AccumulationRegister":
                printPropIfPresent(props, "RegisterType", out);
                break;
            case "AccountingRegister":
                printPropIfPresent(props, "ChartOfAccounts", out);
                printPropIfPresent(props, "Correspondence", out);
                break;
            case "CalculationRegister":
                printPropIfPresent(props, "ChartOfCalculationTypes", out);
                printPropIfPresent(props, "Periodicity", out);
                break;
            case "ChartOfAccounts":
                printPropIfPresent(props, "MaxExtDimensionCount", out);
                printPropIfPresent(props, "CodeLength", out);
                break;
            case "ChartOfCharacteristicTypes":
                printPropIfPresent(props, "CharacteristicExtValues", out);
                break;
            case "ExchangePlan":
                printPropIfPresent(props, "DistributedInfoBase", out);
                break;
            case "CommonModule":
                out.println("  Флаги: " + getModuleFlags(props));
                break;
            case "ScheduledJob":
                printPropIfPresent(props, "MethodName", out);
                printPropIfPresent(props, "Use", out);
                break;
            case "EventSubscription":
                printPropIfPresent(props, "Event", out);
                printPropIfPresent(props, "Handler", out);
                break;
            case "Constant":
            case "DefinedType":
                String typeText = getTypeText(props);
                if (!typeText.isEmpty()) out.println("  Тип: " + typeText);
                break;
            case "HTTPService":
                printPropIfPresent(props, "RootURL", out);
                break;
            case "WebService":
                printPropIfPresent(props, "Namespace", out);
                break;
            case "DocumentJournal":
                printRegisteredDocuments(props, out);
                break;
            default:
                break;
        }
    }

    private void printPropIfPresent(XmlNode props, String propName, PrintStream out) {
        String val = props.childText(propName);
        if (val != null && !val.isEmpty()) {
            out.println("  " + propName + ": " + val);
        }
    }

    private void printOwners(XmlNode props, PrintStream out) {
        XmlNode owners = props.child("Owners");
        if (owners != null) {
            List<String> items = collectItemTexts(owners);
            if (!items.isEmpty()) {
                out.println("  Owners: " + String.join(", ", items));
            }
        }
    }

    private void printRegisterRecords(XmlNode props, PrintStream out) {
        XmlNode rr = props.child("RegisterRecords");
        if (rr != null) {
            List<String> items = collectItemTexts(rr);
            if (!items.isEmpty()) {
                out.println("  Движения: " + String.join(", ", items));
            }
        }
    }

    private void printRegisteredDocuments(XmlNode props, PrintStream out) {
        XmlNode rd = props.child("RegisteredDocuments");
        if (rd != null) {
            List<String> items = collectItemTexts(rd);
            if (!items.isEmpty()) {
                out.println("  Документы: " + String.join(", ", items));
            }
        }
    }

    private void printRegisterFields(XmlNode co, PrintStream out) {
        List<XmlNode> dims = co.children("Dimension");
        if (!dims.isEmpty()) {
            out.println("  Измерения (" + dims.size() + "):");
            for (XmlNode dim : dims) {
                printChildSummary(dim, "    ", out);
            }
        }
        List<XmlNode> resources = co.children("Resource");
        if (!resources.isEmpty()) {
            out.println("  Ресурсы (" + resources.size() + "):");
            for (XmlNode res : resources) {
                printChildSummary(res, "    ", out);
            }
        }
    }

    private void printChildSummary(XmlNode child, String indent, PrintStream out) {
        XmlNode p = child.child("Properties");
        if (p == null) return;
        String cName = safe(p.childText("Name"));
        String cType = getTypeText(p);
        out.println(indent + pad(cName, 30) + cType);
    }

    private void printChildDetail(XmlNode child, PrintStream out) {
        XmlNode p = child.child("Properties");
        if (p == null) return;
        String cName = safe(p.childText("Name"));
        String cType = getTypeText(p);
        String cUuid = child.attr("uuid");
        String indexing = p.childText("Indexing");
        String fillChecking = p.childText("FillChecking");

        StringBuilder sb = new StringBuilder("  ");
        sb.append(pad(cName, 30)).append(cType);
        List<String> flags = new ArrayList<>();
        if (indexing != null && !"DontIndex".equals(indexing)) flags.add(indexing);
        if (fillChecking != null && !"DontCheck".equals(fillChecking)) flags.add("fill:" + fillChecking);
        // Dimension-specific flags
        String master = p.childText("Master");
        if ("true".equals(master)) flags.add("master");
        String mainFilter = p.childText("MainFilter");
        if ("true".equals(mainFilter)) flags.add("mainFilter");

        if (!flags.isEmpty()) sb.append(" [").append(String.join(", ", flags)).append("]");
        if (cUuid != null) sb.append(" {").append(cUuid).append("}");
        out.println(sb);
    }

    private void printNamedGroup(XmlNode co, String childType, String label, PrintStream out) {
        List<XmlNode> items = co.children(childType);
        if (!items.isEmpty()) {
            out.println();
            out.println("--- " + label + " (" + items.size() + ") ---");
            for (XmlNode item : items) {
                printChildDetail(item, out);
            }
        }
    }

    /** Extract type text from a Properties node. */
    private String getTypeText(XmlNode props) {
        XmlNode typeNode = props.child("Type");
        if (typeNode == null) return "";
        List<String> types = collectTypeChildren(typeNode);
        if (types.isEmpty()) {
            String text = typeNode.getText();
            return text != null ? text.trim() : "";
        }
        return String.join(" | ", types);
    }

    /** Collect v8:Type and v8:TypeSet children. */
    private List<String> collectTypeChildren(XmlNode typeNode) {
        List<String> result = new ArrayList<>();
        for (XmlNode child : typeNode.getChildren()) {
            if ("Type".equals(child.getName()) || "TypeSet".equals(child.getName())) {
                String text = child.getText();
                if (text != null && !text.isEmpty()) result.add(text);
            }
        }
        return result;
    }

    /** Collect text from xr:Item children. */
    private List<String> collectItemTexts(XmlNode node) {
        List<String> result = new ArrayList<>();
        for (XmlNode child : node.getChildren()) {
            if ("Item".equals(child.getName()) || "Field".equals(child.getName())) {
                String text = child.getText();
                if (text != null && !text.isEmpty()) result.add(text);
            }
        }
        return result;
    }

    private String getModuleFlags(XmlNode props) {
        List<String> flags = new ArrayList<>();
        if ("true".equals(props.childText("Server"))) flags.add("Server");
        if ("true".equals(props.childText("ServerCall"))) flags.add("ServerCall");
        if ("true".equals(props.childText("ClientManagedApplication"))) flags.add("Client");
        if ("true".equals(props.childText("ExternalConnection"))) flags.add("ExtConn");
        if ("true".equals(props.childText("Global"))) flags.add("Global");
        if ("true".equals(props.childText("Privileged"))) flags.add("Privileged");
        String reuse = props.childText("ReturnValuesReuse");
        if (reuse != null && !"DontUse".equals(reuse)) flags.add("Reuse:" + reuse);
        return flags.isEmpty() ? "(no flags)" : String.join(", ", flags);
    }

    private int countChildren(XmlNode typeNode, String childName) {
        XmlNode co = typeNode.child("ChildObjects");
        if (co == null) return 0;
        return co.children(childName).size();
    }

    private String collectNames(List<XmlNode> items) {
        List<String> names = new ArrayList<>();
        for (XmlNode item : items) {
            XmlNode p = item.child("Properties");
            if (p != null) {
                String n = p.childText("Name");
                if (n != null) names.add(n);
            }
        }
        return String.join(", ", names);
    }

    private String childNames(XmlNode node) {
        List<String> names = new ArrayList<>();
        for (XmlNode c : node.getChildren()) names.add(c.getName());
        return String.join(", ", names);
    }

    static String getMlText(XmlNode node) {
        if (node == null) return "";
        XmlNode item = node.child("item");
        if (item != null) {
            XmlNode content = item.child("content");
            if (content != null && content.getText() != null && !content.getText().isEmpty()) {
                return content.getText();
            }
        }
        String text = node.getText();
        return text != null ? text.trim() : "";
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s + " ";
        return s + " ".repeat(width - s.length());
    }

    private static String safe(String s) { return s != null ? s : ""; }
}
