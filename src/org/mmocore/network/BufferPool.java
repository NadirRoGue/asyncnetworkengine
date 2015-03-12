package org.mmocore.network;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;

/**
 * @author Nadir Román Guerrero
 */
final class BufferPool
{
	private final ByteOrder BYTE_ORDER;
	
	private final int nativeBufferPoolSize;
	private final int nativeBufferSize;
	private final int bufferPoolSize;
	private final int bufferSize;
	private final int stringBufferPoolSize;
	private final int stringBufferSize;
	
	private LinkedList<ByteBuffer> nativePool;
	private LinkedList<ByteBuffer> pool;
	private LinkedList<NioNetStringBuffer> stringPool;
	
	BufferPool(final ByteOrder byteOrder, final int nativePoolSize, final int nativeBufSize, final int poolSize, final int bufferSiz, 
			final int stringPoolSize, final int stringBufSize)
	{
		BYTE_ORDER = byteOrder;
		
		nativeBufferPoolSize = nativePoolSize;
		nativeBufferSize = nativeBufSize;
		
		bufferPoolSize = poolSize;
		bufferSize = bufferSiz;
		
		stringBufferPoolSize = stringPoolSize;
		stringBufferSize = stringBufSize;
		
		nativePool = new LinkedList<>();
		while(nativePool.size() < nativeBufferPoolSize)
		{
			nativePool.add(ByteBuffer.allocateDirect(nativeBufferSize).order(BYTE_ORDER));
		}
		
		pool = new LinkedList<>();
		while(pool.size() < bufferPoolSize)
		{
			pool.add(ByteBuffer.wrap(new byte[bufferSize]).order(BYTE_ORDER));
		}
		
		stringPool = new LinkedList<>();
		while(stringPool.size() < stringBufferPoolSize)
		{
			stringPool.add(new NioNetStringBuffer(stringBufferSize));
		}
	}
	
	ByteBuffer getPooledNativeBuffer()
	{
		ByteBuffer result = null;
		
		synchronized(nativePool)
		{
			if(!nativePool.isEmpty())
			{
				result = nativePool.poll();
			}
			else
				result = ByteBuffer.allocateDirect(nativeBufferSize).order(BYTE_ORDER); // should not happen. Set enough pool to avoid this point
		}
		
		return result;
	}
	
	void recycleNativeBuffer(ByteBuffer buffer)
	{
		if(buffer != null)
		{
			synchronized(nativePool)
			{
				if(nativePool.size() < nativeBufferPoolSize)
				{
					buffer.clear();
					nativePool.addLast(buffer);
				}
			}
		}
	}
	
	ByteBuffer getPooledBuffer()
	{
		ByteBuffer result = null;
		
		synchronized(pool)
		{
			if(!pool.isEmpty())
			{
				result = pool.poll();
			}
			else
				result = ByteBuffer.wrap(new byte[bufferSize]).order(BYTE_ORDER);
		}
		
		return result;
	}
	
	void recycleBuffer(ByteBuffer buffer)
	{
		if(buffer != null)
		{
			synchronized(pool)
			{
				if(pool.size() < bufferPoolSize)
				{
					buffer.clear();
					pool.addLast(buffer);
				}
			}
		}
	}
	
	NioNetStringBuffer getPooledStringBuffer()
	{
		NioNetStringBuffer result = null;
		
		synchronized(stringPool)
		{
			if(!stringPool.isEmpty())
				result = stringPool.poll();
			else
				result = new NioNetStringBuffer(stringBufferSize);
		}
		
		return result;
	}
	
	void recycleStringBuffer(NioNetStringBuffer stringBuffer)
	{
		if(stringBuffer != null)
		{
			synchronized(stringPool)
			{
				if(stringPool.size() < stringBufferPoolSize)
				{
					stringBuffer.clear();
					stringPool.addLast(stringBuffer);
				}
			}
		}
	}
}
