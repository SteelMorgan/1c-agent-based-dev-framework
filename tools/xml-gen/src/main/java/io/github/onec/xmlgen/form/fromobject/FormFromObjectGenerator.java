package io.github.onec.xmlgen.form.fromobject;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.generator.AccumulationRegisterFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.CatalogFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.ChartOfAccountsFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.ChartOfCharacteristicTypesFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.DataProcessorFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.DocumentFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.ExchangePlanFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.GenericFormGenerator;
import io.github.onec.xmlgen.form.fromobject.generator.InformationRegisterFormGenerator;
import io.github.onec.xmlgen.form.preset.FormPreset;
import io.github.onec.xmlgen.form.preset.FormPresetLoader;
import io.github.onec.xmlgen.model.MetadataTypeRegistry;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Точка входа для режима {@code form compile --from-object}.
 */
public class FormFromObjectGenerator {

    private final FormPresetLoader presetLoader = new FormPresetLoader();
    private final ObjectMetaReader metaReader = new ObjectMetaReader();

    public FormDsl generate(Path objectXml,
                            Path outputPath,
                            String presetName,
                            Path presetDir) {
        if (presetName == null || presetName.isEmpty()) presetName = "erp-standard";

        // 1) Resolve object path if missing
        Path resolvedObject = objectXml != null ? objectXml : resolveObjectPath(outputPath);
        if (resolvedObject == null || !Files.isRegularFile(resolvedObject)) {
            throw new FromObjectException("Cannot locate object XML. Searched next to 'Forms/' in OutputPath. " +
                    "Use --object <path> to specify explicitly.");
        }

        // 2) Parse metadata
        ObjectMeta meta = metaReader.read(resolvedObject);

        // 3) Resolve purpose
        String purpose = PurposeResolver.resolve(outputPath, meta.type);

        // 4) Load preset
        FormPreset preset = presetLoader.load(presetName, outputPath, presetDir);

        // 5) Dispatch
        return dispatch(meta, preset, purpose);
    }

    private FormDsl dispatch(ObjectMeta meta, FormPreset preset, String purpose) {
        switch (meta.type) {
            case "Catalog":
                return new CatalogFormGenerator().generate(meta, preset, purpose);
            case "Document":
                return new DocumentFormGenerator().generate(meta, preset, purpose);
            case "InformationRegister":
                return new InformationRegisterFormGenerator().generate(meta, preset, purpose);
            case "AccumulationRegister":
                return new AccumulationRegisterFormGenerator().generate(meta, preset, purpose);
            case "ChartOfCharacteristicTypes":
                return new ChartOfCharacteristicTypesFormGenerator().generate(meta, preset, purpose);
            case "ExchangePlan":
                return new ExchangePlanFormGenerator().generate(meta, preset, purpose);
            case "ChartOfAccounts":
                return new ChartOfAccountsFormGenerator().generate(meta, preset, purpose);
            case "DataProcessor":
            case "Report":
                return new DataProcessorFormGenerator().generate(meta, preset, purpose);
            default:
                MetadataTypeRegistry.TypeDescriptor descriptor = MetadataTypeRegistry.get(meta.type);
                if (descriptor != null && descriptor.hasForms()) {
                    return new GenericFormGenerator().generate(meta, preset, purpose);
                }
                throw new FromObjectException("Unsupported object type: " + meta.type);
        }
    }

    /**
     * По OutputPath вида {@code .../<TypePlural>/<ObjectName>/Forms/<FormName>/Ext/Form.xml}
     * найти {@code .../<TypePlural>/<ObjectName>/<ObjectName>.xml}.
     */
    private Path resolveObjectPath(Path outputPath) {
        if (outputPath == null) return null;
        Path p = outputPath.toAbsolutePath().normalize();
        int count = p.getNameCount();
        for (int i = count - 1; i >= 1; i--) {
            if ("Forms".equalsIgnoreCase(p.getName(i).toString())) {
                // objectDir = parent of "Forms/"
                Path objectDir = p.subpath(0, i);
                // subpath drops root — restore it if absolute
                Path root = p.getRoot();
                Path full = root != null ? root.resolve(objectDir) : objectDir;
                String objectName = p.getName(i - 1).toString();
                Path candidate = full.resolve(objectName + ".xml");
                if (Files.isRegularFile(candidate)) return candidate;
                return candidate; // let caller throw for better error
            }
        }
        return null;
    }
}
