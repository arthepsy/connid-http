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
  import HttpClientScope._
  import HttpConfiguration._

  var scope: HttpClientScope[HttpConfiguration] = _
  implicit val config: HttpConfiguration = new HttpConfiguration

  before {
    scope = new HttpClientScope
    scope.wireMockRule = createWireMock
  }

  test("Dispose") {
    val client = HttpClient(scope.getConfig)
    client.dispose()
    assertThrows[ConnectorIOException] {
      client.executeRequest(new HttpGet(scope.getUrl))
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
      client.createUri(slashes + prefix, null).toString shouldBe url
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
    config.setUrl(None.orNull)
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
    client.setJsonHeaders(req)
    req.getHeaders("Content-Type").map(_.getValue).contains("application/json") shouldBe true
    req.getHeaders("Accept").map(_.getValue).contains("application/json") shouldBe true
  }

  test("xml headers applied") {
    val client = HttpClient(scope.getConfig(false))
    val req = client.createRequest(client.createUri("/"), classOf[HttpGet])
    client.setXmlHeaders(req)
    req.getHeaders("Content-Type").map(_.getValue).contains("application/xml") shouldBe true
    req.getHeaders("Accept").map(_.getValue).contains("application/xml") shouldBe true
  }

  test("RequestAuthenticationNoAuth") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(NONE.name)
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verifyZeroInteractions(req)
  }

  test("RequestAuthenticationBasicAuth") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(DEFAULT_USERNAME)
    config.setPassword(new GuardedString(DEFAULT_PASSWORD.toCharArray))
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verify(req).setHeader(ArgumentMatchers.eq("Authorization"), ArgumentMatchers.anyString)
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationBasicAuthEmptyUsername") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(null)
    config.setPassword(new GuardedString(DEFAULT_PASSWORD.toCharArray))
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verifyZeroInteractions(req)
  }

  test("RequestAuthenticationBasicAuthNoPassword") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(DEFAULT_USERNAME)
    config.setPassword(null)
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verify(req).setHeader(ArgumentMatchers.eq("Authorization"), ArgumentMatchers.anyString)
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationBasicAuthEmptyPassword") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(BASIC.name)
    config.setUsername(DEFAULT_USERNAME)
    config.setPassword(new GuardedString)
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verify(req).setHeader(ArgumentMatchers.eq("Authorization"), ArgumentMatchers.anyString)
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationTokenAuth") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName(DEFAULT_TOKEN_NAME)
    config.setTokenValue(new GuardedString(DEFAULT_TOKEN_VALUE.toCharArray))
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verify(req).setHeader(ArgumentMatchers.eq(DEFAULT_TOKEN_NAME), ArgumentMatchers.eq(DEFAULT_TOKEN_VALUE))
    verifyNoMoreInteractions(req)
  }

  test("RequestAuthenticationTokenAuthNoName") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName(null)
    config.setTokenValue(new GuardedString(DEFAULT_TOKEN_VALUE.toCharArray))
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verifyZeroInteractions(req)
  }

  test("RequestAuthenticationTokenAuthNoValue") {
    val req = mock(classOf[HttpUriRequest])
    val config = scope.getConfig
    config.setAuthMethod(TOKEN.name)
    config.setTokenName(DEFAULT_TOKEN_NAME)
    config.setTokenValue(null)
    val client = HttpClient(config)
    client.authenticateRequest(req)
    verify(req).setHeader(ArgumentMatchers.eq(DEFAULT_TOKEN_NAME), ArgumentMatchers.eq(""))
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
    val client = HttpClient(scope.getConfig)
    client.processResponse(response, Array[Int](200), Array[Int](errorCode))
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
    val req = client.createRequest(client.createUri(url), classOf[HttpGet])
    client.setJsonHeaders(req)
    client.executeRequest(req)
  }

  private def getBasicAuthRequestStatusCode(config: HttpConfiguration) = {
    val client = HttpClient(config)
    val url = "/protected"
    allowBasicAuth(scope.wireMockRule, url)
    this.getResponse(client, url).getStatusLine.getStatusCode
  }

  private def getTokenAuthRequestStatusCode(config: HttpConfiguration) = {
    val client = HttpClient(config)
    val url = "/protected"
    allowTokenAuth(scope.wireMockRule, url)
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
    allowBasicAuth(scope.wireMockRule, url)
    this.getResponse(client, url).getStatusLine.getStatusCode shouldBe HttpStatus.SC_OK
  }

  test("UnauthorizedNotAllowed") {
    val config = scope.getConfig(false, BASIC, true)
    scope.wireMockRule.resetAll()
    scope.wireMockRule.addStubMapping(DEFAULT_STUB)
    this.getBasicAuthRequestStatusCode(config) shouldBe HttpStatus.SC_OK
  }

  test("UnauthorizedNoAuthHeader") {
    val client = HttpClient(scope.getConfig(false, BASIC, true))
    val url = "/protected"
    allowBasicAuth(scope.wireMockRule, url)
    val req = client.createRequest(client.createUri(url), classOf[HttpGet], false)
    client.setJsonHeaders(req)
    client.executeRequest(req).getStatusLine.getStatusCode shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoAuthMethod") {
    val config = scope.getConfig(false, BASIC, true)
    config.setAuthMethod(NONE.name)
    this.getBasicAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoPassword") {
    val config = scope.getConfig(false, BASIC, true)
    config.setPassword(null)
    this.getBasicAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoTokenName") {
    val config = scope.getConfig(false, TOKEN, true)
    config.setTokenName(null)
    this.getTokenAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("UnauthorizedNoTokenValue") {
    val config = scope.getConfig(false, TOKEN, true)
    config.setTokenValue(null)
    this.getTokenAuthRequestStatusCode(config) shouldBe HttpStatus.SC_UNAUTHORIZED
  }

  test("ExecuteRequestException") {
    val httpClient = mock(classOf[CloseableHttpClient])
    val client = new HttpClient(scope.getConfig(false), httpClient)
    doThrow(new IOException).when(httpClient).execute(ArgumentMatchers.any[HttpUriRequest]())
    val req = client.createRequest(client.createUri("/"), classOf[HttpGet])
    client.setJsonHeaders(req)
    assertThrows[ConnectorIOException] {
      client.executeRequest(req)
    }
  }

  test("CloseResponseException") {
    val response = mock(classOf[CloseableHttpResponse])
    val client = HttpClient(scope.getConfig)
    doThrow(new IOException).when(response).close()
    client.closeResponse(response)
  }

  private def getRequestBody(entity: HttpEntity, fail: Boolean) = {
    val response = mock(classOf[CloseableHttpResponse])
    when(response.getEntity).thenReturn(entity)
    HttpClient(scope.getConfig).getResponseBody(response, fail)
  }

  test("ResponseBody") {
    val entity = new BasicHttpEntity
    entity.setContent(new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)))
    this.getRequestBody(entity, false) shouldBe Some("test")
  }

  test("ResponseBodyNull") {
    this.getRequestBody(null, false) shouldBe None
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
