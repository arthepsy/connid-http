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

import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions.ConfigurationException
import org.identityconnectors.framework.spi.{AbstractConfiguration, ConfigurationProperty}

class HttpConfiguration extends AbstractConfiguration  {
  import HttpConfiguration._

  private var url: Option[String] = None
  private var trustAllCertificates: Option[Boolean] = Some(false)
  private var authMethod: Option[String] = Some(NONE.name)
  private var proxyHost: Option[String] = None
  private var proxyPort: Option[Int] = Some(8080)
  private var username: Option[String] = None
  private var password: Option[GuardedString] = None
  private var tokenName: Option[String] = None
  private var tokenValue: Option[GuardedString] = None

  @ConfigurationProperty(displayMessageKey = "http.config.url.display", helpMessageKey = "http.config.url.help", order = 1, required = true)
  def getUrl: String = this.url.orNull
  def setUrl(url: String): Unit = this.url = Option(url)

  @ConfigurationProperty(displayMessageKey = "http.config.trustAllCertificates.display", helpMessageKey = "http.config.trustAllCertificates.help", order = 2)
  def getTrustAllCertificates: java.lang.Boolean = this.trustAllCertificates.map(scala.Predef.boolean2Boolean).orNull
  def setTrustAllCertificates(trustAllCertificates: java.lang.Boolean): Unit = this.trustAllCertificates = Option(trustAllCertificates).map(scala.Predef.Boolean2boolean)

  @ConfigurationProperty(displayMessageKey = "http.config.proxyHost.display", helpMessageKey = "http.config.proxyHost.help", order = 3)
  def getProxyHost: String = this.proxyHost.orNull
  def setProxyHost(proxyHost: String): Unit = this.proxyHost = Option(proxyHost)

  @ConfigurationProperty(displayMessageKey = "http.config.proxyPort.display", helpMessageKey = "http.config.proxyPort.help", order = 4)
  def getProxyPort: Integer = this.proxyPort.map(scala.Predef.int2Integer).orNull
  def setProxyPort(proxyPort: Integer): Unit = this.proxyPort = Option(proxyPort).map(scala.Predef.Integer2int)

  @ConfigurationProperty(displayMessageKey = "http.config.auth.display", helpMessageKey = "http.config.auth.help", order = 5)
  def getAuthMethod: String = this.authMethod.map(AuthMethod.apply).map(_.name).orNull
  def setAuthMethod(authMethod: String): Unit = this.authMethod = Option(authMethod)

  @ConfigurationProperty(displayMessageKey = "http.config.username.display", helpMessageKey = "http.config.username.help", order = 6)
  def getUsername: String = this.username.orNull
  def setUsername(username: String): Unit = this.username = Option(username)

  @ConfigurationProperty(displayMessageKey = "http.config.password.display", helpMessageKey = "http.config.password.help", order = 7)
  def getPassword: GuardedString = this.password.orNull
  def setPassword(password: GuardedString): Unit = this.password = Option(password)

  @ConfigurationProperty(displayMessageKey = "http.config.tokenName.display", helpMessageKey = "http.config.tokenName.help", order = 8)
  def getTokenName: String = this.tokenName.orNull
  def setTokenName(tokenName: String): Unit = this.tokenName = Option(tokenName)

  @ConfigurationProperty(displayMessageKey = "http.config.tokenValue.display", helpMessageKey = "http.config.tokenValue.help", order = 9)
  def getTokenValue: GuardedString = this.tokenValue.orNull
  def setTokenValue(tokenValue: GuardedString): Unit = this.tokenValue = Option(tokenValue)

  override def validate(): Unit = {
    if (StringUtil.isBlank(this.url.orNull)) {
      throw new ConfigurationException("Url must be defined")
    }
    if (StringUtil.isNotBlank(this.proxyHost.orNull)) {
      val proxyPort = this.proxyPort.getOrElse(8080)
      if (proxyPort <= 0 || proxyPort >= 65535)  {
        throw new ConfigurationException("Invalid proxy port")
      }
    }

    if (! (AuthMethod.values exists (_.name equalsIgnoreCase this.authMethod.orNull))) {
      throw new ConfigurationException("Invalid authentication method")
    }
    AuthMethod(this.authMethod.getOrElse("unknown")) match {
      case BASIC if StringUtil.isBlank(this.username.orNull) => throw new ConfigurationException("Username must be defined")
      case TOKEN if StringUtil.isBlank(this.tokenName.orNull) => throw new ConfigurationException("Token name must be defined")
      case TOKEN if this.tokenValue.isEmpty => throw new ConfigurationException("Token name must be defined")
      case _ =>
    }
  }

  override def toString: String = {
    val sb = new StringBuilder
    sb.append(this.getClass.getSimpleName).append("{")
    sb.append("url=").append(this.getUrl).append(',')
    sb.append("trust=").append(this.getTrustAllCertificates).append(',')
    if (StringUtil.isNotBlank(this.proxyHost.orNull)) {
      sb.append("proxyHost=").append(this.proxyHost).append(',')
      sb.append("proxyPort=").append(this.proxyPort).append(',')
    }
    val auth = AuthMethod(this.getAuthMethod)
    sb.append("auth=").append(auth.name).append(',')
    auth match {
      case BASIC => sb.append("username=").append(this.getUsername).append(',')
      case TOKEN => sb.append("tokenName=").append(this.getTokenName).append(',')
      case _ =>
    }
    sb.append('}')
    sb.mkString
  }

}

object HttpConfiguration {
  sealed abstract class AuthMethod(val name: String)
  case object NONE extends AuthMethod("none")
  case object BASIC extends AuthMethod("basic")
  case object TOKEN extends AuthMethod("token")

  object AuthMethod {
    val values = Seq(NONE, BASIC, TOKEN)
    def apply(value: String): AuthMethod = Option(value).map(_.toLowerCase) match {
      case Some(BASIC.name) => BASIC
      case Some(TOKEN.name) => TOKEN
      case _ => NONE
    }
  }
}