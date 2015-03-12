package org.mmocore.network;

import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * @author Nadir Román Guerrero
 * @param <T>
 */
public final class ConnectionHandler<T extends MMOClient<?>> implements CompletionHandler<AsynchronousSocketChannel, Void>
{
	private final ConnectionManager<T> conManager;
	
	ConnectionHandler(final ConnectionManager<T> manager)
	{
		conManager = manager;
	}
	
	@Override
	public synchronized void completed(AsynchronousSocketChannel result, Void attachment)
	{
		conManager.acceptConnection(result);
	}

	@Override
	public synchronized void failed(Throwable exc, Void attachment)
	{
		conManager.failedAcceptConnection(exc);
	}
}
