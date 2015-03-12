package org.mmocore.network;

import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * @author Nadir Román Guerrero
 * @param <T>
 */
public final class ConnectionManager<T extends MMOClient<?>>
{
	private final AsynchronousServerSocketChannel server;
	private final ConnectionHandler<T> conHandler;
	private final Core<T> core;
	
	ConnectionManager(final AsynchronousServerSocketChannel serv, final Core<T> coreRef)
	{
		server = serv;
		conHandler = new ConnectionHandler<>(this);
		core = coreRef;
	}
	
	void init()
	{
		new Thread(new ConnectionAcceptor<>(core, this)).start();
	}
	
	synchronized void tryAcceptConnection()
	{
		try
		{
			server.accept(null, conHandler);
			wait();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	synchronized void acceptConnection(final AsynchronousSocketChannel socket)
	{
		try
		{
			if(core.getAcceptFilter().accept(socket))
			{
				socket.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE);
				socket.setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
				InetSocketAddress isaddr = (InetSocketAddress)socket.getRemoteAddress();
				MMOConnection<T> con = new MMOConnection<>(core, core.getBufferPool(), socket, isaddr.getAddress(), isaddr.getPort());
				con.setClient(core.getClientFactory().create(con));
				con.startReadTask(core.getReadCompletionHandler());
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			notify();
		}
	}
	
	synchronized void failedAcceptConnection(final Throwable t)
	{
		t.printStackTrace();
		notify();
	}
}
