package org.mmocore.network;

import java.nio.ByteBuffer;

/**
 * @author BiggBoss
 * @param <T>
 */
public final class WriteParseTask<T extends MMOClient<?>> implements Runnable
{
	public static int MAX_SEND_PER_PASS;
	public static int HEADER_SIZE;
	
	private final MMOConnection<T> con;
	private final Core<T> core;
	private final AbstractWriteHandler<T> handler;
	private final ByteBuffer DIRECT_WRITE_BUFFER;
	private final ByteBuffer WRITE_BUFFER;
	
	WriteParseTask(final MMOConnection<T> conn, final Core<T> corePtr, AbstractWriteHandler<T> abstractHandler)
	{
		con = conn;
		core = corePtr;
		handler = abstractHandler;
		
		DIRECT_WRITE_BUFFER = core.getBufferPool().getPooledNativeBuffer();
		WRITE_BUFFER = core.getBufferPool().getPooledBuffer();
	}
	
	WriteParseTask(final MMOConnection<T> conn, final Core<T> corePtr)
	{
		this(conn, corePtr,  corePtr.getWriteCompletionHandler());
	}
	
	@Override
	public void run()
	{
		try
		{
			writePacket(con);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	void writePacket(MMOConnection<T> con)
	{
		if (!prepareWriteBuffer(con))
		{
			return;
		}
		
		DIRECT_WRITE_BUFFER.flip();
		
		final int size = DIRECT_WRITE_BUFFER.remaining();
		
		try
		{
			con.setLastWritePassSize(size);
			con.setTempWriteBuffer(DIRECT_WRITE_BUFFER);
			con.startWriteTask(DIRECT_WRITE_BUFFER, handler);
		}
		catch (Exception e)
		{
			con.getClient().onForcedDisconnection();
			core.closeConnectionImpl(con);
		}
		
		
	}
	
	private final boolean prepareWriteBuffer(final MMOConnection<T> con)
	{
		boolean hasPending = false;
		DIRECT_WRITE_BUFFER.clear();
		
		// if there is pending content add it
		if (con.hasPendingWriteBuffer())
		{
			con.movePendingWriteBufferTo(DIRECT_WRITE_BUFFER);
			hasPending = true;
		}
		
		if (DIRECT_WRITE_BUFFER.remaining() > 1 && !con.hasPendingWriteBuffer())
		{
			final NioNetStackList<SendablePacket<T>> sendQueue = con.getSendQueue();
			final T client = con.getClient();
			SendablePacket<T> sp;
			
			for (int i = 0; i < MAX_SEND_PER_PASS; i++)
			{
				synchronized (con.getSendQueue())
				{
					if (sendQueue.isEmpty())
						sp = null;
					else
						sp = sendQueue.removeFirst();
				}
				
				if (sp == null)
					break;
				
				hasPending = true;
				
				// put into WriteBuffer
				putPacketIntoWriteBuffer(client, sp);
				
				WRITE_BUFFER.flip();
				
				if (DIRECT_WRITE_BUFFER.remaining() >= WRITE_BUFFER.limit())
				{
					DIRECT_WRITE_BUFFER.put(WRITE_BUFFER);
				}
				else
				{
					con.createWriteBuffer(WRITE_BUFFER);
					break;
				}
				
				core.getBufferPool().recycleBuffer(WRITE_BUFFER);
			}
		}
		return hasPending;
	}
	
	private final void putPacketIntoWriteBuffer(final T client, final SendablePacket<T> sp)
	{
		WRITE_BUFFER.clear();
		
		// reserve space for the size
		final int headerPos = WRITE_BUFFER.position();
		final int dataPos = headerPos + HEADER_SIZE;
		WRITE_BUFFER.position(dataPos);
		
		// set the write buffer
		sp._buf = WRITE_BUFFER;
		// write content to buffer
		sp.write();
		// delete the write buffer
		sp._buf = null;
		
		// size (inclusive header)
		int dataSize = WRITE_BUFFER.position() - dataPos;
		WRITE_BUFFER.position(dataPos);
		client.encrypt(WRITE_BUFFER, dataSize);
		
		// recalculate size after encryption
		dataSize = WRITE_BUFFER.position() - dataPos;
		
		WRITE_BUFFER.position(headerPos);
		// write header
		WRITE_BUFFER.putShort((short) (dataSize + HEADER_SIZE));
		WRITE_BUFFER.position(dataPos + dataSize);
	}
}
