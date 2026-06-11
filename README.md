# sbt-scalafix-config

An ["sbt-github-actions"](https://github.com/sbt/sbt-github-actions)-like way to configure
[Scalafix](https://scalacenter.github.io/scalafix/): declare your rules and per-rule settings
as sbt keys, and have the build **generate** the `.scalafix.conf` from them — including a
separate file per configuration, so `Test` can run a more relaxed rule set than `Compile`.

> **Status: playground.** This is an experiment that sits on top of
> [`sbt-scalafix`](https://github.com/scalacenter/sbt-scalafix) and drives it through its
> public keys. If the design proves itself, the intent is to fold it into Scalafix proper.

<!-- omit in toc -->
## Table of contents

- [Why](#why)
- [Installation](#installation)
- [Usage](#usage)
- [Keys](#keys)
- [How it works](#how-it-works)
- [Keeping the generated file fresh in CI](#keeping-the-generated-file-fresh-in-ci)
- [Limitations](#limitations)

## Why

Scalafix is normally configured through a hand-written, checked-in `.scalafix.conf` (HOCON).
That's a separate file to learn, with no connection to the rest of your build. This plugin lets
you express the same configuration as typed sbt keys and **generate** the file, so:

- there's one place to configure things — your `build.sbt`;
- you can have **different rules per configuration** (the main motivation: a stricter
  `Compile`, a relaxed `Test`), which `.scalafix.conf` alone can't express;
- the generated file is checked in, so the IDE and the Scalafix CLI still see a real
  `.scalafix.conf`.

## Installation

In `project/plugins.sbt` (add `sbt-scalafix` too — this plugin builds on it):

```scala
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")
addSbtPlugin("org.polyvariant" % "sbt-scalafix-config" % version)
```

This plugin builds only for **sbt 1.x / Scala 2.12**, because `sbt-scalafix` does not publish
an sbt 2.0 artifact.

## Usage

Enable the plugin explicitly (it is **not** auto-applied) and declare your rules:

```scala
lazy val myProject = project
  .enablePlugins(ScalafixConfigPlugin)
  .settings(
    // Compile: the strict set.
    Compile / scalafixConfiguredRules := Seq("DisableSyntax", "OrganizeImports"),
    Compile / scalafixConfiguredSettings := Map(
      "DisableSyntax" -> Map("noFinalize" -> true),
      "OrganizeImports" -> Map(
        "targetDialect" -> "Scala3",
        "groups" -> Seq("re:javax?\\.", "scala", "*"),
      ),
    ),

    // Test: a more relaxed set.
    Test / scalafixConfiguredRules := Seq("DisableSyntax"),
  )
```

Generate the config files and you're ready to run Scalafix as usual:

```sh
sbt scalafixConfiguredGenerateAll   # writes .scalafix.conf and .scalafix.test.conf
sbt 'scalafix --check'              # sbt-scalafix picks up the generated files
sbt 'Test / scalafix --check'
```

`scalafixConfiguredSettings` values may be any HOCON-representable shape: `String`, `Boolean`,
numbers, `null`, `Seq[Any]` (lists), and nested `Map[String, Any]` (objects). They are rendered
to HOCON by the [Typesafe config](https://github.com/lightbend/config) library.

## Keys

All `scalafixConfigured*` keys are scoped per configuration (`Compile`, `Test`). The `*All`
tasks fan out over both.

| Key                              | Scope             | Description                                                                   |
| -------------------------------- | ----------------- | ----------------------------------------------------------------------------- |
| `scalafixConfiguredRules`        | `Compile`, `Test` | The rules to run, rendered as the `rules = [...]` array.                      |
| `scalafixConfiguredSettings`     | `Compile`, `Test` | Per-rule settings, rendered as HOCON blocks.                                  |
| `scalafixConfiguredFile`         | `Compile`, `Test` | Output path. Defaults to `.scalafix.conf` (Compile) / `.scalafix.test.conf`.  |
| `scalafixConfiguredGenerate`     | `Compile`, `Test` | Render and write the `.scalafix.conf` for this configuration.                 |
| `scalafixConfiguredCheck`        | `Compile`, `Test` | Fail if the on-disk file is out of date with the keys.                        |
| `scalafixConfiguredGenerateAll`  | project           | Run `scalafixConfiguredGenerate` for both `Compile` and `Test`.               |
| `scalafixConfiguredCheckAll`     | project           | Run `scalafixConfiguredCheck` for both `Compile` and `Test`.                  |

## How it works

Scalafix has no API to accept configuration content in memory —
`ScalafixArguments.withConfig` only takes a file path. So this plugin renders your keys to a
real `.scalafix.conf` on disk (with a banner header pointing back here) and wires
`sbt-scalafix`'s own `scalafixConfig` setting to it, per configuration:

```scala
Compile / scalafixConfig := Some((Compile / scalafixConfiguredFile).value)  // when rules are set
Test    / scalafixConfig := Some((Test    / scalafixConfiguredFile).value)
```

Because `sbt-scalafix` reads `scalafixConfig` per configuration, `Compile / scalafix` and
`Test / scalafix` each pick up their own generated file. The wiring only kicks in when a
configuration actually declares rules; otherwise `scalafixConfig` is left untouched.

## Keeping the generated file fresh in CI

The generated files are meant to be committed. To make sure they don't drift from the keys,
run the check in CI:

```sh
sbt scalafixConfiguredCheckAll
```

It re-renders from the keys and fails (with a pointer to `scalafixConfiguredGenerate`) if the
on-disk file differs.

## Limitations

- sbt 1.x / Scala 2.12 only (follows `sbt-scalafix`).
- Custom-rule resolution is unchanged — declare custom rules with `sbt-scalafix`'s
  `scalafixDependencies` as usual.
- Rendered HOCON keys are emitted in sorted order (a property of the underlying config
  library), not source order. This is harmless — Scalafix parses the file either way — and
  keeps the output deterministic for the up-to-date check.
