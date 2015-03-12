/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mmocore.network;

import java.nio.channels.CompletionHandler;

/**
 * @author Nadir Román Guerrero
 * @param <T> 
 */
public abstract class AbstractWriteHandler<T extends MMOClient<?>> implements CompletionHandler<Integer, MMOConnection<T>>
{
	protected final Core<T> core;
	
	AbstractWriteHandler(final Core<T> coreRef)
	{
		core = coreRef;
	}
}
