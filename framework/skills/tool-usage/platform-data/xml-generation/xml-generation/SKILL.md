---
name: xml-generation
description: "Генерация XML метаданных 1С из компактного JSON DSL. Поддержка 11 доменов — EPF, Form, MXL, SKD, Role, Config, Subsystem, Interface, Meta (23 типа объектов), Extension (CFE) + утилиты (template, help). ~45 операций CLI в формате Designer. Используй при создании конфигураций, внешних обработок, объектов метаданных, форм, ролей, отчётов, печатных форм, расширений."
---

# XML Generation Module

## Установка

`xml-gen` устанавливается автоматически (`python tools/install.py`, требуется JDK 17+).
Если недоступен: `python tools/install.py --install-xml-gen`

## Маршрутизация по типам

| Тип | Команда | Навык |
|-----|---------|-------|
| Внешняя обработка (EPF) | `epf init/add-form/add-template` | [epf-operations](../epf-operations/) |
| Форма (Form) | `form compile/add-attribute/add-element` | [form-dsl](../form-dsl/) |
| Табличный документ (MXL) | `mxl compile` | [mxl-dsl](../mxl-dsl/) |
| СКД (SKD) | `skd compile` | [skd-dsl](../skd-dsl/) |
| Роль (Role) | `role compile` | [role-dsl](../role-dsl/) |
| Конфигурация (CF) | `config init/info/edit/validate` | [config-operations](../config-operations/) |
| Подсистема + Интерфейс | `subsystem compile/edit` | [subsystem-operations](../subsystem-operations/) |
| Метаданные (23 типа) | `meta compile/info/edit` | [meta-operations](../meta-operations/) |
| Расширение (CFE) | `extension init/borrow/diff` | [extension-operations](../extension-operations/) |
| Валидация / edit / утилиты | `validate`, `form add`, `template add`, `help add` | [xml-gen-cli](../xml-gen-cli/) |

**Не используй** когда: нужен формат EDT, нужны DataSetUnion/CalculatedFields в SKD (workaround: вычисления в запросах).

## Ключевые команды

```bash
# EPF
xml-gen epf init --name MyProcessor output/
xml-gen epf add-form --epf MyProcessor --name MainForm output/

# Compile DSL → XML
xml-gen form compile form.json Form.xml
xml-gen mxl compile template.json Template.xml
xml-gen skd compile schema.json Template.xml
xml-gen role compile role.json output/

# Конфигурация
xml-gen config init --name МояКонфигурация output/
xml-gen config info output/

# Метаданные (23 типа)
xml-gen meta compile meta.json output/
xml-gen meta edit Catalogs/Товары --op add-attribute "Вес: Number(15,3)"

# Подсистемы
xml-gen subsystem compile subsystem.json output/

# Расширения (CFE)
xml-gen extension init --name МоёРасширение --config output/ output_ext/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"

# Универсальные
xml-gen form add output/Catalogs/Товары MainForm
xml-gen template add output/Catalogs/Товары PrintForm --type spreadsheet
xml-gen help add output/Catalogs/Товары
xml-gen validate form Form.xml
```

## Ограничения

- **Только Designer формат** — EDT будет добавлен позже
- **SKD ~90%** — нет DataSetObject/Union, CalculatedFields → workaround через запросы

## Правильно / Неправильно

```bash
# ❌ epf init без --name и output_dir → "--name is required"
xml-gen epf init MyProcessor

# ✅ --name и output_dir обязательны
xml-gen epf init --name MyProcessor output/
```

```bash
# ❌ role compile с файлом на выход (создаётся структура каталогов)
xml-gen role compile role.json Roles/МояРоль.xml

# ✅ output_dir → Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/epf-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/form-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/role-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/xml-gen-cli/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/config-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/subsystem-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/meta-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/extension-operations/SKILL.md
metadata:
  category: 1c-development
  version: "1.0"
---
