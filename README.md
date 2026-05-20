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

## Lint

```
clojure -M:cljfmt check
bin/rblint
```
