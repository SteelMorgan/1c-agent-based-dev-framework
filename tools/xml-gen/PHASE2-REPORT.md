# Phase 2 Implementation Report

**Date:** 2026-02-12  
**Status:** ✅ COMPLETED (Designer format)

## Summary

Phase 2 (Role/Rights) реализован для Designer формата. Модуль позволяет создавать роли 1С из JSON DSL с поддержкой пресетов прав.

## Implemented Features

### Role Compile
Генерация роли из JSON DSL.

**Command:**
```bash
java -jar xml-gen.jar role compile <input.json> <output_dir>
```

**Generated:**
- Roles/<Name>.xml (метаданные роли с UUID)
- Roles/<Name>/Ext/Rights.xml (права доступа)

### Presets
Встроенные пресеты прав:

| Пресет | Описание | Права |
|--------|----------|-------|
| `view` | Просмотр | Read, View, InputByString (для Catalog/Document), Use+View (для DataProcessor/Report) |
| `edit` | Полное редактирование | CRUD + Interactive* + Posting (для Document) |
| `full` | Все права | Расширенная версия edit |

### Supported Object Types
- Configuration
- Catalog
- Document
- DataProcessor
- Report
- InformationRegister
- AccumulationRegister
- И другие типы метаданных 1С

## JSON DSL Format

```json
{
  "name": "МенеджерПродаж",
  "synonym": "Менеджер продаж",
  "comment": "Роль для менеджеров отдела продаж",
  "setForNewObjects": false,
  "setForAttributesByDefault": true,
  "independentRightsOfChildObjects": false,
  "objects": [
    {
      "name": "Document.РеализацияТоваровУслуг",
      "preset": "edit"
    },
    {
      "name": "Catalog.Контрагенты",
      "preset": "view"
    },
    {
      "name": "DataProcessor.ЗагрузкаДанных",
      "rights": {
        "Use": true,
        "View": true
      }
    }
  ]
}
```

## Technical Details

### Architecture
- **DSL Model:** RoleDsl.java (JSON DSL с Jackson)
- **Writer:** RoleWriter.java (генератор XML)
- **Presets:** Встроенная логика в RoleWriter.getPresetRights()

### Key Components
- **UUID Generation:** UuidGenerator.generate() для роли
- **Namespaces:** 
  - Metadata: `http://v8.1c.ru/8.3/MDClasses`
  - Rights: `http://v8.1c.ru/8.2/roles` (исторически 8.2)
- **BOM:** UTF-8 BOM для обоих файлов
- **Indentation:** Табы (\t)

### XML Structure Compliance
- ✅ Корректные namespaces
- ✅ version="2.17"
- ✅ Глобальные флаги (setForNewObjects, setForAttributesByDefault, independentRightsOfChildObjects)
- ✅ Объекты с правами (<object><name><right>)
- ✅ Поддержка RLS (restrictionByCondition) — структура готова

## Testing

### Manual Test
```bash
# Создать JSON DSL
cat > test-role.json <<EOF
{
  "name": "МенеджерПродаж",
  "synonym": "Менеджер продаж",
  "objects": [
    {"name": "Document.РеализацияТоваровУслуг", "preset": "edit"},
    {"name": "Catalog.Контрагенты", "preset": "view"}
  ]
}
EOF

# Сгенерировать роль
java -jar xml-gen.jar role compile test-role.json output/
```

**Result:** ✅ Роль создана, структура корректна

### Files Generated
```
output/
└── Roles/
    └── МенеджерПродаж/
        ├── МенеджерПродаж.xml          (метаданные)
        └── Ext/
            └── Rights.xml              (права)
```

### Preset "edit" for Document
Генерирует 18 прав:
- CRUD: Read, Insert, Update, Delete
- View/Edit: View, Edit
- Interactive: InteractiveInsert, InteractiveDelete, InteractiveSetDeletionMark, InteractiveClearDeletionMark, InteractiveDeleteMarked
- Posting: Posting, UndoPosting, InteractivePosting, InteractivePostingRegular, InteractiveUndoPosting, InteractiveChangeOfPosted
- Other: InputByString

### Preset "view" for Catalog
Генерирует 3 права:
- Read, View, InputByString

## Known Limitations

1. **EDT Format:** Не реализован (только Designer)
2. **RLS Templates:** Структура готова, но не тестировалась
3. **Preset "full":** Упрощённая реализация (= edit)
4. **Русские синонимы:** Не реализованы (только английские имена прав)
5. **Nested Objects:** Права на реквизиты/команды не реализованы

## Code Statistics

- **Java Files:** 2 (RoleDsl, RoleWriter)
- **Lines of Code:** ~350
- **Test Coverage:** Manual testing only

## Next Steps

1. **Automated Tests** — RoleWriterTest с roundtrip
2. **RLS Support** — тестирование restrictionTemplate
3. **Nested Objects** — права на Attribute, Command и т.д.
4. **Russian Synonyms** — поддержка русских имён типов/прав
5. **EDT Format** — реализация для EDT

## References

- **Spec:** `src_temp/cc-1c-skills/docs/1c-role-spec.md`
- **DSL Spec:** `src_temp/cc-1c-skills/docs/role-dsl-spec.md`
- **Fixtures:** `src_temp/mdclasses/src/test/resources/ext/designer/mdclasses/src/cf/Roles/`
