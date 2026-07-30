# AGENTS.md

This file is the working guide for coding agents in this repository. Read it
before making changes. Also read `README.md` or `README_zh-CN.md` when product
context is needed.

## Project Overview

WK Proxy is a Windows/macOS desktop HTTP/HTTPS debugging proxy. It is a Maven
multi-module Java project targeting Java 17, with JavaFX 22, Netty, Micronaut
injection, JFoenix, OSHI/JNA, and optional GraalVM/Gluon native builds.

Modules:

- `common`: shared messages, configuration, providers, executors, utilities,
  and the in-process `MessageQueue`.
- `proxy-server`: Netty proxy pipeline, TLS certificates, throttling, upstream
  proxy support, and local process ownership lookup.
- `gui`: JavaFX desktop application, request views, renderers, settings UI,
  application icons, and resource bundles.
- `api`: auxiliary Spring Boot HTTP/WebSocket API. It is not the desktop entry
  point.

Main entry points:

- Desktop: `com.catas.wicked.proxy.WickedProxyApplication`
- Proxy-only: `com.catas.wicked.server.HttpProxyApplication`
- API: `com.catas.api.ApiApplication`

## Build And Test

Run commands from the repository root.

```bash
# Full JVM build
mvn clean package

# GUI and all required modules
mvn -o -pl gui -am test

# Proxy server and all required modules
mvn -o -pl proxy-server -am test

# Compile without running tests
mvn -o -pl gui -am -DskipTests compile
```

Module POMs explicitly enable tests even though the parent POM skips them by
default. Prefer the `-pl ... -am` commands above for focused work.

To run the desktop app, local snapshot dependencies may need to be refreshed:

```bash
mvn -pl gui -am -DskipTests install
mvn -pl gui javafx:run
```

Some proxy-server tests exercise certificates, sockets, networking, or system
proxy providers. Inspect such tests before running them individually on a
developer machine. Native packaging is platform-specific and uses `compile.sh`
plus the Gluon configuration under `gui/src/main/resources/graal`.

## Architecture And Data Flow

- Netty handlers record requests and responses and publish messages through
  `MessageQueue`.
- `Topic.RECORD` carries new request/response data. `Topic.UPDATE_MSG` carries
  delayed updates such as response completion and process ownership.
- `RequestMessage.processInfo` is optional enhancement data. Lookup failure
  must never fail or block proxy traffic; consumers must support `UNKNOWN`,
  `NOT_FOUND`, `UNSUPPORTED`, `ACCESS_DENIED`, and `ERROR`.
- GUI request state is keyed by `requestId`. URL tree, chronological list, and
  application tree selections/deletions must stay synchronized.
- `MessageService` owns GUI-side request caching and tree coordination.
  `ApplicationMessageTree` owns application/domain grouping.
- JavaFX controls may only be mutated on the JavaFX Application Thread. Queue,
  Netty, process lookup, icon lookup, certificate, and persistence work must
  remain off that thread; return results with `Platform.runLater`.
- Never block a Netty event loop with OSHI, filesystem, certificate, or UI work.

## GUI Conventions

- FXML lives under `gui/src/main/resources/fxml`; styles under `css`; localized
  text under both `lang/messages_en.properties` and
  `lang/messages_zh_CN.properties`.
- Keep the existing package spelling `gui.componet`; renaming it is an unrelated
  migration.
- Controllers are created through Micronaut/Supernaut's `FxmlLoaderFactory`.
  Settings page controllers use prototype scope and lazy loading.
- Settings editing uses `SettingsDraft`. Do not mutate global `Settings` while
  a dialog is being edited. Validate all loaded pages, then commit through
  `SettingsCommitService`.
- Certificate import/install/delete are immediate asset operations; ordinary
  settings are applied as one draft commit.
- The Settings shell uses left navigation for General, Proxy & Network, HTTPS,
  and About. Upstream proxy fields are an embedded section of the proxy page.
- Keep operational desktop UI compact and scan-friendly. Avoid card-heavy or
  marketing layouts. Preserve current teal accent, restrained radii, and
  fixed-layout behavior at minimum window sizes.
- When adding an FXML field or controller, update native-image reflection and
  resource configuration if required.

## Native And Platform Work

- OSHI is used for cross-platform socket-owner lookup. JNA/OSHI versions are
  centralized in the root POM; do not introduce module-local conflicting
  versions.
- macOS and Windows icon/process integrations must fail softly and fall back to
  bundled or generic UI assets.
- When native reachability changes, review the generic
  `META-INF/native-image` configuration and both `graal/darwin` and `graal/win`
  copies. Keep JNI, reflection, resource, serialization, and proxy metadata in
  sync where applicable.
- Do not add runtime network downloads for application logos or other UI assets.

## Change Discipline

- The worktree may already contain user changes. Start with
  `git status --short`; do not revert, overwrite, or reformat unrelated files.
- Follow existing module boundaries and local patterns. Keep changes scoped and
  add tests proportional to the behavioral risk.
- Use structured models/parsers instead of ad hoc text manipulation.
- Do not commit generated `target`, IDE, log, cache, certificate, `.class`, or
  `.lst` files; `.gitignore` already covers them.
- For bug fixes, add a focused regression test when practical. The project
  primarily uses JUnit 4 style; match the surrounding test package.
- Before finishing, run `git diff --check` and the narrowest relevant Maven
  test command. Report any test or native-build step that could not be run.
