# LogAnalyzer — системна утиліта аналізу логів для Windows (Java)

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%2F11-lightgrey.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**LogAnalyzer** — це консольна утиліта (CLI) на Java для Windows, призначена для збору, парсингу, фільтрації та аналізу логів із текстових файлів та системного журналу подій Windows. Утиліта орієнтована на системних адміністраторів та DevOps-інженерів, які потребують швидкого аналізу великих обсягів лог-даних безпосередньо з командного рядка.

---

## Зміст

- [Можливості](#можливості)
- [Системні вимоги](#системні-вимоги)
- [Збірка проекту](#збірка-проекту)
- [Швидкий старт](#швидкий-старт)
- [Аргументи командного рядка](#аргументи-командного-рядка)
- [Приклади використання](#приклади-використання)
- [Архітектура](#архітектура)
- [Технічні характеристики](#технічні-характеристики)

---

## Можливості

LogAnalyzer реалізує наступний набір функцій:

1. **Збір даних з декількох джерел** — підтримка зчитування текстових лог-файлів (`.log`, `.txt`) та імпорт системних подій через утиліту `wevtutil.exe` (вбудована в Windows обгортка над Event Log API).
2. **Парсинг структурованих логів** — автоматичне розпізнавання полів `[Timestamp]`, `[Level]`, `[Source]`, `[Message]` через регулярні вирази. Підтримка експорту у формат CSV.
3. **Багаторівнева фільтрація** — фільтрація подій за рівнем важливості (`Critical`, `Error`, `Warning`, `Information`) та за часовим проміжком (наприклад, останні 24 години).
4. **Пошук за регулярними виразами (Regex)** — пошук специфічних помилок у тексті повідомлення через `java.util.regex`.
5. **Моніторинг у реальному часі (Live Tail)** — режим відстеження файлу в стилі `tail -f` із застосуванням `RandomAccessFile` для відстеження нових записів.
6. **Генерація аналітичного звіту** — створення підсумкового HTML-файлу з SVG-графіком розподілу помилок по годинах та статистикою.
7. **Експорт результатів** — збереження відфільтрованих даних у CSV-файл (RFC 4180-сумісний, з UTF-8 BOM для Excel).

---

## Системні вимоги

| Параметр                | Значення                                     |
|-------------------------|----------------------------------------------|
| Операційна система      | Windows 10 (21H2 і вище) / Windows 11        |
| Java Runtime            | JDK/JRE 17 або вище                          |
| Архітектура             | x64                                          |
| RAM (мінімум)           | 256 МБ                                       |
| Місце на диску          | 10 МБ                                        |

> **Примітка:** для функції `--evtlog` потрібна вбудована в Windows утиліта `wevtutil.exe` (присутня за замовчуванням у Windows 10/11). Інші функції (`--input`, `--tail`, `--export`, `--report`) працюють кросплатформенно.

---

## Збірка проекту

### Варіант 1: Maven

```cmd
cd LogAnalyzerJava
mvn clean package
```

Виконуваний JAR буде створено у `target/LogAnalyzer.jar`.

### Варіант 2: javac + jar (без Maven)

```cmd
cd LogAnalyzerJava
mkdir out
javac -d out -encoding UTF-8 src\main\java\com\loganalyzer\*.java
echo Main-Class: com.loganalyzer.Main > manifest.txt
cd out
jar cfm ..\LogAnalyzer.jar ..\manifest.txt com\
```

### Запуск

```cmd
java -jar LogAnalyzer.jar --help
```

---

## Швидкий старт

```cmd
:: Перегляд довідки
java -jar LogAnalyzer.jar --help

:: Парсинг файлу та вивід усіх записів
java -jar LogAnalyzer.jar --input app.log

:: Фільтрація лише помилок та критичних подій
java -jar LogAnalyzer.jar --input app.log --level Error,Critical

:: Експорт результатів у CSV
java -jar LogAnalyzer.jar --input app.log --export filtered.csv

:: Генерація HTML-звіту
java -jar LogAnalyzer.jar --input app.log --report report.html

:: Live Tail — моніторинг у реальному часі
java -jar LogAnalyzer.jar --input app.log --tail
```

---

## Аргументи командного рядка

| Прапорець          | Тип значення      | Опис                                                                  |
|--------------------|-------------------|-----------------------------------------------------------------------|
| `--help`, `-h`     | —                 | Виводить вбудовану довідку.                                           |
| `--input <шлях>`   | шлях до файлу     | Шлях до вхідного лог-файлу (`.log`, `.txt`).                          |
| `--evtlog <канал>` | назва каналу      | Імпорт подій з Windows Event Log (наприклад, `System`, `Application`). |
| `--level <список>` | через кому        | Фільтр за рівнем важливості: `Critical`, `Error`, `Warning`, `Information`. |
| `--since <годин>`  | ціле число        | Показати записи лише за останні N годин.                              |
| `--regex <шаблон>` | Java regex        | Фільтр повідомлень за регулярним виразом.                             |
| `--tail`           | —                 | Увімкнути режим моніторингу в реальному часі.                         |
| `--export <шлях>`  | шлях до файлу     | Експорт відфільтрованих даних у CSV.                                  |
| `--report <шлях>`  | шлях до файлу     | Згенерувати HTML-звіт з аналітикою.                                   |

---

## Приклади використання

### Пошук критичних помилок за останню добу

```cmd
java -jar LogAnalyzer.jar --input C:\Logs\server.log --level Critical,Error --since 24
```

### Пошук конкретного шаблону через regex

```cmd
java -jar LogAnalyzer.jar --input app.log --regex "Connection\s+timeout|Database\s+error"
```

### Імпорт із системного журналу Windows та генерація звіту

```cmd
java -jar LogAnalyzer.jar --evtlog System --since 48 --report system_report.html
```

### Моніторинг лог-файлу в реальному часі з фільтром

```cmd
java -jar LogAnalyzer.jar --input C:\Logs\nginx.log --tail --level Error
```

---

## Архітектура

```
LogAnalyzerJava/
├── src/main/java/com/loganalyzer/
│   ├── Main.java                 # Точка входу
│   ├── LogLevel.java             # Enum рівнів важливості
│   ├── LogEntry.java             # POJO одного запису логу
│   ├── LogParser.java            # Парсер текстових файлів (regex)
│   ├── EventLogReader.java       # Читач Windows Event Log (wevtutil.exe)
│   ├── LogFilter.java            # Фільтрація записів
│   ├── LiveTail.java             # Режим моніторингу (RandomAccessFile)
│   ├── ReportGenerator.java     # Генератор HTML-звітів (SVG-графіки)
│   ├── CsvExporter.java          # Експорт у CSV
│   └── ArgumentParser.java       # Парсер CLI-аргументів
├── docs/
│   ├── REQUIREMENTS.md
│   └── sample.log
├── pom.xml                       # Maven-конфігурація
├── LICENSE
└── README.md
```

### Принципи дизайну

- **Потокове читання** — файли обробляються через `BufferedReader` з буфером 64 КБ, що дозволяє утримувати споживання heap у межах ~150 МБ навіть для файлів понад 1 ГБ.
- **Розділення відповідальностей** — кожен модуль інкапсульований у власному класі та може використовуватись незалежно.
- **Без зовнішніх залежностей** — `EventLogReader` викликає `wevtutil.exe` через `ProcessBuilder` замість JNI/JNA. Проект збирається без додаткових бібліотек.
- **Java 17 LTS** — використання сучасних можливостей: `var`, `Optional`, `try-with-resources`, switch expressions готовий до апгрейду.

---

## Технічні характеристики

| Метрика                                                     | Цільове значення     |
|-------------------------------------------------------------|----------------------|
| Час парсингу 50 МБ (≈250 000 рядків)                        | ≤ 3 с                |
| Споживання heap при обробці файлу > 1 ГБ                    | ≤ 150 МБ             |
| Обробка `Access Denied`, відсутніх файлів, EOF              | ✅ Корректна          |
| Підтримка Unicode (UTF-8 з fallback на системне кодування)   | ✅                   |
| Коди виходу                                                  | 0 / 1 / 2            |

### Налаштування JVM для великих файлів

Для обробки дуже великих файлів за необхідності збільште heap:

```cmd
java -Xmx256m -jar LogAnalyzer.jar --input huge.log
```

---

## Ліцензія

Проект розповсюджується під ліцензією MIT. Деталі — у файлі [LICENSE](LICENSE).
