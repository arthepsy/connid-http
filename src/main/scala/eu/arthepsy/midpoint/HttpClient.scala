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

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate

import eu.arthepsy.midpoint.HttpConfiguration.{AuthMethod, BASIC, TOKEN}
import org.apache.http.HttpHost
import org.apache.http.client.methods.{CloseableHttpResponse, HttpUriRequest}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.{ConnectionSocketFactory, PlainConnectionSocketFactory}
import org.apache.http.conn.ssl.{NoopHostnameVerifier, SSLConnectionSocketFactory}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.impl.conn.{DefaultProxyRoutePlanner, PoolingHttpClientConnectionManager}
import org.apache.http.ssl.{SSLContexts, TrustStrategy}
import org.apache.http.util.EntityUtils
import org.identityconnectors.common.logging.Log
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.common.{Base64, StringUtil}
import org.identityconnectors.framework.common.exceptions._

import scala.util.{Failure, Success, Try}

class HttpClient[A <: HttpConfiguration](configuration: A, httpClient: CloseableHttpClient) {
  import HttpClient._

  private val log = Log.getLog(getClass)

  def dispose(): Unit = Try(this.httpClient.close()) match {
    case Failure(e) => log.error(e, "{0}", e.getMessage)
    case Success(_) =>
  }

  def createUri(postfixes: String*): URIBuilder = {
    val uri = Try(URI.create(this.configuration.getUrl)) match {
      case Failure(e) =>throw new ConfigurationException(e.getMessage, e)
      case Success(v) => v
    }
    val sb = new StringBuilder
    sb.append(uri.getPath.replaceFirst("/*$", ""))
    for (postfix <- postfixes) {
      if (StringUtil.isNotBlank(postfix)) {
        sb.append('/')
        sb.append(postfix.replaceFirst("^/+", "").replaceFirst("/*$", ""))
      }
    }
    new URIBuilder()
      .setScheme(uri.getScheme)
      .setHost(uri.getHost)
      .setPort(uri.getPort)
      .setPath(sb.mkString)
  }

  def createRequest[B >: Null <: HttpUriRequest](uriBuilder: URIBuilder, reqClass: Class[B], authenticate: Boolean = true): B = {
    val uri = Try(uriBuilder.build) match {
      case Failure(e) =>
        log.error(e, "Failed to build URI")
        throw new ConnectorException(e.getMessage, e)
      case Success(v) => v
    }
    val request = Try(reqClass.getConstructor(classOf[URI]).newInstance(uri)) match {
      case Failure(e) =>
        log.error(e, "Failed to build HTTP request")
        throw new ConnectorException(e.getMessage, e)
      case Success(v) => v
    }
    if (authenticate) {
      this.authenticateRequest(request)
    }
    request
  }

  private[this] def setHeaders(req: HttpUriRequest, mime: String): Unit = {
    req.setHeader("Content-Type", mime)
    val acceptHeader = "Accept"
    req.setHeader(acceptHeader, (req.getHeaders(acceptHeader) ++ mime).mkString)
  }

  def setJsonHeaders(req: HttpUriRequest): Unit = setHeaders(req, "application/json")

  def setXmlHeaders(req: HttpUriRequest): Unit = setHeaders(req, "application/xml")

  def authenticateRequest(req: HttpUriRequest): Unit =
    AuthMethod(this.configuration.getAuthMethod) match {
      case BASIC if Option(this.configuration.getUsername).isDefined =>
        val auth: StringBuilder = new StringBuilder
        auth.append(this.configuration.getUsername).append(':')
        auth.append(Option(this.configuration.getPassword).flatMap(_.reveal).getOrElse(""))
        req.setHeader("Authorization", "Basic " + Base64.encode(auth.toString.getBytes(StandardCharsets.UTF_8)))
      case TOKEN if Option(this.configuration.getTokenName).isDefined =>
        val tokenValue = Option(this.configuration.getTokenValue).flatMap(_.reveal).getOrElse("")
        req.setHeader(this.configuration.getTokenName, tokenValue)
      case _ =>
    }

