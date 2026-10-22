# Vue Script Setup Creator

A JetBrains IDE (WebStorm) plugin that detects variables and methods which are used inside the
`<template>` block of a **Vue 3** single file component but are never defined.

When the mouse is moved over such an identifier, the quick fixes

* **创建变量** – create the variable (`const foo = undefined;`)
* **创建方法** – create the function (`const foo = () => {};`)

are offered. Clicking one of them adds the new binding to the `<script setup>` block of the
component. When the component does not have a `<script setup>` block yet, the block is created
automatically.

---

## Features

* Detects undefined identifiers in **all** template expressions:
  * interpolations – `{{ undefinedVariable }}`
  * attribute expressions – `v-if="conditionFlag"`, `:title="someTitle"`, `@click="missingHandler"`
* The quick fixes are offered in the places where IntelliJ shows inspections:
  * the tooltip that appears when hovering the identifier,
  * the light bulb in the gutter,
  * `Alt+Enter`,
  * the context menu.
* The new binding is written into `<script setup>`, after the existing `import` statements and with
  the indentation of the surrounding code.
* Already existing bindings are never reported, and the original *Unresolved reference* inspection of
  the JavaScript plugin is replaced inside Vue templates so that every problem is reported exactly
  once.
* Chinese texts by default, English texts when the IDE runs with an English locale.

---

## Installation

### Build it locally (no Gradle, no download)

The plugin only needs a local WebStorm installation, the bundled JBR compiler is used:

```powershell
./build-local.ps1
```

The result is written to `build/distributions/vue3-setup-creator-1.0.0.zip`. Install it with
**Settings | Plugins | ⚙ | Install Plugin from Disk…** and restart the IDE.

If WebStorm is not installed in the default location:

```powershell
./build-local.ps1 -IdePath "D:\JetBrains\WebStorm 2026.2.1"
```

### Build it with Gradle

```bash
./gradlew buildPlugin     # -> build/distributions/vue3-setup-creator-1.0.0.zip
./gradlew runIde          # starts WebStorm with the plugin
```

The Gradle build downloads the WebStorm SDK, the Vue.js plugin is bundled with WebStorm.
Gradle 9 or newer is required (the wrapper of this project uses Gradle 9.8.2).

---

## How it works

| Class | Responsibility |
| --- | --- |
| `inspection/VueTemplateUnresolvedReferenceInspection` | Finds unresolved identifiers of a template and registers the two quick fixes. It is registered for the `VueExpr` meta language, so it only runs on the JavaScript/TypeScript expressions of a Vue template. |
| `inspection/CreateScriptSetupDeclarationFix` | The quick fix, it writes the new binding into `<script setup>`. |
| `inspection/VueUnresolvedReferenceSuppressor` | Suppresses the *Unresolved reference* inspection of the JavaScript plugin inside Vue templates, so that the problems are not reported twice. The suppression is only active while the inspection above is enabled. |
| `util/VueSfcModel` | Knows the structure of a `.vue` file: finds `<template>`/`<script setup>`, decides whether an element belongs to the template and writes the new declarations. |

The declaration is generated as follows:

| Action | Generated code |
| --- | --- |
| 创建变量 / Create variable | `const foo = undefined;` |
| 创建方法 / Create method | `const foo = () => {};` |

---

## Settings

The inspection can be switched off in
**Settings | Editor | Inspections | Vue.js | 模板中未定义的变量或方法**
(*Undefined variable or method in template*). It is enabled by default.

---

## Requirements

* WebStorm 2026.2 (or any IntelliJ based IDE with the Vue.js plugin, build 242 and newer)
* Java 25 for the Gradle build (the platform classes of WebStorm 2026.2 are compiled for Java 25,
  the Gradle toolchain downloads a matching JDK automatically)
* Java 21 or newer for `build-local.ps1`, the JBR of the IDE is used and therefore sufficient
