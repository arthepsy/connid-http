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

import org.identityconnectors.framework.common.exceptions.{ConfigurationException, ConnectorException}
import org.identityconnectors.framework.spi.AbstractConfiguration

class Kaka extends HttpConnector[HttpConfiguration]

class HttpConnectorSpec extends BaseFunSuite {
  var scope: HttpConnectorScope = _

  before {
    scope = new HttpConnectorScope
  }

  test("non-initialized connector") {
    val connector = scope.getConnector
    assertThrows[ConnectorException] {
      Option(connector.getConfiguration) shouldBe None
    }
    assertThrows[ConnectorException] {
      Option(connector.configuration) shouldBe None
    }
    assertThrows[ConnectorException] {
      Option(connector.client) shouldBe None
    }
  }

  test("initialized with wrong configuration class") {
    val connector = scope.getConnector
    assertThrows[ConfigurationException] {
      connector.init(new AbstractConfiguration() {
        override def validate(): Unit = ???
      })
    }
  }

  test("initialized connector") {
    val connector = scope.getConnector
    connector.init(scope.getConfig)
    Option(connector.getConfiguration) should not be None
    Option(connector.configuration) should not be None
    Option(connector.client) should not be None
  }

  test("disposing non-initialized connector") {
    val connector = scope.getConnector
    connector.dispose()
  }

  test("disposing initialized connector") {
    val connector = scope.getConnector
    connector.init(scope.getConfig)
    connector.dispose()
  }

}

class HttpConnectorScope extends HttpClientScope[HttpConfiguration] {
  class ImplementedHttpConnector extends HttpConnector[HttpConfiguration]
  def getConnector: HttpConnector[HttpConfiguration] = {
    val connector = new ImplementedHttpConnector
    connector
  }
}
