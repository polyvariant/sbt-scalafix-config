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

import com.typesafe.config.ConfigFactory

class ScalafixConfigTests extends munit.FunSuite {

  // Portable across Scala 2.12/3: turn a java.util.List into a Scala List without
  // scala.jdk / JavaConverters (which differ between those versions).
  private def toScala[A](xs: java.util.List[A]): List[A] = {
    val b = List.newBuilder[A]
    val it = xs.iterator()
    while (it.hasNext)
      b += it.next()
    b.result()
  }

  // Modeled on a real-world .scalafix.conf (algo-sequencing-service): a mix of built-in and
  // custom rules, plus an OrganizeImports block exercising scalar strings/ints/bools and a
  // list of strings including regex entries with backslashes.
  private val sample = ScalafixConfig(
    rules = Seq(
      "DisableSyntax",
      "LeakingImplicitClassVal",
      "NoAutoTupling",
      "NoValInForComprehension",
      "OrganizeImports",
      "RedundantSyntax",
      "SampleLiftNaming",
    ),
    settings = Map(
      "OrganizeImports" -> Map(
        "targetDialect" -> "Scala3",
        "coalesceToWildcardImportThreshold" -> 10,
        "expandRelative" -> true,
        "groupedImports" -> "Merge",
        "importsOrder" -> "SymbolsFirst",
        "importSelectorsOrder" -> "SymbolsFirst",
        "removeUnused" -> true,
        "groups" -> Seq(
          "java",
          "scala",
          "*",
          "com.siriusxm.",
          "re:(playback|contentingestion|coreservices|sxm)\\.",
        ),
      )
    ),
  )

  test("render includes the banner header pointing at the plugin") {
    val out = ScalafixConfig.render(sample)
    assert(out.startsWith(ScalafixConfig.header), s"missing header in:\n$out")
    assert(out.contains("sbt-scalafix-config plugin"))
  }

  test("rendered output parses back to an equivalent structure (round-trip)") {
    val out = ScalafixConfig.render(sample)
    val parsed = ConfigFactory.parseString(out)

    assertEquals(
      toScala(parsed.getStringList("rules")),
      sample.rules.toList,
    )

    val oi = parsed.getConfig("OrganizeImports")
    assertEquals(oi.getString("targetDialect"), "Scala3")
    assertEquals(oi.getInt("coalesceToWildcardImportThreshold"), 10)
    assertEquals(oi.getBoolean("expandRelative"), true)
    assertEquals(oi.getString("groupedImports"), "Merge")
    assertEquals(oi.getBoolean("removeUnused"), true)
    assertEquals(
      toScala(oi.getStringList("groups")),
      List(
        "java",
        "scala",
        "*",
        "com.siriusxm.",
        "re:(playback|contentingestion|coreservices|sxm)\\.",
      ),
    )
  }

  test("regex string with backslashes round-trips exactly") {
    val cfg = ScalafixConfig(
      rules = Seq("OrganizeImports"),
      settings = Map("OrganizeImports" -> Map("groups" -> Seq("re:a\\.b\\.c"))),
    )
    val parsed = ConfigFactory.parseString(ScalafixConfig.render(cfg))
    assertEquals(
      toScala(parsed.getStringList("OrganizeImports.groups")),
      List("re:a\\.b\\.c"),
    )
  }

  test("nested objects render and round-trip") {
    val cfg = ScalafixConfig(
      rules = Seq("DisableSyntax"),
      settings = Map(
        "DisableSyntax" -> Map(
          "noFinalize" -> true,
          "nested" -> Map("deep" -> Map("value" -> 42)),
        )
      ),
    )
    val parsed = ConfigFactory.parseString(ScalafixConfig.render(cfg))
    assertEquals(parsed.getBoolean("DisableSyntax.noFinalize"), true)
    assertEquals(parsed.getInt("DisableSyntax.nested.deep.value"), 42)
  }

  test("empty config renders just rules") {
    val parsed = ConfigFactory.parseString(
      ScalafixConfig.render(ScalafixConfig(rules = Seq.empty, settings = Map.empty))
    )
    assert(parsed.getList("rules").isEmpty)
  }

}
