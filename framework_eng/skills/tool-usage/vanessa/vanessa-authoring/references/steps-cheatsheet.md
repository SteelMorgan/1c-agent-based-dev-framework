# Vanessa Automation Steps Cheatsheet

## Navigation

```gherkin
Я нажимаю кнопку командного интерфейса "Sales"
В панели разделов я выбираю "Main"
В панели функций я выбираю "Quotes"
```

## Windows

```gherkin
открылось окно "Window name"
открылось окно "Window *"                                # * = wildcard
я жду закрытия окна "Window name" в течение 20 секунд
```

## Form fields

```gherkin
в поле с именем "FieldName" я ввожу текст "Value"
В открытой форме в поле с именем "FieldName" я ввожу текст "Value"
я перехожу к следующему реквизиту
элемент формы "FieldName" стал равен "Value"
```

## Buttons

```gherkin
я нажимаю на кнопку "SaveAndClose"                      # by name
В открытой форме я нажимаю на кнопку с заголовком "Save and close"  # by title
```

## Tables (tabular sections)

```gherkin
# Navigate to row
в таблице "List" я перехожу к строке:
    | 'Number'  |
    | 'DOC-001' |

В форме "Form" в таблице "List" я перехожу к строке:
    | 'Code'  | 'Name'   |
    | '00001' | 'Item 1' |

# Select row (double-click)
в таблице "List" я выбираю текущую строку
В форме "Form" в ТЧ "List" я выбираю текущую строку

# Enter value in table cell
В ТЧ "Inventory" в поле "Discount %" я ввожу текст 5
В форме "Form" в ТЧ "TC" в поле с заголовком "Field" я ввожу текст "Value"

# Finish row editing
В форме "Form" в ТЧ "Inventory" я завершаю редактирование строки

# Row count
в таблице "List" 0 строк
в таблице "List" 3 строки
в таблице "List" больше 0 строк
в таблице "List" меньше или равно 5 строк

# Table content
таблица "List" содержит строки:
    | N | Column1 |
    | 1 | Value1  |

таблица "List" стала равной:
    | N | Column1 |
    | 1 | Value1  |
```

## Element state

```gherkin
я вижу элемент "ElementName"
я не вижу элемент "ElementName"
элемент формы "ElementName" доступен
элемент формы с именем "ElementName" не доступен
элемент "ElementName" доступен только для просмотра
элемент "ElementName" доступен не только для просмотра
В ТЧ "TC" поле "Column" доступно
В ТЧ "TC" поле "Column" не доступно
В ТЧ "TC" поле "Column" доступно только для просмотра
```

## Checkboxes

```gherkin
я устанавливаю флаг "CheckboxName"
я снимаю флаг "CheckboxName"
флаг "CheckboxName" равен "Истина"
флаг "CheckboxName" равен "Ложь"
```

## User messages

```gherkin
в логе сообщений TestClient есть строка "Message text"
в логе сообщений TestClient есть строки:
    | "Line 1" |
    | "Line 2" |
```

## Variables and expressions

```gherkin
Я запоминаю значение выражения "ExpressionValue" в переменную "VarName"
выражение внутреннего языка "Контекст.VarName = 1" Истинно
```

## TestClient: session management

```gherkin
# Single user — in Контекст:
Дано Я запускаю тест-клиент для пользователя "Login" с паролем "Password" или подключаю уже существующий

# Multiple users — in scenario body
И я подключаю TestClient "Role1" логин "Login1" пароль "Password1"
И я подключаю TestClient "Role2" логин "Login2" пароль "Password2"
И я активизирую TestClient "Role1"
И я активизирую TestClient "Role2"
И я закрываю TestClient "Role1"
И я закрываю TestClient "Role2"
```

## Conditions and pause

```gherkin
Если "Истина" тогда
    И <step>

пауза 2
```
