package org.mmocore.network;

import java.nio.ByteBuffer;

/**
 * @authors KenM, BiggBoss
 * @param <T>
 */
public final class ReadParseTask<T extends MMOClient<?>> implements Runnable
{
	public static int MAX_READ_PER_PASS;
	public static int HEADER_SIZE;
	
	private final Core<T> core;
	private final BufferPool pool;
	
	private final MMOConnection<T> con;
	private final int readBytes;
	
	ReadParseTask(final Core<T> coreRef, final MMOConnection<T> conn, final int read)
	{
		core = coreRef;
		con = conn;
		pool = core.getBufferPool();
		readBytes = read;
	}
	
	@Override
	public void run()
	{
		readPackets(readBytes, con);
		
		try
		{
			con.startReadTask(core.getReadCompletionHandler());
		}
		catch(Exception e)
		{
			con.getClient().onForcedDisconnection();
			core.closeConnectionImpl(con);
		}
	}
	
	private void readPackets(int result, MMOConnection<T> con)
	{
		if (!con.isClosed())
		{
			ByteBuffer buf = con.getReadBuffer();
			
			// if we try to to do a read with no space in the buffer it will
			// read 0 bytes
			// going into infinite loop
			if (buf.position() == buf.limit())
			{
				System.exit(0);
			}
			
			if (result > 0)
			{
				buf.flip();
				
				final T client = con.getClient();
				
				for (int i = 0; i < MAX_READ_PER_PASS; i++)
				{
					if (!tryReadPacket(client, buf, con))
					{
						return;
					}
				}
				
				// only reachable if MAX_READ_PER_PASS has been reached
				// check if there are some more bytes in buffer
				// and allocate/compact to prevent content lose.
				if (buf.remaining() > 0)
				{
					// move the first byte to the beginning :)
					buf.compact();
				}
				else
				{
					pool.recycleBuffer(buf);
					con.setReadBuffer(null);
				}
			}
			else
			{
				core.closeConnectionImpl(con);
			}
		}
	}
	
	private boolean tryReadPacket(T client, ByteBuffer buf, MMOConnection<T> con)
	{
		switch (buf.remaining())
		{
			case 0:
				// buffer is full
				// nothing to read
				return false;
			case 1:
				// we don`t have enough data for header so we need to read
				buf.compact();
				return false;
			default:
				// data size excluding header size :>
				final int dataPending = (buf.getShort() & 0xFFFF) - HEADER_SIZE;
				
				// do we got enough bytes for the packet?
				if (dataPending <= buf.remaining())
				{
					// avoid parsing dummy packets (packets without body)
					if (dataPending > 0)
					{
						final int pos = buf.position();
						parseClientPacket(pos, buf, dataPending, client);
						buf.position(pos + dataPending);
					}
					
					// if we are done with this buffer
					if (!buf.hasRemaining())
					{
						con.setReadBuffer(null);
						pool.recycleBuffer(buf);
						return false;
					}
					return true;
				}
				
				// we don`t have enough bytes for the dataPacket so we need
				// to read
				buf.position(buf.position() - HEADER_SIZE);
				buf.compact();
				return false;
		}
	}
	
	private void parseClientPacket(int pos, ByteBuffer buf, int dataSize, T client)
	{
		final boolean ret = client.decrypt(buf, dataSize);
		
		if (ret && buf.hasRemaining())
		{
			// apply limit
			final int limit = buf.limit();
			buf.limit(pos + dataSize);
			final ReceivablePacket<T> cp = core.handlePacket(buf, client);
			
			if (cp != null)
			{
				cp._buf = buf;
				cp._sbuf = pool.getPooledStringBuffer();
				cp._client = client;
				
				if (cp.read())
				{
					core.executePacket(cp);
				}
				
				pool.recycleStringBuffer(cp._sbuf);
				
				cp._buf = null;
				cp._sbuf = null;
			}
			buf.limit(limit);
		}
	}
}
