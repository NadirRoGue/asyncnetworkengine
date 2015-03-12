package org.mmocore.network;

/**
 * @author Nadir Román Guerrero
 * @param <T>
 */
public final class ConnectionAcceptor<T extends MMOClient<?>> implements Runnable
{
	private final Core<T> core;
	private final ConnectionManager<T> manager;
	
	ConnectionAcceptor(final Core<T> coreRef, final ConnectionManager<T> man)
	{
		core = coreRef;
		manager = man;
	}
	
	@Override
	public void run()
	{
		while(!core.isShutDown())
		{
			try
			{
				manager.tryAcceptConnection();
			}
			catch(Exception e)
			{
				
			}
		}
	}	
}