  def executeRequest(request: HttpUriRequest): CloseableHttpResponse =
    Try {
      log.info("request to {0}", request.getURI.toString)
      this.httpClient.execute(request)
    } match {
      case Failure(e) => throw new ConnectorIOException(e.getMessage, e)
      case Success(v) => v
    }

  def getResponseBody(response: CloseableHttpResponse, fail: Boolean): Option[String] =
    Try(Option(response.getEntity).map(EntityUtils.toString)) match {
      case Failure(e) =>
        if (fail) {
          throw new ConnectorIOException(e.getMessage, e)
        } else {
          log.warn(e, "{0}", e.getMessage)
          None
        }
      case Success(v) => v
    }

  def processResponse(response: CloseableHttpResponse, validCodes: Seq[Int], errorCodes: Seq[Int]): Option[String] =
    this.processResponse(response, validCodes, errorCodes, contentFail = false)

  def processResponse(response: CloseableHttpResponse, validCodes: Seq[Int], errorCodes: Seq[Int], contentFail: Boolean): Option[String] = {
    val statusCode = response.getStatusLine.getStatusCode
    val content = this.getResponseBody(response, contentFail)
    if (! validCodes.contains(statusCode)) {
      val reason = response.getStatusLine.getReasonPhrase
      val message = s"HTTP error: $statusCode $reason ${content.getOrElse("")}"
      val exception = if (errorCodes.contains(statusCode)) {
        statusCode match {
          case 400 | 405 | 406 =>
            new ConnectorIOException(message)
          case 401 | 402 | 403 | 407 =>
            new PermissionDeniedException(message)
          case 404 | 410 =>
            new UnknownUidException(message)
          case 408 =>
            new OperationTimeoutException(message)
          case 409 =>
            new AlreadyExistsException(message)
          case 412 =>
            new PreconditionFailedException(message)
          case 418 | 501 =>
            new UnsupportedOperationException(message)
          case _ =>
            new ConnectorException(message)
        }
      } else new ConnectorException(message)
      this.closeResponse(response)
      throw exception
    } else {
      this.closeResponse(response)
      content
    }
  }

  def closeResponse(response: CloseableHttpResponse): Unit =
    Try(response.close()) match {
      case Failure(e) => log.warn(e, "{0}", e.getMessage)
      case Success(_) =>
    }
}

object HttpClient {
  def apply[A <: HttpConfiguration](configuration: A): HttpClient[A] =
    apply(configuration, create(configuration))

  def apply[A <: HttpConfiguration](configuration: A, httpClient: CloseableHttpClient): HttpClient[A] =
    new HttpClient(configuration, httpClient)

  def create[A <: HttpConfiguration](configuration: A): CloseableHttpClient = {
    val builder = HttpClientBuilder.create
    if (configuration.getTrustAllCertificates) {
      val sslContext = SSLContexts.custom
        .loadTrustMaterial(None.orNull, new TrustStrategy {
          override def isTrusted(chain: Array[X509Certificate], authType: String): Boolean = true
        })
        .build
      builder.setSSLContext(sslContext)
      builder.setConnectionManager(
        new PoolingHttpClientConnectionManager(
          RegistryBuilder
            .create[ConnectionSocketFactory]
            .register("http", PlainConnectionSocketFactory.INSTANCE)
            .register("https", new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
            .build))
    }
    if (StringUtil.isNotBlank(configuration.getProxyHost)) {
      val proxy = new HttpHost(configuration.getProxyHost, configuration.getProxyPort)
      val routePlanner = new DefaultProxyRoutePlanner(proxy)
      builder.setRoutePlanner(routePlanner)
    }
    builder.build
  }

  implicit private class RevealingGuardedString(val gs: GuardedString) {
    def reveal: Option[String] = {
      val sb = StringBuilder.newBuilder
      Option(gs).foreach(_.access(new GuardedString.Accessor {
        override def access(chars: Array[Char]): Unit = {
          sb.append(new String(chars))
          ()
        }
      }))
      if (sb.nonEmpty) Some(sb.mkString) else None
    }
  }

}
