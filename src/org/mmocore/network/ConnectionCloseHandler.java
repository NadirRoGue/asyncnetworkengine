package org.mmocore.network;

/**
 * @author BiggBoss
 * @param <T> 
 */
public final class ConnectionCloseHandler<T extends MMOClient<?>> extends AbstractWriteHandler<T>
{
	ConnectionCloseHandler(final Core<T> corePtr)
	{
		super(corePtr);
	}
	
	/* (non-Javadoc)
	 * @see java.nio.channels.CompletionHandler#completed(java.lang.Object, java.lang.Object)
	 */
	@Override
	public void completed(Integer result, MMOConnection<T> con)
	{
		//core.closeConnectionImpl(con);
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.CompletionHandler#failed(java.lang.Throwable, java.lang.Object)
	 */
	@Override
	public void failed(Throwable t, MMOConnection<T> con)
	{
		// log t somewhere
		//core.closeConnectionImpl(con);
	}
	
}
