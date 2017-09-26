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

import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.{ConfigurationException, ConnectorException}
import org.identityconnectors.framework.spi.{Configuration, Connector}

import scala.util.Try

trait HttpConnector[A >: Null <: HttpConfiguration] extends Connector {
  private val log = Log.getLog(getClass)

  var internalConfiguration: Option[A] = None
  var internalClient: Option[HttpClient[A]] = None

  private[this] def initialized[B](o: Option[B]) =
    o.getOrElse(throw new ConnectorException("Connector not initialized"))

  def client: HttpClient[A] = initialized(this.internalClient)
  def configuration: A = initialized(this.internalConfiguration)

  override def init(configuration: Configuration): Unit = {
    log.info("Initializing connector with config: {0}", configuration)
    Try(configuration).map(_.asInstanceOf[A]).toOption match {
      case Some(c) =>
        this.internalConfiguration = Option(c.asInstanceOf[A])
        this.internalClient = Option(HttpClient(this.getConfiguration))
      case _ =>
        throw new ConfigurationException(
          s"Configuration is of invalid class type ${configuration.getClass.getSimpleName}")
    }
  }

  override def getConfiguration: A = this.configuration

  override def dispose(): Unit =
    this.internalClient.foreach(_.dispose())
}