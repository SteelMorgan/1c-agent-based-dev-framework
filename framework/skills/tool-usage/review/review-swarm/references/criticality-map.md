# Карта критичности — эскалатор тарифа review-swarm (FR-15, TD §8.1)

Назначение. Эта карта — эскалатор тарифа (control-plane), а не таксономия
репозитория: critical объявляется только на путях-владельцах исполняемого или
governance-инварианта, на наименьшей гранулярности владения. Пути
diff/focused-набора, пересекающиеся с паттернами ниже, ВСЕГДА идут полным
роем (AC-15); серая зона (нет пересечения) — рубрика Оркестратора. Привязка
паттерна к линзе даёт forced-линзу критичного пути (FR-11: линза назначается
принудительно, наблюдение помечается forced и исключается из
strength-статистики, TD §5.7).

Провенанс. Вердикт консилиума CMAP-01 (сессия cons-20260730-065455-3dc36dc4,
2026-07-30, ADOPTED с поправками red-team RT-01..RT-06, синтез E1–E12):
tasks/agentic-operations/CMAP-01-criticality-map/.context/verdict-consilium.md.
Слой обоснований каждого паттерна (владелец инварианта, обе цены ошибки),
очередь кандидатур и полная политика обновления —
tasks/agentic-operations/CMAP-01-criticality-map/map-proposal.md. Этот файл —
исполняемый артефакт с минимальной грамматикой: единственный буллет-список
ниже — все critical-паттерны; генератор в него никогда не пишет (E2).

Известные слепые пятна и топология (обязательный контекст при регенерации):

Паттерн secrets/** — поимённо спящий: каталог gitignored, в git-дереве его
нет, генератором он невосстановим; правило regular-file к нему НЕ применяется
по решению владельца (RT-03); удаление паттерна при регенерации запрещено
(E10). Действует принцип: отсутствие в дереве не равно отсутствию инварианта.

Зеркала агентного фреймворка: .claude — реально исполняемые байты (regular
files по git object mode, режимы 100644/100755), .codex/skills — симлинки
(mode 120000) и глобами НЕ покрываются (E9, RT-01). Режимы git object mode
перепроверяются при каждой регенерации карты.

Freshness-hash входного снапшота (E12): sha256 списка областей верхнего
уровня рабочего дерева, команда
git ls-files | cut -d/ -f1 | sort -u | sha256sum, усечён до 16 hex:
7b7c2ab70c4339bc (вычислен и зафиксирован 2026-07-30 на ветке
agent-cons-01-agent-consilium-20260727). Расхождение хеша — триггер
пересмотра карты и warning в doctor, не автоповышение.

Формат. Маркированный список с паттерном в backticks — паттерн обязательного
полного роя; разделитель привязки линзы — стрелка (юникод или ASCII
дефис-больше, F-015); любой другой trailing-текст на строке паттерна
отклоняется fail-closed при загрузке. Семантика glob: префикс **/ — любой
префикс, включая пустой (корневые каталоги матчатся); сравнение по
нормализованным путям (обратные слэши приводятся к прямым, без ./). Линзы —
только из каталога code-review (security, correctness, concurrency,
performance, data-contracts, tests). Точный duplicate glob с разными линзами —
fail-closed (E8). Пересекающиеся разные glob'ы разрешаются порядком следования
(первая подошедшая привязка выигрывает), поэтому узкий паттерн
managed-secret-families.yaml стоит раньше широкого infra/platform-registry/**
(RT-05).

Политика обновления (кратко; полная версия — в map-proposal.md). Триггеры
пересмотра: новая область верхнего уровня, мердж ветки с новыми областями,
периодический аудит по track record (E12). Guard блокирует только
неклассифицированные области, затронутые данным diff'ом; прочие — warning в
очередь классификации (RT-06). Автоповышение уровня запрещено. Изменение
зеркала легитимно, только если байт-в-байт воспроизводится повторным прогоном
tools/agent-framework-i18n-sync.py от текущего .framework (RT-03). Перед
каждой записью в этот файл — обязательный load-test ядром (E2). Покрытие
регулируется направленным инвариантом: новая карта не увеличивает долю
full-swarm относительно действующей на том же окне diff без именованной
причины в map-proposal.md (E4).

## Паттерны обязательного полного роя

- `**/auth*/**` → security
- `**/crypto*/**` → security
- `**/payment*/**` → data-contracts
- `**/billing*/**` → data-contracts
- `**/migrations/**` → data-contracts
- `.framework/skills/review-swarm/**` → security
- `.framework/skills/review-harness/**` → security
- `.claude/skills/review-swarm/**` → security
- `.claude/skills/review-harness/**` → security
- `packages/contracts/**` → data-contracts
- `infra/platform-registry/managed-secret-families.yaml` → security
- `infra/platform-registry/**` → data-contracts
- `packages/pdn-core/**` → security
- `packages/delegated-actor-authority/**` → security
- `services/identity/**` → security
- `services/platform-secret-provisioner/**` → security
- `infra/openbao/**` → security
- `infra/pii-vault/**` → security
- `services/data-core/**` → data-contracts
- `agents-fleet/crypto/**` → security
- `plugins/pdn-152fz-compliance-agent/**` → security
- `secrets/**` → security

Код самого gate-протокола (review-swarm, review-harness) в обоих зеркалах —
в карте (F-014, E9): его дефект тихо отключает обязательный cross-family gate
для всех остальных артефактов, поэтому серой зоны здесь быть не может; линза
security первична, потому что этот код реализует security-инвариант
cross-family контроля (NFR-01). Пути .codex/skills/review-* в список не
входят сознательно: по git object mode это симлинки, резолвящиеся в
.claude-пути, которые уже покрыты.
