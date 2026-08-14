# leihs-lending

See `README.md` → **Backend Guidelines** for full detail. Key rules:

## Backend

- Resolver signature: `[{{tx :tx pool-id :pool-id user :authenticated-entity} :request} args value]`
- Resource modules: one file per domain in `resources/`. Use `get-one` / `get-multiple` as canonical names for fetchers.
- Registry: flat kebab-case keyword → fn maps in `graphql/queries.clj` and `graphql/mutations.clj`. Keys must be `get-one`/`get-multiple` only — combine logic for different parent contexts (e.g. `order_id` vs `reservation_ids`) inside one function rather than adding new keys.
- Resolver wrappers (innermost → outermost): kebab-case → camelCase → error. Never add try/catch inside resolvers.
- Errors: `(throw (ex-info "msg" {:status 403}))` — never return nil for failures
- Two GraphQL endpoints: `/lending/graphql` (root, no auth) and `/lending/:pool-id/graphql` (pool-scoped, auth required). Default to pool schema.
- Schema: custom scalars `UUID`, `NonEmptyString`; non-null via `(non-null :Type)`; field resolvers inline via `:resolve :key`
- HoneySQL: minimize `[:raw "..."]` and `[:cast ...]`; prefer the DSL (subqueries as maps, `[:any ...]`, etc.). Avoid casts in resolvers — use GraphQL scalars to parse args into the right JVM type (e.g. `:Date` → `LocalDate`) so next.jdbc binds them correctly without explicit casting.
- Comments: use docstrings (`"..."` between fn name and args), not line comments (`;`)

## Frontend

- Never modify the vendored components in `src/leihs/lending/client/components/ui/` (coming from https://ui.shadcn.com/), except those in the `customized` subfolder
