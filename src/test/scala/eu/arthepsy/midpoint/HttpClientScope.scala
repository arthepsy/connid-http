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

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import eu.arthepsy.midpoint.HttpConfiguration.{AuthMethod, BASIC, NONE, TOKEN}
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

  val DefaultStatusCode = 200
  val DefaultResponse = ""
  val DefaultJsonResponse = "{}"
  val DefaultXmlResponse =  "<?xml version='1.0' encoding='UTF-8'?>"

  val DefaultStub: StubMapping =
    get(anyUrl)
    .atPriority(5)
    .willReturn(aResponse
      .withStatus(401)
      .withBody(DefaultResponse))
    .build

  implicit def b2b[A](x: Boolean): Option[Boolean] = Option(x)

  def createWireMock: WireMockRule = {
    val wireMockRule = new WireMockRule(wireMockConfig.dynamicPort.dynamicHttpsPort)
    wireMockRule.addStubMapping(DefaultStub)
    wireMockRule
  }

  implicit class WireMockResponse(val wmock: WireMockRule) {
    private[this] def addAuthentication(mapping: MappingBuilder, authMethod: AuthMethod) = {
      authMethod match {
        case BASIC => mapping.withBasicAuth(DefaultUsername, DefaultPassword)
        case TOKEN => mapping.withHeader(DefaultTokenName, equalTo(DefaultTokenValue))
        case _ => mapping
      }
    }
    def respond(url: String): Unit =
      this.respond(url, NONE, DefaultResponse, DefaultStatusCode)
    def respond(url: String, body: String): Unit =
      this.respond(url, NONE, body, DefaultStatusCode)
    def respond(url: String, body: String, status: Int): Unit =
      this.respond(url, NONE, body, status)
    def respond(url: String, authMethod: AuthMethod, body: String = DefaultResponse, status: Int = DefaultStatusCode): Unit = {
      var mapping: MappingBuilder = get(urlPathEqualTo(url))
      mapping = this.addAuthentication(mapping, authMethod)
      wmock.stubFor(mapping.willReturn(aResponse().withStatus(status).withBody(body)))
      ()
    }
    def respondJson(url: String): Unit =
      this.respondJson(url, NONE, DefaultJsonResponse, DefaultStatusCode)
    def respondJson(url: String, body: String): Unit =
      this.respondJson(url, NONE, body, DefaultStatusCode)
    def respondJson(url: String, body: String, status: Int): Unit =
      this.respondJson(url, NONE, body, status)
    def respondJson(url: String, authMethod: AuthMethod, body: String = DefaultJsonResponse, status: Int = DefaultStatusCode): Unit = {
      var mapping: MappingBuilder = get(urlPathEqualTo(url))
      mapping = this.addAuthentication(mapping, authMethod)
      wmock.stubFor(mapping.willReturn(okJson(body).withStatus(status)))
      ()
    }
    def respondXml(url: String): Unit =
      this.respondXml(url, NONE, DefaultXmlResponse, DefaultStatusCode)
    def respondXml(url: String, body: String): Unit =
      this.respondXml(url, NONE, body, DefaultStatusCode)
    def respondXml(url: String, body: String, status: Int): Unit =
      this.respondXml(url, NONE, body, status)
    def respondXml(url: String, authMethod: AuthMethod, body: String = DefaultXmlResponse, status: Int = DefaultStatusCode): Unit = {
      var mapping: MappingBuilder = get(urlPathEqualTo(url))
      mapping = this.addAuthentication(mapping, authMethod)
      wmock.stubFor(mapping.willReturn(okXml(body).withStatus(status)))
      ()
    }

    def respondTextXml(url: String): Unit =
      this.respondTextXml(url, NONE, DefaultXmlResponse, DefaultStatusCode)
    def respondTextXml(url: String, body: String): Unit =
      this.respondTextXml(url, NONE, body, DefaultStatusCode)
    def respondTextXml(url: String, body: String, status: Int): Unit =
      this.respondTextXml(url, NONE, body, status)
    def respondTextXml(url: String, authMethod: AuthMethod, body: String = DefaultXmlResponse, status: Int = DefaultStatusCode): Unit = {
      val mapping: MappingBuilder = get(urlPathEqualTo(url))
      this.addAuthentication(mapping, authMethod)
      wmock.stubFor(mapping.willReturn(okTextXml(body).withStatus(status)))
      ()
    }
  }

}
