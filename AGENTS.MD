# AGENTS.md

## Project overview

This repository is a private template for Gerbarium Minecraft Fabric mods.

Target stack:

- Minecraft: 1.20.1
- Fabric Loader: 0.15.11
- Fabric API: 0.92.2+1.20.1
- Java: 17
- Gradle wrapper: 8.7
- Fabric Loom: 1.6-SNAPSHOT
- Yarn mappings: 1.20.1+build.10

This repository is intended to be used as a private GitHub template repository for creating new Gerbarium Fabric mods.

---

## Hard rules

Do not upgrade Minecraft, Java, Gradle, Fabric Loader, Fabric API, Yarn mappings, or Fabric Loom unless the user explicitly asks for a version migration.

Never change this Minecraft 1.20.1 template to Java 21, Java 25, or Gradle 9.x.

Do not remove `splitEnvironmentSourceSets()`.

Do not move client-only code into `src/main/java`.

Do not use client-only Minecraft classes from common/server code.

Do not change the license from `All Rights Reserved`.

Do not add public/open-source license files such as MIT, Apache-2.0, GPL, LGPL, or CC0 unless explicitly requested.

Do not add secrets, tokens, passwords, server IP private notes, SSH keys, deployment credentials, RCON passwords, or Velocity forwarding secrets to the repository.

Do not assume this project is open source.

---

## Source layout

Common/server-safe code goes here:

```text
src/main/java
```

Client-only code goes here:

```text
src/client/java
```

Common resources go here:

```text
src/main/resources
```

Fabric metadata:

```text
src/main/resources/fabric.mod.json
```

Mixin config:

```text
src/main/resources/gerbarium_template.mixins.json
```

---

## Package rules

Default template package:

```text
com.gerbarium.template
```

When creating a new mod from this template, rename the package according to the mod purpose.

Examples:

```text
com.gerbarium.regions
com.gerbarium.core
com.gerbarium.mobs
com.gerbarium.items
com.gerbarium.worldgen
```

---

## Mod id rules

Use lowercase snake_case for mod ids.

Good examples:

```text
gerbarium_template
gerbarium_regions_bridge
gerbarium_core
gerbarium_mobs
gerbarium_items
```

Bad examples:

```text
GerbariumTemplate
gerbarium-template
gerbarium.template
```

The following values must stay consistent:

- `gradle.properties` -> `mod_id`
- `gradle.properties` -> `archives_base_name`
- `fabric.mod.json` -> `id`
- Java constant `MOD_ID`
- assets folder under `src/main/resources/assets/<mod_id>`
- data folder under `src/main/resources/data/<mod_id>`
- mixin config filename if renamed

---

## Build and validation commands

Before finishing any code task, run:

```bash
./gradlew clean build
```

On Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

If the build fails, fix the root cause. Do not hide errors.

---

## GitHub Actions rules

The CI must use Java 17.

The Gradle wrapper must use Gradle 8.7 for this Minecraft 1.20.1 template.

Do not use `java-version: 25`.

Do not use `gradle-9.x`.

---

## Fabric rules

Prefer Fabric API events, registries, commands, networking, lifecycle callbacks, and normal extension points before using Mixins.

Use Mixins only when normal Fabric APIs cannot solve the problem.

Keep Mixins small and targeted.

Each Mixin method must use a unique prefix.

Example:

```java
gerbariumTemplate$onLoadWorld
```

Avoid broad or fragile injections.

Avoid modifying core Minecraft behavior unless the user explicitly asks.

---

## Mixin rules

Mixin config is stored here:

```text
src/main/resources/gerbarium_template.mixins.json
```

Default Mixin package:

```text
com.gerbarium.template.mixin
```

Mixin classes should be stored here:

```text
src/main/java/com/gerbarium/template/mixin
```

Use Mixins only when Fabric API events or callbacks are not enough.

Avoid `@Overwrite` unless the user explicitly asks for it and there is no safer option.

Prefer small `@Inject` methods with clear targets.

Good example:

```java
@Inject(method = "loadWorld", at = @At("HEAD"))
private void gerbariumTemplate$onLoadWorld(CallbackInfo ci) {
    GerbariumTemplateMod.LOGGER.info("World is loading");
}
```

Bad patterns:

```text
large overwrites
random method patches without explanation
client-only mixins in common config
server tick loop patches without strong reason
chunk loading patches without strong reason
```

---

## Client/server safety

Anything that imports classes from these packages must stay in `src/client/java` unless there is a very specific reason:

```text
net.minecraft.client.*
net.fabricmc.api.ClientModInitializer
```

Common code in `src/main/java` must be safe to load on a dedicated server.

If adding networking, validate packets on the server side.

Never trust client input.

Client-only things include:

```text
MinecraftClient
Screen
HandledScreen
KeyBinding
HudRenderCallback
EntityRenderer
BlockEntityRenderer
WorldRenderer
client renderers
client GUI screens
client keybinds
```

Server/common-safe things include:

```text
ModInitializer
commands
server lifecycle events
registries
config loading
storage
server networking
packet definitions
data models
utility classes without client imports
```

---

## Template philosophy

This repository should remain minimal, stable, and easy to reuse.

Do not add large feature systems directly into the template unless explicitly requested.

Good things to include in the template:

- basic mod initializer
- client initializer
- basic Mixin example
- build workflow
- package structure
- config skeleton
- networking skeleton
- command skeleton
- docs for future agents

Bad things to include by default:

- huge gameplay systems
- production server configs
- secrets
- launcher-specific credentials
- unrelated libraries
- experimental version upgrades
- database integrations
- SSH automation
- production deployment scripts

