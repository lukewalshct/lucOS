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

	private List<UserKernel.PageFrame> _freeMemory;
	
	private Lock _freeMemLock;
	
	public VMBackgroundMemMgr(List<UserKernel.PageFrame> freeMemory, Lock freeMemLock)
	{
		this._freeMemory = freeMemory;
		
		this._freeMemLock = freeMemLock;
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
			
			//TODO: manage mem
			
			Lib.debug('f', "VM mem manager yielding");
			
			KThread.yield();					
		}			
	}
}
