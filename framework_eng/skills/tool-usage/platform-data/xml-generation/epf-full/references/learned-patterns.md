# Learned Patterns — epf-full

Verified practices and anti-patterns collected from real tasks.
`confirmed` — verified rule, `candidate` — requires re-validation.

---

```
status: candidate
область: сборка бинарной .epf из дерева исходников xml-gen (внешняя обработка с управляемой формой)
приём: Полный цикл: (1) epf init → (2) epf add-form --default (регистрация формы в ChildObjects
       и DefaultForm) → (3) form compile поверх скелета (DSL формы) → (4) пакетирование бинаря
       Конфигуратором из дерева исходников:
       `1cv8 DESIGNER /S <сервер\база> /N <пользователь> /P <пароль> /DisableStartupDialogs
        /LoadExternalDataProcessorOrReportFromFiles <корневой.xml> <выход.epf>`.
       Designer-load компилирует модули — это и есть СИЛЬНАЯ проверка (EXIT=0 = принято платформой).
       При правке ТОЛЬКО тела модуля формы (не разметки) достаточно повторить шаг 4.
антиприём: НЕ полагаться на `xml-gen validate` как достаточную проверку .epf/формы — валидатор
           пропускает структурные дефекты разметки формы, которые валит уже Designer-load (EXIT=1
           "Ошибка загрузки документа"). Финальная проверка пригодности = Designer-load + рантайм-прогон,
           а не только validate.
почему: бинарь .epf собирается ТОЛЬКО Конфигуратором (LoadExternalDataProcessorOrReportFromFiles)
        из дерева исходников; xml-gen готовит дерево, но не пакует. Валидатор xml-gen структурно
        слабее загрузчика платформы — "No issues" на структурно битой форме возможно.
        Дефекты конкретной версии генератора фиксировать в проектном реестре дефектов инструмента,
        а не считать каноном.
источник: внешняя обработка с управляемой формой, цикл xml-gen→Designer, 2026-06
```
