package org.mmocore.network;

import java.nio.channels.CompletionHandler;

/**
 * @author Nadir Román Guerrero
 * @param <T> 
 */
public final class ReadHandler<T extends MMOClient<?>> implements CompletionHandler<Integer, MMOConnection<T>>
{
	private Core<T> core;
	
	ReadHandler(Core<T> corePtr)
	{
		core = corePtr;
	}
	
	@Override
	public void completed(Integer read, MMOConnection<T> con)
	{
		core.executeReadTask(new ReadParseTask<>(core, con, read));
	}

	@Override
	public void failed(Throwable t, MMOConnection<T> con)
	{
		// Second part of read result = -2
		con.getClient().onForcedDisconnection();
		core.closeConnectionImpl(con);
	}
	
	
}
