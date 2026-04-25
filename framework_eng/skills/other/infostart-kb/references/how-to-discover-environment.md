# How to discover the 1C project's environment context

The agent must not "make up" the environment — it must be discovered from
the project and the machine. Below are practical recipes for every element
of the "environment context" (see SKILL.md, escalation section).

Rule: first look in project/repo files, then on the system, and only if
nothing is found — ask the user.

## 1. Operating system

Where to look (by priority):

1. **Environment vars / sysfs (Linux)** —
   `cat /etc/os-release` (fields `ID`, `VERSION_ID`, `PRETTY_NAME`).
2. **Kernel and architecture** — `uname -a`. The string contains
   `Linux`, `microsoft-standard-WSL2` (= Windows host + WSL2),
   `Darwin` (macOS).
3. **Windows (from bash)** — `systeminfo | grep -i "OS Name"` or
   `cmd.exe /c ver`. Via PowerShell: `[System.Environment]::OSVersion`.
4. **Docker container** — check for `/.dockerenv`; if present, the OS
   inside the container may differ from the host OS, which matters for 1C
   (server/client/agent may run in different containers).

Separate the OS where the **1C server** runs from the OS where the
**client** (thick/thin/web) runs. COM/WSH/path questions almost always
relate to the client.

## 2. 1C platform version

Where to look (by priority):

1. **Project CLAUDE.md / AGENTS.md** — the "Environment" section usually
   fixes the working version (e.g. `8.3.27.2074`).
2. **Installed distributions on the machine:**
   - Linux: `ls /opt/1cv8/x86_64/` or `ls /opt/1cv8/` — subdirectories are
     named by version (`8.3.27.2074/`).
   - Windows: `dir "C:\Program Files\1cv8"` or
     `reg query "HKLM\SOFTWARE\1C\1cv8\InstalledLocation"`.
3. **Running server/cluster** — version via the admin console or the
   `rac` utility (`rac cluster list` → `version`).
   *Caveat:* `rac` requires a locally reachable cluster agent; inside a
   dev-container without port forwarding it typically returns
   `Connection refused` — fall back to CLAUDE.md/AGENTS.md and the IB
   connection string.
4. **Infobase** — the base itself requires a compatible platform on open;
   the minimum required version is a configuration property
   (`Configuration.xml`, `<Version>` / `<ConfigurationExtensionCompatibilityMode>`).
5. **Configuration compatibility mode** — NOT equal to the platform
   version but constrains the API; see the root node properties.

If server and client run different versions — report **both** and flag
it as a risk: some bugs and solutions hinge on exactly that mismatch.

## 3. Current configuration and its version

Where to look (by priority):

1. **`Configuration.xml`** (Designer dump format) — the path depends on
   the repo layout: typically `src/Configuration/Configuration.xml`, but
   `src/xml/Configuration.xml`, `cf/Configuration.xml`, etc. are common.
   Reliable lookup:
   `find <project> -maxdepth 5 -name Configuration.xml` and pick the root
   one (not under `Extensions/`). Inside, look for `<Name>`, `<Synonym>`,
   `<Version>`, `<Vendor>`. This gives the configuration **name**
   (Cyrillic in Russian standard configs — `ERPУправлениеПредприятием`,
   `БухгалтерияПредприятия`, `УправлениеТорговлей`; Latin in English
   configs and forks — `DriveTrSalesAndServices`, `SmallBusiness`, etc.),
   the **release version** (e.g. `2.5.17.123` or `1.6.1.4`), and the vendor.
