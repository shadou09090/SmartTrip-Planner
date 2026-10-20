# Oracle Wallet connection check

Small, isolated Spring Boot application for checking a real connection to Oracle Autonomous Database. On startup, it connects through the Wallet TNS alias and executes `SELECT 1 FROM DUAL`.

## Requirements

- JDK 17
- Maven 3.6.3 or newer
- Autonomous Database Wallet downloaded from Oracle, extracted to a local directory

The project uses Spring Boot 3.5.7 and Oracle JDBC `ojdbc11` 23.26.3.0.0. Oracle publishes `ojdbc11` for JDK 17; `oraclepki`, `osdt_core`, and `osdt_cert` provide the Wallet security support. The application intentionally uses JDBC directly and has no web server, JPA, or connection pool.

## Configure Windows environment variables

Set these variables in a PowerShell window. Use the directory containing the extracted Wallet files (including `tnsnames.ora`), the TNS alias from that file, and the database user's credentials:

```powershell
$env:ORACLE_WALLET_PATH = 'C:\path\to\extracted\wallet'
$env:ORACLE_TNS_ALIAS = 'your_tns_alias_low'
$env:ORACLE_DB_USERNAME = 'your_database_username'
$env:ORACLE_DB_PASSWORD = 'your_database_password'
```

Do not put the Wallet or credentials in this project. Keep credentials in environment variables; do not paste them into source files, `application.properties`, or command history that you intend to retain. The local `.gitignore` excludes common Wallet and secret file patterns.

## Run from PowerShell

From the repository root, set `JAVA_HOME` for this PowerShell process and invoke Maven with the JDK 17 `java.exe` explicitly. This leaves global Java and Maven settings unchanged:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location .\oracle-wallet-test
mvn -version
mvn spring-boot:run
```

Confirm `mvn -version` reports Java 17 before running. The app prints a success message only after it connects and the query returns `1`. On failure, it prints a concise diagnostic without displaying credentials, the JDBC URL, or raw driver messages. A successful build alone does not confirm database connectivity.

## Run from IntelliJ IDEA

1. Open `oracle-wallet-test\pom.xml` as a Maven project (or open the folder as a separate project window).
2. In **File > Project Structure**, select JDK 17 for the Project SDK.
3. In **Settings > Build, Execution, Deployment > Build Tools > Maven**, set the importer JDK and Maven runner JRE to JDK 17.
4. Create a run configuration for `com.smarttrip.oraclewallet.OracleWalletTestApplication`. Add `ORACLE_WALLET_PATH`, `ORACLE_TNS_ALIAS`, `ORACLE_DB_USERNAME`, and `ORACLE_DB_PASSWORD` in the run configuration's environment variables.
5. Run the application. Keep the run configuration local and do not share it if it contains credentials.

If your Maven installation still launches under Java 8, use the PowerShell commands above with `JAVA_HOME` set to the installed JDK 17, or set IntelliJ's Maven runner JRE to that JDK. Do not change system-wide installations for this test.
