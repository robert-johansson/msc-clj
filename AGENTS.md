# Repository Guidelines

## Project Structure & Module Organization
The repository follows a lightweight `deps.edn` layout: core source lives under `src/msc/*`, mirroring the functional areas (`term`, `truth`, `stamp`, `fifo`, `memory`, `infer`, `decide`, `engine`, `check`). Tests sit in `test/msc/*` with namespaces that mirror their production counterparts (e.g., `msc.term-test`). Keep engine data immutable: each namespace should expose pure transforms so the top-level `msc.engine/step` remains the only place where inputs, RNG, and emitted effects meet.

## Build, Test, and Development Commands
Use the Clojure CLI tasks defined in `deps.edn`:
- `clj -X:test` — runs the default test runner and property suites under `test/`.
- `clj -M -m msc.engine` — executes the engine’s REPL-friendly entry point for manual stepping.
- `clj -T:build uber` — (optional) produces a deployable JAR via the `build.clj` script when present.
Prefix commands with `CLJ_CONFIG=./` if you need repository-local tool aliases.

## Coding Style & Naming Conventions
Stick to idiomatic Clojure: two-space indentation, predicate names ending in `?`, transducers or reducers for sequence-heavy code, and lower-kebab namespaces (`msc.truth`). Keep data literals small and explanatory; favor pure functions returning updated engine maps instead of mutating nested state. Run `cljfmt check` (or `cljfmt fix`) before committing to catch spacing issues, and prefer docstrings describing inputs/outputs.

## Testing Guidelines
Unit tests rely on `clojure.test`; property tests live in `test.check`. Name files `<ns>-test.clj` and test vars `foo-test` or `foo-property`. Each inference or revision rule should have a deterministic triple: minimal fixture, call, expected immutable result. For reproducibility, seed RNG threads by passing a `:rng` key into `msc.engine/step`, and fail fast when truth or stamp invariants break via `msc.check`.

## Commit & Pull Request Guidelines
Use short, imperative commit subjects (`Add assumption-of-failure reducer`). Reference tickets or doc sections in the body when relevant and call out observable behavior changes. Pull requests should summarize the change graph, list added commands/tests, attach console snippets for long-run scenarios, and note any TODOs deferred to follow-ups. Include reviewers early when modifying truth math, stamp semantics, or any files under `msc.engine`, as they impact all other modules.

## Security & Configuration Tips
Keep PRNG wiring explicit so reproducible experiments stay deterministic; thread seeds through `msc.engine/step` outputs instead of using globals. When wiring future side-effect adapters (clock, operator IO), return them as data in the `effects` vector so sandboxes remain pure and inspectable before execution.
