/**
 * The MIT License
 * Copyright (c) 2010 Tad Glines
 *
 * Contributors: Ovea.com, Mycila.com
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
package com.codeminders.socketio.server.transport.jetty;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.codeminders.socketio.server.*;

import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

public final class JettyWebSocketTransport extends AbstractTransport
{
    private static final Logger LOGGER = Logger.getLogger(JettyWebSocketTransport.class.getName());

    private final WebSocketServerFactory wsFactory = new WebSocketServerFactory();

    @Override
    public void init()
    {
        wsFactory.getPolicy().setMaxTextMessageSize(getConfig().getInt(SocketIOServlet.MAX_TEXT_MESSAGE_SIZE, 32000));
        wsFactory.getPolicy().setInputBufferSize(getConfig().getBufferSize());
        wsFactory.getPolicy().setIdleTimeout(getConfig().getMaxIdle());

        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.fine(getType() + " configuration:\n" +
                    " - bufferSize=" + wsFactory.getPolicy().getInputBufferSize() + "\n" +
                    " - maxIdle=" + wsFactory.getPolicy().getIdleTimeout());
    }

    @Override
    public TransportType getType()
    {
        return TransportType.WEB_SOCKET;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       Transport.InboundFactory inboundFactory,
                       SessionManager sessionFactory) throws IOException {

        String sessionId = null;
        String transport = null;

        String path = request.getPathInfo();
        if (path != null && path.length() > 0 && !"/".equals(path)) {
            if (path.startsWith("/")) path = path.substring(1);
            String[] parts = path.split("/");
            if (parts.length >= 3) {
                transport = parts[1] == null || parts[1].length() == 0 || parts[1].equals("null") ? null : parts[1];
                sessionId = parts[2] == null || parts[2].length() == 0 || parts[2].equals("null") ? null : parts[2];
            }
        }

        if ("GET".equals(request.getMethod()) && sessionId != null
                && ("websocket".equals(transport) || "flashsocket".equals(transport))) {
            boolean hixie = request.getHeader("Sec-WebSocket-Key1") != null;

            SocketIOInbound inbound = inboundFactory.getInbound(request);
            if (inbound == null)
            {
                if (hixie)
                    response.setHeader("Connection", "close");

                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
            else
            {
                SocketIOSession session;
                final TransportConnection connection;
                session = sessionFactory.getSession(sessionId);

                if (session == null)
                {
                    session = sessionFactory.createSession(inbound, sessionId);
                    connection = createConnection(session);
                }
                else
                {
                    connection = session.getTransportHandler();
                }

                wsFactory.acceptWebSocket(new WebSocketCreator() {
                        @Override
                        public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest,
                                                      ServletUpgradeResponse servletUpgradeResponse)
                        {
                            return connection;
                        }
                    }, request, response);
            }
        }
        else
        {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, this + " transport error: Invalid request");
        }
    }

    @Override
    public TransportConnection createConnection()
    {
        return new JettyWebSocketTransportConnection(this);
    }

}