package org.mmocore.network;

import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * @author Nadir Román Guerrero
 * @param <T>
 */
public final class IteratingBlockingQueue<T>
{
	private LinkedList<T> queue;
	
	IteratingBlockingQueue()
	{
		queue = new LinkedList<>();
	}
	
	synchronized boolean isEmpty()
	{
		return queue.isEmpty();
	}
	
	/**
	 * Returns the next processing element
	 * @return T (next processing element)
	 * @throws NoSuchElementException 
	 */
	synchronized T next() throws NoSuchElementException
	{
		T t = queue.removeFirst();
		queue.addLast(t);
		return t;
	}
	
	synchronized void remove(T val)
	{
		queue.remove(val);
	}
}
