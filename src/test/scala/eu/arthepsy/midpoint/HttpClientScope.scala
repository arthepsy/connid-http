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
  def getConfig(https: Option[Boolean] = None, authMethod: HttpConfiguration.AuthMethod = None.orNull, startServer: Boolean = false): A = {
    val config = classz.getConstructor().newInstance()
    if (https.isDefined) {
      if (startServer) {
        this.wireMockRule.start()
      }
      if (https.getOrElse(false)) {
        config.setTrustAllCertificates(true)
        config.setUrl(this.getUrl(true))
      } else {
        config.setUrl(this.getUrl)
      }
      Option(authMethod) match {
        case Some(BASIC) =>
          config.setAuthMethod(HttpConfiguration.BASIC.name)
          config.setUsername(DefaultUsername)
          config.setPassword(new GuardedString(DefaultPassword.toCharArray))
        case Some(TOKEN) =>
          config.setAuthMethod(HttpConfiguration.TOKEN.name)
          config.setTokenName(DefaultTokenName)
          config.setTokenValue(new GuardedString(DefaultTokenValue.toCharArray))
        case _ =>
      }
    }
    config
  }

  def getUrl: String = this.getUrl(this.wireMockRule, https = false)
  def getUrl(https: Boolean): String = this.getUrl(this.wireMockRule, https)
  def getUrl(wireMockRule: WireMockRule, https: Boolean): String = {
    val host = "http://127.0.0.1:"
    if (wireMockRule.isRunning) {
      if (https) {
        host.replace("http", "https") + this.wireMockRule.httpsPort
      } else host + this.wireMockRule.port
    } else host + "0"
  }

}

object HttpClientScope {
  val DefaultUsername = "admin"
  val DefaultPassword = "password"

  val DefaultTokenName = "Token"
  val DefaultTokenValue = "abc123"

  val DefaultStub: StubMapping =
    get(anyUrl)
    .atPriority(5)
    .willReturn(aResponse
      .withStatus(401)
      .withBody("{\"code\":401,\"message\":\"Bad credentials\"}"))
    .build

  implicit def b2b[A](x: Boolean): Option[Boolean] = Option(x)

  def createWireMock: WireMockRule = {
    val wireMockRule = new WireMockRule(wireMockConfig.dynamicPort.dynamicHttpsPort)
    wireMockRule.addStubMapping(DefaultStub)
    wireMockRule
  }

  def allowBasicAuth(wireMockRule: WireMockRule, url: String): Unit = {
    wireMockRule.stubFor(
      get(urlPathEqualTo(url))
        .withBasicAuth(DefaultUsername, DefaultPassword)
        .willReturn(aResponse
          .withStatus(200)
          .withBody("{}")))
    ()
  }

  def allowTokenAuth(wireMockRule: WireMockRule, url: String): Unit = {
    wireMockRule.stubFor(
      get(urlPathEqualTo(url))
        .withHeader(DefaultTokenName, equalTo(DefaultTokenValue))
        .willReturn(aResponse
          .withStatus(200)
          .withBody("{}")))
    ()
  }
}
