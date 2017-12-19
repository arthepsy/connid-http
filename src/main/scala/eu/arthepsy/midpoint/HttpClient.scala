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

class HttpClient[A <: HttpConfiguration](private val configuration: A, private val httpClient: CloseableHttpClient) {
  import HttpClient._

  implicit val self: HttpClient[A] = this
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
    postfixes.filter(StringUtil.isNotBlank).foreach(postfix => {
      sb.append('/')
      sb.append(postfix.replaceFirst("^/+", "").replaceFirst("/*$", ""))
    })
    new URIBuilder()
      .setScheme(uri.getScheme)
      .setHost(uri.getHost)
      .setPort(uri.getPort)
      .setPath(sb.mkString)
  }

  def createRequest[B >: Null <: HttpUriRequest](uri: URI, reqClass: Class[B]): B = {
    createRequest(uri, reqClass, authenticate = true)
  }
  def createRequest[B >: Null <: HttpUriRequest](uri: URI, reqClass: Class[B], authenticate: Boolean): B = {
    val request = Try(reqClass.getConstructor(classOf[URI]).newInstance(uri)) match {
      case Failure(e) =>
        log.error(e, "Failed to build HTTP request")
        throw new ConnectorException(e.getMessage, e)
      case Success(v) => v
    }
    if (authenticate) {
      request.authenticate
    }
    request
  }

  def createRequest[B >: Null <: HttpUriRequest](uriBuilder: URIBuilder, reqClass: Class[B], authenticate: Boolean = true): B = {
    val uri = Try(uriBuilder.build) match {
      case Failure(e) =>
        log.error(e, "Failed to build URI")
        throw new ConnectorException(e.getMessage, e)
      case Success(v) => v
    }
    createRequest(uri, reqClass, authenticate)
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

  implicit class RequestMethods[A <: HttpConfiguration, B >: Null <: HttpUriRequest](val request: B) {
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

    private[this] def setContentType(contentType: String): HttpUriRequest = {
      request.setHeader("Content-Type", contentType)
      request
    }
    private[this] def acceptContentType(contentType: String)  = {
      val acceptHeader = "Accept"
      request.setHeader(acceptHeader, (request.getHeaders(acceptHeader).map(_.getValue) :+ contentType).mkString(","))
      request
    }

    val CONTENT_TYPE_JSON = "application/json"
    val CONTENT_TYPE_XML= "application/xml"
    val CONTENT_TYPE_TEXT_XML = "text/xml"

    def asJson: HttpUriRequest = setContentType(CONTENT_TYPE_JSON)
    def asXml: HttpUriRequest = setContentType(CONTENT_TYPE_XML)
    def asTextXml: HttpUriRequest = setContentType(CONTENT_TYPE_TEXT_XML)
    def acceptJson: HttpUriRequest = acceptContentType(CONTENT_TYPE_JSON)
    def acceptXml: HttpUriRequest = acceptContentType(CONTENT_TYPE_XML)
    def acceptTextXml: HttpUriRequest = acceptContentType(CONTENT_TYPE_TEXT_XML)

    def authenticate(implicit client: HttpClient[A]): HttpClient[A] = {
      AuthMethod(client.configuration.getAuthMethod) match {
        case BASIC if Option(client.configuration.getUsername).isDefined =>
          val auth: StringBuilder = new StringBuilder
          auth.append(client.configuration.getUsername).append(':')
          auth.append(Option(client.configuration.getPassword).flatMap(_.reveal).getOrElse(""))
          request.setHeader("Authorization", "Basic " + Base64.encode(auth.toString.getBytes(StandardCharsets.UTF_8)))
        case TOKEN if Option(client.configuration.getTokenName).isDefined =>
          val tokenValue = Option(client.configuration.getTokenValue).flatMap(_.reveal).getOrElse("")
          request.setHeader(client.configuration.getTokenName, tokenValue)
        case _ =>
      }
      client
    }

    def execute(implicit client: HttpClient[A]): CloseableHttpResponse =
      Try {
        client.log.info("request to {0}", request.getURI.toString)
        client.httpClient.execute(request)
      } match {
        case Failure(e) => throw new ConnectorIOException(e.getMessage, e)
        case Success(v) => v
      }
  }

  implicit class ResponseMethods[A <: HttpConfiguration](val response: CloseableHttpResponse) {
    def getContent(fail: Boolean)(implicit client: HttpClient[A]): Option[String] =
      Try(Option(response.getEntity).map(EntityUtils.toString)) match {
        case Failure(e) =>
          if (fail) {
            throw new ConnectorIOException(e.getMessage, e)
          } else {
            client.log.warn(e, "{0}", e.getMessage)
            None
          }
        case Success(v) => v
      }

    def process(validCodes: Seq[Int], errorCodes: Seq[Int])(implicit client: HttpClient[A]): Option[String] =
      this.process(validCodes, errorCodes, contentFail = false)

    def process(validCodes: Seq[Int], errorCodes: Seq[Int], contentFail: Boolean)(implicit client: HttpClient[A]): Option[String] = {
      val statusCode = response.getStatusLine.getStatusCode
      val content = this.getContent(contentFail)
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
        this.tryClose
        throw exception
      } else {
        this.tryClose
        content
      }
    }

    def tryClose(implicit client: HttpClient[A]): Unit =
      Try(response.close()) match {
        case Failure(e) => client.log.warn(e, "{0}", e.getMessage)
        case Success(_) =>
      }
  }

}
