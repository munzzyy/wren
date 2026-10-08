// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.crypto

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ScryptTest {

  private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

  private fun scrypt(passphrase: String, salt: String, n: Int, r: Int, p: Int): String {
    return hex(Scrypt.derive(passphrase.toByteArray(), salt.toByteArray(), n, r, p, 64))
  }

  @Test
  fun `RFC 7914 vector 1, empty passphrase and salt`() {
    assertEquals(
      "77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906",
      scrypt("", "", 16, 1, 1)
    )
  }

  @Test
  fun `RFC 7914 vector 2, p of 16`() {
    assertEquals(
      "fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b3731622eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640",
      scrypt("password", "NaCl", 1024, 8, 16)
    )
  }

  @Test
  fun `RFC 7914 vector 3, n of 16384`() {
    assertEquals(
      "7023bdcb3afd7348461c06cd81fd38ebfda8fbba904f8e3ea9b543f6545da1f2d5432955613f0fcf62d49705242a9af9e61e85dc0d651e40dfcf017b45575887",
      scrypt("pleaseletmein", "SodiumChloride", 16384, 8, 1)
    )
  }

  @Test
  fun `RFC 7914 vector 4, n of 2^20, when the test JVM has 1 GiB to spare`() {
    assumeTrue("Needs about 1.1 GiB of heap, this JVM has ${Runtime.getRuntime().maxMemory() shr 20} MiB", Runtime.getRuntime().maxMemory() > (1200L shl 20))

    val started = System.nanoTime()
    assertEquals(
      "2101cb9b6a511aaeaddbbe09cf70f881ec568d574a2ffd4dabe5ee9820adaa478e56fd8f4ba5d09ffa1c6d927c40f4c337304049e8a952fbcbf45c6fa77a41a4",
      scrypt("pleaseletmein", "SodiumChloride", 1 shl 20, 8, 1)
    )
    println("scrypt n=2^20 r=8 p=1 took ${(System.nanoTime() - started) / 1_000_000} ms")
  }

  @Test
  fun `export defaults match Python's hashlib scrypt, timed`() {
    val params = Wrenx.DEFAULT_PARAMS
    val salt = ByteArray(16) { it.toByte() }
    Scrypt.derive("warm up".toByteArray(), salt, params.n, params.r, params.p, 32)

    val started = System.nanoTime()
    val key = Scrypt.derive("correct horse battery staple".toByteArray(), salt, params.n, params.r, params.p, 32)
    println("scrypt n=${params.n} r=${params.r} p=${params.p} took ${(System.nanoTime() - started) / 1_000_000} ms")

    assertEquals("7a8e34241db898d59175c696538c417467a975ffe569068425f16188d3159c58", hex(key))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `n must be a power of two`() {
    Scrypt.derive(ByteArray(1), ByteArray(1), 1000, 8, 1, 32)
  }
}