2. **EDT project** — `src/Configuration/Configuration.mdo` (XML), same fields.
3. **Project `docs/`, `README`, `AGENTS.md`** — frequently fix
   "name + version + which standard config" (e.g. "DSSL Drive — a fork
   of УНФ translated to English").
4. **MCP tools:** `mcp__1c-mcp__get_metadata_structure` /
   `list_metadata_objects` — shows the root name and composition.
5. **From the running base** — in Designer: root config properties,
   fields "Name", "Version", "Vendor".

Standard vs customized — check for extensions (`exts/`, `Extensions/`
catalog) and metadata prefixes (e.g. `DSSL_*`, `ep_*`). This matters:
a solution for vanilla УТ 11 may not work in a heavily customized
configuration.

**БСП version** (if the project uses БСП) — typically embedded in the
common module `СтандартныеПодсистемыСервер` / `StandardSubsystemsServer`,
in a function like `ВерсияБиблиотеки()` / `LibraryVersion()`.
Alternatively — `src/CommonModules/StandardSubsystemsServer/Ext/Module.bsl`
 (grep for `"Version"` / `Version`).

## 4. DBMS variant

Where to look (by priority):

1. **Infobase connection string** — the project often records it in
   `AGENTS.md` / `CLAUDE.md` (file / client-server type, DBMS flavor).
   Examples:
   - `/F"..."` → file-based base;
   - `/S"server\base"` → client-server (DBMS specified separately);
   - `Srvr=...;Ref=...;DBMS=MSSQLServer|PostgreSQL|IBMDB2|OracleDatabase` —
     explicit in connection strings from `ibases.v8i` / `1CEStart.cfg`.
2. **Cluster infobase registry** — `rac infobase list --cluster=<id>` →
   `dbms` field (`MSSQLServer`, `PostgreSQL`, `IBMDB2`, `OracleDatabase`).
3. **Running DBMS processes on the server** (indirect signal):
   - Linux: `ps -ef | grep -Ei 'postgres|mssql|sqlservr'`;
   - Windows: `sc query | findstr /I "SQL Postgre"`;
   - Docker: `docker ps` — image names (`postgres:15`,
     `mcr.microsoft.com/mssql/server`).

   *Caveat:* this only works if the DBMS and the current session run on
   the same host. In a dev-container where the DBMS lives on a separate
   host (e.g. `onec-infra`), you will see no processes — fall back to
   the IB connection string from CLAUDE.md/AGENTS.md (item 1).
4. **File-based base** — if the IB is a single `1CD` file, DBMS questions
   do not apply; that in itself is the answer: "file-based variant".

For performance / locking / query-plan questions — specify **both the
DBMS type and its major version** (e.g. PostgreSQL 15 vs 13 behave
noticeably differently).

## 5. Execution and compatibility mode

- **Client-server vs file-based** — see above (connection string).
- **Compatibility mode** — configuration property
  (`Configuration.xml`, `<CompatibilityMode>`); determines which
  platform innovations are available.
- **Managed application vs ordinary forms** — root config,
  `ModalityUseMode`, `SynchronousPlatformExtensionAndAddInCallUseMode`,
  `InterfaceCompatibilityMode`.

## 6. Project libraries

Where to look:

1. **Project docs** — `docs/project-libraries.md` (if present), `README`,
   `AGENTS.md` / `CLAUDE.md` often explicitly list libraries
   (canonical names: БСП, ДФИ, Коннектор_HTTP, etc.). The file
   `docs/project-libraries.md` is a convention, not a standard — if it's
   missing, fall through to item 2.
2. **Common modules** — the `src/CommonModules/` directory:
   - БСП: modules `СтандартныеПодсистемы*` / `StandardSubsystems*`,
     `Common`, `Users`, `SafeModeManager`;
   - ДФИ: modules prefixed/naming `ДФИ` / `DFI`;
   - Коннектор_HTTP: modules/EPFs with `Коннектор` / `Connector` and
     HTTP functions.
3. **Subsystems** — `src/Subsystems/` or the "Subsystems" root node in
   the configuration; subsystem names often match library names.
4. **Extensions** — `exts/` may contain library-like extensions.

For a community answer, not only "is the library present" but also its
**version** matters (especially БСП) — see above for finding the version.

## 7. What to do if something cannot be found

If, after all checks, some environment element cannot be determined —
**do not guess**. Either flag it in the final answer as "unknown"
(a valid reason to lower confidence), or — if the choice between TOP-N
candidates is impossible without it — ask the user a pointed question in
the escalation format: "To choose between options A and B I need the
server OS (Windows/Linux) and the DBMS variant (MS SQL / PostgreSQL) —
please confirm".

Bad: asking "what's your environment?" without specifics.
Good: asking precisely for the parameter that decides the answer.
