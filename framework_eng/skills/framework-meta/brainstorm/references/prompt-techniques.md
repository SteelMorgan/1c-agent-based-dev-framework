# Catalog of prompt techniques for brainstorming

> Reference for the `brainstorm` skill. Here are techniques for generation, validation, and convergence that work in prompt-only mode (without model fine-tuning). For each, it lists: essence, when it fits, usage template, typical mistakes.

## Technique map

| Goal | Techniques |
|------|--------|
| Expand the space | Morphological analysis, analogies, reversal, provocation, worst-idea, SCAMPER, TRIZ |
| Understand the task more deeply | 5 Whys, JTBD reformulation, Cynefin classification, Ishikawa |
| Change perspective | Six Thinking Hats, persona switching, role injection, time/scale shift |
| Stress test | Pre-mortem, assumption inversion, red team, devil's advocate |
| Narrow the choice | Weighted criteria matrix, MMR/maximin, paired comparison |
| Reduce LLM mode collapse | Independent generation, axis-explicit prompting, persona switching, completion-mode prefix, logit anchoring through examples |

---

## Divergent techniques (space expansion)

### 1. Morphological analysis (Zwicky)

**Essence:** break the solution into N independent axes, each with a list of values. Solution = one point in an N-dimensional space.

**When:** there is a multidimensional solution space, guaranteed coverage is needed. Basic technique of the `brainstorm` skill.

**Template prompt:**
```
Задача: <X>.
Шаг 1: предложи 3–5 осей, по которым решения этой задачи могут принципиально различаться.
Для каждой оси — название, объяснение, 2–4 значения.
Шаг 2: проверь ортогональность — обоснуй, что выбор по одной оси не предопределяет другую.
Если предопределяет — объедини оси.
```

**Typical mistake:** evaluation axes ("good vs bad"). These are not axes, but criteria - they belong in Phase 5.

---

### 2. SCAMPER

**Essence:** 7 operators over an existing solution: **S**ubstitute, **C**ombine, **A**dapt, **M**odify, **P**ut to other use, **E**liminate, **R**everse.

**When:** there is already a starting idea/prototype/MVP, need to broaden it with variations. Not for "from scratch" tasks.

**Template:**
```
Текущее решение: <описание>.
Применимо к каждому из 7 операторов SCAMPER предложи минимум одну
содержательную вариацию. Если оператор не применим — обоснуй почему.
```

**Typical mistake:** use as a checkbox list "for the sake of it". If an operator does not yield an interesting variant, honestly say "not applicable" instead of inventing a weak variation.

---

### 3. Analogies from another domain / biomimicry

**Essence:** "how would this task be solved in [biology / military logistics / music / another industry]".

**When:** the most powerful anti-mode-collapse technique. Helps get out of a local optimum, especially when the LLM is fixated on the "standard" solution from its own domain.

**Template:**
```
Задача: <X>.
Назови 5 несвязанных доменов (биология, военная логистика, классическая музыка,
средневековая торговля, спорт), в которых решают структурно похожую задачу.
Для каждого — как именно решают и какой механизм/приём можно перенести.
```

**Typical mistake:** use a "neighboring" domain (for example, for a backend task - frontend). Weak effect. Really distant domains are needed.

---

### 4. Assumption inversion / reversal

**Essence:** list the implicit assumptions of the task, invert each one, see what survives.

**When:** the task is phrased "as usual", and there is a suspicion that some conditions were accepted blindly. Good at exposing constraints that are actually unnecessary.

**Template:**
```
Задача: <X>.
Шаг 1: выпиши 5–7 неявных допущений в формулировке задачи (то, что мы
молча приняли как истинное).
Шаг 2: для каждого допущения — сформулируй инверсию.
Шаг 3: если инверсия истинна — какие новые решения становятся возможны?
```

**Typical mistake:** invert only what is convenient to invert. The most valuable assumptions are the ones that seem "self-evident".

---

### 5. First-principles decomposition

**Essence:** strip the task down to the domain axioms (physics, economics, basic user properties), then assemble the solution again.

**When:** when standard solutions are irritatingly similar; when you want not "like others"; when legacy-thinking is suspected. The opposite of analogies.

