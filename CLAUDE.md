# leihs-lending

See `README.md` → **Backend Guidelines** for full detail. Key rules:

## Backend

- Resolver signature: `[{{tx :tx pool-id :pool-id user :authenticated-entity} :request} args value]`
- Resource modules: one file per domain in `resources/`. Use `get-one` / `get-multiple` as canonical names for fetchers.
- Registry: flat kebab-case keyword → fn maps in `graphql/queries.clj` and `graphql/mutations.clj`
- Resolver wrappers (innermost → outermost): kebab-case → camelCase → error. Never add try/catch inside resolvers.
- Errors: `(throw (ex-info "msg" {:status 403}))` — never return nil for failures
- Two GraphQL endpoints: `/lending/graphql` (root, no auth) and `/lending/:pool-id/graphql` (pool-scoped, auth required). Default to pool schema.
- Schema: custom scalars `UUID`, `NonEmptyString`; non-null via `(non-null :Type)`; field resolvers inline via `:resolve :key`
