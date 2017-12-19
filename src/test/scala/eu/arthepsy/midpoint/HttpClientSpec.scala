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

import java.io.{ByteArrayInputStream, IOException}
import java.net.URI
import java.nio.charset.StandardCharsets

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlMatching}
import org.apache.commons.lang3.StringUtils
import org.apache.http.{HttpEntity, HttpStatus, StatusLine}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpRequestBase, HttpUriRequest}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.identityconnectors.common.security.GuardedString
import org.identityconnectors.framework.common.exceptions._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._

class HttpClientSpec extends BaseFunSuite {
  import HttpClient._
  import HttpClientScope._
  import HttpConfiguration._

  var scope: HttpClientScope[HttpConfiguration] = _
  implicit val config: HttpConfiguration = new HttpConfiguration

  before {
    scope = new HttpClientScope
    scope.wireMockRule = createWireMock
  }

  test("Dispose") {
    implicit val client = HttpClient(scope.getConfig)
    client.dispose()
    assertThrows[ConnectorIOException] {
      client.createRequest(new URI(scope.getUrl), classOf[HttpGet]).execute
    }
  }

  test("DisposeWithException") {
    val httpClient = mock(classOf[CloseableHttpClient])
    val client = new HttpClient(scope.getConfig, httpClient)
    doThrow(new IOException).when(httpClient).close()
    client.dispose()
  }

  test("CreateUriPostfixes") {
    val client = HttpClient(scope.getConfig(false))
    val prefix = "/decoy"
    val url = scope.getUrl + prefix
    for (i <- 0 to 3) {
      val slashes = StringUtils.repeat('/', i)
      client.createUri(slashes + prefix, nullValue).toString shouldBe url
      client.createUri(slashes + prefix, "").toString shouldBe url
      client.createUri(slashes + prefix, "@all").toString shouldBe url + "/@all"
      client.createUri(slashes + prefix, slashes + "@all").toString shouldBe url + "/@all"
    }
  }

  test("CreateUriHeadTail") {
    val client = HttpClient(scope.getConfig(false))
    val url = scope.getUrl + "/foo/bar/decoy"
    for (i <- 0 to 3) {
      val slashes = StringUtils.repeat('/', i)
      client.createUri(slashes + "foo", "bar", "decoy").toString shouldBe url
      client.createUri("foo" + slashes, "bar", "decoy").toString shouldBe url
      client.createUri(slashes + "foo" + slashes, "bar", "decoy").toString shouldBe url
      client.createUri("foo", slashes + "bar", "decoy").toString shouldBe url
      client.createUri("foo", "bar" + slashes, "decoy").toString shouldBe url
      client.createUri("foo", slashes + "bar" + slashes, "decoy").toString shouldBe url
      client.createUri("foo", "bar", slashes + "decoy").toString shouldBe url
      client.createUri("foo", "bar", "decoy" + slashes).toString shouldBe url
      client.createUri("foo", "bar", slashes + "decoy" + slashes).toString shouldBe url
    }
  }

  test("createUri fails with invalid Configuration URL") {
    val config = scope.getConfig
    val client = HttpClient(config)
    config.setUrl(nullValue)
    assertThrows[ConfigurationException] {
      client.createUri("/")
    }
    config.setUrl("://")
    assertThrows[ConfigurationException] {
      client.createUri("/")
    }
  }

  test("json headers applied") {
    val client = HttpClient(scope.getConfig(false))
    val req = client.createRequest(client.createUri("/"), classOf[HttpGet])
    req.asJson.acceptJson
    req.getHeaders("Content-Type").map(_.getValue).contains("application/json") shouldBe true
    req.getHeaders("Accept").map(_.getValue).contains("application/json") shouldBe true
  }

  test("xml headers applied") {
    val client = HttpClient(scope.getConfig(false))
    val req = client.createRequest(client.createUri("/"), classOf[HttpGet])
    req.asXml.acceptXml
    req.getHeaders("Content-Type").map(_.getValue).contains("application/xml") shouldBe true
    req.getHeaders("Accept").map(_.getValue).contains("application/xml") shouldBe true
  }

