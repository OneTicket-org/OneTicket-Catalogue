# OneTicket-Catalogue

Catalogue is the Kotlin/Spring Boot service that owns venue and event data.

## Local configuration

The service uses Spring Boot datasource properties to connect to its own PostgreSQL database. Environment variables override the local defaults shown below.

| Environment variable | Required | Local default | Purpose |
| --- | --- | --- | --- |
| `SERVER_PORT` | No | `8081` | HTTP port used by the Catalogue process. The local default avoids Keycloak, which publishes host port `8080`. |
| `SPRING_DATASOURCE_URL` | No | `jdbc:postgresql://localhost:5433/catalogue_db` | JDBC URL for the Catalogue database. |
| `SPRING_DATASOURCE_USERNAME` | No | `catalogue` | PostgreSQL account used by Catalogue. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | None | Password for the Catalogue PostgreSQL account. Do not commit this value. |

Start PostgreSQL from the central `OneTicket-Main` Compose project before running Catalogue locally. Set `SPRING_DATASOURCE_PASSWORD` in the IntelliJ run configuration or in the shell environment. Its value should match `CATALOGUE_DB_PASSWORD` in the central, untracked `.env` file.

Spring Boot does not load `.env` files automatically. When Catalogue is added as a service to the central Compose project, Compose must explicitly pass the values from its `.env` file to the Catalogue container. Inside Compose, the JDBC URL must use the Compose service name and container port, for example `jdbc:postgresql://postgres:5432/catalogue_db`, rather than the host-local URL above.

Containers have separate network namespaces, so different services may listen on the same internal port. Only host-published ports need to be unique. For example, a container may listen on `8080` while Compose publishes it as `8081:8080`; `SERVER_PORT` controls the port Spring listens on inside that container.

For PowerShell, set the password for the current terminal session before running the service:

```powershell
$env:SPRING_DATASOURCE_PASSWORD = "<value of CATALOGUE_DB_PASSWORD from OneTicket-Main/.env>"
.\gradlew.bat bootRun
```
