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

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author KenM
 * @author BiggBoss
 * @param <T> 
 * 
 */
public class MMOConnection<T extends MMOClient<?>>
{
	private final Core<T> _core;
	
	private final BufferPool _pool;
	
	private final AsynchronousSocketChannel _socket;
	
	private final InetAddress _address;
	
	private final int _port;
	
	private final NioNetStackList<SendablePacket<T>> _sendQueue;
	
	//private SendablePacket<T> _closePacket;
	
	private ByteBuffer _readBuffer;
	
	private ByteBuffer _primaryWriteBuffer;
	
	private ByteBuffer _secondaryWriteBuffer;
	
	private volatile boolean _pendingClose;
	
	private T _client;
	
	private int _lastWritePass;
	private ByteBuffer _tempBuffer;
	private boolean _pendingWrite = false;
	private ReentrantLock _writeLock;
	
	public MMOConnection(final Core<T> core, final BufferPool bufPool, final AsynchronousSocketChannel socket, InetAddress address, int port)
	{
		_core = core;
		_pool = bufPool;
		_socket = socket;
		_address = address;
		_port = port;
		
		_sendQueue = new NioNetStackList<>();
		
		_writeLock = new ReentrantLock();
	}
	
	final void setPendingWritting(boolean val)
	{
		_writeLock.lock();
		_pendingWrite = val;
		_writeLock.unlock();
	}
	
	final void setLastWritePassSize(int size)
	{
		_lastWritePass = size;
	}
	
	final int getLastWritePassSize()
	{
		return _lastWritePass;
	}
	
	final void setTempWriteBuffer(final ByteBuffer buf)
	{
		_tempBuffer = buf;
	}
	
	final ByteBuffer getTempWriteBuffer()
	{
		return _tempBuffer;
	}
	
	final void setClient(final T client)
	{
		_client = client;
	}
	
	public final T getClient()
	{
		return _client;
	}
	
	public final void sendPacket(final SendablePacket<T> sp)
	{
		sp._client = _client;
		
		if (_pendingClose)
			return;
		
		synchronized (getSendQueue())
		{
			_sendQueue.addLast(sp);
		}
		
		executeWriteTask(_core.getWriteCompletionHandler());
	}
	
	void executeWriteTask(AbstractWriteHandler<T> handler)
	{
		if (!_sendQueue.isEmpty())
		{
			try
			{
				_writeLock.lock();
				if(!_pendingWrite)
				{
					_core.executeWriteTask(new WriteParseTask<>(this, _core, handler));
					_pendingWrite = true;
				}
				_writeLock.unlock();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public final InetAddress getInetAddress()
	{
		return _address;
	}
	
	public final int getPort()
	{
		return _port;
	}
	
	final void close() throws Exception
	{
		_socket.close();
	}
	
	final void startReadTask(ReadHandler<T> handler) 
	{
		if(_readBuffer == null)
			_readBuffer = _pool.getPooledBuffer();
		
		try
		{
			_socket.read(_readBuffer, -1, TimeUnit.MILLISECONDS, this, handler);
		}
		catch(Exception e)
		{
			_client.onDisconnection();
			_core.closeConnectionImpl(this);
		}
	}
	
	final void startWriteTask(ByteBuffer buf, AbstractWriteHandler<T> handler)
	{
		_socket.write(buf, -1, TimeUnit.MILLISECONDS, this, handler);
	}
	
	final void createWriteBuffer(final ByteBuffer buf)
	{
		if (_primaryWriteBuffer == null)
		{
			_primaryWriteBuffer = _pool.getPooledBuffer();
			_primaryWriteBuffer.put(buf);
		}
		else
		{
			final ByteBuffer temp = _pool.getPooledBuffer();
			temp.put(buf);
			
			final int remaining = temp.remaining();
			_primaryWriteBuffer.flip();
			final int limit = _primaryWriteBuffer.limit();
			
			if (remaining >= _primaryWriteBuffer.remaining())
			{
				temp.put(_primaryWriteBuffer);
				_pool.recycleBuffer(_primaryWriteBuffer);
				_primaryWriteBuffer = temp;
			}
			else
			{
				_primaryWriteBuffer.limit(remaining);
				temp.put(_primaryWriteBuffer);
				_primaryWriteBuffer.limit(limit);
				_primaryWriteBuffer.compact();
				_secondaryWriteBuffer = _primaryWriteBuffer;
				_primaryWriteBuffer = temp;
			}
		}
	}
	
	final boolean hasPendingWriteBuffer()
	{
		return _primaryWriteBuffer != null;
	}
	
	final void movePendingWriteBufferTo(final ByteBuffer dest)
	{
		_primaryWriteBuffer.flip();
		dest.put(_primaryWriteBuffer);
		_pool.recycleBuffer(_primaryWriteBuffer);
		_primaryWriteBuffer = _secondaryWriteBuffer;
		_secondaryWriteBuffer = null;
	}
	
	final void setReadBuffer(final ByteBuffer buf)
	{
		_readBuffer = buf;
	}
	
	final ByteBuffer getReadBuffer()
	{
		return _readBuffer;
	}
	
	public final boolean isClosed()
	{
		return _pendingClose;
	}
	
	final NioNetStackList<SendablePacket<T>> getSendQueue()
	{
		return _sendQueue;
	}
	
	/*final SendablePacket<T> getClosePacket()
	{
	    return _closePacket;
	}*/
	
	@SuppressWarnings("unchecked")
	public final void close(final SendablePacket<T> sp)
	{
		
		close(new SendablePacket[] { sp });
	}
	
	public final void close(final SendablePacket<T>[] closeList)
	{
		if (_pendingClose)
			return;
		
		synchronized (getSendQueue())
		{
			if (!_pendingClose)
			{
				_pendingClose = true;
				_sendQueue.clear();
				for (SendablePacket<T> sp : closeList)
					_sendQueue.addLast(sp);
			}
		}
		
		try
		{
			executeWriteTask(_core.getCloserCompletionHandler());
		}
		catch (Exception e)
		{
			_core.closeConnectionImpl(this);
		}
	}
	
	final void releaseBuffers()
	{
		if (_primaryWriteBuffer != null)
		{
			_pool.recycleBuffer(_primaryWriteBuffer);
			_primaryWriteBuffer = null;
			
			if (_secondaryWriteBuffer != null)
			{
				_pool.recycleBuffer(_secondaryWriteBuffer);
				_secondaryWriteBuffer = null;
			}
		}
		
		if (_readBuffer != null)
		{
			_pool.recycleBuffer(_readBuffer);
			_readBuffer = null;
		}
		
		if(_tempBuffer != null)
		{
			_pool.recycleNativeBuffer(_tempBuffer);
			_tempBuffer = null;
		}
	}
	
	AsynchronousSocketChannel getSocket()
	{
		return _socket;
	}
}
