/*
 * The MIT License
 * Copyright (c) 2017 Andris Raugulis (moo@arthepsy.eu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package eu.arthepsy.midpoint

import eu.arthepsy.midpoint.HttpConfiguration.{AuthMethod, BASIC, NONE, TOKEN}
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions.ConfigurationException

class HttpConfigurationSpec extends BaseFunSuite {
  test("default config") {
    val config = new HttpConfiguration

    config should not be null
    config.getUrl shouldBe null
    config.getTrustAllCertificates.booleanValue shouldBe false
    config.getProxyHost shouldBe null
    config.getProxyPort.intValue shouldBe 8080
    config.getAuthMethod shouldBe NONE.name
    config.getUsername shouldBe null
    config.getPassword shouldBe null
    config.getTokenName shouldBe null
    config.getTokenValue shouldBe null
    assert(config.toString.startsWith("HttpConfiguration{"))
    assert(config.toString.endsWith("}"))
  }

  test("setup config") {
    val config = new HttpConfiguration
    config should not be null
    config.setUrl("http://127.0.0.1")
    config.setTrustAllCertificates(true)
    config.setProxyHost("127.0.0.1")
    config.setProxyPort(3128)
    config.setAuthMethod("BASIC")
    config.setUsername("user")
    config.setPassword(new GuardedString("password".toCharArray))
    config.setTokenName("token")
    config.setTokenValue(new GuardedString("abc".toCharArray))

    config.getUrl shouldBe "http://127.0.0.1"
    config.getTrustAllCertificates.booleanValue shouldBe true
    config.getProxyHost shouldBe "127.0.0.1"
    config.getProxyPort.intValue shouldBe 3128
    config.getAuthMethod shouldBe BASIC.name
    config.getUsername shouldBe "user"
    config.getPassword.hashCode shouldBe new GuardedString("password".toCharArray).hashCode
    config.getTokenName shouldBe "token"
    config.getTokenValue.hashCode shouldBe new GuardedString("abc".toCharArray).hashCode
  }

  test("toString") {
    val config = new HttpConfiguration
    config.setProxyHost(null)
    config.setProxyPort(0)
    config.toString.contains("proxyHost") shouldBe false
    config.toString.contains("proxyPort") shouldBe false
    config.setProxyHost("127.0.0.1")
    config.setProxyPort(3128)
    config.toString.contains("proxyHost") shouldBe true
    config.toString.contains("proxyPort") shouldBe true
    config.setAuthMethod(BASIC.name)
    assert(config.toString.contains("username"))
    config.setAuthMethod(TOKEN.name)
    assert(config.toString.contains("tokenName"))
  }

  test("AuthMethod") {
    AuthMethod(null) shouldBe NONE
    AuthMethod("unknown") shouldBe NONE
    AuthMethod("basic") shouldBe BASIC
    AuthMethod("basic") shouldBe BASIC
    AuthMethod("basic") shouldBe BASIC
    AuthMethod("BaSiC") shouldBe BASIC
    AuthMethod("BASIC") shouldBe BASIC
    AuthMethod("token") shouldBe TOKEN
    AuthMethod("ToKeN") shouldBe TOKEN
    AuthMethod("TOKEN") shouldBe TOKEN
  }

  private[this] def getConfig = {
    val config = new HttpConfiguration
    config.setUrl("http://127.0.0.1")
    config.setTrustAllCertificates(true)
    config.setProxyHost("127.0.0.1")
    config.setProxyPort(3128)
    config.setAuthMethod("BASIC")
    config.setUsername("user")
    config.setPassword(new GuardedString("password".toCharArray))
    config.setTokenName("token")
    config.setTokenValue(new GuardedString("abc".toCharArray))
    config
  }

  test("validate Url") {
    val config = this.getConfig
    config.setUrl(null)
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

  test("validate proxy") {
    val config = this.getConfig
    config.setProxyHost("127.0.0.1")
    config.setProxyPort(8080)
    config.validate()
  }

  test("validate proxy port too low") {
    val config = this.getConfig
    config.setProxyHost("127.0.0.1")
    config.setProxyPort(-123)
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

  test("validate proxy port too high") {
    val config = this.getConfig
    config.setProxyHost("127.0.0.1")
    config.setProxyPort(99999)
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

  test("validate proxy port do not care") {
    val config = this.getConfig
    config.setProxyHost(null)
    config.setProxyPort(99999)
    config.validate()
  }

  test("validate AuthMethod null") {
    val config = this.getConfig
    config.setAuthMethod(null)
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

  test("validate AuthMethod unknown") {
    val config = this.getConfig
    config.setAuthMethod("unknown")
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

  test("validate AuthMethod none") {
    val config = this.getConfig
    config.setAuthMethod(NONE.name)
    config.validate()
  }

  test("validate AuthMethod basic") {
    val config = this.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername("username")
    config.setPassword(new GuardedString("password".toCharArray))
    config.validate()
  }

  test("validate AuthMethod basic no username") {
    val config = this.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(null)
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

  test("validate AuthMethod token") {
    val config = this.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName("token")
    config.setTokenValue(new GuardedString("abc123".toCharArray))
    config.validate()
  }

  test("validate AuthMethod token no name") {
    val config = this.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName(null)
    config.setTokenValue(new GuardedString("abc123".toCharArray))
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

  test("validate AuthMethod token no value") {
    val config = this.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName("token")
    config.setTokenValue(null)
    assertThrows[ConfigurationException] {
      config.validate()
    }
  }

}
