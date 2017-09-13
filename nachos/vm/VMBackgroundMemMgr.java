package nachos.vm;

import nachos.threads.*;
import nachos.machine.*;
import nachos.vm.*;
import nachos.userprog.*;

import java.util.*;

/**
 * A class meant to be run as a background thread that manages
 * the free memory list for the VM Kernel. Ensures that there
 * is at least N number of free memory pages availalbe. If 
 * processes need more memory, they must wait until this 
 * manager replenishes the free memory list.
 * @author luke
 *
 */
public class VMBackgroundMemMgr implements Runnable {

	private List<PageFrame> _freeMemory;
	
	private Lock _freeMemLock;
	
	private ICondition _freeMemAvailable;
	
	private VMKernel _kernel;
	
	private static final int MIN_FREE_PAGES = 4;
	
	public VMBackgroundMemMgr(VMKernel kernel, List<PageFrame> freeMemory, 
			Lock freeMemLock, ICondition freeMemAvailable)
	{
		this._kernel = kernel;
		
		this._freeMemory = freeMemory;
		
		this._freeMemLock = freeMemLock;
		
		this._freeMemAvailable = freeMemAvailable;
	}
	
	public void run()
	{
		Lib.debug('f', "VM BACKGROUND MEM MGR THREAD STARTING");
		
		manageFreeMemory();
		
		Lib.debug('f', "VM BACKGROUND MEM MGR THREAD TERMINATING");
	}
	
	public void manageFreeMemory()
	{
		while(true){ 
			
			Lib.debug('f', "VM mem manager checking free memory...");
			
			//enter critical section
			this._freeMemLock.acquire();
			
			try
			{
				//ensure that there's at least MIN_FREE_PAGES available
				while(this._freeMemory.size() < MIN_FREE_PAGES)
				{
					Lib.debug('f', "Not enough free mem available (" + this._freeMemory.size() +
						" pages), freeing up memory...");
					
					int pageSize = Machine.processor().pageSize;
					
					int physPageNum = this._kernel.evictPage();
					
					PageFrame freeFrame = new PageFrame();
					
					freeFrame.startIndex = physPageNum * pageSize;
					
					freeFrame.endIndex = (physPageNum * pageSize) + pageSize -1;
					
					this._freeMemory.add(freeFrame);					
							
					this._kernel.setPageNotInUse(physPageNum);
					
					Lib.debug('f', "Freed page frame and added to free mem list (# free = " +
						this._freeMemory.size() + ")");
				}
				
				Lib.debug('f', "Free memory sufficient (" + this._freeMemory.size() + " pages)");
			}
			finally
			{
				//exit critical section
				this._freeMemLock.release();
			}			
			
			Lib.debug('f', "VM mem manager yielding");
			
			KThread.yield();					
		}			
	}
}