**Template:**
```
Задача: <X>.
Шаг 1: какие фундаментальные ограничения этой задачи (физические, информационные,
экономические, человеческие)? Не «как принято решать», а что объективно нельзя обойти.
Шаг 2: исходя только из этих ограничений, какое минимальное решение возможно?
Шаг 3: какие «стандартные» элементы решения на самом деле не следуют из ограничений
и являются конвенциональным наследием?
```

**Typical mistake:** call conventions "principles" ("we've always had a REST API"). Test: if one person can change it by a decision, it is not a principle.

---

### 6. Provocation / PO (de Bono)

**Essence:** intentionally absurd statement as a seed ("what if we had no database", "what if the answer had to arrive in 0 ms", "what if every request were handled manually").

**When:** cheaply breaks through a local minimum. Good when generation falls into obvious options.

**Template:**
```
Задача: <X>.
Допустим, абсурдное условие: <провокация>.
Не отбрасывай его сразу. Опиши, как бы выглядело решение в этих условиях.
Какие из элементов такого решения можно перенести в реалистичный сценарий?
```

**Typical mistake:** not absurd enough a provocation ("what if the budget is 20% smaller" is not a provocation, it's a variation). A violation of a basic assumption is needed.

---

### 7. Worst possible idea

**Essence:** generate deliberately bad ideas, then invert them.

**When:** removes the fear of "saying something stupid", expands boundaries. Especially useful at the beginning of a session when the model is being cautious.

**Template:**
```
Задача: <X>.
Назови 5 максимально плохих решений этой задачи. Чем хуже — тем лучше.
Для каждого — что именно делает его плохим.
Затем: для 2–3 самых плохих — какая инверсия плохих свойств даёт интересное решение?
```

**Typical mistake:** generate "weak" ideas instead of "bad" ones. Weak ideas are boring; bad ones are funny and productive.

---

### 8. TRIZ (simplified: contradictions)

**Essence:** formulate a conflict of parameters ("we want X, but that breaks Y"), search for solutions by resolving the contradiction.

**When:** engineering tasks with an explicit trade-off. "We want speed, but need reliability", "we want flexibility, but need simplicity".

**Template:**
```
Задача: <X>.
Шаг 1: сформулируй центральное противоречие в формате «хотим увеличить A,
но это ухудшает B».
Шаг 2: предложи решения через типовые приёмы:
  - разделение в пространстве (A и B в разных частях системы)
  - разделение во времени (A в одну фазу, B в другую)
  - разделение по условию (A для одного класса случаев, B для другого)
  - переход на другой уровень (A на уровне системы, B на уровне подсистемы)
  - инверсия (сделать B полезным свойством)
```

**Typical mistake:** immediately look for a "compromise". TRIZ says the real solution **resolves** the contradiction, it does not balance it.

---

## Framing techniques (Phase 0)

### 9. 5 Whys

**Essence:** ask "why" five times to get to the root task.

**When:** the request sounds like a solution, not a task ("make button X"). Dangerous for action bias.

**Template:**
```
Запрос пользователя: <X>.
Задай 5 уровней вопроса «почему» (или «зачем»), уходя от сформулированного
решения к корневой потребности. После 5-го уровня — переформулируй задачу
как корневую потребность, а не как способ её решения.
```

---

### 10. Jobs-to-be-Done

**Essence:** "what job is the user hiring the solution to do?" Shift from "what to build" to "what needs to be achieved".

**When:** product tasks, UX solutions, edge cases of integrations. Helps understand that different technical solutions can do the same job equally well.

**Template:**
```
Когда <ситуация>, я хочу <мотивация>, чтобы <ожидаемый результат>.
Сформулируй 2–3 версии этой формулы для запроса <X>. Выбери ту, которая
лучше отражает реальную потребность.
```

---

### 11. Cynefin classification

**Essence:** classify the task: clear / complicated / complex / chaotic. The approach depends on the class.

**When:** before starting work - to understand whether brainstorming is needed at all.

| Class | Sign | Approach |
|-------|---------|--------|
| Clear | One correct answer exists and is known | Apply best practice, brainstorming not needed |
| Complicated | A solution exists, but it requires expert analysis | Short brainstorming, emphasis on analysis |
| Complex | The solution emerges only through experiments | Full brainstorming, emphasis on divergence and hypotheses |
| Chaotic | First it needs to be stabilized | Not brainstorming yet, need to act and observe |

---

## Perspective-shift techniques

### 12. Six Thinking Hats (de Bono)

**Essence:** forced rotation through six perspectives:
- **White:** facts, data, what we know
- **Red:** emotions, intuition, no justification
- **Black:** risks, what could go wrong
- **Yellow:** benefits, optimistic scenario
- **Green:** creativity, new ideas
- **Blue:** process, meta-reflection

**When:** a discussion with a tendency toward "everyone for" or "everyone against"; group dynamics; for LLMs - remove the "assistant voice" through explicit roles.

**Template:**
```
Задача: <X>.
Пройдись последовательно в 6 ролях. Для каждой — отдельный абзац.
Не смешивай роли в одном абзаце.
[список ролей с описаниями]
```

---

### 13. Persona switching / role injection

**Essence:** explicitly assign the LLM a role with a viewpoint opposite the default one.

**When:** the default "assistant voice" is the main source of mode collapse in open-ended tasks. Persona prompting changes the distribution.

**Template:**
```
Ты — <роль с конкретными чертами: суровый CTO с 20 годами в финтехе /
скептичный investor / параноик-безопасник / pragmatic product manager
с дедлайном через неделю>. С этой позиции — <вопрос>.
```

**Typical mistake:** a vague persona ("creative person"). Specific traits that shape the viewpoint are needed.

---

### 14. Time / scale shift

**Essence:** "what would this task look like in 10 years / at 1000x scale / for one user instead of a million".

**When:** when the solution depends heavily on the current scale and there is suspicion that we are optimizing for a local point.

**Template:**
```
Решение: <X>.
Опиши, как оно будет работать (или ломаться) при:
- 10x росте нагрузки
- 100x росте нагрузки
- сжатии в 10 раз (нишевый сервис вместо массового)
- через 5 лет, если домен останется тем же
- через 5 лет, если рядом появится новая технология <Y>
```

---

## Stress test (Phase 4)

### 15. Pre-mortem (Klein)

**Essence:** "imagine we did this and in a year it failed - why?" Stronger than a normal risk list because it shifts the frame from "what could go wrong" to "what already went wrong".

**When:** before locking in a solution. Works best on a shortlist (Phase 5), but can also be applied to each hypothesis after Phase 3.

**Template:**
```
Допустим, мы выбрали решение <X> и реализовали его. Прошёл год.
Решение признано провалом. Опиши 5 наиболее вероятных причин провала
в порядке убывания вероятности.
Для каждой — какой ранний сигнал мы могли бы заметить до полного провала.
```

---

### 16. Assumption inversion (as stress test)

The same as technique No. 4, but applied to the **chosen** solution rather than the task. "What assumption does this solution rest on? What if it is wrong?"

---

### 17. Red team / devil's advocate

**Essence:** a separate challenge with the task "tear the solution to pieces."

**When:** for finalists before the final selection. Especially valuable as a **separate challenge** (without generation context) - otherwise the same agent defends what it just proposed.

**Template:**
```
Решение: <X> (без контекста, как оно появилось).
Ты — red team. Твоя единственная задача — найти причины не делать это.
Не балансируй с плюсами. Назови 7 серьёзных проблем, отсортируй
по убыванию серьёзности.
```

---

## Convergence (Phase 5)

### 18. Weighted criteria matrix

**Essence:** criteria × weights × scores = weighted sum.

**When:** when finalists are comparable and an explicit choice is needed. Boring, but removes "choose by first impression".

**Critical:** weights are fixed **before** scoring, otherwise you tune them to the favorite.

**Template:**
```
Шаг 1: предложи 5–7 критериев оценки решений для задачи <X>.
Шаг 2: назначь вес каждому (сумма = 100%).
Шаг 3: ПОДТВЕРДИ веса с пользователем — НЕ переходи к оценке без подтверждения.
Шаг 4: оцени каждое решение по каждому критерию (1–5).
Шаг 5: взвешенная сумма + ранжирование.
```

---

### 19. MMR / maximin selection (anti-collapse convergence)

**Essence:** choose not the top 3 by sum, but **3 maximally different good ones** (quality threshold + maximize difference).

**When:** always, if the finale has more than one option. Otherwise the finalists converge to one point in the axis space.

**Template:**
```
Из shortlist выбери 3 решения по правилу:
- каждое имеет суммарную оценку ≥ <порог>
- из всех таких троек выбери ту, в которой решения максимально удалены
  друг от друга по осям из Phase 1
Покажи координаты выбранных в осях, чтобы было видно различие.
```

---

### 20. Paired comparison (for small lists)

**Essence:** compare all pairs, who beats whom. The final result is the tournament winner.

**When:** 4–6 finalists, criteria are hard to formalize but easy to compare for two specific options.

**Template:**
```
Финалистов <N>. Образуй все <N×(N-1)/2> пар.
Для каждой пары: какой вариант лучше и почему (1 предложение).
Подсчитай победы. Выведи таблицу.
```

---

## Anti-mode-collapse techniques (LLM-specific)

### 21. Independent generation (context isolation)

**Problem:** generating N ideas in one answer anchors on the first one. By the fifth idea the model barely finds variations.

**Solution:** for each idea - a separate call / separate instruction "forget previous ideas". If technically impossible - explicit instruction:

```
Сейчас сгенерируй ОДНУ гипотезу для координаты <ось1=X, ось2=Y>.
Не сравнивай с предыдущими. Не пытайся быть «другой».
Просто следуй координатам.
```

---

### 22. Axis-explicit prompting

**Problem:** "give 5 diverse options" produces lexically different but semantically close ideas.

**Solution:** each generation is explicitly tied to coordinates from the morphological analysis. This turns "diversity" from a subjective request into an objective condition.

---

### 23. Completion-mode / prefix injection

**Problem:** RLHF-trained models have a narrow "default assistant voice" that narrows the space.

**Solution:** give a strong stylistic prefix or response opening, different from the default. Works for both tone and content.

```
Начни ответ с фразы: «На самом деле, наиболее интересный угол здесь — это <...>».
Не используй формулировки «есть несколько подходов» / «давайте рассмотрим».
```

---

### 24. Logit anchoring through examples (few-shot)

**Problem:** the model does not understand what level of divergence is expected from it.

**Solution:** show 1–2 examples of strongly different hypotheses to set the "scale of difference".

```
Пример того, насколько различными должны быть гипотезы:
- Гипотеза A: <радикально один подход>
- Гипотеза B: <радикально другой подход>
Теперь сгенерируй гипотезы того же масштаба различия для задачи <X>.
```

---

### 25. Verbalized distribution (as in the discussed article)

**Essence:** "give N options with estimates of their probability/appropriateness."

**When:** you want not only ideas, but also an understanding of how confident the model is in them. Calibration is poor, but as a **relative** signal for filtering - useful.

**Template:**
```
Сгенерируй 7 гипотез решения <X>.
Для каждой укажи:
- вероятность, что она применима в стандартных условиях (0–1)
- условие, при котором она становится наиболее уместной
Не используй температуру оценки выше 0.9 и ниже 0.05 — это сигнал
о неоткалиброванности.
```

**Important:** do not trust absolute probability values, use only relative order.

---

## What DOESN'T work (anti-patterns)

| Technique | Why it doesn't work |
|-------|--------------------|
| "Give 10 ideas" without structure | Ideas 4-10 are weak variations of the first |
| "Be creative" | Vague instruction, does not change the distribution |
| "Think outside the box" | Same thing, rhetoric without a mechanism |
| Self-evaluation by the same model without role switching | The "inner critic" is lazy, defends its ideas |
| Brainstorming without explicit success criteria | Nothing to evaluate during convergence |
| Full grid with 4+ axes and 3+ values | Combinatorial explosion, focus is lost |
| Convergence "by sum of points" without a diversity rule | 3 finalists are variants of one |

---

## Cheat sheet: which technique for which situation

| Situation | Starting technique |
|----------|-----------------|
| The request is vague, unclear what they want | 5 Whys / JTBD |
| The task sounds "standard", need a fresh angle | First-principles or analogies |
| There is already a solution, need variations | SCAMPER |
| There is a conflict of parameters in the task | TRIZ |
| Suspicion that extra constraints were accepted | Assumption inversion |
| Ideas turn out "correct and boring" | Provocation or worst-idea |
| Too many alternatives, need to choose | Weighted criteria + MMR |
| A finalist is chosen, need protection against failure | Pre-mortem + red team |
| The group is leaning toward one decision | Six Hats or devil's advocate |
| The solution is optimized for the current moment | Time/scale shift |
