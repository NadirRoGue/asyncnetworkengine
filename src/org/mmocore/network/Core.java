/* This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package org.mmocore.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author KenM
 * @author Nadir Román Guerrero
 * @param <T> 
 */
public final class Core<T extends MMOClient<?>>
{
	// default BYTE_ORDER
	private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	// default HEADER_SIZE
	private static final int HEADER_SIZE = 2;
	// Server Socket
	private AsynchronousServerSocketChannel _server;
	
	private ConnectionManager<T> _conManager;
	private final ReadHandler<T> _readHandler;
	private final WriteHandler<T> _writeHandler;
	private final ConnectionCloseHandler<T> _conCloserHandler;
	// Implementations
	private final IPacketHandler<T> _packetHandler;
	private final IMMOExecutor<T> _executor;
	private final IClientFactory<T> _clientFactory;
	private final IAcceptFilter _acceptFilter;
	// Configurations
	private final int THREADPOOL_SIZE;
	
	// Buffer pool
	private BufferPool _bufPool;
	// Connected clients
	//private IteratingBlockingQueue<MMOConnection<T>> _connectionList;
	// Read/writer executor service
	private ThreadPoolExecutor _parseExecutor;
	
	private boolean _shutdown;
	
	public Core(final CoreConfig sc, final IMMOExecutor<T> executor, final IPacketHandler<T> packetHandler, final IClientFactory<T> clientFactory, final IAcceptFilter acceptFilter)
	{
		THREADPOOL_SIZE = sc.ASYNC_THREAD_POOL_SIZE;
		
		ReadParseTask.HEADER_SIZE = HEADER_SIZE;
		ReadParseTask.MAX_READ_PER_PASS = sc.MAX_READ_PER_PASS;
		
		WriteParseTask.HEADER_SIZE = HEADER_SIZE;
		WriteParseTask.MAX_SEND_PER_PASS = sc.MAX_SEND_PER_PASS;
		
		//_connectionList = new IteratingBlockingQueue<MMOConnection<T>>();
		
		_bufPool = new BufferPool(BYTE_ORDER, sc.NATIVE_BUF_POOL_SIZE, sc.NATIVE_BUF_SIZE, sc.HELPER_BUFFER_COUNT, sc.HELPER_BUFFER_SIZE, sc.STRING_BUF_POOL_SIZE, sc.STRING_BUF_SIZE);
		
		_acceptFilter = acceptFilter;
		_packetHandler = packetHandler;
		_clientFactory = clientFactory;
		_executor = executor;

		_parseExecutor = new ThreadPoolExecutor(sc.WORKERS_THREAD_POOL_SIZE, sc.WORKERS_THREAD_POOL_SIZE, 3000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		
		_readHandler = new ReadHandler<>(this);
		_writeHandler = new WriteHandler<>(this);
		_conCloserHandler = new ConnectionCloseHandler<>(this);
	}
	
	public final void openServerSocket(InetAddress address, int tcpPort) throws IOException
	{
		ThreadPoolExecutor executorService = new ThreadPoolExecutor(THREADPOOL_SIZE, THREADPOOL_SIZE, 3000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
		AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(executorService);
		_server = AsynchronousServerSocketChannel.open(group);
		
		if (address == null)
			_server.bind(new InetSocketAddress(tcpPort));
		else
			_server.bind(new InetSocketAddress(address, tcpPort));
		
		_conManager = new ConnectionManager<>(_server, this);
		_conManager.init();
	}
	
	ReceivablePacket<T> handlePacket(ByteBuffer buf, T client)
	{
		return _packetHandler.handlePacket(buf, client);
	}
	
	void executePacket(ReceivablePacket<T> packet)
	{
		_executor.execute(packet);
	}
	
	void executeReadTask(ReadParseTask<T> task)
	{
		_parseExecutor.execute(task);
	}
	
	void executeWriteTask(WriteParseTask<T> task)
	{
		_parseExecutor.execute(task);
	}
	
	BufferPool getBufferPool()
	{
		return _bufPool;
	}
	
	public boolean isShutDown()
	{
		return _shutdown;
	}
	
	IClientFactory<T> getClientFactory()
	{
		return _clientFactory;
	}
	
	IAcceptFilter getAcceptFilter()
	{
		return _acceptFilter;
	}
	
	ReadHandler<T> getReadCompletionHandler()
	{
		return _readHandler;
	}
	
	WriteHandler<T> getWriteCompletionHandler()
	{
		return _writeHandler;
	}
	
	ConnectionCloseHandler<T> getCloserCompletionHandler()
	{
		return _conCloserHandler;
	}
	
	final void finishConnection(final MMOConnection<T> con)
	{
		try
		{
			con.getSocket().close();
		}
		catch (IOException e)
		{
			con.getClient().onForcedDisconnection();
			closeConnectionImpl(con);
		}
	}
	
	final void closeConnectionImpl(final MMOConnection<T> con)
	{
		try
		{
			// notify connection
			con.getClient().onDisconnection();
		}
		finally
		{
			try
			{
				// close socket and the SocketChannel
				con.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			finally
			{
				con.releaseBuffers();
			}
		}
	}
	
	public final void shutdown()
	{
		_shutdown = true;
	}
}
