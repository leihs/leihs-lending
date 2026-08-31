# leihs-lending

Management backend for leihs lending pools. Provides a GraphQL API and a web interface for lending managers.

**Tech:** Clojure, Ring, Lacinia (GraphQL), reitit, http-kit. Port 3270.

## Run (dev)

```
bin/dev-run-backend
```

Sign in at http://localhost:3270/lending/sign-in (password auth, dev only).

## Build uberjar

```
bin/build
```

## Test

```
# start backend first
DB_NAME=leihs_test bin/dev-run-backend

# graphql specs
DB_NAME=leihs_test bin/rspec spec/graphql

# feature specs (browser)
DB_NAME=leihs_test bin/rspec spec/features
```

Slow motion option for feature specs:

```
SPEC_SLOW_MOTION=0.5 DB_NAME=leihs_test bin/rspec spec/features
```

## Lint

```
clojure -M:cljfmt check
bin/rblint
```

## Backend Guidelines

### Project layout

Significant files and folders:

```
src/leihs/lending/server/
  ring.clj                  # middleware stack
  routes.clj                # Reitit route definitions
  graphql.clj               # pool-scoped GraphQL handler + schema
  root_graphql.clj          # unauthenticated root GraphQL handler + schema
  sign_in.clj               # HTML sign-in form (cross-cutting page)
  home.clj                  # logged-in landing page (cross-cutting page)
  graphiql.clj              # GraphiQL UI (cross-cutting page)
  db.clj                    # jdbc helpers (kebab-case transform)
  resources/                # one file per domain (users, orders, reservations, …) — GraphQL data fetchers
  html/                     # one file per resource-tied server-rendered page (see below)
  middlewares/
    authenticate.clj        # URI-based session auth check
    authorize.clj           # pool-level role check
    pool_id.clj             # extracts :pool-id from path
    spa.clj                 # SPA fallback routing
  graphql/
    resolvers.clj           # compose all resolvers + wrapper pipeline
    queries.clj             # flat map: query-key → fn
    mutations.clj           # flat map: mutation-key → fn

resources/
  schema.edn                # pool-scoped GraphQL schema (Lacinia EDN)
  root-schema.edn           # root GraphQL schema (unauthenticated queries)
```

### Two GraphQL endpoints

- `/lending/graphql` — root schema (`root-schema.edn`), no auth required, used for sign-in queries
- `/lending/:pool-id/graphql` — pool schema (`schema.edn`), requires authenticated user with pool role

Add queries/mutations to root schema only when they must be unauthenticated. Everything else goes in the pool schema.

### Server-rendered HTML pages (non-GraphQL)

Some routes render a full HTML response directly instead of going through GraphQL — a printable document, a sign-in form, a landing page. Two kinds, in different places:

- **Cross-cutting** (not tied to a business resource): `sign_in.clj`, `home.clj`, `graphiql.clj`, at the top level of `server/`.
- **Resource-tied**: one namespace per page in `html/`, mirroring `resources/`'s one-file-per-domain split but for full HTML responses instead of GraphQL data. Example: `html/contracts.clj` renders `GET /lending/:pool-id/contracts/:contract-id`.

Conventions (see `html/contracts.clj`):

- `hiccup2.core` + a manual `"<!DOCTYPE html>"` string prefix — no templating engine
- The exported fn name mirrors the action, e.g. `show` — not a generic `handler`
- Fetch data via the same `resources/*` fns and `:tx`/`:pool-id` the request map already carries (same as resolvers), but there's no resolver-wrapper pipeline here — auth and error handling are explicit in the handler itself
- When authorization depends on the resource's own data (e.g. "is this user the owner of this specific record?"), it can't be a route-level middleware check — load the resource first, then check in the handler, instead of (or alongside) `authorize/wrap`'s pool-role-only check
- Page-specific static assets (e.g. print CSS) live under `resources/public/lending/<name>/`, not `resources/public/lending/assets/` — that path is the SPA's own build output and gets overwritten by the frontend build

### Schema conventions (Lacinia EDN)

- Custom scalars: `UUID`, `NonEmptyString`, `Date` (date-only), `DateTime` (timestamp)
- Non-null fields: `(non-null :Type)`
- Field-level resolvers declared inline: `:resolve :resolver-key`
- Paginated lists use the Connection pattern with `:first` and `:after` args
- Input types for complex mutation arguments

### Resolver conventions

**Signature:**

```clojure
(defn get-one
  [{{tx :tx pool-id :pool-id user :authenticated-entity} :request} args value]
  ...)
```

- `tx` — active DB transaction
- `pool-id` — current inventory pool (UUID), injected by `middlewares/pool_id`
- `authenticated-entity` — current user with access-rights

**Registry:** flat keyword → fn maps in `graphql/queries.clj` and `graphql/mutations.clj`. Keys are kebab-case; must match resolver keys in schema (`:resolve :my-resolver`).

**Resource modules:** one file per domain in `resources/`. Export named functions; reference them in the registry. Use `get-one` / `get-multiple` as the canonical names for single-item and list fetchers. Base sqlmap pattern for reusable query composition:

```clojure
(defn base-sqlmap [pool-id]
  (-> (sql/select :orders.*)
      (sql/from :orders)
      (sql/where [:= :orders.inventory_pool_id pool-id])))
```

### Resolver wrapper pipeline

Applied in `graphql/resolvers.clj` to all resolvers (innermost → outermost):

1. `wrap-resolver-with-kebab-case` — converts incoming args/value keys to kebab-case
2. `wrap-resolver-with-camelCase` — converts outgoing result keys to camelCase
3. `wrap-resolver-with-error` — catches `Throwable`, returns GraphQL error with `:code`

Never add try/catch inside individual resolvers — let the error wrapper handle it.

### Error handling

```clojure
(throw (ex-info "Not authorized" {:status 403}))
```

- `:status` in `ex-data` becomes the GraphQL error `code`
- Never return `nil` for auth/validation failures — always throw
- Use `clojure.spec` assertions for input validation before DB access

### Authorization flow

Request goes through (in order):

1. `session/wrap-authenticate` — loads user from session cookie into `:authenticated-entity`
2. `authenticate/wrap` — redirects/401s unauthenticated requests (skips `/lending/sign-in` and root GraphQL)
3. `authorize/wrap` — checks user has `lending_manager` or `inventory_manager` role in `:pool-id`

Pool roles:

```clojure
(def AUTHORIZED-ROLES #{"lending_manager" "inventory_manager"})
```

### Key libraries

| Concern         | Library                             |
| --------------- | ----------------------------------- |
| GraphQL         | `com.walmartlabs/lacinia`           |
| SQL builder     | `com.github.seancorfield/honeysql`  |
| JDBC            | `com.github.seancorfield/next.jdbc` |
| Routing         | `metosin/reitit`                    |
| HTTP server     | `http-kit/http-kit`                 |
| Case conversion | `camel-snake-kebab`                 |
| Logging         | `taoensso/timbre`                   |
