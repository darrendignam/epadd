# Running ePADD in Docker

Server deployment of the ePADD WAR on Tomcat 9 (Java 11). The image builds the
WAR itself, so no local Maven or JDK is needed.

## Quick start

```bash
cp .env.example .env        # adjust as needed
docker compose up -d --build
```

Open <http://localhost:8080/>. It redirects to `/epadd/`. The first start takes
about a minute; `docker compose ps` shows `healthy` when it is ready.

By default the port is bound to `127.0.0.1` only. ePADD has no login of its own,
so do not expose it to a network without the auth sidecar below or an equivalent.

## Configuration

Set in `.env` or the shell. All have defaults.

| Variable | Default | Purpose |
|---|---|---|
| `EPADD_MODE` | `appraisal` | `appraisal`, `processing`, `discovery` or `delivery`. Anything else aborts startup. |
| `EPADD_LANGUAGE` | `en` | UI language. |
| `EPADD_ADMIN_CONTACT` | empty | Contact shown in the UI, written as `admin=`. |
| `EPADD_JAVA_OPTS` | `-Xmx4g` | JVM options. ePADD recommends 8 GB or more of RAM for real collections. |
| `EPADD_MEM_LIMIT` | `6g` | Container memory limit. Keep it above `-Xmx`. |
| `EPADD_BIND_ADDRESS` | `127.0.0.1` | Host address the port is published on. |
| `EPADD_PORT` | `8080` | Host port. |

The entrypoint writes `/data/config/epadd.properties` from these variables on
every start. To hand-edit that file instead, set `EPADD_REGENERATE_PROPERTIES=false`
in the container environment (add it under `environment:` in the compose file).

## Volumes

| Volume | Path | Contents |
|---|---|---|
| `epadd-repos` | `/data/repos` | Archives (`epadd-appraisal`, `epadd-processing`, ...). The data that matters. |
| `epadd-settings` | `/data/settings` | Settings and caches. |
| `epadd-config` | `/data/config` | Generated `epadd.properties`. |
| `epadd-logs` | `/data/logs` | `epadd.log` and warnings log. |

The container runs as uid/gid `10001`. For bind mounts instead of named volumes,
`chown 10001:10001` the host directories first, otherwise the entrypoint exits
with a "not writable" error.

Back up `epadd-repos`:

```bash
docker run --rm -v epadd_epadd-repos:/data -v "$PWD":/backup alpine \
    tar czf /backup/epadd-repos.tgz -C /data .
```

## Authentication sidecar

Anyone who can reach ePADD can read and modify every archive in it. The overlay
`docker-compose.auth.yml` puts two containers in front:

```
browser --HTTPS--> Caddy --> oauth2-proxy --> epadd:8080
                (TLS, ACME)  (OIDC login)    (no published port)
```

* [oauth2-proxy](https://oauth2-proxy.github.io/oauth2-proxy/) handles login
  against any OpenID Connect provider (Keycloak, Authentik, Entra ID, Google,
  Okta, GitHub via an OIDC bridge, ...). Unauthenticated requests are redirected
  to the provider; authenticated ones are proxied through.
* [Caddy](https://caddyserver.com/) terminates TLS and obtains certificates
  automatically.
* The overlay removes ePADD's host port (`ports: !reset []`, needs Compose
  2.24 or newer), so the proxy is the only way in. Do not add a `ports:` entry
  back to the `epadd` service.

### 1. Register the app with your identity provider

Create an OIDC client with:

* Redirect URI: `https://<EPADD_DOMAIN>/oauth2/callback`
* Scopes: `openid email profile`
* Client type: confidential (you get a client secret)

Note the issuer URL (the one that serves `/.well-known/openid-configuration`),
client ID and client secret.

### 2. Configure `.env`

```bash
EPADD_DOMAIN=epadd.example.org
OIDC_ISSUER_URL=https://idp.example.org/realms/main
OIDC_CLIENT_ID=epadd
OIDC_CLIENT_SECRET=<from the provider>
OAUTH2_COOKIE_SECRET=<generate below>
OAUTH2_ALLOWED_EMAILS_FILE=./docker/auth/allowed-emails.txt
```

Generate the cookie secret (32 random bytes, URL-safe base64):

```bash
openssl rand -base64 32 | tr -- '+/' '-_'
```

`EPADD_DOMAIN` must resolve to the host and ports 80 and 443 must be reachable
from the internet for Caddy's certificate challenge. For an internal-only host,
use Caddy's internal CA or terminate TLS on your existing proxy (see below).

### 3. Start

```bash
docker compose -f docker-compose.yml -f docker-compose.auth.yml up -d --build
```

Verify:

```bash
curl -sI https://epadd.example.org/ | head -n 3   # expect 302 to your provider
```

### Who gets in

Logging in proves identity, not permission. Access is granted by either or both of:

* `OAUTH2_ALLOWED_EMAILS_FILE`: a file of email addresses, one per line. This is
  the recommended setting for collections with sensitive correspondence. Create
  the file before starting (`docker/auth/allowed-emails.txt` is git-ignored), or
  Docker will create a directory at that path and the proxy will fail to start.
  Edits take effect after `docker compose restart oauth2-proxy`.
* `OAUTH2_EMAIL_DOMAINS`: admits everyone with an address in that domain
  (comma-separated list, `*` for anyone the provider authenticates). Usually too
  broad.

With neither set, nobody is admitted (the proxy answers 403). You can also restrict
who may use the client in the identity provider, or use group claims via
`OAUTH2_PROXY_ALLOWED_GROUPS` and `OAUTH2_PROXY_OIDC_GROUPS_CLAIM` (the provider
must emit a groups claim); add those under `environment:` for `oauth2-proxy`.

ePADD has no user concept. Every authenticated user has the same full access to
every archive, including delete and export. Treat the group you admit as one
trust boundary.

### Notes

* **Timeouts.** oauth2-proxy defaults to a 30 second upstream timeout, which
  breaks long ePADD operations such as importing and indexing a large mbox.
  The overlay raises it to `3600s`; adjust with `OAUTH2_UPSTREAM_TIMEOUT`.
* **Sessions.** The oauth2-proxy cookie lasts 7 days by default. ePADD keeps its
  own Tomcat session, so a user can be logged in to the proxy and still see
  ePADD start fresh after a restart.
* **Existing reverse proxy.** If you already run nginx, Traefik or HAProxy with
  TLS, drop Caddy: keep only the `oauth2-proxy` service, publish its port
  `4180` on `127.0.0.1`, and point your proxy at it. Alternatively use the
  proxy's forward-auth feature (`auth_request` in nginx, ForwardAuth in
  Traefik) against oauth2-proxy's `/oauth2/auth` endpoint, and publish ePADD on
  `127.0.0.1` only.
* **Other sidecars.** Authelia, Authentik's embedded outpost and Pomerium follow
  the same pattern: a proxy that authenticates and forwards to `epadd:8080`
  over the internal network, with ePADD's port unpublished.
* **Not a fix for ePADD's own vulnerabilities.** The sidecar keeps
  unauthenticated users out. It does not remove application-level issues
  reachable by an authenticated user. Keep the image updated.