---

## Version lock

This template is locked to Minecraft 1.20.1.

Expected `gradle.properties` values:

```properties
minecraft_version=1.20.1
yarn_mappings=1.20.1+build.10
loader_version=0.15.11
loom_version=1.6-SNAPSHOT
fabric_api_version=0.92.2+1.20.1
```

Expected Java version:

```text
17
```

Expected Gradle wrapper version:

```text
8.7
```

Expected Gradle wrapper distribution:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
```

Do not migrate these versions unless the user explicitly asks.

---

## GitHub Actions requirements

The workflow must use Java 17.

Good:

```yaml
- name: Set up Java 17
  uses: actions/setup-java@v5
  with:
    distribution: microsoft
    java-version: 17
```

Bad:

```yaml
java-version: 25
```

Bad:

```yaml
java-version: latest
```

The build command should be:

```bash
./gradlew clean build
```

---

## New mod rename checklist

When creating a new mod from this template, update these files:

```text
settings.gradle
gradle.properties
src/main/resources/fabric.mod.json
src/main/resources/gerbarium_template.mixins.json
src/main/java/com/gerbarium/template/GerbariumTemplateMod.java
src/client/java/com/gerbarium/template/client/GerbariumTemplateClient.java
```

Rename these template values:

```text
gerbarium_template
gerbarium-template
Gerbarium Template
com.gerbarium.template
GerbariumTemplateMod
GerbariumTemplateClient
gerbariumTemplate$
```

Example for a regions mod:

```text
gerbarium_regions_bridge
gerbarium-regions-bridge
Gerbarium Regions Bridge
com.gerbarium.regions
GerbariumRegionsBridge
GerbariumRegionsBridgeClient
gerbariumRegions$
```

Rename Java folders from:

```text
src/main/java/com/gerbarium/template
src/client/java/com/gerbarium/template
```

to something like:

```text
src/main/java/com/gerbarium/regions
src/client/java/com/gerbarium/regions
```

Rename resource folder from:

```text
src/main/resources/assets/gerbarium_template
```

to:

```text
src/main/resources/assets/<new_mod_id>
```

If data exists, rename:

```text
src/main/resources/data/gerbarium_template
```

to:

```text
src/main/resources/data/<new_mod_id>
```

Optional but recommended: rename the mixin config file from:

```text
gerbarium_template.mixins.json
```

to:

```text
<new_mod_id>.mixins.json
```

Then update `fabric.mod.json`:

```json
"mixins": [
  "<new_mod_id>.mixins.json"
]
```

---

## Security rules

Do not expose or commit:

- `.env`
- `.env.local`
- `.env.production`
- SSH private keys
- GitHub tokens
- Discord bot tokens
- database passwords
- Minecraft server RCON passwords
- Velocity forwarding secret
- API keys
- production IP notes if sensitive
- deployment credentials

Forbidden secret-like files:

```text
*.pem
*.key
*.p12
*.jks
id_rsa
id_ed25519
```

If a secret appears in the repository:

1. Do not print the secret.
2. Warn the user.
3. Recommend removing it from Git history.
4. Recommend rotating the secret.
5. Add an ignore rule if appropriate.

---

## License rules

This repository is proprietary.

Expected license in `fabric.mod.json`:

```json
"license": "All Rights Reserved"
```

Expected `LICENSE` type:

```text
All Rights Reserved
```

Do not replace the license with:

```text
CC0
MIT
Apache-2.0
GPL
LGPL
MPL
Unlicense
```

unless the user explicitly asks.

---

## MCP rules

AI agents may use MCP tools only for safe development tasks.

Allowed MCP usage:

- reading repository files
- editing files inside this repository
- checking Git status and diffs
- reading GitHub issues and pull requests
- reading official documentation
- checking dependency documentation
- checking GitHub Actions logs
- reviewing pull requests

Forbidden MCP usage:

- reading files outside the project folder unless explicitly requested
- accessing SSH keys
- accessing `.env` files
- accessing production servers
- deploying to production
- rotating secrets
- modifying GitHub repository visibility
- changing license settings
- publishing packages
- deleting branches
- force-pushing
- modifying protected branches directly
- accessing personal files unrelated to the repository

Recommended MCP tools:

```text
GitHub MCP
Filesystem MCP limited to this repository folder
Git MCP
Context7 MCP
Fetch/Web MCP for official documentation
```

Not recommended for this template:

```text
SSH MCP
Docker production MCP
database MCP
Discord MCP
browser automation MCP
unrestricted filesystem access
```

---

## Preferred response style for code changes

When editing files, provide complete replacement files where practical.

The user prefers full ready-to-replace files instead of tiny scattered patches.

When giving commands, include Windows PowerShell variants if relevant.

Do not scatter one logical change across many unexplained snippets.

For config files, prefer full replacement files.

Important config files include:

```text
build.gradle
gradle.properties
settings.gradle
fabric.mod.json
gradle/wrapper/gradle-wrapper.properties
.github/workflows/build.yml
*.mixins.json
```

---

## Final checklist

Before completing a task, verify:

- Java target is 17
- Gradle wrapper is 8.7
- Minecraft version is 1.20.1
- Fabric Loader is 0.15.11
- Fabric API is 0.92.2+1.20.1
- Fabric Loom is 1.6-SNAPSHOT
- Fabric metadata is valid
- `fabric.mod.json` has `"environment": "*"` if both client and server are supported
- client-only code is under `src/client/java`
- server/common code does not import client classes
- mixins are small and targeted
- no secrets are committed
- license remains `All Rights Reserved`
- `./gradlew clean build` passes