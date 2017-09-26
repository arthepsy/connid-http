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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import eu.arthepsy.midpoint.HttpConfiguration.{BASIC, TOKEN}
import org.identityconnectors.common.security.GuardedString

class HttpClientScope[A <: HttpConfiguration] {
  import HttpClientScope._

  implicit val classz: Class[A] = classOf[HttpConfiguration].asInstanceOf[Class[A]]
  var wireMockRule : WireMockRule = _

  def getConfig: A = getConfig()
  def getConfig(https: Option[Boolean] = None, authMethod: HttpConfiguration.AuthMethod = null, startServer: Boolean = false): A = {
    val config = classz.getConstructor().newInstance()
    if (https.isDefined) {
      if (startServer) {
        this.wireMockRule.start()
      }
      if (https.get) {
        config.setTrustAllCertificates(true)
        config.setUrl(this.getUrl(true))
      } else {
        config.setUrl(this.getUrl)
      }
      Option(authMethod) match {
        case Some(BASIC) =>
          config.setAuthMethod(HttpConfiguration.BASIC.name)
          config.setUsername(DEFAULT_USERNAME)
          config.setPassword(new GuardedString(DEFAULT_PASSWORD.toCharArray))
        case Some(TOKEN) =>
          config.setAuthMethod(HttpConfiguration.TOKEN.name)
          config.setTokenName(DEFAULT_TOKEN_NAME)
          config.setTokenValue(new GuardedString(DEFAULT_TOKEN_VALUE.toCharArray))
        case _ =>
      }
    }
    config
  }

  def getUrl: String = this.getUrl(this.wireMockRule, false)
  def getUrl(https: Boolean): String = this.getUrl(this.wireMockRule, https)
  def getUrl(wireMockRule: WireMockRule, https: Boolean): String = {
    val host = "http://127.0.0.1:"
    if (wireMockRule.isRunning) if (https) return host.replace("http", "https") + this.wireMockRule.httpsPort
    else return host + this.wireMockRule.port
    host + "0"
  }

}

object HttpClientScope {
  val DEFAULT_USERNAME = "admin"
  val DEFAULT_PASSWORD = "password"

  val DEFAULT_TOKEN_NAME = "Token"
  val DEFAULT_TOKEN_VALUE = "abc123"

  val DEFAULT_STUB: StubMapping =
    get(anyUrl)
    .atPriority(5)
    .willReturn(aResponse
      .withStatus(401)
      .withBody("{\"code\":401,\"message\":\"Bad credentials\"}"))
    .build

  implicit def b2b[A](x: Boolean): Option[Boolean] = Option(x)

  def createWireMock: WireMockRule = {
    val wireMockRule = new WireMockRule(wireMockConfig.dynamicPort.dynamicHttpsPort)
    wireMockRule.addStubMapping(DEFAULT_STUB)
    wireMockRule
  }

  def allowBasicAuth(wireMockRule: WireMockRule, url: String): Unit = {
    wireMockRule.stubFor(
      get(urlPathEqualTo(url))
        .withBasicAuth(DEFAULT_USERNAME, DEFAULT_PASSWORD)
        .willReturn(aResponse
          .withStatus(200)
          .withBody("{}")))
    ()
  }

  def allowTokenAuth(wireMockRule: WireMockRule, url: String): Unit = {
    wireMockRule.stubFor(
      get(urlPathEqualTo(url))
        .withHeader(DEFAULT_TOKEN_NAME, equalTo(DEFAULT_TOKEN_VALUE))
        .willReturn(aResponse
          .withStatus(200)
          .withBody("{}")))
    ()
  }
}
