# Сторонние компоненты и уведомления

Лицензия проекта ([PolyForm Small Business 1.0.0](LICENSE)) распространяется только на
собственный код и тексты 1c-agent-based-dev-framework. Перечисленные ниже файлы
(или их части) заимствованы из сторонних проектов и **остаются под своими
исходными лицензиями**; лицензия проекта их не перелицензирует. `framework*` означает
оба каталога `framework/` и `framework_eng/` (английская версия — перевод).

## Не покрываются лицензией проекта (технические данные)

- `tools/xml-gen/src/test/resources/**` — тестовые фикстуры (XML-выгрузки
  метаданных/форм платформы 1С:Предприятие), используются только для тестов;
  права на исходные объекты принадлежат их правообладателям.
- `framework*/skills/tool-usage/vanessa/vanessa-authoring/references/steps.json` —
  справочная выгрузка библиотеки шагов [Pr-Mex/vanessa-automation](https://github.com/Pr-Mex/vanessa-automation)
  (BSD-3-Clause, © Pautov Leonid); распространяется по лицензии оригинала.

## Apache License 2.0

- [openai/skills](https://github.com/openai/skills) — `framework*/skills/framework-meta/skill-creator/**`
  (SKILL.md, scripts/init_skill.py), `framework*/skills/tool-usage/browser-ui/screenshot/**`,
  `framework*/skills/tool-usage/browser-ui/playwright-interactive/references/snippets.md`; изменены.
- [anthropics/skills](https://github.com/anthropics/skills) — `framework*/skills/framework-meta/skill-creator/scripts/quick_validate.py`,
  `package_skill.py`; изменены.
- [microsoft/playwright-cli](https://github.com/microsoft/playwright-cli) — `framework*/skills/tool-usage/browser-ui/playwright*/**`.

Полный текст Apache License 2.0 — в `LICENSE.txt` соответствующих каталогов;
уведомления — в их `NOTICE.txt`.

## Nikolay-Shirokov/cc-1c-skills — MIT

Источник: <https://github.com/Nikolay-Shirokov/cc-1c-skills>. Код и текст, скопированные или портированные (в т.ч. «1:1»):

- `tools/web-test/browser.mjs`
- `tools/web-test/dom.mjs`
- `tools/web-test/run.mjs` (частично)
- `tools/web-test/test-runner.mjs` (частично)
- `tools/img-grid/grid.py` (частично)
- `docs/reference/cc-1c-skills/**`
- `framework*/skills/tool-usage/platform-data/xml-generation/epf-full/references/epf-bsp.md`
- `framework*/skills/tool-usage/platform-data/xml-generation/skd-dsl/**`
- `framework*/skills/tool-usage/browser-ui/web-test-1c/regress.md`
- `framework*/skills/tool-usage/browser-ui/web-test-1c/recording.md`
- `tools/xml-gen/src/main/java/io/github/onec/xmlgen/form/preset/FormPresetLoader.java` (таблица пресетов, портирована из form-compile.py / presets/erp-standard.json)

```
MIT License

Copyright (c) 2025-2026 Nick Shirokov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## brake71/1c-ssl-skills — MIT

Источник: <https://github.com/brake71/1c-ssl-skills>. Справочные материалы по БСП 3.1.11:

- `framework*/skills/bsl-practices/ssl-patterns/references/bsp-3.1.11/**`

```
MIT License

Copyright (c) 2026 Чекменев Дмитрий Алексеевич

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## yellow-hammer/skills-onescript — MIT

Источник: <https://github.com/yellow-hammer/skills-onescript>. Навыки OneScript:

- `framework*/skills/onescript/**`

```
# MIT License

Copyright (c) 2026 Ivan Karlo

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## vercel-labs/skills — MIT

Источник: <https://github.com/vercel-labs/skills>. Навык find-skills:

- `.claude/skills/find-skills/**`
- `.claude/rules/find-skills.md` (частично)
- `framework*/skills/other/find-skills/**`

```
MIT License

Copyright (c) 2026 Vercel, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Только идеи (код не заимствовался)

- [alonehobo/BslEdit](https://github.com/alonehobo/BslEdit) (`1c-form-viewer`, MIT) — логика проверок форм
  в `xml-gen` реализована независимо; реестр — `framework/docs/third-party-tools.md`.
- AndreevED/1c-ai-feature-dev-workflow, rmartynenko/workflow-dev-1c-claude-code,
  comol/cursor_rules_1c, onec-docker-образы (firstBitMarksistskaya, pravets,
  alexandermyasnikov123) — подходы и практики, без копирования текста/кода.

## Зависимости сборки

- `tools/xml-gen`: Maven-зависимости mdclasses, bsl-common-library (LGPL-3.0) и др. —
  подключаются при сборке, в репозиторий не вендорятся; Gradle Wrapper — Apache-2.0.
- `tools/web-test`: npm-пакет playwright — Apache-2.0.
