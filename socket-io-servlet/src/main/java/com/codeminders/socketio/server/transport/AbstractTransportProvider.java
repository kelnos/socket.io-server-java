package com.codeminders.socketio.server.transport;

import com.codeminders.socketio.common.SocketIOException;
import com.codeminders.socketio.protocol.EngineIOProtocol;
import com.codeminders.socketio.server.HttpRequest;
import com.codeminders.socketio.server.SocketIOProtocolException;
import com.codeminders.socketio.server.Transport;
import com.codeminders.socketio.server.TransportProvider;
import com.codeminders.socketio.server.TransportType;
import com.codeminders.socketio.server.UnsupportedTransportException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Alexander Sova (bird@codeminders.com)
 */
public abstract class AbstractTransportProvider implements TransportProvider {

    private static final Logger LOGGER = Logger.getLogger(AbstractTransportProvider.class.getName());

    protected Map<TransportType, Transport> transports = new EnumMap<>(TransportType.class);

    protected final ServletConfig servletConfig;
    protected final ServletContext servletContext;

    protected AbstractTransportProvider(ServletConfig servletConfig, ServletContext servletContext) {
        this.servletConfig = servletConfig;
        this.servletContext = servletContext;
    }

    /**
     *   Creates and initializes all available transports
     */
    @Override
    public void init()
            throws SocketIOException
    {
        addIfNotNull(TransportType.XHR_POLLING,   createXHRPollingTransport());
        addIfNotNull(TransportType.JSONP_POLLING, createJSONPPollingTransport());
        addIfNotNull(TransportType.WEB_SOCKET,    createWebSocketTransport());

        for (Transport t : transports.values())
            t.init();
    }

    @Override
    public void destroy()
    {
        for (Transport t : getTransports())
            t.destroy();
    }

    @Override
    public Transport getTransport(HttpRequest request)
            throws UnsupportedTransportException, SocketIOProtocolException
    {
        String transportName = request.getParameter(EngineIOProtocol.TRANSPORT);
        if(transportName == null)
            throw new SocketIOProtocolException("Missing transport parameter");

        TransportType type = TransportType.UNKNOWN;

        if("websocket".equals(transportName))
            type = TransportType.from(transportName);

        if("polling".equals(transportName)) {
            if(request.getParameter(EngineIOProtocol.JSONP_INDEX) != null)
                type = TransportType.JSONP_POLLING;
            else
                type = TransportType.XHR_POLLING;
        }

        Transport t = transports.get(type);
        if(t == null)
            throw new UnsupportedTransportException(transportName);

        return t;
    }

    @Override
    public Transport getTransport(TransportType type)
    {
        return transports.get(type);
    }

    @Override
    public Collection<Transport> getTransports()
    {
        return transports.values();
    }

    protected Transport createXHRPollingTransport()
    {
        return new XHRPollingTransport(servletConfig, servletContext);
    }

    protected Transport createJSONPPollingTransport()
    {
        return null;
    }

    protected Transport createWebSocketTransport()
    {
        return null;
    }

    private void addIfNotNull(TransportType type, Transport transport)
    {
        if(transport != null)
            transports.put(type, transport);
    }
}