  test("text xml headers applied") {
    val client = HttpClient(scope.getConfig(false))
    val req = client.createRequest(client.createUri("/"), classOf[HttpGet])
    req.asTextXml.acceptTextXml
    req.getHeaders("Content-Type").map(_.getValue).contains("text/xml") shouldBe true
    req.getHeaders("Accept").map(_.getValue).contains("text/xml") shouldBe true
  }

  test("accept headers") {
    val client = HttpClient(scope.getConfig(false))
    val req = client.createRequest(client.createUri("/"), classOf[HttpGet])
    req.setHeader("Accept", "text/html")
    req.getHeaders("Accept").map(_.getValue).contains("text/html") shouldBe true
    req.acceptJson
    req.getHeaders("Accept").flatMap(_.getValue.split(",")).contains("application/json") shouldBe true
    req.getHeaders("Accept").flatMap(_.getValue.split(",")).contains("text/html") shouldBe true
  }

  test("RequestAuthenticationNoAuth") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(NONE.name)
    implicit val client = HttpClient(config)
    req.authenticate
    verifyZeroInteractions(req)
  }

  test("RequestAuthenticationBasicAuth") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(DefaultUsername)
    config.setPassword(new GuardedString(DefaultPassword.toCharArray))
    implicit val client = HttpClient(config)
    req.authenticate
    verify(req).setHeader(ArgumentMatchers.eq("Authorization"), ArgumentMatchers.anyString)
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationBasicAuthEmptyUsername") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(nullValue)
    config.setPassword(new GuardedString(DefaultPassword.toCharArray))
    implicit val client = HttpClient(config)
    req.authenticate
    verifyZeroInteractions(req)
  }

  test("RequestAuthenticationBasicAuthNoPassword") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(DefaultUsername)
    config.setPassword(nullValue)
    implicit val client = HttpClient(config)
    req.authenticate
    verify(req).setHeader(ArgumentMatchers.eq("Authorization"), ArgumentMatchers.anyString)
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationBasicAuthEmptyPassword") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(DefaultUsername)
    config.setPassword(new GuardedString)
    implicit val client = HttpClient(config)
    req.authenticate
    verify(req).setHeader(ArgumentMatchers.eq("Authorization"), ArgumentMatchers.anyString)
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationTokenAuth") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName(DefaultTokenName)
    config.setTokenValue(new GuardedString(DefaultTokenValue.toCharArray))
    implicit val client = HttpClient(config)
    req.authenticate
    verify(req).setHeader(ArgumentMatchers.eq(DefaultTokenName), ArgumentMatchers.eq(DefaultTokenValue))
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationTokenAuthNoName") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName(nullValue)
    config.setTokenValue(new GuardedString(DefaultTokenValue.toCharArray))
    implicit val client = HttpClient(config)
    req.authenticate
    verifyZeroInteractions(req)
  }

  test("RequestAuthenticationTokenAuthNoValue") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName(DefaultTokenName)
    config.setTokenValue(nullValue)
    implicit val client = HttpClient(config)
    req.authenticate
    verify(req).setHeader(ArgumentMatchers.eq(DefaultTokenName), ArgumentMatchers.eq(""))
    verifyNoMoreInteractions(req)
  }

  private def testStatusCodeResponse(statusCode: Int): Unit = {
    this.testStatusCodeResponse(statusCode, statusCode)
  }

  private def testStatusCodeResponse(statusCode: Int, errorCode: Int): Unit = {
    val response = mock(classOf[CloseableHttpResponse])
    val statusLine = mock(classOf[StatusLine])
    when(response.getStatusLine).thenReturn(statusLine)
    when(statusLine.getStatusCode).thenReturn(statusCode)
    implicit val client = HttpClient(scope.getConfig)
    response.process(Array[Int](200), Array[Int](errorCode))
    ()
  }

  test("ResponseErrors100") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(100)
    }
  }

  test("ResponseErrors101") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(100, 101)
    }
  }

  test("ResponseErrors200") {
    this.testStatusCodeResponse(200)
  }

  test("ResponseErrors400") {
    assertThrows[ConnectorIOException] {
      this.testStatusCodeResponse(400)
    }
  }

  test("ResponseErrors401") {
    assertThrows[PermissionDeniedException] {
      this.testStatusCodeResponse(401)
    }
  }

  test("ResponseErrors402") {
    assertThrows[PermissionDeniedException] {
      this.testStatusCodeResponse(402)
    }
  }

  test("ResponseErrors403") {
    assertThrows[PermissionDeniedException] {
      this.testStatusCodeResponse(403)
    }
  }

  test("ResponseErrors404") {
    assertThrows[UnknownUidException] {
      this.testStatusCodeResponse(404)
    }
  }

  test("ResponseErrors405") {
    assertThrows[ConnectorIOException] {
      this.testStatusCodeResponse(405)
    }
  }

  test("ResponseErrors406") {
    assertThrows[ConnectorIOException] {
      this.testStatusCodeResponse(406)
    }
  }

  test("ResponseErrors407") {
    assertThrows[PermissionDeniedException] {
      this.testStatusCodeResponse(407)
    }
  }

  test("ResponseErrors408") {
    assertThrows[OperationTimeoutException] {
      this.testStatusCodeResponse(408)
    }
  }

  test("ResponseErrors409") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(409)
    }
  }

  test("ResponseErrors410") {
    assertThrows[UnknownUidException] {
      this.testStatusCodeResponse(410)
    }
  }

  test("ResponseErrors411") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(411)
    }
  }

  test("ResponseErrors412") {
    assertThrows[PreconditionFailedException] {
      this.testStatusCodeResponse(412)
    }
  }

  test("ResponseErrors413") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(413)
    }
  }

  test("ResponseErrors414") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(414)
    }
  }

  test("ResponseErrors415") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(415)
    }
  }

  test("ResponseErrors416") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(416)
    }
  }

  test("ResponseErrors417") {
    assertThrows[ConnectorException] {
      this.testStatusCodeResponse(417)
    }
  }

  test("ResponseErrors418") {
    assertThrows[UnsupportedOperationException] {
      this.testStatusCodeResponse(418)
    }
  }

  test("ResponseErrors501") {
    assertThrows[UnsupportedOperationException] {
      this.testStatusCodeResponse(501)
    }
  }

  test("WrongRequestUri") {
    assertThrows[ConnectorException] {
      val uriBuilder = new URIBuilder
      uriBuilder.setScheme("^")
      val client = HttpClient(scope.getConfig)
      client.createRequest(uriBuilder, classOf[HttpGet])
    }
  }

  test("WrongRequestClass") {
    assertThrows[ConnectorException] {
      val uriBuilder = new URIBuilder
      val client =  HttpClient(scope.getConfig)
      client.createRequest(uriBuilder, classOf[HttpRequestBase])
    }
  }

  private def getResponse(client: HttpClient[HttpConfiguration], url: String) = {
    implicit val c = client
    client.createRequest(client.createUri(url), classOf[HttpGet]).execute
  }

  private def getBasicAuthRequestStatusCode(config: HttpConfiguration) = {
    val client = HttpClient(config)
    val url = "/protected"
    scope.wireMockRule.respond(url, BASIC)
    this.getResponse(client, url).getStatusLine.getStatusCode
  }

  private def getTokenAuthRequestStatusCode(config: HttpConfiguration) = {
    val client = HttpClient(config)
    val url = "/protected"
    scope.wireMockRule.respond(url, TOKEN)
    this.getResponse(client, url).getStatusLine.getStatusCode
  }

  test("OkRequestWithBasic") {
    val config = scope.getConfig(false, BASIC, true)
    this.getBasicAuthRequestStatusCode(config)  shouldBe HttpStatus.SC_OK
  }

  test("OkRequestWithToken") {
    val config = scope.getConfig(false, TOKEN, true)
    this.getTokenAuthRequestStatusCode(config) shouldBe HttpStatus.SC_OK
  }

  test("OkRequestViaSSL") {
    val config = scope.getConfig(true, BASIC, true)
    this.getBasicAuthRequestStatusCode(config) shouldBe HttpStatus.SC_OK
  }

  test("OkRequestViaProxy") {
    scope.wireMockRule.start()
    val proxyMock = createWireMock
    proxyMock.resetMappings()
    proxyMock.stubFor(get(urlMatching(".*")).willReturn(aResponse.proxiedFrom(scope.getUrl)))
    proxyMock.start()
    val config = scope.getConfig(false, BASIC, false)
    config.setProxyHost("127.0.0.1")
    config.setProxyPort(proxyMock.port)
    val client = HttpClient(config)
    val url = "/protected"
    scope.wireMockRule.respond(url, BASIC)
    this.getResponse(client, url).getStatusLine.getStatusCode shouldBe HttpStatus.SC_OK
  }

  test("UnauthorizedNotAllowed") {
    val config = scope.getConfig(false, BASIC, true)
    scope.wireMockRule.resetAll()
    scope.wireMockRule.addStubMapping(DefaultStub)
    this.getBasicAuthRequestStatusCode(config) shouldBe HttpStatus.SC_OK
  }

  test("UnauthorizedNoAuthHeader") {
    implicit val client: HttpClient[HttpConfiguration] = HttpClient(scope.getConfig(false, BASIC, true))
    val url = "/protected"
    scope.wireMockRule.respond(url, BASIC)
    val req = client.createRequest(client.createUri(url), classOf[HttpGet], false)
    req.asJson.execute.getStatusLine.getStatusCode shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoAuthMethod") {
    val config = scope.getConfig(false, BASIC, true)
    config.setAuthMethod(NONE.name)
    this.getBasicAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoPassword") {
    val config = scope.getConfig(false, BASIC, true)
    config.setPassword(nullValue)
    this.getBasicAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoTokenName") {
    val config = scope.getConfig(false, TOKEN, true)
    config.setTokenName(nullValue)
    this.getTokenAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoTokenValue") {
    val config = scope.getConfig(false, TOKEN, true)
    config.setTokenValue(nullValue)
    this.getTokenAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("ExecuteRequestException") {
    val httpClient = mock(classOf[CloseableHttpClient])
    implicit val client = new HttpClient(scope.getConfig(false), httpClient)
    doThrow(new IOException).when(httpClient).execute(ArgumentMatchers.any[HttpUriRequest]())
    val req = client.createRequest(client.createUri("/"), classOf[HttpGet]).asJson
    assertThrows[ConnectorIOException] {
      req.execute
    }
  }

  test("CloseResponseException") {
    val response = mock(classOf[CloseableHttpResponse])
    implicit val client = HttpClient(scope.getConfig)
    doThrow(new IOException).when(response).close()
    response.tryClose
  }

  private def getRequestBody[A](entity: HttpEntity, fail: Boolean) = {
    val response = mock(classOf[CloseableHttpResponse])
    when(response.getEntity).thenReturn(entity)
    implicit val client = HttpClient(scope.getConfig)
    response.getContent(fail)
  }

  test("ResponseBody") {
    val entity = new BasicHttpEntity
    entity.setContent(new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)))
    this.getRequestBody(entity, false) shouldBe Some("test")
  }

  test("ResponseBodyNull") {
    this.getRequestBody(nullValue, false) shouldBe None
  }

  test("ResponseBodyExceptionWarn") {
    val entity = mock(classOf[HttpEntity])
    doThrow(new IOException).when(entity).getContent
    this.getRequestBody(entity, false) shouldBe None
  }

  test("ResponseBodyExceptionFail") {
    val entity = mock(classOf[HttpEntity])
    doThrow(new IOException).when(entity).getContent
    assertThrows[ConnectorIOException] {
      this.getRequestBody(entity, true) shouldBe None
    }
  }

}
