package nachos.threads;

import nachos.machine.*;

/*
 * Contains some common/utility methods common to several
 * Kernel test classes.
 */
public class KernelTestBase {

	/*
	 * Runs all threads in an array of threads.
	 */
	protected void runThreads(KThread[] threads)
	{
		if(threads == null) return;
		
		boolean intStatus = Machine.interrupt().disable();
		
		for(int i = 0; i < threads.length; i++)
		{
			threads[i].fork();
		}
		
		Machine.interrupt().restore(intStatus);
	}
	
	/*
	 * Joins on all threads in the array.
	 */
	protected void joinThreads(KThread[] threads) throws InterruptedException
	{
		if(threads == null) return;
		
		for(int i = 0; i< threads.length; i++)
		{
			threads[i].join();
		}
	}
}
