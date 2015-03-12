package org.mmocore.network;

/**
 * @author BiggBoss
 * @param <T>
 */
public final class WriteHandler<T extends MMOClient<?>> extends AbstractWriteHandler<T>
{
	WriteHandler(final Core<T> corePtr)
	{
		super(corePtr);
	}
	
	@Override
	public void completed(Integer result, MMOConnection<T> con)
	{
		// check if no error happened
		if (result >= 0)
		{
			// check if we written everything
			if (result != con.getLastWritePassSize())
			{
				// incomplete write
				con.createWriteBuffer(con.getTempWriteBuffer());
			}
			
			core.getBufferPool().recycleNativeBuffer(con.getTempWriteBuffer());
			con.setTempWriteBuffer(null);
			con.setPendingWritting(false);
			con.executeWriteTask(this);
		}
		else
		{
			con.getClient().onForcedDisconnection();
			core.closeConnectionImpl(con);
		}
	}

	@Override
	public void failed(Throwable t, MMOConnection<T> con)
	{
		con.getClient().onForcedDisconnection();
		core.closeConnectionImpl(con);
	}
}
