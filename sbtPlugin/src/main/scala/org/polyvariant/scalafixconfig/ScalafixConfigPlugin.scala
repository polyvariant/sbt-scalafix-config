/*
 * Copyright 2026 Polyvariant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polyvariant.scalafixconfig

import sbt.*
import scalafix.sbt.ScalafixPlugin

import Keys.*

/** An "sbt-github-actions–like" way to configure Scalafix: declare rules and per-rule settings as
  * sbt keys, and have the build generate a `.scalafix.conf` from them. A separate file is generated
  * per configuration (`Compile`/`Test`), so e.g. `Test` can run a more relaxed rule set than
  * `Compile`.
  *
  * Enable explicitly: `.enablePlugins(ScalafixConfigPlugin)`. The generated file is checked in and
  * wired into sbt-scalafix's `scalafixConfig`, so the IDE and the Scalafix CLI pick it up too.
  */
object ScalafixConfigPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = ScalafixPlugin

  import ScalafixPlugin.autoImport.scalafixConfig

  object autoImport {

    val scalafixConfiguredRules = settingKey[Seq[String]](
      "Scalafix rules to run, rendered as the `rules = [...]` array in the generated config"
    )

    val scalafixConfiguredSettings = settingKey[Map[String, Any]](
      "Per-rule Scalafix settings, rendered as HOCON blocks. Values may be String, Boolean, " +
        "numbers, null, Seq[Any] (lists) or nested Map[String, Any] (objects)."
    )

    val scalafixConfiguredFile = settingKey[File](
      "Path of the generated .scalafix.conf for this configuration (checked in)"
    )

    val scalafixConfiguredGenerate = taskKey[File](
      "Generate the .scalafix.conf for this configuration from the scalafixConfigured* keys"
    )

    val scalafixConfiguredCheck = taskKey[Unit](
      "Fail if the generated .scalafix.conf is out of date with the scalafixConfigured* keys"
    )

    val scalafixConfiguredGenerateAll = taskKey[Unit](
      "Generate .scalafix.conf for all configurations (Compile + Test)"
    )

    val scalafixConfiguredCheckAll = taskKey[Unit](
      "Check .scalafix.conf for all configurations (Compile + Test)"
    )

  }

  import autoImport.*

  private def configScopedSettings(config: Configuration): Seq[Setting[?]] =
    inConfig(config)(
      Seq(
        scalafixConfiguredRules := Seq.empty,
        scalafixConfiguredSettings := Map.empty,
        scalafixConfiguredFile := {
          // `.scalafix.conf` for Compile so editors/CLI find it by default; a sibling file
          // for every other configuration.
          val name =
            if (config == Compile)
              ".scalafix.conf"
            else
              s".scalafix.${config.name}.conf"
          (LocalRootProject / baseDirectory).value / name
        },
        scalafixConfiguredGenerate := {
          val file = scalafixConfiguredFile.value
          val contents = ScalafixConfig.render(
            ScalafixConfig(
              rules = scalafixConfiguredRules.value,
              settings = scalafixConfiguredSettings.value,
            )
          )
          IO.write(file, contents)
          streams.value.log.info(s"[scalafixConfiguredGenerate] (${config.name}) wrote $file")
          file
        },
        scalafixConfiguredCheck := {
          val log = streams.value.log
          val file = scalafixConfiguredFile.value
          val expected = ScalafixConfig.render(
            ScalafixConfig(
              rules = scalafixConfiguredRules.value,
              settings = scalafixConfiguredSettings.value,
            )
          )
          val actual =
            if (file.isFile)
              IO.read(file)
            else
              ""
          if (actual != expected)
            sys.error(
              s"[scalafixConfiguredCheck] (${config.name}) $file is out of date — " +
                s"run scalafixConfiguredGenerate"
            )
          else
            log.info(s"[scalafixConfiguredCheck] (${config.name}) $file is up to date")
        },
        // Auto-wire sbt-scalafix to the generated file when this configuration declares rules.
        scalafixConfig := {
          if (scalafixConfiguredRules.value.nonEmpty)
            Some(scalafixConfiguredFile.value)
          else
            scalafixConfig.value
        },
      )
    )

  override def projectSettings: Seq[Setting[?]] =
    configScopedSettings(Compile) ++
      configScopedSettings(Test) ++
      Seq(
        scalafixConfiguredGenerateAll := Def
          .sequential(
            Compile / scalafixConfiguredGenerate,
            Test / scalafixConfiguredGenerate,
          )
          .value,
        scalafixConfiguredCheckAll := Def
          .sequential(
            Compile / scalafixConfiguredCheck,
            Test / scalafixConfiguredCheck,
          )
          .value,
      )

}
